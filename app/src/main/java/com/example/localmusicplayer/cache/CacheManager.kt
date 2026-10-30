package com.supermite.smp.cache

import android.content.Context
import com.supermite.smp.logging.LogRedactor
import java.io.File
import java.util.UUID

/** The only cache root. SAF music/download destinations and account stores are never children of it. */
class CacheManager(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("cache_policy", Context.MODE_PRIVATE)
    val root = File(app.noBackupFilesDir, "managed-cache")
    val storage = synchronized(stores) { stores.getOrPut(root.canonicalPath) { CacheStorage(root, ::isEnabled, ::limitBytes, { totalLimitMb * MB }) } }
    val artwork = synchronized(artworkStores) { artworkStores.getOrPut(root.canonicalPath) { ArtworkCache(storage) } }

    init {
        if (!preferences.contains("totalLimitMb")) {
            val old = app.getSharedPreferences("settings", Context.MODE_PRIVATE).getFloat("streamCacheLimitGb", 2f)
            preferences.edit().putLong("totalLimitMb", (old * 1024).toLong().coerceIn(64, 32768)).apply()
        }
    }

    var totalLimitMb: Long
        get() = preferences.getLong("totalLimitMb", 2048).coerceIn(16, 32768)
        set(value) { preferences.edit().putLong("totalLimitMb", value.coerceIn(16, 32768)).apply(); storage.enforce() }

    fun isEnabled(category: CacheCategory) = preferences.getBoolean("${category.folder}.enabled", true)
    fun setEnabled(category: CacheCategory, value: Boolean) {
        preferences.edit().putBoolean("${category.folder}.enabled", value).apply()
        storage.enforce()
    }
    fun limitMb(category: CacheCategory) = preferences.getLong("${category.folder}.limitMb", category.defaultLimitMb).coerceIn(1, 32768)
    fun limitBytes(category: CacheCategory) = limitMb(category) * MB
    fun setLimitMb(category: CacheCategory, value: Long) {
        preferences.edit().putLong("${category.folder}.limitMb", value.coerceIn(1, 32768)).apply()
        storage.enforce()
    }
    fun directory(category: CacheCategory) = storage.directory(category)
    fun file(category: CacheCategory, relative: String) = storage.file(category, relative)
    fun keyedFile(category: CacheCategory, key: String, extension: String = "bin") = storage.keyedFile(category, key, extension)
    fun usage() = storage.usage()
    fun clear(category: CacheCategory) = storage.clear(category)
    fun enforce() = storage.enforce()

    /** Disabling durable audio cache never silently disables playback or bypasses TEMP policy. */
    fun storePlayback(relative: String, bytes: ByteArray): CacheLease? {
        storage.writePinned(CacheCategory.AUDIO, relative, bytes)?.let { return CacheLease(storage, it, true) }
        val scratch = "playback/${UUID.randomUUID()}-${File(relative).name}"
        return storage.writePinned(CacheCategory.TEMP, scratch, bytes)?.let { CacheLease(storage, it, false) }
    }
    fun leaseAudio(relative: String): CacheLease? = storage.findPinned(CacheCategory.AUDIO, relative)?.let {CacheLease(storage,it,true)}

    fun createWorkFile(extension: String = "tmp"): CacheWorkFile? = storage.createWorkFile("work/${UUID.randomUUID()}.${extension.filter(Char::isLetterOrDigit).take(8).ifBlank { "tmp" }}")

    /** Routes previous StreamCache-relative names without mixing budgets or server audio IDs. */
    fun legacyStreamFile(relative: String): File {
        val head = relative.substringBefore('/')
        val category = when (head) {
            "subsonic_stream", "webdav_stream" -> CacheCategory.AUDIO
            "remote_artwork" -> CacheCategory.ARTWORK
            "webdav_index" -> CacheCategory.INDEX
            "debug_logs" -> CacheCategory.LOGS
            else -> CacheCategory.TEMP
        }
        return file(category, "legacy/$relative")
    }

    /** Old persisted artwork paths continue to resolve after an owned legacy folder is moved. */
    fun resolveLegacyPath(path: String): File {
        val original = File(path)
        if (original.isFile) return original
        val oldStream = File(app.getExternalFilesDir(null) ?: app.filesDir, "StreamCache")
        relativeWithin(original, oldStream)?.let { return legacyStreamFile(it) }
        relativeWithin(original, File(app.filesDir, "stream_artwork"))?.let { return file(CacheCategory.ARTWORK, "legacy/stream_artwork/$it") }
        relativeWithin(original, File(app.filesDir, "artwork"))?.let { return file(CacheCategory.ARTWORK, "legacy/local_artwork/$it") }
        return original
    }

    /** Run once on an IO thread. Only named, app-owned cache folders are migrated. */
    @Synchronized fun migrateLegacy(): Int {
        var moved = 0
        val stream = File(app.getExternalFilesDir(null) ?: app.filesDir, "StreamCache")
        val sources = listOf(
            File(stream, "subsonic_stream") to legacyStreamFile("subsonic_stream"),
            File(stream, "webdav_stream") to legacyStreamFile("webdav_stream"),
            File(stream, "remote_artwork") to legacyStreamFile("remote_artwork"),
            File(stream, "webdav_index") to legacyStreamFile("webdav_index"),
            File(stream, "debug_logs") to legacyStreamFile("debug_logs"),
            File(app.filesDir, "stream_artwork") to file(CacheCategory.ARTWORK, "legacy/stream_artwork"),
            File(app.filesDir, "artwork") to file(CacheCategory.ARTWORK, "legacy/local_artwork"),
            File(app.cacheDir, "stream_metadata") to file(CacheCategory.TEMP, "legacy/stream_metadata")
        )
        for ((source, destination) in sources) {
            if (!source.isDirectory) continue
            val safeSource = source.canonicalFile
            val ownedRoots = listOfNotNull(app.filesDir, app.cacheDir, app.getExternalFilesDir(null)).map { it.canonicalPath + File.separator }
            if (java.nio.file.Files.isSymbolicLink(source.toPath()) || ownedRoots.none { safeSource.path.startsWith(it) }) continue
            fun ownedEntry(file: File) = !java.nio.file.Files.isSymbolicLink(file.toPath()) &&
                (file.canonicalPath == safeSource.path || file.canonicalPath.startsWith(safeSource.path + File.separator))
            source.walkTopDown().onEnter(::ownedEntry)
                .filter { it.isFile && ownedEntry(it) }.forEach { old ->
                    val target = File(destination, old.relativeTo(source).path)
                    runCatching {
                        target.parentFile?.mkdirs()
                        if (source.name == "debug_logs") {
                            // Never import an old plaintext credential into the new retention pool.
                            old.bufferedReader().use { input -> target.bufferedWriter().use { output -> input.forEachLine { output.appendLine(LogRedactor.redact(it)) } } }
                            target.setLastModified(old.lastModified())
                            check(old.delete())
                        } else if (target.exists()) {
                            // Preserve the newly written destination; remove only the redundant old cache.
                            check(old.delete())
                        } else {
                            if (!old.renameTo(target)) { old.copyTo(target); check(old.delete()) }
                        }
                        moved++
                    }
                }
            source.walkTopDown().onEnter(::ownedEntry)
                .filter { it.isDirectory && ownedEntry(it) }
                .toList().sortedByDescending { it.path.length }.forEach { it.delete() }
        }
        // Standalone WebDAV metadata scratch files never contain user-owned source music.
        app.cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("webdav_meta_") }?.forEach { it.delete() }
        storage.enforce()
        return moved
    }

    private fun relativeWithin(file: File, directory: File): String? = runCatching {
        val prefix = directory.canonicalPath + File.separator
        file.canonicalPath.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.replace(File.separatorChar, '/')
    }.getOrNull()

    companion object {
        const val MB = 1024L * 1024L
        private val stores = mutableMapOf<String, CacheStorage>()
        private val artworkStores = mutableMapOf<String, ArtworkCache>()
    }
}
