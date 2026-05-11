package com.supermite.smp.conversion

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.StandardArtwork
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class AudioMetadataUpdate(
    val title: String = "",
    val artist: String = "",
    val composer: String = "",
    val year: String = "",
    val lyrics: String = "",
    val album: String = "",
    val coverBytes: ByteArray? = null
)

data class AudioMetadataWriteResult(
    val success: Boolean,
    val warnings: List<String> = emptyList()
)

class AudioMetadataWriter {
    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun write(file: File, update: AudioMetadataUpdate): AudioMetadataWriteResult {
        val warnings = mutableListOf<String>()
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            if (update.title.isNotBlank()) tag.setField(FieldKey.TITLE, update.title)
            if (update.artist.isNotBlank()) tag.setField(FieldKey.ARTIST, update.artist)
            if (update.composer.isNotBlank()) tag.setField(FieldKey.COMPOSER, update.composer)
            if (update.year.isNotBlank()) tag.setField(FieldKey.YEAR, update.year)
            if (update.album.isNotBlank()) tag.setField(FieldKey.ALBUM, update.album)
            if (update.lyrics.isNotBlank()) {
                runCatching { tag.setField(FieldKey.LYRICS, update.lyrics) }
                    .onFailure { warnings += "歌词字段写入失败：${it.message ?: "不支持该格式"}" }
            }
            update.coverBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
                runCatching {
                    val artwork = StandardArtwork().apply {
                        binaryData = bytes
                        mimeType = detectImageMimeType(bytes)
                        pictureType = 3
                    }
                    tag.setField(artwork)
                }.onFailure {
                    warnings += "封面写入失败：${it.message ?: "不支持该格式"}"
                }
            }
            audio.commit()
            AudioMetadataWriteResult(success = true, warnings = warnings)
        } catch (error: Exception) {
            AudioMetadataWriteResult(success = false, warnings = listOf(error.message ?: "元数据写入失败"))
        }
    }

    private fun detectImageMimeType(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "image/jpeg"
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return "image/webp"
        return "image/jpeg"
    }
}
