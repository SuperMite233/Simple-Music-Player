package com.supermite.smp.updates

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant

data class PublishedRelease(
    val tag: String, val version: String, val pageUrl: String, val apkUrl: String,
    val apkName: String, val publishedAt: Instant, val prerelease: Boolean,
    val metadataUrl: String = "", val versionCode: Int? = null
)

/** GitHub creation order and numeric fragments of version names are not update order. */
object ReleasePolicy {
    fun parse(array: JSONArray): List<PublishedRelease> = (0 until array.length()).mapNotNull { index ->
        val root = array.optJSONObject(index) ?: return@mapNotNull null
        if (root.optBoolean("draft")) return@mapNotNull null
        val published = runCatching { Instant.parse(root.optString("published_at")) }.getOrNull() ?: return@mapNotNull null
        val assets = root.optJSONArray("assets") ?: return@mapNotNull null
        val validAssets = (0 until assets.length()).mapNotNull(assets::optJSONObject)
        val apk = validAssets.filter {
            val name = it.optString("name")
            name.endsWith(".apk", true) && !name.contains("unsigned", true) && validDownload(it.optString("browser_download_url"))
        }.sortedWith(compareBy<JSONObject> { it.optString("name").contains("debug", true) }.thenBy { it.optString("name") }).firstOrNull() ?: return@mapNotNull null
        val tag = root.optString("tag_name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val page = root.optString("html_url").takeIf(::validPage) ?: return@mapNotNull null
        val metadata = validAssets.firstOrNull { it.optString("name") == "SMP-release.json" && validDownload(it.optString("browser_download_url")) }
        PublishedRelease(tag, tag.removePrefix("v"), page, apk.optString("browser_download_url"), apk.optString("name"),
            published, root.optBoolean("prerelease"), metadata?.optString("browser_download_url").orEmpty())
    }

    fun latest(releases: List<PublishedRelease>, prerelease: Boolean): PublishedRelease? = releases
        .filter { it.prerelease == prerelease }.maxWithOrNull(compareBy<PublishedRelease> { it.publishedAt }.thenBy { it.tag })

    fun isNewer(release: PublishedRelease, installedVersion: String, installedCode: Int, builtAt: String): Boolean {
        release.versionCode?.let { return it > installedCode }
        if (release.version.removePrefix("v") == installedVersion.removePrefix("v")) return false
        val installedAt = runCatching { Instant.parse(builtAt) }.getOrDefault(Instant.EPOCH)
        return release.publishedAt.isAfter(installedAt)
    }

    fun withMetadata(release: PublishedRelease, metadata: JSONObject): PublishedRelease {
        if (metadata.optString("packageName") != "com.supermite.smp" ||
            metadata.optString("versionName") != release.version || metadata.optInt("versionCode", -1) <= 0) return release
        return release.copy(versionCode = metadata.getInt("versionCode"))
    }

    private fun validDownload(value: String) = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null &&
            uri.path.startsWith("/SuperMite233/Simple-Music-Player/releases/download/")
    }.getOrDefault(false)
    private fun validPage(value: String) = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null &&
            uri.path.startsWith("/SuperMite233/Simple-Music-Player/releases/tag/")
    }.getOrDefault(false)
}
