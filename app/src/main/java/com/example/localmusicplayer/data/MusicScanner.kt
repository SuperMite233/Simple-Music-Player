package com.supermite.smp.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
            MediaStore.Audio.Media.MIME_TYPE
        )
        val result = mutableListOf<Track>()
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
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val displayName = cursor.getString(nameIndex).orEmpty()
                val id = "media:$mediaId"
                val metadata = readMetadata(uri, id)
                result += Track(
                    id = id,
                    uri = uri.toString(),
                    title = cursor.getString(titleIndex).orEmpty().ifBlank { metadata.title }.ifBlank { displayName.substringBeforeLast('.') },
                    artist = splitArtists(cursor.getString(artistIndex).orEmpty().ifBlank { metadata.artist }),
                    album = cursor.getString(albumIndex).orEmpty().ifBlank { metadata.album },
                    durationMs = cursor.getLong(durationIndex).coerceAtLeast(metadata.durationMs),
                    trackNumber = normalizeTrackNumber(cursor.getInt(trackIndex)).takeIf { it > 0 } ?: metadata.trackNumber,
                    year = cursor.getInt(yearIndex).takeIf { it > 0 } ?: metadata.year,
                    mimeType = cursor.getString(mimeIndex).orEmpty(),
                    sourcePath = displayName,
                    lyricsUri = lyricsByBaseName[baseName(displayName)].orEmpty(),
                    artworkPath = metadata.artworkPath
                )
            }
        }
        return result
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
        val audioByFileName = mutableMapOf<String, Track>()
        val tracks = documents
            .filter { it.isAudio }
            .map { item ->
                buildTrackFromDocument(item, lyricsByBaseName).also { track ->
                    audioByFileName[item.name.lowercase(Locale.ROOT)] = track
                }
            }
            .toMutableList()

        documents.filter { it.isCue }.forEach { cue ->
            tracks += buildCueTracks(cue, audioByFileName)
        }

        return tracks.distinctBy { it.id }.sortedWith(trackComparator)
    }

    private fun buildTrackFromDocument(item: DocumentItem, lyricsByBaseName: Map<String, String>): Track {
        val id = "doc:${item.uri}"
        val metadata = readMetadata(item.uri, id)
        val fallbackTitle = item.name.substringBeforeLast('.')
        return Track(
            id = id,
            uri = item.uri.toString(),
            title = metadata.title.ifBlank { fallbackTitle },
            artist = splitArtists(metadata.artist),
            album = metadata.album,
            durationMs = metadata.durationMs,
            trackNumber = metadata.trackNumber,
            year = metadata.year,
            mimeType = item.mimeType,
            sourcePath = listOf(item.relativePath, item.name).filter { it.isNotBlank() }.joinToString("/"),
            lyricsUri = lyricsByBaseName[baseName(item.name)].orEmpty(),
            artworkPath = metadata.artworkPath
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
        val isAudio: Boolean = mimeType.startsWith("audio/") || extension in audioExtensions
    }

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

