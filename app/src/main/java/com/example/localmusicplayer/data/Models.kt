package com.supermite.smp.data

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val date: String = "",
    val composer: String = "",
    val mimeType: String = "",
    val sourcePath: String = "",
    val lyricsUri: String = "",
    val artworkPath: String = "",
    val cueSheet: String? = null,
    val cueIndex: Int? = null,
    val cueStartMs: Long = 0L,
    val cueEndMs: Long = 0L
) {
    val displayTitle: String
        get() = title.ifBlank { sourcePath.substringAfterLast('/').ifBlank { "未知曲目" } }

    val displayArtist: String
        get() = artist.ifBlank { "未知艺术家" }

    val displayAlbum: String
        get() = album.ifBlank { "未知专辑" }

    val isCueTrack: Boolean
        get() = cueSheet != null

    val hasLyrics: Boolean
        get() = lyricsUri.isNotBlank()
}

data class TrackEdit(
    val alias: String = "",
    val comment: String = "",
    val tags: List<String> = emptyList(),
    val rating: Int = 0
) {
    fun normalizedTags(): List<String> = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: MutableList<String> = mutableListOf(),
    val systemType: String = "",
    var updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    var description: String = "",
    var tags: MutableList<String> = mutableListOf()
) {
    val isSystem: Boolean
        get() = systemType.isNotBlank()

    val isLocked: Boolean
        get() = systemType == "favorites" || systemType == "history" || systemType == "local" || systemType == "ranking"
}

data class HistoryItem(
    val trackId: String,
    val playedAt: Long,
    val positionMs: Long = 0L
)

data class CueTrack(
    val number: Int,
    val title: String,
    val performer: String,
    val fileName: String,
    val startMs: Long
)

