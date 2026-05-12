package com.supermite.smp.stream

import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class WebDavItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: String = ""
)

class WebDavClient(
    private val serverUrl: String,
    private val username: String = "",
    private val password: String = "",
    private val port: Int = 0,
    private val ignoreCert: Boolean = false
) {
    private val baseUrl: String = buildBaseUrl(serverUrl, port)
    private val httpClient: OkHttpClient = buildHttpClient(ignoreCert)

    private var digestNonce: String? = null
    private var digestRealm: String? = null
    private var digestQop: String? = null
    private var digestOpaque: String? = null
    private var digestAlgorithm: String? = null
    private var digestNc: Int = 0

    fun listDirectory(path: String = ""): List<WebDavItem> {
        val cleanPath = path.trim('/')
        val encodedPath = encodePath(cleanPath)
        val url = if (cleanPath.isBlank()) {
            ensureTrailingSlash(baseUrl)
        } else {
            ensureTrailingSlash("${baseUrl.trimEnd('/')}/$encodedPath")
        }

        val (_, body, _) = doDavRequest(url, "PROPFIND", "1", """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:resourcetype/>
  </D:prop>
</D:propfind>""")
        return parsePropfindResponse(body, path)
    }

    fun download(path: String): ByteArray {
        val cleanPath = path.trimStart('/')
        val url = "${baseUrl.trimEnd('/')}/${encodePath(cleanPath)}"
        val result = executeDavRequestWithAuthNegotiation(url, "GET", null, null, withAuth = true)
        if (result.code in 200..299) {
            return result.bodyBytes ?: result.body.toByteArray(Charsets.UTF_8)
        }
        throw Exception("HTTP ${result.code}${result.body.takeIf { it.isNotBlank() }?.let { ": ${it.take(300)}" } ?: ""}")
    }

    fun testConnection(): Result<String> {
        return runCatching {
            val requestBody = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop><D:displayname/><D:resourcetype/></D:prop>
</D:propfind>"""

            val testUrl = ensureTrailingSlash(baseUrl)
            val hasAuth = username.isNotBlank() && password.isNotBlank()
            val (code, body, responseHeaders) = doDavRequest(testUrl, "PROPFIND", "0", requestBody, withAuth = hasAuth)

            if (code in 200..299 || code == 207) {
                return Result.success("连接成功")
            }

            val authHeader = getHeaderIgnoreCase(responseHeaders, "WWW-Authenticate").orEmpty()
            val location = getHeaderIgnoreCase(responseHeaders, "Location").orEmpty()
            logDebug(
                "响应: HTTP $code" +
                    if (authHeader.isNotBlank()) " Auth=$authHeader" else "" +
                    if (location.isNotBlank()) " Location=$location" else "" +
                    if (body.isNotBlank()) " Body=${body.take(300)}" else ""
            )

            throw Exception(
                when (code) {
                    401 -> "认证失败 (HTTP 401)，请检查用户名、密码或服务器认证方式"
                    403 -> "服务器拒绝访问 (HTTP 403)，如果账号密码确认正确，通常是 WebDAV 地址不对、该路径禁止 PROPFIND，或服务端禁止当前 User-Agent"
                    in 300..399 -> "服务器返回重定向 HTTP $code${if (location.isNotBlank()) " -> $location" else ""}，请改用重定向后的 WebDAV 地址"
                    else -> "服务器返回 HTTP $code"
                }
            )
        }
    }

    fun streamHeaders(): Map<String, String> {
        val auth = runCatching { buildAuthHeader("GET", URL(ensureTrailingSlash(baseUrl))) }.getOrDefault("")
        return mapOf("User-Agent" to USER_AGENT) + if (auth.isNotBlank()) mapOf("Authorization" to auth) else emptyMap()
    }

    fun findCoverImage(dirPath: String): String? {
        val items = runCatching { listDirectory(dirPath) }.getOrElse { return null }
        val coverNames = listOf("cover", "album", "folder", "artwork", "front")
        val imageExts = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
        val images = items.filter { item ->
            !item.isDirectory && imageExts.any { item.name.lowercase().endsWith(it) }
        }
        if (images.isEmpty()) return null
        val preferred = images.firstOrNull { img ->
            coverNames.any { img.name.lowercase().substringBeforeLast('.').contains(it) }
        }
        val selected = preferred ?: images.first()
        return "${baseUrl.trimEnd('/')}/${encodePath("${dirPath.trimEnd('/')}/${selected.path.substringAfterLast('/')}")}"
    }

    private data class DavResponse(
        val code: Int,
        val body: String,
        val headers: Map<String, String>,
        val bodyBytes: ByteArray? = null
    )

    private fun doDavRequest(
        url: String,
        method: String,
        depth: String,
        body: String,
        withAuth: Boolean = true
    ): Triple<Int, String, Map<String, String>> {
        val result = executeDavRequestWithAuthNegotiation(url, method, depth, body, withAuth)
        return Triple(result.code, result.body, result.headers)
    }

    private fun executeDavRequestWithAuthNegotiation(
        url: String,
        method: String,
        depth: String?,
        body: String?,
        withAuth: Boolean
    ): DavResponse {
        if (!withAuth || username.isBlank() || password.isBlank()) {
            return executeDavRequest(url, method, depth, body, withAuth = false)
        }

        if (digestNonce != null) {
            val digestResult = executeDavRequest(url, method, depth, body, withAuth = true)
            if (digestResult.code != 401) return digestResult
            clearDigestState()
        }

        // First request must be unauthenticated, so Basic-only servers can challenge normally.
        val challenge = executeDavRequest(url, method, depth, body, withAuth = false)
        if (challenge.code in 200..299 || challenge.code == 207) return challenge

        val authHeader = getHeaderIgnoreCase(challenge.headers, "WWW-Authenticate").orEmpty()
        val location = getHeaderIgnoreCase(challenge.headers, "Location").orEmpty()
        logDebug(
            "认证探测: HTTP ${challenge.code}" +
                if (authHeader.isNotBlank()) " Auth=$authHeader" else " 无 WWW-Authenticate" +
                if (location.isNotBlank()) " Location=$location" else ""
        )

        if (authHeader.contains("Digest", ignoreCase = true)) {
            parseDigestChallenge(authHeader)
            return executeDavRequest(url, method, depth, body, withAuth = true)
        }

        if (authHeader.contains("Basic", ignoreCase = true) || challenge.code == 401) {
            return executeDavRequest(url, method, depth, body, withAuth = true)
        }

        return challenge
    }

    private fun executeDavRequest(
        url: String,
        method: String,
        depth: String?,
        body: String?,
        withAuth: Boolean
    ): DavResponse {
        val requestUrl = URL(url)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")

        if (depth != null) requestBuilder.header("Depth", depth)

        if (withAuth) {
            val auth = buildAuthHeader(method, requestUrl)
            if (auth.isNotBlank()) requestBuilder.header("Authorization", auth)
        }

        val requestBody = body?.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = requestBuilder.method(method.uppercase(), requestBody).build()

        logDebug("请求: ${request.method} ${request.url.encodedPath}${request.url.encodedQuery?.let { "?$it" } ?: ""}${if (withAuth) " with Authorization" else " without Authorization"}")

        httpClient.newCall(request).execute().use { response: Response ->
            val headersMap = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                val name = response.headers.name(i)
                val value = response.headers.value(i)
                headersMap[name] = if (headersMap.containsKey(name)) headersMap[name] + ", " + value else value
            }
            val code = response.code
            val responseBody = response.body

                if (method.equals("GET", ignoreCase = true) && code in 200..299) {
                    val bytes = responseBody?.bytes() ?: ByteArray(0)
                    return DavResponse(code, "", headersMap, bytes)
                }

                val text = responseBody?.string().orEmpty()
                return DavResponse(code, text, headersMap)
        }
    }

    private fun getHeaderIgnoreCase(headers: Map<String, String>, name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    private fun buildAuthHeader(method: String, url: URL): String {
        if (username.isBlank() || password.isBlank()) return ""
        return if (digestNonce != null) makeDigestAuth(method, url) else makeBasicAuth()
    }

    private fun makeBasicAuth(): String {
        // RFC 7617 allows UTF-8 when servers opt in, but most public WebDAV examples use ASCII.
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
    }

    private fun parseDigestChallenge(header: String) {
        val digestIndex = header.indexOf("Digest", ignoreCase = true)
        val digestPart = if (digestIndex >= 0) header.substring(digestIndex + "Digest".length) else header
        digestRealm = findDigestParam(digestPart, "realm")
        digestNonce = findDigestParam(digestPart, "nonce")
        digestQop = findDigestParam(digestPart, "qop")
        digestOpaque = findDigestParam(digestPart, "opaque")
        digestAlgorithm = findDigestParam(digestPart, "algorithm")
        digestNc = 0
    }

    private fun clearDigestState() {
        digestNonce = null
        digestRealm = null
        digestQop = null
        digestOpaque = null
        digestAlgorithm = null
        digestNc = 0
    }

    private fun findDigestParam(header: String, name: String): String? {
        return Regex("""$name\s*=\s*(?:"([^"]*)"|([^,\s]+))""", RegexOption.IGNORE_CASE)
            .find(header)
            ?.let { it.groupValues.getOrNull(1)?.takeIf { value -> value.isNotEmpty() } ?: it.groupValues.getOrNull(2) }
    }

    private fun makeDigestAuth(method: String, url: URL): String {
        val realm = digestRealm ?: return makeBasicAuth()
        val nonce = digestNonce ?: return makeBasicAuth()
        val algorithm = digestAlgorithm?.lowercase()?.trim().orEmpty()
        if (algorithm.isNotEmpty() && algorithm != "md5" && algorithm != "md5-sess") {
            logDebug("不支持的 Digest algorithm=$digestAlgorithm，尝试回退 Basic")
            return makeBasicAuth()
        }

        digestNc++
        val ncStr = digestNc.toString(16).padStart(8, '0')
        val cnonce = randomHex(16)
        val uri = requestUri(url)
        val selectedQop = digestQop
            ?.split(',')
            ?.map { it.trim().trim('"') }
            ?.firstOrNull { it.equals("auth", ignoreCase = true) }

        val ha1Base = md5Hex("$username:$realm:$password")
        val ha1 = if (algorithm == "md5-sess") md5Hex("$ha1Base:$nonce:$cnonce") else ha1Base
        val ha2 = md5Hex("${method.uppercase()}:$uri")
        val response = if (selectedQop != null) {
            md5Hex("$ha1:$nonce:$ncStr:$cnonce:$selectedQop:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }

        val parts = mutableListOf(
            "Digest username=\"$username\"",
            "realm=\"$realm\"",
            "nonce=\"$nonce\"",
            "uri=\"$uri\"",
            "response=\"$response\""
        )
        if (algorithm.isNotEmpty()) parts += "algorithm=$digestAlgorithm"
        if (selectedQop != null) {
            parts += "qop=$selectedQop"
            parts += "nc=$ncStr"
            parts += "cnonce=\"$cnonce\""
        }
        digestOpaque?.let { parts += "opaque=\"$it\"" }
        return parts.joinToString(", ")
    }

    private fun requestUri(url: URL): String {
        return buildString {
            append(if (url.path.isNullOrBlank()) "/" else url.path)
            if (!url.query.isNullOrBlank()) {
                append('?')
                append(url.query)
            }
        }
    }

    private fun parsePropfindResponse(xml: String, requestPath: String): List<WebDavItem> {
        val items = mutableListOf<WebDavItem>()

        // Match common namespace prefixes, not just D:. Some servers use d:, a:, or no fixed prefix.
        val responsePattern = Regex(
            """<[^:>]*:?response\b[^>]*>(.*?)</[^:>]*:?response>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val requestEncoded = requestPath.trim('/')

        responsePattern.findAll(xml).forEach { responseMatch ->
            val block = responseMatch.groupValues[1]
            val href = Regex("""<[^:>]*:?href\b[^>]*>(.*?)</[^:>]*:?href>""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.getOrNull(1)?.trim()?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return@forEach

            val name = href.trimEnd('/').substringAfterLast('/').ifBlank { href }
            if (name.isBlank()) return@forEach
            val hrefTrim = href.trim('/').trimStart('/')
            if (hrefTrim == requestEncoded) return@forEach

            val isDir = Regex("""<[^:>]*:?collection\b[^>]*/>|<[^:>]*:?collection\b[^>]*>\s*</[^:>]*:?collection>""", RegexOption.IGNORE_CASE).containsMatchIn(block)
            val size = Regex("""<[^:>]*:?getcontentlength\b[^>]*>(\d+)</[^:>]*:?getcontentlength>""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val lastModified = Regex("""<[^:>]*:?getlastmodified\b[^>]*>(.*?)</[^:>]*:?getlastmodified>""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()

            items += WebDavItem(
                name = name,
                path = href.trimStart('/'),
                isDirectory = isDir,
                size = size,
                lastModified = lastModified
            )
        }
        return items.sortedWith(compareBy<WebDavItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    companion object {
        // Do not pretend to be curl here. Some public servers/WAFs have curl-specific rules.
        private const val USER_AGENT = "SMP-WebDavClient/1.0"

        var debugLogger: ((String) -> Unit)? = null

        private fun logDebug(msg: String) {
            debugLogger?.invoke(msg)
        }

        private fun encodePath(path: String): String {
            return try {
                path.split('/').joinToString("/") { segment ->
                    java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                }
            } catch (_: Exception) { path }
        }

        private fun buildBaseUrl(serverUrl: String, port: Int): String {
            val url = serverUrl.trimEnd('/')
            if (port <= 0) return url
            return try {
                val u = URL(url)
                val protocol = u.protocol
                val host = u.host
                val path = if (u.path.isNullOrBlank()) "/" else u.path
                val query = if (u.query != null) "?${u.query}" else ""
                "$protocol://$host:$port$path$query".trimEnd('/')
            } catch (e: Exception) {
                url
            }
        }

        private fun ensureTrailingSlash(url: String): String {
            return if (url.endsWith('/')) url else "$url/"
        }

        private fun md5Hex(input: String): String {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }

        private fun randomHex(length: Int): String {
            val bytes = ByteArray(length / 2 + 1)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }.take(length)
        }

        private fun buildHttpClient(ignoreCert: Boolean): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)

            if (ignoreCert) {
                val trustManager = trustAllManager()
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                }
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true }
            }
            return builder.build()
        }

        private fun trustAllManager(): X509TrustManager {
            return object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        }
    }
}
