package com.supermite.smp.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.text.Collator
import java.util.Locale

class MusicScanner(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    var skipNoMediaFolders: Boolean = false

    fun scanMediaStore(): List<Track> {
        val lyricsByBaseName = scanLyricFiles()
        val tracks = scanAudioCollection(lyricsByBaseName).toMutableList()
        tracks += scanCueFiles(tracks)
        return tracks.distinctBy { it.id }.sortedWith(trackComparator)
    }

    fun scanDocumentTree(treeUri: Uri): List<Track> {
        val documents = mutableListOf<DocumentItem>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        collectDocuments(treeUri, rootUri, "", documents)
        return buildTracksFromDocuments(documents)
    }

    fun scanDocumentFiles(uris: List<Uri>): List<Track> {
        val documents = uris.map { uri ->
            DocumentItem(
                name = queryDisplayName(uri),
                uri = uri,
                relativePath = "",
                mimeType = resolver.getType(uri).orEmpty()
            )
        }
        return buildTracksFromDocuments(documents)
    }

    fun readText(uriText: String): String? {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val charsets = listOf(Charsets.UTF_8, charset("GB18030"), Charsets.ISO_8859_1)
        return charsets
            .mapNotNull { charset -> runCatching { String(bytes, charset) }.getOrNull() }
            .minByOrNull { text -> text.count { it == '\uFFFD' } }
    }

    private fun scanAudioCollection(lyricsByBaseName: Map<String, String>): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        val result = mutableListOf<Track>()
        val fileAlbumIndexCache = mutableMapOf<String, AlbumIndex?>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val displayName = cursor.getString(nameIndex).orEmpty()
                val filePath = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                val sourcePath = filePath.ifBlank { displayName }
                val fileName = filePath.substringAfterLast(File.separatorChar).ifBlank { displayName }
                val id = "media:$mediaId"
                val metadata = readMetadata(uri, id)
                val fileAlbumIndex = fileAlbumIndexForPath(filePath, fileAlbumIndexCache)
                val indexedTrack = fileAlbumIndex?.trackForName(fileName)
                val indexedArtwork = indexedTrack?.coverUri.orEmpty().ifBlank { fileAlbumIndex?.coverUri.orEmpty() }
                result += Track(
                    id = id,
                    uri = uri.toString(),
                    title = indexedTrack?.title.orEmpty().ifBlank { cursor.getString(titleIndex).orEmpty() }.ifBlank { metadata.title }.ifBlank { displayName.substringBeforeLast('.') },
                    artist = splitArtists(indexedTrack?.artist.orEmpty().ifBlank { cursor.getString(artistIndex).orEmpty() }.ifBlank { metadata.artist }.ifBlank { fileAlbumIndex?.circle.orEmpty() }),
                    album = indexedTrack?.album.orEmpty().ifBlank { fileAlbumIndex?.albumTitle.orEmpty() }.ifBlank { cursor.getString(albumColumnIndex).orEmpty() }.ifBlank { metadata.album },
                    durationMs = (indexedTrack?.durationMs ?: 0L).takeIf { it > 0 } ?: cursor.getLong(durationIndex).coerceAtLeast(metadata.durationMs),
                    trackNumber = (indexedTrack?.trackNumber ?: 0).takeIf { it > 0 } ?: normalizeTrackNumber(cursor.getInt(trackIndex)).takeIf { it > 0 } ?: metadata.trackNumber,
                    year = cursor.getInt(yearIndex).takeIf { it > 0 } ?: metadata.year,
                    date = indexedTrack?.date.orEmpty().ifBlank { fileAlbumIndex?.releaseDate.orEmpty() }.ifBlank { metadata.date },
                    composer = indexedTrack?.composer.orEmpty().ifBlank { metadata.composer },
                    mimeType = cursor.getString(mimeIndex).orEmpty(),
                    sourcePath = sourcePath,
                    lyricsUri = lyricsByBaseName[baseName(sourcePath)].orEmpty(),
                    artworkPath = indexedArtwork.ifBlank { metadata.artworkPath }
                )
            }
        }
        return result
    }

    private fun fileAlbumIndexForPath(audioPath: String, cache: MutableMap<String, AlbumIndex?>): AlbumIndex? {
        if (audioPath.isBlank()) return null
        val parent = File(audioPath).parentFile ?: return null
        return cache.getOrPut(parent.absolutePath) {
            parseAlbumIndexFile(File(parent, "album.json"))
        }
    }

    private fun parseAlbumIndexFile(file: File): AlbumIndex? {
        if (!file.isFile) return null
        val root = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return null
        val parent = file.parentFile ?: return null
        fun resolveSibling(path: String): String {
            if (path.isBlank() || path.startsWith("content://") || path.startsWith("file://") || path.startsWith("http://") || path.startsWith("https://")) return path
            val direct = File(parent, path.replace('\\', File.separatorChar))
            if (direct.exists()) return direct.absolutePath
            return File(parent, path.replace('\\', '/').substringAfterLast('/')).takeIf { it.exists() }?.absolutePath.orEmpty()
        }
        val tracks = mutableListOf<IndexedTrack>()
        root.optJSONArray("tracks")?.let { array ->
            for (index in 0 until array.length()) {
                val track = array.optJSONObject(index) ?: continue
                tracks += IndexedTrack(
                    file = track.optString("file"),
                    title = track.optString("title"),
                    artist = track.optString("artist"),
                    album = track.optString("album"),
                    trackNumber = track.optInt("trackNumber", index + 1),
                    durationMs = track.optLong("durationMs", 0L),
                    date = track.optString("date"),
                    composer = track.optString("composer"),
                    coverUri = resolveSibling(track.optString("cover"))
                )
            }
        }
        return AlbumIndex(
            relativePath = parent.absolutePath,
            albumTitle = root.optString("albumTitle"),
            circle = root.optString("circle"),
            releaseDate = root.optString("releaseDate"),
            coverUri = resolveSibling(root.optString("cover")),
            trackCount = root.optInt("trackCount", tracks.size),
            tracks = tracks
        )
    }

    private fun scanLyricFiles(): Map<String, String> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val result = mutableMapOf<String, String>()
        runCatching {
            resolver.query(
                collection,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.lrc"),
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    result[baseName(cursor.getString(nameIndex).orEmpty())] = uri.toString()
                }
            }
        }
        return result
    }

    private fun scanCueFiles(mediaTracks: List<Track>): List<Track> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val audioByName = mediaTracks.associateBy { it.sourcePath.lowercase(Locale.ROOT) }
        val result = mutableListOf<Track>()
        runCatching {
            resolver.query(
                collection,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.cue"),
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val cueUri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    result += buildCueTracks(DocumentItem(cursor.getString(nameIndex).orEmpty(), cueUri, "", "text/plain"), audioByName)
                }
            }
        }
        return result
    }

    private fun buildTracksFromDocuments(documents: List<DocumentItem>): List<Track> {
        val lyricsByBaseName = documents.filter { it.isLyrics }.associate { baseName(it.name) to it.uri.toString() }
        val documentsByPath = documents.associateBy { listOf(it.relativePath, it.name).filter { part -> part.isNotBlank() }.joinToString("/").lowercase(Locale.ROOT) }
        val albumIndexes = documents.filter { it.isAlbumIndex }.mapNotNull { parseAlbumIndex(it, documentsByPath) }.associateBy { it.relativePath }
        val artworkByFolder = documents.filter { it.isImage }
            .groupBy { it.relativePath }
            .mapValues { (_, images) -> preferredArtwork(images)?.uri?.toString().orEmpty() }
        val audioByFileName = mutableMapOf<String, Track>()
        val tracks = documents
            .filter { it.isAudio }
            .map { item ->
                val albumIndex = albumIndexes[item.relativePath]
                val indexedTrack = albumIndex?.trackFor(item)
                val artwork = indexedTrack?.coverUri.orEmpty()
                    .ifBlank { albumIndex?.coverUri.orEmpty() }
                    .ifBlank { artworkByFolder[item.relativePath].orEmpty() }
                buildTrackFromDocument(item, lyricsByBaseName, artwork, albumIndex, indexedTrack).also { track ->
                    audioByFileName[item.name.lowercase(Locale.ROOT)] = track
                }
            }
            .toMutableList()

        documents.filter { it.isCue }.forEach { cue ->
            tracks += buildCueTracks(cue, audioByFileName)
        }

        return tracks.distinctBy { it.id }.sortedWith(trackComparator)
    }

    private fun buildTrackFromDocument(
        item: DocumentItem,
        lyricsByBaseName: Map<String, String>,
        folderArtworkUri: String,
        albumIndex: AlbumIndex? = null,
        indexedTrack: IndexedTrack? = null
    ): Track {
        val id = "doc:${item.uri}"
        val metadata = readMetadata(item.uri, id)
        val fallbackTitle = item.name.substringBeforeLast('.')
        return Track(
            id = id,
            uri = item.uri.toString(),
            title = indexedTrack?.title.orEmpty().ifBlank { metadata.title }.ifBlank { fallbackTitle },
            artist = splitArtists(indexedTrack?.artist.orEmpty().ifBlank { metadata.artist }.ifBlank { albumIndex?.circle.orEmpty() }),
            album = indexedTrack?.album.orEmpty().ifBlank { albumIndex?.albumTitle.orEmpty() }.ifBlank { metadata.album },
            durationMs = (indexedTrack?.durationMs ?: 0L).takeIf { it > 0 } ?: metadata.durationMs,
            trackNumber = (indexedTrack?.trackNumber ?: 0).takeIf { it > 0 } ?: metadata.trackNumber,
            year = metadata.year,
            date = indexedTrack?.date.orEmpty().ifBlank { albumIndex?.releaseDate.orEmpty() }.ifBlank { metadata.date },
            composer = indexedTrack?.composer.orEmpty().ifBlank { metadata.composer },
            mimeType = item.mimeType,
            sourcePath = listOf(item.relativePath, item.name).filter { it.isNotBlank() }.joinToString("/"),
            lyricsUri = lyricsByBaseName[baseName(item.name)].orEmpty(),
            artworkPath = metadata.artworkPath.ifBlank { folderArtworkUri }
        )
    }

    private fun parseAlbumIndex(item: DocumentItem, documentsByPath: Map<String, DocumentItem>): AlbumIndex? {
        val text = readText(item.uri.toString()) ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val relativePath = item.relativePath
        fun resolveSibling(path: String): String {
            if (path.isBlank() || path.startsWith("content://") || path.startsWith("file://") || path.startsWith("http://") || path.startsWith("https://")) return path
            val normalized = path.replace('\\', '/').substringAfterLast('/')
            val key = listOf(relativePath, normalized).filter { it.isNotBlank() }.joinToString("/").lowercase(Locale.ROOT)
            return documentsByPath[key]?.uri?.toString().orEmpty()
        }
        val tracks = mutableListOf<IndexedTrack>()
        val array = root.optJSONArray("tracks")
        if (array != null) {
            for (index in 0 until array.length()) {
                val track = array.optJSONObject(index) ?: continue
                val file = track.optString("file")
                tracks += IndexedTrack(
                    file = file,
                    title = track.optString("title"),
                    artist = track.optString("artist"),
                    album = track.optString("album"),
                    trackNumber = track.optInt("trackNumber", index + 1),
                    durationMs = track.optLong("durationMs", 0L),
                    date = track.optString("date"),
                    composer = track.optString("composer"),
                    coverUri = resolveSibling(track.optString("cover"))
                )
            }
        }
        return AlbumIndex(
            relativePath = relativePath,
            albumTitle = root.optString("albumTitle"),
            circle = root.optString("circle"),
            releaseDate = root.optString("releaseDate"),
            coverUri = resolveSibling(root.optString("cover")),
            trackCount = root.optInt("trackCount", tracks.size),
            tracks = tracks
        )
    }

    private fun preferredArtwork(images: List<DocumentItem>): DocumentItem? {
        if (images.isEmpty()) return null
        val preferredNames = listOf("cover", "folder", "album", "front")
        return images.minWithOrNull(
            compareBy<DocumentItem> { item ->
                val name = item.name.substringBeforeLast('.').lowercase(Locale.ROOT)
                preferredNames.indexOfFirst { name.contains(it) }.takeIf { it >= 0 } ?: preferredNames.size
            }.thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    private fun buildCueTracks(cue: DocumentItem, audioByFileName: Map<String, Track>): List<Track> {
        val cueText = readText(cue.uri.toString()) ?: return emptyList()
        val parsed = CueParser.parse(cueText)
        if (parsed.isEmpty()) return emptyList()

        return parsed.mapIndexedNotNull { index, cueTrack ->
            val fileKey = cueTrack.fileName.replace('\\', '/').substringAfterLast('/').lowercase(Locale.ROOT)
            val source = audioByFileName[cueTrack.fileName.lowercase(Locale.ROOT)]
                ?: audioByFileName[fileKey]
                ?: return@mapIndexedNotNull null
            val nextStart = parsed.getOrNull(index + 1)?.startMs ?: 0L
            val end = when {
                nextStart > cueTrack.startMs -> nextStart
                source.durationMs > cueTrack.startMs -> source.durationMs
                else -> 0L
            }
            source.copy(
                id = "cue:${cue.uri}#${cueTrack.number}",
                title = cueTrack.title,
                artist = splitArtists(cueTrack.performer.ifBlank { source.artist }),
                album = source.album,
                durationMs = if (end > cueTrack.startMs) end - cueTrack.startMs else source.durationMs,
                trackNumber = cueTrack.number,
                date = source.date,
                composer = source.composer,
                cueSheet = cue.uri.toString(),
                cueIndex = cueTrack.number,
                cueStartMs = cueTrack.startMs,
                cueEndMs = end
            )
        }
    }

    private fun collectDocuments(treeUri: Uri, directoryUri: Uri, relativePath: String, out: MutableList<DocumentItem>) {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
        val children = mutableListOf<DocumentItem>()
        resolver.query(
            childUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex).orEmpty()
                val mimeType = cursor.getString(mimeIndex).orEmpty()
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                children += DocumentItem(name, uri, relativePath, mimeType)
            }
        }
        if (skipNoMediaFolders && children.any { it.name.equals(".nomedia", ignoreCase = true) }) return
        children.forEach { item ->
            if (item.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val nextPath = listOf(relativePath, item.name).filter { it.isNotBlank() }.joinToString("/")
                collectDocuments(treeUri, item.uri, nextPath, out)
            } else {
                out += item
            }
        }
    }

    private fun readMetadata(uri: Uri, trackId: String): Metadata {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            val artworkPath = retriever.embeddedPicture?.let { saveArtwork(trackId, it) }.orEmpty()
            Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                trackNumber = parseTrackNumber(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0,
                date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE).orEmpty(),
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER).orEmpty(),
                artworkPath = artworkPath
            )
        }.getOrDefault(Metadata()).also {
            retriever.release()
        }
    }

    private fun saveArtwork(trackId: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "artwork").apply { mkdirs() }
        val file = File(dir, "${Integer.toHexString(trackId.hashCode())}.jpg")
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            file.writeBytes(bytes)
        }
        return file.absolutePath
    }

    private fun queryDisplayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
    }

    private data class Metadata(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
        val trackNumber: Int = 0,
        val year: Int = 0,
        val date: String = "",
        val composer: String = "",
        val artworkPath: String = ""
    )

    private data class DocumentItem(
        val name: String,
        val uri: Uri,
        val relativePath: String,
        val mimeType: String
    ) {
        val extension: String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isCue: Boolean = extension == "cue"
        val isLyrics: Boolean = extension == "lrc"
        val isAlbumIndex: Boolean = name.equals("album.json", ignoreCase = true)
        val isImage: Boolean = extension in setOf("jpg", "jpeg", "png", "webp")
        val isAudio: Boolean = mimeType.startsWith("audio/") || extension in audioExtensions
    }

    private data class AlbumIndex(
        val relativePath: String,
        val albumTitle: String,
        val circle: String,
        val releaseDate: String,
        val coverUri: String,
        val trackCount: Int,
        val tracks: List<IndexedTrack>
    ) {
        fun trackFor(item: DocumentItem): IndexedTrack? {
            return trackForName(item.name)
        }

        fun trackForName(name: String): IndexedTrack? {
            val normalized = name.replace('\\', '/').substringAfterLast('/')
            val itemName = normalized.lowercase(Locale.ROOT)
            val itemBase = normalized.substringBeforeLast('.').lowercase(Locale.ROOT)
            return tracks.firstOrNull { track ->
                val fileName = track.file.replace('\\', '/').substringAfterLast('/').lowercase(Locale.ROOT)
                fileName == itemName || fileName.substringBeforeLast('.') == itemBase
            }
        }
    }

    private data class IndexedTrack(
        val file: String,
        val title: String,
        val artist: String,
        val album: String,
        val trackNumber: Int,
        val durationMs: Long,
        val date: String,
        val composer: String,
        val coverUri: String
    )

    companion object {
        private val audioExtensions = setOf(
            "mp3", "flac", "wav", "m4a", "mp4", "aac", "ogg", "opus", "wma", "mid", "midi"
        )
        private val zhCollator: Collator = Collator.getInstance(Locale.CHINA)

        val trackComparator: Comparator<Track> = Comparator { left, right ->
            val titleCompare = zhCollator.compare(left.displayTitle, right.displayTitle)
            if (titleCompare != 0) titleCompare else left.displayArtist.compareTo(right.displayArtist, ignoreCase = true)
        }

        private fun normalizeTrackNumber(value: Int): Int {
            if (value <= 0) return 0
            return if (value > 1000) value % 1000 else value
        }

        private fun parseTrackNumber(value: String?): Int {
            return normalizeTrackNumber(value?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0)
        }

        private fun baseName(name: String): String {
            return name.replace('\\', '/')
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .lowercase(Locale.ROOT)
        }

        private fun splitArtists(value: String): String {
            return value.split(Regex("""\s*(?:;|；|/|\\|、|,|，|&|\+| feat\.? | ft\.? | and )\s*""", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" / ")
        }
    }
}

