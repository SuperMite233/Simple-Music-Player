package com.supermite.smp.stream

import android.net.Uri
import com.supermite.smp.data.Track
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class DizzylabClient(private val cookie: String) {
    fun discoverMusicUrl(savedUserId: String): Pair<String, String> {
        if (savedUserId.isValidUserId()) {
            return savedUserId to "$BASE/u/$savedUserId/music"
        }

        val pages = listOf(BASE, "$BASE/albums", "$BASE/u/")
        pages.forEach { pageUrl ->
            val html = runCatching { get(pageUrl) }.getOrNull() ?: return@forEach
            findProfileUserId(html)?.let { userId ->
                return userId to "$BASE/u/$userId/music"
            }
        }

        error("无法识别 DizzyLab 个人信息地址，请确认已登录后再加载。")
    }

    fun albums(musicUrl: String, query: String = ""): List<StreamAlbum> {
        val startUrl = if (query.isBlank()) musicUrl else musicUrl.trimEnd('/') + "/?q=" + URLEncoder.encode(query, "UTF-8")
        val allAlbums = mutableListOf<StreamAlbum>()
        var currentUrl: String? = startUrl
        while (currentUrl != null) {
            val html = get(currentUrl)
            allAlbums += albumsFromHtml(html)
            currentUrl = nextPageUrl(html, currentUrl)
        }
        return allAlbums.distinctBy { it.url }
    }

    private fun albumsFromHtml(html: String): List<StreamAlbum> {
        val purchasedHtml = html.sectionAfterAny(listOf("搜索已购专辑", "已购", "Purchased")).ifBlank { html }
        val hrefMatches = Regex("""href=["'](/d/[^"']+)["']""", RegexOption.IGNORE_CASE).findAll(purchasedHtml).toList()
        val windowAlbums = hrefMatches.mapIndexedNotNull { index, match ->
            val href = absoluteUrl(match.groupValues[1])
            if (!isAlbumUrl(href)) return@mapIndexedNotNull null
            val start = purchasedHtml.lastIndexOf("<div", match.range.first).takeIf { it >= 0 } ?: match.range.first
            val end = hrefMatches.getOrNull(index + 1)?.range?.first ?: (match.range.first + 5000).coerceAtMost(purchasedHtml.length)
            val block = purchasedHtml.substring(start, end.coerceAtMost(purchasedHtml.length))
            val title = parsedAlbumTitle(block, href)
            if (!isAlbumTitle(title)) return@mapIndexedNotNull null
            StreamAlbum(
                id = href,
                title = title,
                url = href,
                coverUrl = firstImageUrl(block)
            )
        }.distinctBy { it.url }
        if (windowAlbums.isNotEmpty()) return windowAlbums

        val cardPattern = Regex(
            """<div class=["']card border-0["'].*?<a\s+href=["'](/d/[^"']+)["'][^>]*>.*?<img\b[^>]*(?:data-src|src)=["']([^"']+)["'][^>]*>.*?title=["']([^"']+)["'][^>]*onclick=["']updateplayer\(["'][^"']+["']\)["'].*?<a\b[^>]*href=["'](/l/[^"']+)["'][^>]*>(.*?)</a>.*?购买于\s*([0-9-]+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val cardAlbums = cardPattern.findAll(purchasedHtml)
            .map { match ->
                val cover = firstImageUrl(match.value).ifBlank {
                    match.groupValues[2]
                        .takeUnless { isPlaceholderImage(it) }
                        ?.let { absoluteUrl(it) }
                        .orEmpty()
                }
                StreamAlbum(
                    id = absoluteUrl(match.groupValues[1]),
                    title = parsedAlbumTitle(match.value, absoluteUrl(match.groupValues[1])).ifBlank { match.groupValues[3].cleanText() },
                    url = absoluteUrl(match.groupValues[1]),
                    coverUrl = cover
                )
            }
            .filter { isAlbumTitle(it.title) }
            .distinctBy { it.url }
            .toList()
        if (cardAlbums.isNotEmpty()) return cardAlbums

        val linkPattern = Regex("""<a\b([^>]*)href=["']([^"']+)["']([^>]*)>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return linkPattern.findAll(purchasedHtml)
            .mapNotNull { match ->
                val href = absoluteUrl(match.groupValues[2])
                if (!isAlbumUrl(href)) return@mapNotNull null
                val block = match.groupValues[4]
                val title = parsedAlbumTitle(block, href)
                if (!isAlbumTitle(title)) return@mapNotNull null
                val cover = firstImageUrl(block)
                StreamAlbum(
                    id = href,
                    title = title,
                    url = href,
                    coverUrl = cover
                )
            }
            .distinctBy { it.url }
            .toList()
    }

    private fun firstImageUrl(block: String): String {
        val candidates = mutableListOf<String>()
        Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE).findAll(block).forEach { image ->
            val attrs = image.groupValues[1]
            listOf("data-src", "data-original", "data-lazy-src", "data-echo", "data-url").forEach { attr ->
                attrValue(attrs, attr)?.let { candidates += it }
            }
            attrValue(attrs, "srcset")
                ?.split(',')
                ?.mapNotNull { it.trim().substringBefore(' ').takeIf(String::isNotBlank) }
                ?.let { candidates += it }
            attrValue(attrs, "src")?.let { candidates += it }
        }
        Regex("""background-image\s*:\s*url\((['"]?)(.*?)\1\)""", RegexOption.IGNORE_CASE)
            .findAll(block)
            .mapNotNull { it.groupValues.getOrNull(2) }
            .forEach { candidates += it }

        val usable = candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isPlaceholderImage(it) }
            .distinct()
        return (usable.firstOrNull { isLikelyAlbumImage(it) } ?: usable.firstOrNull())
            ?.let { absoluteUrl(it) }
            .orEmpty()
    }

    private fun attrValue(attrs: String, attr: String): String? {
        return Regex("""\b${Regex.escape(attr)}\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(attrs)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun isPlaceholderImage(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        if (lower.startsWith("data:")) return true
        return listOf(
            "holder_cover",
            "placeholder",
            "loading",
            "spinner",
            "blank",
            "lazyload"
        ).any { lower.contains(it) }
    }

    private fun isLikelyAlbumImage(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower.contains("/media/cover/") ||
            lower.contains("/cover/") ||
            lower.contains("cover") ||
            lower.contains("album")
    }

    fun albumDetails(album: StreamAlbum): StreamAlbumDetails {
        val html = get(album.url)
        val title = pageTitle(html).ifBlank { album.title }
        val tracks = parseTracks(html, title)
        val coverUrl = album.coverUrl.ifBlank { tracks.firstOrNull()?.artworkPath.orEmpty() }
        return StreamAlbumDetails(
            album = album.copy(title = title, coverUrl = coverUrl),
            tracks = tracks,
            circle = parseCircle(html),
            releaseDate = parseReleaseDate(html),
            description = metaDescription(html).ifBlank { firstParagraph(html) },
            tags = parseTags(html)
        )
    }

    fun streamHeaders(): Map<String, String> {
        return mapOf(
            "Cookie" to cookie,
            "User-Agent" to USER_AGENT,
            "Referer" to BASE
        )
    }

    fun download(url: String, referer: String = BASE): ByteArray {
        val connection = open(url, accept = "*/*", referer = referer)
        return connection.inputStream.use { it.readBytes() }
    }

    fun imageBytes(url: String, referer: String = BASE): ByteArray {
        val connection = open(url, accept = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8", referer = referer)
        return connection.inputStream.use { it.readBytes() }
    }

    private fun findProfileUserId(html: String): String? {
        val profileTextPattern = Regex("""<a\b[^>]*href=["']([^"']*/u/(\d+)/?)["'][^>]*>.*?(?:个人信息|Profile).*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        profileTextPattern.find(html)?.groupValues?.getOrNull(2)?.takeIf { it.isValidUserId() }?.let { return it }

        Regex("""/u/(\d+)/music""").find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isValidUserId() }?.let { return it }
        Regex("""/u/(\d+)/?""").findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .firstOrNull { it.isValidUserId() }
            ?.let { return it }

        Regex("""第\s*(\d+)\s*位用户""").find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isValidUserId() }?.let { return it }
        return null
    }

    private fun parseTracks(html: String, albumTitle: String): List<Track> {
        val coverUrl = Regex("""<img\b[^>]*(?:data-src|src)=["']([^"']*/media/cover/[^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { absoluteUrl(it) }.orEmpty()
        val dataAudioPattern = Regex(
            """<li\b[^>]*data-id=["']?(\d+)["']?[^>]*data-audio=["']([^"']+)["'][^>]*>(.*?)</li>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val dataTracks = dataAudioPattern.findAll(html).map { match ->
            val rawText = stripTags(match.groupValues[3]).cleanText()
            val number = match.groupValues[1].toIntOrNull()?.plus(1) ?: 0
            val duration = parseDurationText(rawText)
            val cleanedText = rawText
                .replace(Regex("""^\d+\.\s*"""), "")
                .replace(Regex("""\s*\(\d{1,2}:\d{2}(?::\d{2})?\)\s*$"""), "")
                .trim()
            val (trackTitle, trackArtist) = splitTitleArtist(cleanedText)
            Track(
                id = "stream:dizzylab:${match.groupValues[2].hashCode()}",
                uri = absoluteUrl(match.groupValues[2]),
                title = trackTitle.ifBlank { "Track $number" },
                artist = trackArtist.ifBlank { parseTrackArtist(rawText).ifBlank { parseCircle(html).ifBlank { "DizzyLab" } } },
                album = albumTitle,
                durationMs = duration,
                trackNumber = number,
                mimeType = mimeFromUrl(match.groupValues[2]),
                sourcePath = absoluteUrl(match.groupValues[2]),
                artworkPath = coverUrl
            )
        }.toList()
        if (dataTracks.isNotEmpty()) return dataTracks

        val audioSources = Regex("""(?:src|href)=["']([^"']+\.(?:mp3|flac|wav|m4a|aac|ogg)(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { absoluteUrl(it.groupValues[1]) }
            .distinct()
            .toList()
        val names = Regex("""<(?:li|tr|div)\b[^>]*(?:track|song|audio|playlist)[^>]*>(.*?)</(?:li|tr|div)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { stripTags(it.groupValues[1]).cleanText() }
            .filter { it.isNotBlank() && it.length <= 120 }
            .toList()
        return audioSources.mapIndexed { index, url ->
            val rawName = names.getOrNull(index).orEmpty()
            val (trackTitle, trackArtist) = splitTitleArtist(rawName)
            Track(
                id = "stream:dizzylab:${url.hashCode()}",
                uri = url,
                title = trackTitle.ifBlank { "Track ${index + 1}" },
                artist = trackArtist.ifBlank { parseCircle(html).ifBlank { "DizzyLab" } },
                album = albumTitle,
                durationMs = parseDurationNear(html, url),
                mimeType = mimeFromUrl(url),
                sourcePath = url,
                artworkPath = coverUrl
            )
        }
    }

    private fun nextPageUrl(html: String, currentUrl: String): String? {
        val nextByAttr = Regex(
            """<a\b[^>]*\b(?:rel=["']next["']|aria-label=["'](?:Next|下一页)["'])[^>]*href=["']([^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE)
        )
        nextByAttr.find(html)?.groupValues?.getOrNull(1)?.let { href ->
            buildNextPageUrl(currentUrl, href)?.let { return it }
        }

        val nextPattern = Regex(
            """<a\b[^>]*href=["']([^"']+)["'][^>]*>.*?(?:下一页|Next|›|»|&rsaquo;|&raquo;|\u203a|次へ|next\s*page).*?</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        nextPattern.find(html)?.groupValues?.getOrNull(1)?.let { href ->
            buildNextPageUrl(currentUrl, href)?.let { return it }
        }

        val pages = Regex("""[?&]page=(\d+)""")
            .findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .distinct()
            .sorted()
            .toList()
        if (pages.isEmpty()) return null
        val currentPage = Regex("""[?&]page=(\d+)""").find(currentUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val maxPage = pages.last()
        val nextPage = if (currentPage + 1 <= maxPage) currentPage + 1 else null
        if (nextPage == null || nextPage !in 1..maxPage) return null
        return buildPageUrl(currentUrl, nextPage)
    }

    private fun buildNextPageUrl(currentUrl: String, href: String): String? {
        val pageNum = Regex("""[?&]page=(\d+)""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val currentPage = Regex("""[?&]page=(\d+)""").find(currentUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        if (pageNum <= currentPage) return null
        return buildPageUrl(currentUrl, pageNum)
    }

    private fun buildPageUrl(currentUrl: String, pageNum: Int): String {
        val base = currentUrl.substringBefore("?").trimEnd('/')
        val query = currentUrl.substringAfter("?", "").replace(Regex("""[&]?page=\d+"""), "")
        val sep = if (query.isBlank()) "" else "&"
        return "$base/?$query${sep}page=$pageNum"
    }

    private fun get(url: String): String {
        val connection = open(url)
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun open(url: String, accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", referer: String = BASE): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Cookie", cookie)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            setRequestProperty("Referer", referer)
        }
    }

    private fun absoluteUrl(value: String): String {
        return URL(URL("$BASE/"), value).toString()
    }

    private fun isAlbumUrl(url: String): Boolean {
        val path = Uri.parse(url).path.orEmpty().lowercase(Locale.ROOT)
        if (!path.startsWith("/d/") && !path.contains("/album")) return false
        val blocked = listOf("order", "orders", "inbox", "logout", "login", "cart", "checkout", "search")
        return blocked.none { path.contains(it) }
    }

    private fun parsedAlbumTitle(block: String, href: String): String {
        val target = absoluteUrl(href)
        val candidates = mutableListOf<String>()
        Regex("""<a\b([^>]*)href=["']([^"']+)["']([^>]*)>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(block)
            .filter { absoluteUrl(it.groupValues[2]) == target }
            .forEach { link ->
                attrValue(link.groupValues[1] + " " + link.groupValues[3], "title")?.let { candidates += it }
                candidates += link.groupValues[4]
            }
        candidates += stripTags(block)
        candidates += Uri.parse(href).lastPathSegment.orEmpty()
        return candidates
            .map { albumTitleCandidate(it) }
            .firstOrNull { isAlbumTitle(it) }
            .orEmpty()
    }

    private fun albumTitleCandidate(value: String): String {
        return stripTags(value)
            .cleanText()
            .replace(Regex("""^[▶\s]+"""), "")
            .substringBefore("购买于")
            .substringBefore("璐拱浜")
            .replace(Regex("""@\S+\s*$"""), "")
            .replace(Regex("""\b\d{4}-\d{1,2}-\d{1,2}\b"""), "")
            .cleanText()
    }

    private fun albumTitle(block: String, href: String): String {
        val imageTitle = Regex("""<img\b[^>]*(?:alt|title)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.getOrNull(1).orEmpty()
        return imageTitle.cleanText()
            .ifBlank { stripTags(block).cleanText() }
            .ifBlank { Uri.parse(href).lastPathSegment.orEmpty() }
            .substringBefore("购买于")
            .trim()
    }

    private fun isAlbumTitle(title: String): Boolean {
        if (title.isBlank()) return false
        if (title.contains("这是一张Hi-Res专辑", ignoreCase = true)) return false
        if (title.contains("Hi-Res专辑", ignoreCase = true) && title.length <= 24) return false
        if (title.contains("This is a Hi-Res album", ignoreCase = true)) return false
        val blocked = listOf("个人信息", "我的关注", "全部订单", "收件箱", "退出登录", "首页", "社团", "兑换", "设置", "搜索", "repo", "DEF")
        return blocked.none { title.contains(it, ignoreCase = true) }
    }

    private fun pageTitle(html: String): String {
        return Regex("""<h1\b[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let { stripTags(it).cleanText() }
            ?: Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.let { stripTags(it).substringBefore("|").cleanText() }.orEmpty()
    }

    private fun metaDescription(html: String): String {
        return Regex("""<meta\b[^>]*(?:name|property)=["'](?:description|og:description)["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.cleanText().orEmpty()
    }

    private fun firstParagraph(html: String): String {
        return Regex("""<p\b[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { stripTags(it.groupValues[1]).cleanText() }
            .firstOrNull { it.length > 20 }.orEmpty()
    }

    private fun parseTags(html: String): List<String> {
        val tagLinks = Regex("""<a\b[^>]*href=["'][^"']*(?:tag|tags|genre)[^"']*["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { stripTags(it.groupValues[1]).cleanText() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (tagLinks.isNotEmpty()) return tagLinks
        return Regex("""#([\p{L}\p{N}_+\-]+)""")
            .findAll(stripTags(html).cleanText())
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    private fun parseCircle(html: String): String {
        return Regex("""<a\b[^>]*href=["']/l/[^"']+["'][^>]*>@?\s*(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let { stripTags(it).cleanText().removePrefix("@").trim() }
            ?: labeledValue(html, listOf("社团", "Circle", "社團"))
    }

    private fun parseReleaseDate(html: String): String {
        return Regex("""发布于\s*([0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日|[0-9]{4}-[0-9]{1,2}-[0-9]{1,2})""")
            .find(stripTags(html).cleanText())?.groupValues?.getOrNull(1)
            ?: labeledValue(html, listOf("发布日期", "发行日期", "Release"))
    }

    private fun parseTrackArtist(text: String): String {
        return text.substringBeforeLast("(")
            .substringAfterLast(" - ", "")
            .trim()
    }

    private fun splitTitleArtist(cleanedText: String): Pair<String, String> {
        val parts = cleanedText.split(" - ", limit = 2)
        return if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            cleanedText to ""
        }
    }

    private fun parseDurationText(text: String): Long {
        val match = Regex("""\((\d{1,2}):(\d{2})(?::(\d{2}))?\)""").find(text) ?: return 0L
        val first = match.groupValues[1].toLongOrNull() ?: 0L
        val second = match.groupValues[2].toLongOrNull() ?: 0L
        val third = match.groupValues.getOrNull(3)?.toLongOrNull()
        return if (third == null) (first * 60 + second) * 1000L else (first * 3600 + second * 60 + third) * 1000L
    }

    private fun labeledValue(html: String, labels: List<String>): String {
        labels.forEach { label ->
            val escaped = Regex.escape(label)
            val value = Regex("""$escaped\s*[:：]\s*</?[^>]*>\s*([^<\n]+)""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.cleanText()
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }

    private fun parseDurationNear(html: String, url: String): Long {
        val index = html.indexOf(url.substringBefore("?"))
        val window = if (index >= 0) html.substring((index - 300).coerceAtLeast(0), (index + 300).coerceAtMost(html.length)) else html
        val match = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(window) ?: return 0L
        val first = match.groupValues[1].toLongOrNull() ?: 0L
        val second = match.groupValues[2].toLongOrNull() ?: 0L
        val third = match.groupValues.getOrNull(3)?.toLongOrNull()
        return if (third == null) (first * 60 + second) * 1000L else (first * 3600 + second * 60 + third) * 1000L
    }

    private fun mimeFromUrl(url: String): String {
        return when (Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            else -> "audio/mpeg"
        }
    }

    private fun stripTags(value: String): String {
        return value
            .replace(Regex("""<script\b.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("""<style\b.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("""<[^>]+>"""), " ")
    }

    private fun String.sectionAfterAny(markers: List<String>): String {
        val index = markers.map { indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return ""
        return substring(index)
    }

    private fun String.cleanText(): String {
        return replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun String.isValidUserId(): Boolean = matches(Regex("""\d{2,}"""))

    companion object {
        private const val BASE = "https://www.dizzylab.net"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) SMP/beta1.1.0"
    }
}

data class StreamAlbum(
    val id: String,
    val title: String,
    val url: String,
    val coverUrl: String = ""
)

data class StreamAlbumDetails(
    val album: StreamAlbum,
    val tracks: List<Track>,
    val circle: String = "",
    val releaseDate: String = "",
    val description: String = "",
    val tags: List<String> = emptyList()
)
