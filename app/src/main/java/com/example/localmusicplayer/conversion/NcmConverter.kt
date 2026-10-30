package com.supermite.smp.conversion

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.supermite.smp.cache.CacheManager
import com.supermite.smp.cache.CacheWorkFile
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class NcmConverter(private val context: Context) {
    data class Result(val outputFile: File, val title: String, val format: String, val mediaScanRequested: Boolean = false,
        private val temporaryWork: CacheWorkFile? = null) : AutoCloseable {
        val fileName: String get() = NcmFileNames.sanitize(title) + "." + format
        val isTemporary: Boolean get() = temporaryWork != null
        override fun close() { temporaryWork?.close() }
    }

    fun convert(uri: Uri, outputDir: File? = null): Result = convertInternal(uri, outputDir, temporary = false)
    fun convertTemporary(uri: Uri): Result = convertInternal(uri, null, temporary = true)

    private fun convertInternal(uri: Uri, outputDir: File?, temporary: Boolean): Result {
        var work: CacheWorkFile? = null
        try {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法打开文件")
        BufferedInputStream(input).use { stream ->
            val header = readExact(stream, 10)
            require(header.copyOfRange(0, 8).contentEquals("CTENFDAM".toByteArray(Charsets.US_ASCII))) { "不是有效的 NCM 文件" }
            val rc4Key = readRc4Key(stream)
            val metadata = readMetadata(stream)
            val format = metadata.optString("format").ifBlank { "mp3" }.lowercase(Locale.ROOT)
            require(format in setOf("mp3", "flac")) { "不支持的 NCM 音频格式" }
            val title = metadata.optString("musicName").ifBlank { "converted_music" }
            val coverBytes = readCover(stream)

            val output = if (temporary) {
                work = CacheManager(context).createWorkFile(format) ?: error("转换临时缓存已关闭或无法创建，请检查缓存设置")
                work!!.file
            } else {
                val targetDir = (outputDir ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "ConvertedMusic")).apply {
                    check(isDirectory || mkdirs()) { "无法创建转换输出目录" }
                }
                uniqueFile(targetDir, sanitizeFileName(title), format)
            }
            (work?.outputStream() ?: output.outputStream()).use { out ->
                val keyStream = buildKeyStream(rc4Key)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var keyOffset = 0
                var total = 0L
                try {
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        NcmCipher.apply(buffer, read, keyStream, keyOffset)
                        keyOffset = (keyOffset + read) and 0xff
                        out.write(buffer, 0, read)
                        total += read
                    }
                    require(total > 0) { "NCM 文件没有音频数据" }
                } catch (error: Throwable) {
                    output.delete()
                    throw error
                }
            }
            val artists = metadata.optJSONArray("artist")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONArray(index)?.optString(0)
                }.filter { it.isNotBlank() }
            }.orEmpty()
            AudioMetadataWriter().write(
                output,
                AudioMetadataUpdate(
                    title = title,
                    artist = artists.joinToString(" / "),
                    album = metadata.optString("album"),
                    coverBytes = coverBytes
                )
            )
            work?.validate()
            val scanRequested = !temporary && runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath),
                    arrayOf(if (format == "flac") "audio/flac" else "audio/mpeg"), null)
            }.isSuccess
            return Result(output, title, format, scanRequested, work)
        }
        } catch (error: Throwable) { work?.close(); throw error }
    }

    private fun readRc4Key(stream: InputStream): ByteArray {
        val keyLength = readLittleEndianInt(stream)
        require(keyLength in 1 until 1024) { "NCM 密钥长度异常" }
        val encrypted = readExact(stream, keyLength)
        for (index in encrypted.indices) encrypted[index] = (encrypted[index].toInt() xor 0x64).toByte()
        val decrypted = aesDecrypt(CORE_KEY, encrypted)
        require(decrypted.size > 17) { "NCM 密钥内容异常" }
        return decrypted.copyOfRange(17, decrypted.size)
    }

    private fun readMetadata(stream: InputStream): JSONObject {
        val metaLength = readLittleEndianInt(stream)
        require(metaLength in 0..4 * 1024 * 1024) { "NCM 元数据长度异常" }
        if (metaLength == 0) return JSONObject()
        require(metaLength >= 22) { "NCM 元数据不完整" }
        val raw = readExact(stream, metaLength)
        for (index in raw.indices) raw[index] = (raw[index].toInt() xor 0x63).toByte()
        val payload = raw.copyOfRange(22, raw.size)
        val decrypted = aesDecrypt(META_KEY, Base64.getDecoder().decode(payload))
        val jsonText = String(decrypted, Charsets.UTF_8).removePrefix("music:")
        return JSONObject(jsonText)
    }

    private fun readCover(stream: InputStream): ByteArray? {
        skipFully(stream, 5)
        val coverFrameLength = readLittleEndianInt(stream)
        val imageLength = readLittleEndianInt(stream)
        require(coverFrameLength in 0..20 * 1024 * 1024 && imageLength in 0..coverFrameLength) { "NCM 封面长度异常" }
        val imageBytes = if (imageLength > 0) readExact(stream, imageLength) else ByteArray(0)
        val remaining = coverFrameLength - imageLength
        if (remaining > 0) skipFully(stream, remaining)
        return imageBytes.takeIf { it.isNotEmpty() }
    }

    private fun aesDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun buildKeyStream(key: ByteArray): ByteArray {
        val sBox = ByteArray(256) { it.toByte() }
        var j = 0
        for (i in 0 until 256) {
            j = (j + (sBox[i].toInt() and 0xff) + (key[i % key.size].toInt() and 0xff)) and 0xff
            val temp = sBox[i]
            sBox[i] = sBox[j]
            sBox[j] = temp
        }
        val keyStream = ByteArray(256)
        for (k in 1 until 256) {
            val left = sBox[k].toInt() and 0xff
            val right = sBox[(left + k) and 0xff].toInt() and 0xff
            keyStream[k - 1] = sBox[(left + right) and 0xff]
        }
        val left = sBox[0].toInt() and 0xff
        val right = sBox[left].toInt() and 0xff
        keyStream[255] = sBox[(left + right) and 0xff]
        return keyStream
    }

    private fun readLittleEndianInt(stream: InputStream): Int {
        val bytes = readExact(stream, 4)
        return (bytes[0].toInt() and 0xff) or
            ((bytes[1].toInt() and 0xff) shl 8) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[3].toInt() and 0xff) shl 24)
    }

    private fun readExact(stream: InputStream, length: Int): ByteArray {
        require(length in 0..20 * 1024 * 1024) { "NCM 数据长度异常" }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = stream.read(bytes, offset, length - offset)
            if (read < 0) error("文件内容不完整")
            if (read == 0) {
                val next = stream.read()
                if (next < 0) error("文件内容不完整")
                bytes[offset++] = next.toByte()
            } else offset += read
        }
        return bytes
    }

    private fun skipFully(stream: InputStream, bytes: Int) {
        var remaining = bytes.toLong()
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0L) {
                if (stream.read() < 0) error("文件内容不完整")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun sanitizeFileName(value: String): String {
        return NcmFileNames.sanitize(value)
    }

    private fun uniqueFile(dir: File, name: String, extension: String): File {
        var file = File(dir, "$name.$extension")
        var index = 1
        while (file.exists()) {
            file = File(dir, "$name-$index.$extension")
            index++
        }
        return file
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        val CORE_KEY = byteArrayOf(0x68, 0x7A, 0x48, 0x52, 0x41, 0x6D, 0x73, 0x6F, 0x35, 0x6B, 0x49, 0x6E, 0x62, 0x61, 0x78, 0x57)
        val META_KEY = byteArrayOf(0x23, 0x31, 0x34, 0x6C, 0x6A, 0x6B, 0x5F, 0x21, 0x5C, 0x5D, 0x26, 0x30, 0x55, 0x3C, 0x27, 0x28)
    }
}
