package com.supermite.smp.data

import android.content.Context
import android.net.Uri
import com.supermite.smp.cache.CacheCategory
import com.supermite.smp.cache.CacheManager
import com.supermite.smp.cache.CacheStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LibraryStore internal constructor(private val file: File, private val metadataCache: CacheManager? = null) {
    constructor(context: Context) : this(File(context.filesDir, "library_state.json"), CacheManager(context))

    val edits: MutableMap<String, TrackEdit> = mutableMapOf()
    val tracks: MutableList<Track> = mutableListOf()
    val playlists: MutableList<Playlist> = mutableListOf()
    val history: MutableList<HistoryItem> = mutableListOf()
    val playCounts: MutableMap<String, Int> = mutableMapOf()
    /** Long-lived listening records, separate from reconstructible network/disk caches. */
    private val rememberedTracks: MutableList<Track> = mutableListOf()
    private val trackAliases: MutableMap<String, String> = mutableMapOf()
    var albumPlaylistsCreated: Boolean = false
        private set

    val webDavServers: MutableList<WebDavServer> = mutableListOf()
    val subsonicServers: MutableList<SubsonicServer> = mutableListOf()
    var selectedSubsonicServerId: String = ""

    init {
        load()
        ensureSystemPlaylists()
    }

    @Synchronized fun saveEdit(trackId: String, edit: TrackEdit) {
        val normalized = edit.copy(tags = edit.normalizedTags(), rating = edit.rating.coerceIn(0, 5))
        val canonical = canonicalTrackId(trackId)
        sourceIdsFor(trackId).plus(canonical).forEach { edits[it] = normalized }
        save()
    }

    fun savedTracks(): List<Track> = tracks.toList()

    /** Every retained source variant; use [trackForRecord] to resolve a logical history/favorite ID. */
    fun knownTracks(): List<Track> = (tracks + rememberedTracks).distinctBy { it.id }

    fun canonicalTrackId(trackId: String): String {
        var current = trackId
        val seen = mutableSetOf<String>()
        while (seen.add(current)) {
            val next = trackAliases[current] ?: return current
            if (next == current) return current
            current = next
        }
        return trackId // Never follow corrupt cycles from an imported/old library.
    }

    fun sourceIdsFor(trackId: String): Set<String> {
        val canonical = canonicalTrackId(trackId)
        return trackAliases.keys.filter { canonicalTrackId(it) == canonical }.toSet() + canonical
    }

    fun trackForRecord(trackId: String): Track? {
        val known = knownTracks()
        return known.firstOrNull { it.id == trackId }
            ?: known.firstOrNull { canonicalTrackId(it.id) == canonicalTrackId(trackId) }
            ?: cachedTrack(trackId)
    }

    /** Call before recording a play and when online results are received. Remote auth URLs are never saved. */
    @Synchronized fun rememberTracks(newTracks: Collection<Track>, persist: Boolean = true) {
        newTracks.filter { TrackIdentity.isStreaming(it) }.forEach { incoming ->
            val position = rememberedTracks.indexOfFirst { it.id == incoming.id }
            val snapshot = incoming.recordSnapshot(rememberedTracks.getOrNull(position))
            if (position >= 0) rememberedTracks[position] = snapshot else rememberedTracks.add(snapshot)
            metadataCache?.let { cache -> runCatching {
                cache.storage.write(CacheCategory.METADATA, "records/${CacheStorage.sha256(incoming.id.toByteArray())}.json",
                    snapshot.toJson().toString().toByteArray(Charsets.UTF_8))
            } }
        }
        registerIdentities(newTracks)
        if (persist) save()
    }

    /** Drop only reconstructible in-memory metadata after the corresponding cache category is cleared. */
    @Synchronized fun pruneUnreferencedMetadata() {
        val userRecords = userRecordIds()
        rememberedTracks.removeAll { canonicalTrackId(it.id) !in userRecords }
        val localIds = tracks.map { it.id }.toSet()
        trackAliases.keys.filter { it !in localIds && canonicalTrackId(it) !in userRecords }.toList().forEach { trackAliases.remove(it) }
        save()
    }

    @Synchronized fun saveTracks(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks.filterNot { TrackIdentity.isStreaming(it) }.distinctBy { it.id })
        rememberTracks(newTracks, persist = false)
        playlists.forEach { playlist ->
            val uniqueIds = playlist.trackIds.distinct()
            playlist.trackIds.clear()
            playlist.trackIds.addAll(uniqueIds)
        }
        save()
    }

    @Synchronized fun addHistory(trackId: String, positionMs: Long = 0L) {
        val canonical = canonicalTrackId(trackId)
        playCounts[canonical] = (playCounts[canonical] ?: 0).toLong().plus(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        history.removeAll { canonicalTrackId(it.trackId) == canonical }
        history.add(0, HistoryItem(canonical, System.currentTimeMillis(), positionMs))
        while (history.size > HISTORY_LIMIT) history.removeAt(history.lastIndex)
        save()
    }

    fun playCount(trackId: String): Int = playCounts[canonicalTrackId(trackId)] ?: 0

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
        if (!playlist.canManuallyEditTracks()) return
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        playlist.trackIds.removeAll(ids.toSet())
        playlist.trackIds.addAll(0, ids)
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        if (!playlist.canManuallyEditTracks()) return
        playlist.trackIds.remove(trackId)
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun removeTracksFromPlaylist(playlistId: String, trackIds: Collection<String>) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        if (!playlist.canManuallyEditTracks()) return
        playlist.trackIds.removeAll(trackIds.toSet())
        playlist.updatedAt = System.currentTimeMillis()
        save()
    }

    fun removeTracksEverywhere(trackIds: Collection<String>) {
        val ids = trackIds.toSet()
        val remaining = knownTracks().filterNot { it.id in ids }
        val erasedRecords = ids.map { canonicalTrackId(it) }.filter { canonical ->
            remaining.none { canonicalTrackId(it.id) == canonical }
        }.toSet()
        playlists.forEach { playlist ->
            playlist.trackIds.removeAll(ids)
            playlist.updatedAt = System.currentTimeMillis()
        }
        rememberedTracks.removeAll { it.id in ids }
        history.removeAll { canonicalTrackId(it.trackId) in erasedRecords }
        trackAliases.keys.filter { canonicalTrackId(it) in erasedRecords }.plus(erasedRecords).forEach {
            edits.remove(it)
            playCounts.remove(it)
        }
        trackAliases.keys.filter { canonicalTrackId(it) in erasedRecords }.toList().forEach { trackAliases.remove(it) }
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

    fun isFavorite(trackId: String): Boolean = favoritePlaylist().trackIds.any { canonicalTrackId(it) == canonicalTrackId(trackId) }

    fun setFavorite(trackId: String, favorite: Boolean) {
        val playlist = favoritePlaylist()
        val canonical = canonicalTrackId(trackId)
        playlist.trackIds.removeAll { canonicalTrackId(it) == canonical }
        if (favorite) {
            playlist.trackIds.add(0, canonical)
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
            .map { (album, albumTracks) -> "专辑：$album" to albumTracks.sortedWith(compareBy<Track> { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.displayTitle.lowercase() }).map { it.id }.distinct() }
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
            .put("webDavServers", JSONArray(webDavServers.map { server ->
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("alias", server.alias)
                    .put("url", server.url)
                    .put("username", server.username)
                    .put("password", server.password)
                    .put("port", server.port)
                    .put("ignoreCert", server.ignoreCert)
            }))
            .put("subsonicServers", JSONArray(subsonicServers.map { server ->
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("url", server.url)
                    .put("username", server.username)
                    .put("password", server.password)
                    .put("port", server.port)
                    .put("ignoreCert", server.ignoreCert)
            }))
            .put("selectedSubsonicServerId", selectedSubsonicServerId)
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
        root.optJSONArray("webDavServers").forEachObject { obj ->
            val server = WebDavServer(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name"),
                alias = obj.optString("alias", ""),
                url = obj.optString("url"),
                username = obj.optString("username"),
                password = obj.optString("password"),
                port = obj.optInt("port", 0),
                ignoreCert = obj.optBoolean("ignoreCert", false)
            )
            if (webDavServers.none { it.url == server.url }) {
                webDavServers += server
            }
        }
        root.optJSONArray("subsonicServers").forEachObject { obj ->
            val server = SubsonicServer(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name"),
                url = obj.optString("url"),
                username = obj.optString("username"),
                password = obj.optString("password"),
                port = obj.optInt("port", 0),
                ignoreCert = obj.optBoolean("ignoreCert", false)
            )
            if (subsonicServers.none { it.url == server.url && it.username == server.username }) {
                subsonicServers += server
            }
        }
        selectedSubsonicServerId = root.optString("selectedSubsonicServerId", selectedSubsonicServerId)
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
                .filter { id -> trackForRecord(id) != null }
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
        val existingTrackIds = knownTracks().map { it.id }.toSet() + trackAliases.values
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
        playlists.removeAll { it.systemType in setOf("album", "cue") && it.trackIds.isEmpty() }
    }

    private fun ensureSystemPlaylists() {
        if (playlists.none { it.id == FAVORITES_ID }) {
            playlists.add(0, Playlist(FAVORITES_ID, "我喜欢的音乐", mutableListOf(), "favorites"))
            save()
        }
    }

    private fun load() {
        val root = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject() else JSONObject()
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

        root.optJSONArray("webDavServers").forEachObject { obj ->
            webDavServers += WebDavServer(
                id = obj.optString("id"),
                name = obj.optString("name"),
                alias = obj.optString("alias", ""),
                url = obj.optString("url"),
                username = obj.optString("username"),
                password = obj.optString("password"),
                port = obj.optInt("port", 0),
                ignoreCert = obj.optBoolean("ignoreCert", false)
            )
        }

        root.optJSONArray("subsonicServers").forEachObject { obj ->
            subsonicServers += SubsonicServer(
                id = obj.optString("id"),
                name = obj.optString("name"),
                url = obj.optString("url"),
                username = obj.optString("username"),
                password = obj.optString("password"),
                port = obj.optInt("port", 0),
                ignoreCert = obj.optBoolean("ignoreCert", false)
            )
        }
        selectedSubsonicServerId = root.optString("selectedSubsonicServerId", "")
        root.optJSONArray("rememberedTracks").forEachObject { rememberedTracks.add(it.toTrack().recordSnapshot()) }
        val aliases = root.optJSONObject("trackAliases") ?: JSONObject()
        aliases.keys().forEach { key ->
            aliases.optString(key).takeIf { it.isNotBlank() }?.let { trackAliases[key] = it }
        }
        registerIdentities(knownTracks())
    }

    @Synchronized fun save() {
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
        root.put("recordSchema", 1)
        val userRecords = userRecordIds()
        root.put("rememberedTracks", JSONArray(rememberedTracks.filter { canonicalTrackId(it.id) in userRecords }.map { it.recordSnapshot().toJson() }))
        val localIds = tracks.map { it.id }.toSet()
        root.put("trackAliases", JSONObject(trackAliases.filter { it.key in localIds || canonicalTrackId(it.key) in userRecords }))

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

        root.put("webDavServers", JSONArray(webDavServers.map { server ->
            JSONObject()
                .put("id", server.id)
                .put("name", server.name)
                .put("alias", server.alias)
                .put("url", server.url)
                .put("username", server.username)
                .put("password", server.password)
                .put("port", server.port)
                .put("ignoreCert", server.ignoreCert)
        }))

        root.put("subsonicServers", JSONArray(subsonicServers.map { server ->
            JSONObject()
                .put("id", server.id)
                .put("name", server.name)
                .put("url", server.url)
                .put("username", server.username)
                .put("password", server.password)
                .put("port", server.port)
                .put("ignoreCert", server.ignoreCert)
        }))
        root.put("selectedSubsonicServerId", selectedSubsonicServerId)

        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(root.toString(2))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun registerIdentities(incoming: Collection<Track>) {
        val known = (knownTracks() + incoming).associateBy { it.id }.values
        val byTitle = known.groupBy { TrackIdentity.normalize(it.title) }
        val artistsByGroup = known.groupBy { canonicalTrackId(it.id) }.mapValues { (_, group) ->
            group.map { TrackIdentity.artist(it.artist) }.filter { it.isNotBlank() }.toSet()
        }.toMutableMap()
        incoming.forEach { track ->
            if (track.id.isBlank()) return@forEach
            run {
                val oldCanonical = canonicalTrackId(track.id)
                val oldGroupArtists = artistsByGroup[oldCanonical].orEmpty()
                val candidates = byTitle[TrackIdentity.normalize(track.title)].orEmpty().filter { candidate ->
                    candidate.id != track.id && trackAliases.containsKey(candidate.id) &&
                        canonicalTrackId(candidate.id) != oldCanonical && TrackIdentity.matches(track, candidate)
                }.groupBy { canonicalTrackId(it.id) }.filter { (canonical, _) ->
                    val incomingArtist = TrackIdentity.artist(track.artist)
                    val groupArtists = artistsByGroup[canonical].orEmpty()
                    val combinedArtists = groupArtists + oldGroupArtists + listOf(incomingArtist).filter { it.isNotBlank() }
                    combinedArtists.size <= 1
                }
                val match = candidates.keys.singleOrNull()
                val incomingArtist = TrackIdentity.artist(track.artist)
                if (match != null && (incomingArtist.isBlank() || oldGroupArtists.all { it == incomingArtist })) {
                    trackAliases.keys.filter { canonicalTrackId(it) == oldCanonical }.forEach { trackAliases[it] = match }
                    trackAliases[track.id] = match
                    artistsByGroup[match] = artistsByGroup[match].orEmpty() + oldGroupArtists
                    artistsByGroup.remove(oldCanonical)
                } else if (!trackAliases.containsKey(track.id)) trackAliases[track.id] = track.id
            }
        }
        // Collapse legacy per-source entries once; subsequent plays are added only to the logical key.
        trackAliases.keys.toList().forEach { trackAliases[it] = canonicalTrackId(it) }
        trackAliases.keys.groupBy { canonicalTrackId(it) }.forEach { (canonical, sourceIds) ->
            val count = sourceIds.sumOf { (playCounts[it] ?: 0).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            sourceIds.forEach { playCounts.remove(it) }
            if (count > 0) playCounts[canonical] = count
            val existingEdits = (listOfNotNull(edits[canonical]) + sourceIds.mapNotNull { edits[it] }).distinct()
            if (existingEdits.isNotEmpty()) {
                val merged = TrackEdit(
                    alias = existingEdits.firstOrNull { it.alias.isNotBlank() }?.alias.orEmpty(),
                    comment = existingEdits.map { it.comment }.filter { it.isNotBlank() }.distinct().joinToString("\n\n"),
                    tags = existingEdits.flatMap { it.normalizedTags() }.distinct(),
                    rating = existingEdits.maxOf { it.rating }.coerceIn(0, 5)
                )
                sourceIds.forEach { edits[it] = merged }
            }
        }
        val logicalHistory = history.sortedByDescending { it.playedAt }.distinctBy { canonicalTrackId(it.trackId) }
            .take(HISTORY_LIMIT).map { it.copy(trackId = canonicalTrackId(it.trackId)) }
        history.clear(); history.addAll(logicalHistory)
        playlists.firstOrNull { it.id == FAVORITES_ID }?.let { favorite ->
            val ids = favorite.trackIds.map { canonicalTrackId(it) }.distinct()
            favorite.trackIds.clear(); favorite.trackIds.addAll(ids)
        }
    }

    private fun userRecordIds(): Set<String> = (history.map { it.trackId } + playCounts.keys + edits.keys +
        playlists.filter { it.systemType.isBlank() || it.systemType == "favorites" }.flatMap { it.trackIds })
        .map { canonicalTrackId(it) }.toSet()

    private fun cachedTrack(trackId: String): Track? = runCatching {
        val cache = metadataCache ?: return@runCatching null
        val entry = cache.storage.find(CacheCategory.METADATA, "records/${CacheStorage.sha256(trackId.toByteArray())}.json")
            ?.takeIf { it.length() <= 512 * 1024 } ?: return@runCatching null
        val track = JSONObject(entry.readText()).toTrack().recordSnapshot().takeIf { it.id == trackId } ?: return@runCatching null
        if (rememberedTracks.none { it.id == track.id }) rememberedTracks.add(track)
        registerIdentities(listOf(track))
        track
    }.getOrNull()

    companion object {
        const val FAVORITES_ID = "system:favorites"
        const val HISTORY_ID = "system:history"
        const val LOCAL_ID = "system:local"
        const val RANKING_ID = "system:ranking"
        const val HISTORY_LIMIT = 100
    }
}

private fun Playlist.canManuallyEditTracks(): Boolean {
    return systemType.isBlank()
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
        .put("sourceReference", sourceReference)
        .put("artworkReference", artworkReference)
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
        cueEndMs = optLong("cueEndMs"),
        sourceReference = optString("sourceReference"),
        artworkReference = optString("artworkReference")
    )
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

/** Keep source references and metadata, never temporary signed/authenticated playback URLs. */
private fun Track.recordSnapshot(previous: Track? = null): Track {
    fun publicAddress(value: String): String {
        if (!value.startsWith("https://") && !value.startsWith("http://")) return value
        return runCatching {
            val uri = java.net.URI(value)
            java.net.URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toASCIIString()
        }.getOrElse { value.substringBefore('?').substringBefore('#').replace(Regex("^(https?://)[^/@]+@"), "$1") }
    }
    fun isRemote(value: String) = value.startsWith("https://") || value.startsWith("http://")
    val original = listOf(sourceReference, sourcePath, previous?.sourceReference.orEmpty(), previous?.sourcePath.orEmpty(), uri)
        .firstOrNull { isRemote(it) }?.let(::publicAddress).orEmpty()
    val dizzyPage = if (id.startsWith("stream:dizzylab:")) {
        val disc = id.removePrefix("stream:dizzylab:").substringBefore(':')
        "https://www.dizzylab.net/d/" + disc + "/"
    } else ""
    val savedUri = when {
        id.startsWith("stream:subsonic:") -> "subsonic://record/" + id.removePrefix("stream:subsonic:")
        id.startsWith("stream:dizzylab:") -> "https://www.dizzylab.net/stream-record/" + java.net.URLEncoder.encode(id,"UTF-8")
        id.startsWith("webdav:") -> original.ifBlank { "webdav://record/" + id.removePrefix("webdav:") }
        else -> publicAddress(uri)
    }
    val savedReference = when {
        id.startsWith("stream:subsonic:") -> listOf(sourceReference,sourcePath,previous?.sourceReference.orEmpty()).firstOrNull { it.startsWith("subsonic|") }
            ?: "subsonic|" + id.removePrefix("stream:subsonic:").replace(':','|')
        id.startsWith("stream:dizzylab:") -> dizzyPage
        TrackIdentity.isStreaming(this) -> original
        else -> sourceReference
    }
    val savedPath = when {
        id.startsWith("stream:subsonic:") -> savedReference
        id.startsWith("stream:dizzylab:") -> dizzyPage
        id.startsWith("webdav:") -> original.ifBlank { savedUri }
        else -> publicAddress(sourcePath)
    }
    val savedArtworkReference = if (id.startsWith("stream:subsonic:"))
        listOf(artworkReference, previous?.artworkReference.orEmpty()).firstOrNull { it.startsWith("subsonic|") }.orEmpty()
        else publicAddress(artworkReference.ifBlank { previous?.artworkReference.orEmpty() })
    return copy(uri = savedUri, sourcePath = savedPath, sourceReference = savedReference,
        artworkPath = publicAddress(artworkPath), artworkReference = savedArtworkReference)
}

