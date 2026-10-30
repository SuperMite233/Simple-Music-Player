package com.supermite.smp.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    private fun release(tag: String, date: String, beta: Boolean, apk: String = "SMP.apk", draft: Boolean = false) = JSONObject()
        .put("tag_name", tag).put("published_at", date).put("prerelease", beta).put("draft", draft)
        .put("html_url", "https://github.com/SuperMite233/Simple-Music-Player/releases/tag/$tag")
        .put("assets", JSONArray().put(JSONObject().put("name", apk)
            .put("browser_download_url", "https://github.com/SuperMite233/Simple-Music-Player/releases/download/$tag/$apk")))

    @Test fun choosesPublicationDateWithinEachChannel() {
        val all = ReleasePolicy.parse(JSONArray().put(release("v99.0.0", "2026-01-01T00:00:00Z", false))
            .put(release("beta-2.0.0-20261029", "2026-10-29T09:00:00Z", true))
            .put(release("v2.1.0", "2026-10-28T09:00:00Z", false))
            .put(release("beta-old", "2026-03-01T00:00:00Z", true)))
        assertEquals("v2.1.0", ReleasePolicy.latest(all, false)?.tag)
        assertEquals("beta-2.0.0-20261029", ReleasePolicy.latest(all, true)?.tag)
    }

    @Test fun ignoresDraftsUnsignedMissingAndForeignAssets() {
        val bad = release("foreign", "2026-10-30T00:00:00Z", true)
        bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.org/malicious.apk")
        val all = ReleasePolicy.parse(JSONArray().put(bad).put(release("draft", "2026-11-01T00:00:00Z", true, draft=true))
            .put(release("unsigned", "2026-12-01T00:00:00Z", true, "SMP-unsigned.apk"))
            .put(release("source", "2026-12-02T00:00:00Z", true, "source.zip")))
        assertTrue(all.isEmpty())
    }

    @Test fun currentTagAndOlderOtherChannelDoNotPromptButSameDayCodeCanUpdate() {
        val r = ReleasePolicy.parse(JSONArray().put(release("beta-2.0.0-20261029", "2026-10-29T10:00:00Z", true))).single()
        assertFalse(ReleasePolicy.isNewer(r, r.version, 31, "2026-10-29T09:00:00Z"))
        val updated = ReleasePolicy.withMetadata(r, JSONObject().put("packageName", "com.supermite.smp")
            .put("versionName", r.version).put("versionCode", 32))
        assertTrue(ReleasePolicy.isNewer(updated, r.version, 31, "2026-10-29T11:00:00Z"))
        assertFalse(ReleasePolicy.isNewer(updated, r.version, 32, "2026-10-29T09:00:00Z"))
        assertFalse(ReleasePolicy.isNewer(r, "other", 31, "2026-10-30T00:00:00Z"))
    }
}
