package com.supermite.smp.cache

import java.io.File
import java.net.URI
import java.net.URLDecoder

/** Durable URL aliases + content-addressed blobs; neither URLs nor account tokens are written to disk. */
class ArtworkCache(private val storage: CacheStorage) {
    private val locks = Array(64) { Any() }

    fun cachedPath(url: String): String {
        if (url.isBlank()) return ""
        val alias = storage.find(CacheCategory.ARTWORK, aliasName(url)) ?: return ""
        if(alias.length()>128)return ""
        val blobName = runCatching { alias.readText().trim() }.getOrDefault("")
        if (!blobName.matches(Regex("blobs/[a-f0-9]{64}\\.img"))) return ""
        return storage.find(CacheCategory.ARTWORK, blobName)?.absolutePath.orEmpty()
    }

    fun invalidate(url: String) {
        val path=cachedPath(url)
        storage.file(CacheCategory.ARTWORK,aliasName(url)).delete()
        if(path.isNotBlank())File(path).delete()
    }

    fun getOrFetch(url: String, fetch: () -> ByteArray): String {
        if (url.isBlank()) return ""
        synchronized(locks[(canonicalKey(url).hashCode() and Int.MAX_VALUE) % locks.size]) {
            cachedPath(url).takeIf(String::isNotBlank)?.let { return it }
            return put(url, fetch())
        }
    }

    fun put(url: String, bytes: ByteArray): String {
        if (url.isBlank() || bytes.isEmpty()) return ""
        val name = "blobs/${CacheStorage.sha256(bytes)}.img"
        val blob = storage.find(CacheCategory.ARTWORK, name) ?: storage.write(CacheCategory.ARTWORK, name, bytes) ?: return ""
        storage.pin(blob)
        try {
            if (storage.write(CacheCategory.ARTWORK, aliasName(url), name.toByteArray()) == null) return ""
        } finally { storage.unpin(blob) }
        return blob.takeIf(File::isFile)?.absolutePath.orEmpty()
    }

    private fun aliasName(url: String) = "aliases/${CacheStorage.sha256(canonicalKey(url).toByteArray())}.ref"

    companion object {
        /** Subsonic salts/tokens change on every request; image identity does not. Keep user/id/size. */
        fun canonicalKey(url: String): String = runCatching {
            val uri = URI(url)
            if(uri.host==null)return@runCatching url.substringBefore('#')
            val transient = setOf("t", "s", "p", "token", "access_token", "auth", "authorization", "signature", "sig", "expires", "expiry", "policy", "key-pair-id")
            val query = uri.rawQuery.orEmpty().split('&').filter { part ->
                val key = URLDecoder.decode(part.substringBefore('='), "UTF-8").lowercase()
                part.isNotBlank() && key !in transient && !key.startsWith("x-amz-") && !key.startsWith("x-oss-")
            }.sorted().joinToString("&")
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            "${uri.scheme.orEmpty().lowercase()}://${uri.host.orEmpty().lowercase()}$port${uri.rawPath.orEmpty()}${if(query.isBlank()) "" else "?$query"}"
        }.getOrDefault(url.substringBefore('#'))
    }
}
