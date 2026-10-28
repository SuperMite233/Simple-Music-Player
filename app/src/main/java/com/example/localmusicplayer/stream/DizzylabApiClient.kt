package com.supermite.smp.stream

import com.supermite.smp.data.Track
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Token API and the small web-session bridge; no native paid checkout is implemented. */
class DizzylabApiClient(
    private val token: String = "",
    private val cookie: String = "",
    private val origin: HttpUrl = BASE.toHttpUrl(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()
) {
    fun sessionFromWebsite(): Pair<String, DizzylabUser> {
        check(cookie.split(';').any { it.trim().startsWith("sessionid=") }) { "请先登录 Dizzylab" }
        val home = webText("/")
        val uid = Regex("""href=["']/u/(\d+)/?["'][^>]*>\s*(?:<[^>]+>\s*)*(?:个人信息|Profile)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(home)?.groupValues?.get(1)
            ?: Regex("""/u/(\d+)/music""").find(home)?.groupValues?.get(1)
            ?: error("登录已失效，请重新登录 Dizzylab")
        val html = webText("/u/$uid/music/").replace("&amp;", "&")
        val value = Regex("""[?&]token=([a-fA-F0-9]{40})""").find(html)?.groupValues?.get(1)
            ?: error("无法取得 Dizzylab 会话，请重新登录")
        val user = DizzylabApiClient(value, cookie, origin, http).user()
        check(user.id == uid) { "Dizzylab 账号不一致，请重新登录" }
        return value to user
    }

    fun user(): DizzylabUser {
        val obj = get("getmyinfo/", auth = true).getJSONObject("user")
        return DizzylabUser(obj.get("uid").toString(), obj.optString("username"), obj.optString("mail"),
            obj.optString("cover"), obj.optString("user_group"))
    }

    fun albums(section: DizzylabSection, offset: Int, query: String = ""): DizzylabPage<StreamAlbum> {
        val path = when (section) {
            DizzylabSection.PURCHASED -> "getmydisc/"
            DizzylabSection.LIKED -> "getmylike/"
            DizzylabSection.SEARCH -> "search/"
            else -> "getdiscs/"
        }
        val params = linkedMapOf("l" to "$offset", "r" to "${offset + PAGE_SIZE}", "sort" to "ad")
        if (section == DizzylabSection.SEARCH) params["keyword"] = query
        if (section == DizzylabSection.DISCOVER) params["type"] = "album"
        val json = get(path, params, section != DizzylabSection.DISCOVER)
        val items = requiredArray(json, "discs").objects().map(::album).map {
            if (section == DizzylabSection.PURCHASED) it.copy(purchased = true) else it
        }
        return page(json, items, offset)
    }

    fun circles(offset: Int, following: Boolean = false): DizzylabPage<DizzylabCircle> {
        val json = get(if (following) "getmyfollowing/" else "getlabels/", bounds(offset), following)
        val array = requiredArray(json, if (following) "discs" else "labels")
        val items = array.objects().map { obj ->
            DizzylabCircle(obj.optString(if (following) "id" else "labelid"), obj.optString("title"),
                obj.optString(if (following) "cover" else "labelcover"), obj.optString("description"))
        }
        return page(json, items, offset)
    }

    fun circleAlbums(circle: DizzylabCircle, offset: Int): DizzylabPage<StreamAlbum> {
        val json = get("getlabeldiscs/", bounds(offset) + ("labelid" to circle.id), auth = true)
        return page(json, requiredArray(json, "discs").objects().map(::album), offset)
    }

    fun feed(offset: Int): DizzylabPage<DizzylabFeedEntry> {
        val json = get("getfeed/", bounds(offset) + ("sort" to "ad"), auth = true)
        val items = requiredArray(json, "labels").objects().map { obj ->
            val circle = DizzylabCircle(obj.optString("labelid"), obj.optString("title"), obj.optString("labelcover"))
            DizzylabFeedEntry(circle, obj.optString("add_date"), obj.optJSONArray("discs").objects().map(::album))
        }
        return page(json, items, offset)
    }

    fun details(discId: String): StreamAlbumDetails {
        val json = get("getthisdicsinfo/", mapOf("discid" to discId))
        val a = album(json)
        val tracks = requiredArray(json, "tracks").objects().map { t ->
            val trackId = t.get("id").toString()
            Track(id = DizzylabTrackRef(discId, trackId).id, uri = mediaUrl(t.optString("url")),
                title = t.optString("title"), artist = t.optString("authers"), album = a.title,
                durationMs = 0, trackNumber = trackId.toIntOrNull() ?: 0, mimeType = "audio/mpeg",
                sourcePath = a.url, artworkPath = t.optString("coverurl", a.coverUrl))
        }
        return StreamAlbumDetails(a, tracks, json.optString("label"), json.optString("release_date"),
            json.optString("disc_description"), json.optJSONArray("tags").strings(),
            purchased = json.optBoolean("ihavethis"), liked = json.optBoolean("ilikeit"))
    }

    fun downloadUrl(ref: DizzylabTrackRef, bitrate: Int): String {
        require(bitrate == 128 || bitrate == 320)
        val json = get("gettrackdownloadurl/", mapOf("discid" to ref.discId, "trackid" to ref.trackId,
            "packtype" to "$bitrate"), auth = true)
        return mediaUrl(json.getJSONObject("track").getString("url"))
    }

    fun setAlbumLike(discId: String): Boolean = post("ilikethisornot/", mapOf("discid" to discId)).getBoolean("ilikeit")

    fun claimFree(discId: String) {
        // Recheck entitlement and price immediately before the explicit user action.
        val d = details(discId)
        if (d.purchased) return
        check(d.album.price == 0.0 && d.album.onSale && !d.album.preSelling) { "请在网页完成该专辑的购买" }
        check(post("unlockafreedisc/", mapOf("discid" to discId)).optString("state") == "ok") {
            "免费领取未完成，请刷新或在网页继续"
        }
    }

    fun redeem(code: String, confirm: Boolean = false): DizzylabRedemption {
        require(code.isNotBlank()) { "请输入兑换码" }
        val json = post(if (confirm) "comfirm_redeem/" else "redeem/", mapOf("code" to code.trim()))
        return DizzylabRedemption(json.optString("res"), json.optJSONObject("disc")?.let(::album))
    }

    private fun album(obj: JSONObject): StreamAlbum {
        val id = obj.get("id").toString()
        return StreamAlbum(id, obj.optString("title"), "$BASE/d/${encode(id)}/", obj.optString("cover"),
            price = if (obj.has("price") && !obj.isNull("price")) obj.optDouble("price") else null,
            purchased = obj.optBoolean("ihavethis"), circle = obj.optString("label"),
            onSale = obj.optBoolean("onsell"), preSelling = obj.optBoolean("ispreselling"))
    }

    private fun bounds(offset: Int) = mapOf("l" to "$offset", "r" to "${offset + PAGE_SIZE}")

    private fun requiredArray(json: JSONObject, name: String): JSONArray =
        json.optJSONArray(name) ?: error("Dizzylab 返回的数据不完整，请刷新后重试")

    private fun <T> page(json: JSONObject, items: List<T>, offset: Int) = DizzylabPage(items,
        offset + PAGE_SIZE, json.optBoolean("canshowmore") && items.isNotEmpty())

    private fun get(path: String, params: Map<String, String> = emptyMap(), auth: Boolean = false): JSONObject {
        if (auth) check(token.isNotBlank()) { "请先登录 Dizzylab" }
        val builder = origin.newBuilder().addPathSegments("apis/$path")
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        if (token.isNotBlank()) builder.addQueryParameter("token", token)
        return json(Request.Builder().url(builder.build()).header("Accept", "application/json").build())
    }

    private fun post(path: String, fields: Map<String, String>): JSONObject {
        check(token.isNotBlank()) { "请先登录 Dizzylab" }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("token", token)
        fields.forEach { (k, v) -> body.addFormDataPart(k, v) }
        return json(Request.Builder().url(origin.newBuilder().addPathSegments("apis/$path").build())
            .header("Accept", "application/json").header("Referer", origin.toString()).post(body.build()).build())
    }

    private fun json(request: Request): JSONObject {
        val response = try { http.newCall(request).execute() } catch (_: java.io.IOException) {
            if (request.method == "POST") error("连接中断，操作结果尚未确认；请先刷新状态，不要重复提交")
            else error("Dizzylab 网络连接失败，请稍后重试")
        }
        response.use { r ->
            check(r.isSuccessful) { "Dizzylab 请求失败（HTTP ${r.code}）" }
            check(r.body?.contentType()?.subtype?.contains("json") == true) {
                "Dizzylab 未返回有效数据，请检查登录状态或该内容的访问权限"
            }
            return try { JSONObject(r.body!!.string()) } catch (_: org.json.JSONException) {
                error("Dizzylab 返回的数据无法识别")
            }
        }
    }

    private fun webText(path: String): String {
        var url = origin.resolve(path) ?: error("无效页面")
        repeat(5) {
            check(url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port) { "网页跳转超出 Dizzylab 站点" }
            http.newCall(Request.Builder().url(url).header("Cookie", cookie).build()).execute().use { r ->
                if (r.isRedirect) url = url.resolve(r.header("Location").orEmpty()) ?: error("页面跳转无效")
                else {
                    check(r.isSuccessful) { "Dizzylab 页面读取失败" }
                    return r.body?.string() ?: error("Dizzylab 页面为空")
                }
            }
        }
        error("Dizzylab 页面跳转过多")
    }

    companion object {
        const val BASE = "https://www.dizzylab.net"
        const val PAGE_SIZE = 24
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        fun mediaUrl(value: String): String {
            val url = value.toHttpUrlOrNull() ?: error("Dizzylab 媒体地址无效")
            check(url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                url.host in setOf("streaming.dizzylab.net", "cdn.dizzylab.net", "albumsbase.dizzylab.net")) {
                "Dizzylab 返回了不受支持的媒体地址"
            }
            return url.toString()
        }
        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
            (0 until length()).mapNotNull { optJSONObject(it) }
        private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else
            (0 until length()).map { optString(it) }
    }
}

data class DizzylabTrackRef(val discId: String, val trackId: String) {
    val id: String get() = "stream:dizzylab:${DizzylabApiClient.encode(discId)}:${DizzylabApiClient.encode(trackId)}"
    companion object {
        fun from(track: Track): DizzylabTrackRef? {
            if (!track.id.startsWith("stream:dizzylab:")) return null
            val values = track.id.removePrefix("stream:dizzylab:").split(':')
            if (values.size != 2 || values.any { it.isBlank() }) return null
            return runCatching { DizzylabTrackRef(URLDecoder.decode(values[0], "UTF-8"), URLDecoder.decode(values[1], "UTF-8")) }.getOrNull()
        }
    }
}

enum class DizzylabSection(val title: String) { DISCOVER("发现专辑"), PURCHASED("已购音乐"), LIKED("喜欢的专辑"), SEARCH("搜索结果"), CIRCLES("社团"), CIRCLE("社团作品") }
data class DizzylabPage<T>(val items: List<T>, val nextOffset: Int, val hasMore: Boolean)
data class DizzylabUser(val id: String, val name: String, val email: String, val cover: String, val group: String)
data class DizzylabCircle(val id: String, val name: String, val cover: String = "", val description: String = "")
data class DizzylabFeedEntry(val circle: DizzylabCircle, val date: String, val albums: List<StreamAlbum>)
data class DizzylabRedemption(val result: String, val album: StreamAlbum?)
