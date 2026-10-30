package com.supermite.smp.conversion

internal object NcmCipher {
    /** Preserve the RC4-derived stream offset across short reads from content providers. */
    fun apply(buffer: ByteArray, length: Int, keyStream: ByteArray, offset: Int) {
        require(keyStream.size == 256 && length in 0..buffer.size)
        for (index in 0 until length) buffer[index] = (buffer[index].toInt() xor keyStream[(offset + index) and 0xff].toInt()).toByte()
    }
}

internal object NcmFileNames {
    fun sanitize(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifBlank { "converted_music" }
        val result = StringBuilder()
        for (codePoint in cleaned.codePoints().toArray()) {
            val text = String(Character.toChars(codePoint))
            if ((result.toString() + text).toByteArray(Charsets.UTF_8).size > 180) break
            result.append(text)
        }
        return result.toString().ifBlank { "converted_music" }
    }
}
