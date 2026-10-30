package com.supermite.smp.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryRecordsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun state() = File(temporary.newFolder(), "library_state.json")
    private fun song(id: String, artist: String = "Artist", album: String = "Album", title: String = "A Song", duration: Long = 180_000) =
        Track(id, if(id.startsWith("stream:")) "https://music.example.invalid/play?token=fixture-only" else "file:///music/$id.mp3",
            title,artist,album,duration,sourcePath=if(id.startsWith("stream:subsonic:"))"subsonic|server|song||album" else "")

    @Test fun sameSongSharesHistoryCountsEditsAndFavoritesAcrossSourcesAfterRestart() {
        val file=state();val store=LibraryStore(file)
        val local=song("local");val remote=song("stream:subsonic:server:song",title="Ａ Song")
        store.saveTracks(listOf(local));store.addHistory(local.id);store.addHistory(local.id)
        store.saveEdit(local.id,TrackEdit(alias="Personal name",tags=listOf("favorite"),rating=3));store.setFavorite(local.id,true)
        store.rememberTracks(listOf(remote));store.addHistory(remote.id)
        val restored=LibraryStore(file)
        assertEquals(3,restored.playCount(local.id));assertEquals(3,restored.playCount(remote.id))
        assertEquals(1,restored.history.size);assertEquals(2,restored.sourceIdsFor(remote.id).size)
        assertEquals("Personal name",restored.edits[remote.id]?.alias);assertTrue(restored.isFavorite(remote.id))
        assertEquals(1,restored.savedTracks().size);assertEquals(2,restored.knownTracks().size)
        assertFalse(file.readText().contains("fixture-only"))
        assertTrue(restored.trackForRecord(remote.id)!!.uri.startsWith("subsonic://record/"))
    }

    @Test fun unknownArtistCannotBridgeDifferentArtistsWithSameAlbumAndTitle() {
        val store=LibraryStore(state())
        val a=song("stream:dizzylab:a:a",artist="Singer A")
        val incomplete=song("stream:dizzylab:b:b",artist="未知艺术家")
        val b=song("stream:dizzylab:c:c",artist="Singer B")
        store.rememberTracks(listOf(a,incomplete,b));store.rememberTracks(listOf(incomplete))
        assertEquals(store.canonicalTrackId(a.id),store.canonicalTrackId(incomplete.id))
        assertNotEquals(store.canonicalTrackId(a.id),store.canonicalTrackId(b.id))
        store.addHistory(a.id);store.addHistory(b.id)
        assertEquals(2,store.history.size)
    }

    @Test fun missingArtistWithAmbiguousAlbumDoesNotGuessAGroup() {
        val store=LibraryStore(state())
        val a=song("stream:dizzylab:a:a",artist="Singer A")
        val b=song("stream:dizzylab:b:b",artist="Singer B")
        val incomplete=song("stream:dizzylab:c:c",artist="")
        store.rememberTracks(listOf(a,b,incomplete))
        assertEquals(incomplete.id,store.canonicalTrackId(incomplete.id))
        assertFalse(TrackIdentity.matches(a,b))
    }

    @Test fun localRescanDoesNotDiscardRemoteMetadataOrMergedRecord() {
        val file=state();val store=LibraryStore(file)
        val local=song("local");val remote=song("stream:dizzylab:disc:song")
        store.saveTracks(listOf(local));store.rememberTracks(listOf(remote));store.addHistory(remote.id)
        store.saveTracks(emptyList())
        val restored=LibraryStore(file)
        assertTrue(restored.savedTracks().isEmpty());assertEquals(1,restored.knownTracks().size)
        assertEquals(remote.id,restored.trackForRecord(restored.history.single().trackId)?.id)
        assertEquals(1,restored.playCount(remote.id))
    }

    @Test fun mergedRatingCanBeLoweredWithoutOldAliasRestoringOldValue() {
        val file=state();val store=LibraryStore(file)
        val a=song("stream:dizzylab:a:a");val b=song("stream:dizzylab:b:b")
        store.rememberTracks(listOf(a,b));store.saveEdit(a.id,TrackEdit(rating=5));store.saveEdit(b.id,TrackEdit(rating=1))
        assertEquals(1,LibraryStore(file).edits[a.id]?.rating)
    }

    @Test fun legacyRecordsMergeOnceWithoutDoublingCountsDuringRepeatedSaves() {
        val file=state()
        fun item(id: String)=JSONObject().put("id",id).put("uri","file:///$id").put("title","A Song").put("artist","Artist").put("album","Album").put("durationMs",180000)
        file.writeText(JSONObject().put("tracks",JSONArray().put(item("one")).put(item("two")))
            .put("playCounts",JSONObject().put("one",2).put("two",3))
            .put("history",JSONArray().put(JSONObject().put("trackId","one").put("playedAt",10)).put(JSONObject().put("trackId","two").put("playedAt",20).put("positionMs",42)))
            .put("edits",JSONObject().put("one",JSONObject().put("rating",2).put("tags",JSONArray(listOf("a")))).put("two",JSONObject().put("rating",4).put("tags",JSONArray(listOf("b")))))
            .toString())
        var store=LibraryStore(file)
        repeat(3){store.save();store=LibraryStore(file)}
        assertEquals(5,store.playCount("one"));assertEquals(5,store.playCount("two"));assertEquals(1,store.history.size)
        assertEquals(42L,store.history.single().positionMs);assertEquals(setOf("a","b"),store.edits["one"]?.tags?.toSet())
        assertEquals(4,store.edits["two"]?.rating)
    }

    @Test fun versionsAndLongDurationDifferencesAreNotCollapsed() {
        assertFalse(TrackIdentity.matches(song("a"),song("b",title="A Song (Live)")))
        assertFalse(TrackIdentity.matches(song("a"),song("b",duration=300_000)))
        assertFalse(TrackIdentity.matches(song("a",artist="",album=""),song("b",artist="",album="")))
    }

    @Test fun browsingMetadataBecomesLongLivedOnlyWhenAssociatedWithUserRecords() {
        val file=state();val store=LibraryStore(file);val remote=song("stream:dizzylab:disc:song")
        store.rememberTracks(listOf(remote))
        assertEquals(0,JSONObject(file.readText()).getJSONArray("rememberedTracks").length())
        store.addHistory(remote.id)
        assertEquals(1,JSONObject(file.readText()).getJSONArray("rememberedTracks").length())
        store.pruneUnreferencedMetadata()
        assertEquals(1,LibraryStore(file).playCount(remote.id))
    }

    @Test fun persistedSourcesRemainResolvableAfterAudioCacheEviction() {
        val file=state();val store=LibraryStore(file)
        val subsonic=song("stream:subsonic:server:song").copy(artworkReference="subsonic|server|cover")
        val dizzy=song("stream:dizzylab:disc:song",title="Dizzy Song")
        val dav=song("webdav:source",title="DAV Song").copy(uri="https://dav.example.invalid/music/song.mp3",sourcePath="https://dav.example.invalid/music/song.mp3")
        store.rememberTracks(listOf(subsonic,dizzy,dav))
        for(track in listOf(subsonic,dizzy,dav)) {
            store.rememberTracks(listOf(track.copy(uri="file:///evicted-cache/audio.bin",sourcePath="/evicted-cache/audio.bin")))
            store.addHistory(track.id)
        }
        val restored=LibraryStore(file)
        assertTrue(restored.trackForRecord(subsonic.id)!!.uri.startsWith("subsonic://record/"))
        assertEquals("subsonic|server|cover",restored.trackForRecord(subsonic.id)!!.artworkReference)
        assertTrue(restored.trackForRecord(dizzy.id)!!.uri.startsWith("https://www.dizzylab.net/stream-record/"))
        assertEquals("https://dav.example.invalid/music/song.mp3",restored.trackForRecord(dav.id)!!.uri)
        assertFalse(file.readText().contains("evicted-cache"))
    }
}
