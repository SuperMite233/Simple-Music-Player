package com.supermite.smp.stream

import com.supermite.smp.data.SubsonicServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0
)

data class SubsonicAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val songCount: Int = 0,
    val durationSeconds: Long = 0,
    val coverArt: String = "",
    val year: Int = 0,
    val starred: Boolean = false
)

data class SubsonicSong(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val album: String = "",
    val albumId: String = "",
    val durationSeconds: Long = 0,
    val track: Int = 0,
    val year: Int = 0,
    val contentType: String = "",
    val suffix: String = "",
    val coverArt: String = "",
    val starred: Boolean = false,
    val userRating: Int = 0,
    val size: Long = 0
)

data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val owner: String = "",
    val songCount: Int = 0,
    val durationSeconds: Long = 0,
    val coverArt: String = "",
    val comment: String = "",
    val public: Boolean = false
)

enum class SubsonicTranscodeMode(val label: String, val maxBitRate: Int?) {
    LOSSLESS("无损", null),
    KBPS_320("320 kbps", 320),
    KBPS_192("192 kbps", 192),
    KBPS_128("128 kbps", 128),
    KBPS_96("96 kbps", 96)
}

class SubsonicClient(
    private val server: SubsonicServer,
    private val weakNetworkMode: Boolean = false
) {
    private val baseUrl = buildBaseUrl(server.url, server.port)
    private val httpClient = buildHttpClient(server.ignoreCert)
    private val random = SecureRandom()

    fun ping(): Result<String> = runCatching {
        val root = call("ping")
        checkStatus(root)
        "连接成功"
    }

    fun search(query: String): SubsonicSearchResult {
        val root = call("search3", mapOf("query" to query, "artistCount" to "20", "albumCount" to "30", "songCount" to "60"))
        val result = root.optJSONObject("searchResult3") ?: JSONObject()
        return SubsonicSearchResult(
            artists = jsonArray(result, "artist").mapNotNull { parseArtist(it) },
            albums = jsonArray(result, "album").mapNotNull { parseAlbum(it) },
            songs = jsonArray(result, "song").mapNotNull { parseSong(it) }
        )
    }

    fun randomSongs(size: Int = 4): List<SubsonicSong> {
        val root = call("getRandomSongs", mapOf("size" to size.coerceIn(1, 500).toString()))
        val songs = root.optJSONObject("randomSongs") ?: JSONObject()
        return jsonArray(songs, "song").mapNotNull { parseSong(it) }
    }

    fun artists(): List<SubsonicArtist> {
        val root = call("getArtists")
        val artistsRoot = root.optJSONObject("artists") ?: JSONObject()
        val result = mutableListOf<SubsonicArtist>()
        jsonArray(artistsRoot, "index").forEach { index ->
            result += jsonArray(index, "artist").mapNotNull { parseArtist(it) }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    fun albums(type: String = "alphabeticalByName", size: Int = 80, offset: Int = 0): List<SubsonicAlbum> {
        val root = call("getAlbumList2", mapOf("type" to type, "size" to size.toString(), "offset" to offset.toString()))
        val list = root.optJSONObject("albumList2") ?: JSONObject()
        return jsonArray(list, "album").mapNotNull { parseAlbum(it) }
    }

    fun favoriteSongs(): List<SubsonicSong> {
        val root = call("getStarred2")
        val starred = root.optJSONObject("starred2") ?: JSONObject()
        return jsonArray(starred, "song").mapNotNull { parseSong(it) }
    }

    fun playlists(): List<SubsonicPlaylist> {
        val root = call("getPlaylists")
        val playlists = root.optJSONObject("playlists") ?: JSONObject()
        return jsonArray(playlists, "playlist").mapNotNull { parsePlaylist(it) }
            .sortedBy { it.name.lowercase() }
    }

    fun playlistSongs(playlistId: String): Pair<SubsonicPlaylist?, List<SubsonicSong>> {
        val root = call("getPlaylist", mapOf("id" to playlistId))
        val playlist = root.optJSONObject("playlist") ?: JSONObject()
        val info = parsePlaylist(playlist)
        val songs = jsonArray(playlist, "entry").mapNotNull { parseSong(it) }
        return info to songs
    }

    fun albumSongs(albumId: String): List<SubsonicSong> {
        val root = call("getAlbum", mapOf("id" to albumId))
        val album = root.optJSONObject("album") ?: JSONObject()
        return jsonArray(album, "song").mapNotNull { parseSong(it) }
    }

    fun artistAlbums(artistId: String): List<SubsonicAlbum> {
        val root = call("getArtist", mapOf("id" to artistId))
        val artist = root.optJSONObject("artist") ?: JSONObject()
        return jsonArray(artist, "album").mapNotNull { parseAlbum(it) }
    }

    fun streamUrl(songId: String, mode: SubsonicTranscodeMode): String {
        val params = mutableListOf("id" to songId)
        mode.maxBitRate?.let { params += "maxBitRate" to it.toString() }
        return endpointUrl("stream", params)
    }

    fun downloadBytes(songId: String, mode: SubsonicTranscodeMode, onProgress: ((Long, Long) -> Unit)? = null): ByteArray {
        val request = Request.Builder().url(streamUrl(songId, mode)).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: return ByteArray(0)
            val total = body.contentLength()
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val out = java.io.ByteArrayOutputStream()
                var readTotal = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    readTotal += read
                    onProgress?.invoke(readTotal, total)
                }
                return out.toByteArray()
            }
        }
    }

    fun coverArtUrl(coverArt: String): String {
        if (coverArt.isBlank()) return ""
        return endpointUrl("getCoverArt", listOf("id" to coverArt))
    }

    fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    fun createPlaylist(name: String, songIds: List<String> = emptyList()): Boolean {
        val params = mutableListOf("name" to name)
        songIds.forEach { id -> params += "songId" to id }
        val result = call("createPlaylist", params)
        return result.optString("status") == "ok"
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        call("updatePlaylist", listOf("playlistId" to playlistId, "songIdToAdd" to songId))
    }

    fun starSong(songId: String, star: Boolean) {
        call(if (star) "star" else "unstar", mapOf("id" to songId))
    }

    fun setRating(songId: String, rating: Int) {
        call("setRating", mapOf("id" to songId, "rating" to rating.coerceIn(0, 5).toString()))
    }

    fun scrobble(songId: String, submission: Boolean) {
        runCatching { call("scrobble", mapOf("id" to songId, "submission" to submission.toString())) }
    }

    private fun call(method: String, params: Map<String, String> = emptyMap()): JSONObject {
        return call(method, params.entries.map { it.key to it.value })
    }

    private fun call(method: String, params: List<Pair<String, String>>): JSONObject {
        val request = Request.Builder().url(endpointUrl(method, params)).get().build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(240)}")
            val root = JSONObject(text).optJSONObject("subsonic-response") ?: JSONObject(text)
            checkStatus(root)
            return root
        }
    }

    private fun checkStatus(root: JSONObject) {
        if (!root.optString("status").equals("failed", ignoreCase = true)) return
        val error = root.optJSONObject("error")
        error("Subsonic ${error?.optInt("code") ?: 0}: ${error?.optString("message").orEmpty()}")
    }

    private fun endpointUrl(method: String, params: List<Pair<String, String>>): String {
        val all = buildList {
            addAll(authParams().entries.map { it.key to it.value })
            addAll(params)
        }
        val query = all.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
        return "${baseUrl.trimEnd('/')}/rest/${method.removeSuffix(".view")}.view?$query"
    }

    private fun authParams(): Map<String, String> {
        val salt = randomSalt()
        return mapOf(
            "u" to server.username,
            "t" to md5Hex(server.password + salt),
            "s" to salt,
            "v" to API_VERSION,
            "c" to CLIENT_ID,
            "f" to "json"
        )
    }

    private fun parseArtist(obj: JSONObject): SubsonicArtist? {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        return SubsonicArtist(id, obj.optString("name"), obj.optInt("albumCount", 0))
    }

    private fun parseAlbum(obj: JSONObject): SubsonicAlbum? {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        return SubsonicAlbum(
            id = id,
            title = obj.optString("name").ifBlank { obj.optString("title") },
            artist = obj.optString("artist"),
            artistId = obj.optString("artistId"),
            songCount = obj.optInt("songCount", 0),
            durationSeconds = obj.optLong("duration", 0),
            coverArt = obj.optString("coverArt"),
            year = obj.optInt("year", 0),
            starred = obj.has("starred")
        )
    }

    private fun parseSong(obj: JSONObject): SubsonicSong? {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        return SubsonicSong(
            id = id,
            title = obj.optString("title").ifBlank { obj.optString("name") },
            artist = obj.optString("artist"),
            artistId = obj.optString("artistId"),
            album = obj.optString("album"),
            albumId = obj.optString("albumId"),
            durationSeconds = obj.optLong("duration", 0),
            track = obj.optInt("track", 0),
            year = obj.optInt("year", 0),
            contentType = obj.optString("contentType"),
            suffix = obj.optString("suffix"),
            coverArt = obj.optString("coverArt"),
            starred = obj.has("starred"),
            userRating = obj.optInt("userRating", 0),
            size = obj.optLong("size", 0)
        )
    }

    private fun parsePlaylist(obj: JSONObject): SubsonicPlaylist? {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        return SubsonicPlaylist(
            id = id,
            name = obj.optString("name"),
            owner = obj.optString("owner"),
            songCount = obj.optInt("songCount", 0),
            durationSeconds = obj.optLong("duration", 0),
            coverArt = obj.optString("coverArt"),
            comment = obj.optString("comment"),
            public = obj.optBoolean("public", false)
        )
    }

    private fun jsonArray(parent: JSONObject, key: String): List<JSONObject> {
        val value = parent.opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> listOf(value)
            else -> emptyList()
        }
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun buildBaseUrl(serverUrl: String, port: Int): String {
        val url = serverUrl.trimEnd('/')
        if (port <= 0) return url
        return runCatching {
            val parsed = URL(url)
            val path = if (parsed.path.isNullOrBlank()) "" else parsed.path.trimEnd('/')
            "${parsed.protocol}://${parsed.host}:$port$path"
        }.getOrDefault(url)
    }

    private fun buildHttpClient(ignoreCert: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(if (weakNetworkMode) 30L else 15L, TimeUnit.SECONDS)
            .readTimeout(if (weakNetworkMode) 180L else 60L, TimeUnit.SECONDS)
            .writeTimeout(if (weakNetworkMode) 180L else 60L, TimeUnit.SECONDS)
            .callTimeout(if (weakNetworkMode) 240L else 90L, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
        if (!ignoreCert) return builder.build()

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    companion object {
        private const val API_VERSION = "1.16.1"
        private const val CLIENT_ID = "SMP"
    }
}

data class SubsonicSearchResult(
    val artists: List<SubsonicArtist> = emptyList(),
    val albums: List<SubsonicAlbum> = emptyList(),
    val songs: List<SubsonicSong> = emptyList()
)
