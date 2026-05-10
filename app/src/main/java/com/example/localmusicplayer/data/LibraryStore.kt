package com.supermite.smp.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library_state.json")

    val edits: MutableMap<String, TrackEdit> = mutableMapOf()
    val tracks: MutableList<Track> = mutableListOf()
    val playlists: MutableList<Playlist> = mutableListOf()
    val history: MutableList<HistoryItem> = mutableListOf()
    val playCounts: MutableMap<String, Int> = mutableMapOf()
    var albumPlaylistsCreated: Boolean = false
        private set

    init {
        load()
        ensureSystemPlaylists()
    }

    fun saveEdit(trackId: String, edit: TrackEdit) {
        edits[trackId] = edit.copy(tags = edit.normalizedTags())
        save()
    }

    fun savedTracks(): List<Track> = tracks.toList()

    fun saveTracks(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks.distinctBy { it.id })
        playlists.forEach { playlist ->
            val uniqueIds = playlist.trackIds.distinct()
            playlist.trackIds.clear()
            playlist.trackIds.addAll(uniqueIds)
        }
        save()
    }

    fun addHistory(trackId: String, positionMs: Long = 0L) {
        playCounts[trackId] = (playCounts[trackId] ?: 0) + 1
        history.removeAll { it.trackId == trackId }
        history.add(0, HistoryItem(trackId, System.currentTimeMillis(), positionMs))
        while (history.size > HISTORY_LIMIT) history.removeAt(history.lastIndex)
        save()
    }

    fun playCount(trackId: String): Int = playCounts[trackId] ?: 0

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.ifBlank { "新播放列表" })
        playlists.add(playlist)
        save()
        return playlist
    }

    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id && !it.isLocked }
        save()
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        addToPlaylist(playlistId, listOf(trackId))
    }

    fun addToPlaylist(playlistId: String, trackIds: Collection<String>) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        playlist.trackIds.removeAll(ids.toSet())
        playlist.trackIds.addAll(0, ids)
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        playlist.trackIds.remove(trackId)
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun removeTracksFromPlaylist(playlistId: String, trackIds: Collection<String>) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        playlist.trackIds.removeAll(trackIds.toSet())
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun removeTracksEverywhere(trackIds: Collection<String>) {
        val ids = trackIds.toSet()
        playlists.forEach { playlist ->
            playlist.trackIds.removeAll(ids)
            playlist.updatedAt = System.currentTimeMillis()
        }
        history.removeAll { it.trackId in ids }
        ids.forEach {
            edits.remove(it)
            playCounts.remove(it)
        }
        save()
    }

    fun savePlaylistMeta(playlistId: String, description: String, tags: List<String>) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        playlist.description = description.trim()
        playlist.tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun saveAlbumPlaylistMeta(album: String, description: String, tags: List<String>) {
        val name = "专辑：${album.ifBlank { "未知专辑" }}"
        val playlist = playlists.firstOrNull { it.systemType == "album" && it.name == name } ?: return
        playlist.description = description.trim()
        playlist.tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun favoritePlaylist(): Playlist = playlists.first { it.id == FAVORITES_ID }

    fun isFavorite(trackId: String): Boolean = favoritePlaylist().trackIds.contains(trackId)

    fun setFavorite(trackId: String, favorite: Boolean) {
        val playlist = favoritePlaylist()
        if (favorite) {
            playlist.trackIds.remove(trackId)
            playlist.trackIds.add(0, trackId)
        } else {
            playlist.trackIds.remove(trackId)
        }
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun createAlbumPlaylistsIfNeeded(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val desired = tracks
            .filter { it.album.isNotBlank() }
            .groupBy { it.displayAlbum }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (album, albumTracks) -> "专辑：$album" to albumTracks.map { it.id }.distinct() }
        val existingByName = playlists.filter { it.systemType == "album" }.groupBy { it.name }
        existingByName.values.forEach { duplicates ->
            duplicates.drop(1).forEach { duplicate -> playlists.remove(duplicate) }
        }
        desired.forEach { (name, trackIds) ->
            val existingAlbum = playlists.firstOrNull { it.systemType == "album" && it.name == name }
            if (existingAlbum != null) {
                existingAlbum.trackIds.clear()
                existingAlbum.trackIds.addAll(trackIds)
                existingAlbum.updatedAt = System.currentTimeMillis()
            } else {
                playlists.add(
                    Playlist(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        trackIds = trackIds.toMutableList(),
                        systemType = "album",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        albumPlaylistsCreated = true
        save()
    }

    fun createCuePlaylistsFromTracks(tracks: List<Track>) {
        val cueGroups = tracks
            .filter { it.cueSheet?.isNotBlank() == true }
            .groupBy { it.cueSheet.orEmpty() }
        if (cueGroups.isEmpty()) return

        cueGroups.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (cueSheet, cueTracks) ->
            val name = "CUE：" + cueDisplayName(cueSheet)
            val trackIds = cueTracks.sortedBy { it.cueIndex ?: it.trackNumber }.map { it.id }.distinct()
            val existing = playlists.firstOrNull { it.systemType == "cue" && it.name == name }
            if (existing != null) {
                existing.trackIds.clear()
                existing.trackIds.addAll(trackIds)
                existing.updatedAt = System.currentTimeMillis()
            } else {
                playlists.add(
                    Playlist(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        trackIds = trackIds.toMutableList(),
                        systemType = "cue",
                        updatedAt = System.currentTimeMillis(),
                        description = "根据 CUE 文件自动创建的歌单。"
                    )
                )
            }
        }
        save()
    }

    fun exportUserConfig(settings: JSONObject): JSONObject {
        return JSONObject()
            .put("settings", settings)
            .put(
                "playlists",
                JSONArray(playlists.filter { it.systemType.isBlank() || it.id == FAVORITES_ID }.map { it.toJson() })
            )
    }

    fun importUserConfig(root: JSONObject): JSONObject {
        val imported = mutableListOf<Playlist>()
        root.optJSONArray("playlists").forEachObject { obj ->
            val id = obj.optString("id", UUID.randomUUID().toString())
            val systemType = obj.optString("systemType", "").takeIf { it == "favorites" }.orEmpty()
            imported += Playlist(
                id = if (systemType == "favorites") FAVORITES_ID else id,
                name = obj.optString("name", if (systemType == "favorites") "我喜欢的音乐" else "播放列表"),
                trackIds = obj.optJSONArray("trackIds").toStringList().toMutableList(),
                systemType = systemType,
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                description = obj.optString("description", ""),
                tags = obj.optJSONArray("tags").toStringList().toMutableList()
            )
        }

        playlists.removeAll { it.systemType.isBlank() }
        imported.firstOrNull { it.id == FAVORITES_ID }?.let { importedFavorite ->
            val favorite = favoritePlaylist()
            favorite.trackIds.clear()
            favorite.trackIds.addAll(importedFavorite.trackIds)
            favorite.description = importedFavorite.description
            favorite.tags = importedFavorite.tags
            favorite.updatedAt = System.currentTimeMillis()
        }
        imported.filter { it.id != FAVORITES_ID }.forEach { playlist ->
            playlists.add(playlist.copy(id = playlist.id.ifBlank { UUID.randomUUID().toString() }, systemType = ""))
        }
        ensureSystemPlaylists()
        save()
        return root.optJSONObject("settings") ?: JSONObject()
    }

    fun visiblePlaylists(historyTrackIds: List<String>): List<Playlist> {
        repairPlaylistData()
        val historyPlaylist = Playlist(
            id = HISTORY_ID,
            name = "播放历史",
            trackIds = historyTrackIds.distinct().take(HISTORY_LIMIT).toMutableList(),
            systemType = "history",
            updatedAt = history.firstOrNull()?.playedAt ?: 0L
        )
        val rankingPlaylist = Playlist(
            id = RANKING_ID,
            name = "播放排行",
            trackIds = playCounts.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .filter { id -> tracks.any { it.id == id } }
                .take(HISTORY_LIMIT)
                .toMutableList(),
            systemType = "ranking",
            updatedAt = System.currentTimeMillis(),
            description = "播放次数最多的前 100 首音乐。"
        )
        val localPlaylist = Playlist(
            id = LOCAL_ID,
            name = "本地音乐",
            trackIds = tracks.map { it.id }.distinct().toMutableList(),
            systemType = "local",
            updatedAt = tracks.maxOfOrNull { it.durationMs } ?: 0L,
            description = "已扫描和导入的本地音乐。"
        )
        ensureSystemPlaylists()
        return listOf(localPlaylist, favoritePlaylist(), rankingPlaylist, historyPlaylist) +
            playlists.filter { it.id != FAVORITES_ID }.sortedWith(
                compareBy<Playlist> {
                    when (it.systemType) {
                        "" -> 0
                        "cue" -> 1
                        "album" -> 2
                        else -> 3
                    }
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }

    private fun repairPlaylistData() {
        val existingTrackIds = tracks.map { it.id }.toSet()
        playlists.forEach { playlist ->
            val uniqueIds = playlist.trackIds.distinct().filter { it in existingTrackIds || playlist.systemType == "favorites" }
            playlist.trackIds.clear()
            playlist.trackIds.addAll(uniqueIds)
        }
        playlists.filter { it.systemType == "album" || it.systemType == "cue" }
            .groupBy { it.systemType to it.name }
            .values
            .forEach { duplicates ->
                val first = duplicates.firstOrNull() ?: return@forEach
                duplicates.drop(1).forEach { duplicate ->
                    first.trackIds.addAll(duplicate.trackIds)
                    playlists.remove(duplicate)
                }
                val uniqueIds = first.trackIds.distinct()
                first.trackIds.clear()
                first.trackIds.addAll(uniqueIds)
            }
    }

    private fun ensureSystemPlaylists() {
        if (playlists.none { it.id == FAVORITES_ID }) {
            playlists.add(0, Playlist(FAVORITES_ID, "我喜欢的音乐", mutableListOf(), "favorites"))
            save()
        }
    }

    private fun load() {
        if (!file.exists()) return
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        albumPlaylistsCreated = root.optBoolean("albumPlaylistsCreated", false)

        val editObj = root.optJSONObject("edits") ?: JSONObject()
        editObj.keys().forEach { key ->
            val value = editObj.optJSONObject(key) ?: return@forEach
            edits[key] = TrackEdit(
                alias = value.optString("alias"),
                comment = value.optString("comment"),
                tags = value.optJSONArray("tags").toStringList(),
                rating = value.optInt("rating").coerceIn(0, 5)
            )
        }

        root.optJSONArray("tracks").forEachObject { obj ->
            tracks += obj.toTrack()
        }
        val uniqueTracks = tracks.distinctBy { it.id }
        tracks.clear()
        tracks.addAll(uniqueTracks)

        root.optJSONArray("playlists").forEachObject { obj ->
            playlists += Playlist(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "播放列表"),
                trackIds = obj.optJSONArray("trackIds").toStringList().distinct().toMutableList(),
                systemType = obj.optString("systemType", ""),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                createdAt = obj.optLong("createdAt", obj.optLong("updatedAt", System.currentTimeMillis())),
                description = obj.optString("description", ""),
                tags = obj.optJSONArray("tags").toStringList().toMutableList()
            )
        }

        root.optJSONArray("history").forEachObject { obj ->
            history += HistoryItem(
                trackId = obj.optString("trackId"),
                playedAt = obj.optLong("playedAt"),
                positionMs = obj.optLong("positionMs")
            )
        }
        while (history.size > HISTORY_LIMIT) history.removeAt(history.lastIndex)

        val countsObj = root.optJSONObject("playCounts") ?: JSONObject()
        countsObj.keys().forEach { key ->
            playCounts[key] = countsObj.optInt(key, 0).coerceAtLeast(0)
        }
    }

    private fun save() {
        val root = JSONObject()
        root.put("albumPlaylistsCreated", albumPlaylistsCreated)

        val editObj = JSONObject()
        edits.forEach { (id, edit) ->
            editObj.put(
                id,
                JSONObject()
                    .put("alias", edit.alias)
                    .put("comment", edit.comment)
                    .put("tags", JSONArray(edit.normalizedTags()))
                    .put("rating", edit.rating.coerceIn(0, 5))
            )
        }
        root.put("edits", editObj)

        root.put("tracks", JSONArray(tracks.map { it.toJson() }))

        playlists.forEach { playlist ->
            val uniqueIds = playlist.trackIds.distinct()
            playlist.trackIds.clear()
            playlist.trackIds.addAll(uniqueIds)
        }
        root.put("playlists", JSONArray(playlists.map { it.toJson() }))

        root.put("history", JSONArray(history.map { item ->
            JSONObject()
                .put("trackId", item.trackId)
                .put("playedAt", item.playedAt)
                .put("positionMs", item.positionMs)
        }))

        val countsObj = JSONObject()
        playCounts.forEach { (id, count) -> countsObj.put(id, count) }
        root.put("playCounts", countsObj)

        file.writeText(root.toString(2))
    }

    companion object {
        const val FAVORITES_ID = "system:favorites"
        const val HISTORY_ID = "system:history"
        const val LOCAL_ID = "system:local"
        const val RANKING_ID = "system:ranking"
        const val HISTORY_LIMIT = 100
    }
}

private fun cueDisplayName(cueSheet: String): String {
    val decoded = Uri.decode(cueSheet)
    return decoded
        .substringAfterLast('/')
        .substringAfterLast(':')
        .substringBeforeLast('.')
        .ifBlank { "未命名 CUE" }
}

private fun Playlist.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("name", name)
        .put("trackIds", JSONArray(trackIds))
        .put("systemType", systemType)
        .put("updatedAt", updatedAt)
        .put("createdAt", createdAt)
        .put("description", description)
        .put("tags", JSONArray(tags))
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

private fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
    if (this == null) return
    for (index in 0 until length()) {
        optJSONObject(index)?.let(block)
    }
}

private fun Track.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("uri", uri)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)
        .put("trackNumber", trackNumber)
        .put("year", year)
        .put("date", date)
        .put("composer", composer)
        .put("mimeType", mimeType)
        .put("sourcePath", sourcePath)
        .put("lyricsUri", lyricsUri)
        .put("artworkPath", artworkPath)
        .put("cueSheet", cueSheet)
        .put("cueIndex", cueIndex)
        .put("cueStartMs", cueStartMs)
        .put("cueEndMs", cueEndMs)
}

private fun JSONObject.toTrack(): Track {
    return Track(
        id = optString("id"),
        uri = optString("uri"),
        title = optString("title"),
        artist = optString("artist"),
        album = optString("album"),
        durationMs = optLong("durationMs"),
        trackNumber = optInt("trackNumber"),
        year = optInt("year"),
        date = optString("date"),
        composer = optString("composer"),
        mimeType = optString("mimeType"),
        sourcePath = optString("sourcePath"),
        lyricsUri = optString("lyricsUri"),
        artworkPath = optString("artworkPath"),
        cueSheet = optNullableString("cueSheet"),
        cueIndex = optNullableInt("cueIndex"),
        cueStartMs = optLong("cueStartMs"),
        cueEndMs = optLong("cueEndMs")
    )
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

