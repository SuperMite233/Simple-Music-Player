package com.supermite.smp.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Conservative musical identity: source IDs remain untouched and playable variants are retained. */
object TrackIdentity {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[\\p{P}\\p{Z}\\s]+"), " ").trim()

    fun artist(value: String): String = normalize(value).takeUnless { it in UNKNOWN_ARTISTS }.orEmpty()
    fun album(value: String): String = normalize(value).takeUnless { it in UNKNOWN_ALBUMS }.orEmpty()

    fun matches(a: Track, b: Track): Boolean {
        val title = normalize(a.title)
        if (title.isBlank() || title in UNKNOWN_TITLES || title != normalize(b.title)) return false
        if (a.isCueTrack != b.isCueTrack) return false
        if (a.durationMs > 0 && b.durationMs > 0 &&
            abs(a.durationMs - b.durationMs) > max(10_000L, (max(a.durationMs, b.durationMs) * 0.08).toLong())) return false
        val aArtist = artist(a.artist)
        val bArtist = artist(b.artist)
        if (aArtist.isNotBlank() && bArtist.isNotBlank()) return aArtist == bArtist
        val aAlbum = album(a.album)
        return aAlbum.isNotBlank() && aAlbum == album(b.album)
    }

    fun isStreaming(track: Track): Boolean = track.id.startsWith("stream:") || track.id.startsWith("webdav:") ||
        track.uri.startsWith("https://") || track.uri.startsWith("http://")

    private val UNKNOWN_ARTISTS = setOf("", "未知", "未知艺术家", "未知歌手", "unknown", "unknown artist", "<unknown>")
    private val UNKNOWN_ALBUMS = setOf("", "未知", "未知专辑", "unknown", "unknown album", "<unknown>")
    private val UNKNOWN_TITLES = setOf("未知", "未知曲目", "unknown", "unknown title", "<unknown>")
}
