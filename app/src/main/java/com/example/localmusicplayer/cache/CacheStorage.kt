package com.supermite.smp.cache

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.BufferedOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

enum class CacheCategory(val label: String, val folder: String, val defaultLimitMb: Long) {
    AUDIO("串流音频", "audio", 1536),
    ARTWORK("封面图片", "artwork", 192),
    METADATA("在线歌曲信息", "metadata", 96),
    INDEX("目录索引", "index", 32),
    LOGS("运行日志", "logs", 32),
    TEMP("转换与导出临时文件", "temporary", 64)
}

data class CacheUsage(val category: CacheCategory, val bytes: Long, val limitBytes: Long, val enabled: Boolean)

/** File-only policy engine, deliberately independent from downloads, preferences and credentials. */
class CacheStorage(
    val root: File,
    private val enabled: (CacheCategory) -> Boolean,
    private val limit: (CacheCategory) -> Long,
    private val totalLimit: () -> Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val pinned = mutableMapOf<String, Int>()

    init { root.mkdirs() }

    fun directory(category: CacheCategory): File = File(root, category.folder).apply { mkdirs() }

    fun file(category: CacheCategory, relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute && !relative.startsWith('/') && !relative.startsWith('\\') &&
            !Regex("^[A-Za-z]:").containsMatchIn(relative) && relative.replace('\\','/').split('/').none {it==".."}) { "Cache path must be relative" }
        val parent = directory(category).canonicalFile
        val result = File(parent, relative).canonicalFile
        require(result.path.startsWith(parent.path + File.separator)) { "Cache path escapes category" }
        return result
    }

    fun keyedFile(category: CacheCategory, key: String, extension: String = "bin"): File =
        file(category, sha256(key.toByteArray()) + "." + extension.filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" })

    fun find(category: CacheCategory, relative: String): File? = synchronized(lock) {
        if (!enabled(category)) return@synchronized null
        file(category, relative).takeIf { it.isFile && it.length() > 0L }?.also { it.setLastModified(now()) }
    }

    fun write(category: CacheCategory, relative: String, bytes: ByteArray): File? = synchronized(lock) {
        if (!enabled(category) || bytes.isEmpty() || bytes.size.toLong() > limit(category) || bytes.size.toLong() > totalLimit()) return@synchronized null
        val target = file(category, relative)
        val protectedCategory = files(category).filter {it!=target && isPinned(it)}.sumOf(File::length)
        val protectedTotal = CacheCategory.entries.flatMap(::files).filter {it!=target && isPinned(it)}.sumOf(File::length)
        if(bytes.size.toLong()+protectedCategory>limit(category) || bytes.size.toLong()+protectedTotal>totalLimit())return@synchronized null
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".pending")
        try {
            temporary.writeBytes(bytes)
            check(temporary.renameTo(target) || run { temporary.copyTo(target, overwrite = true); temporary.delete() })
            target.setLastModified(now())
            enforceLocked()
            target.takeIf { it.isFile }
        } finally { temporary.delete() }
    }

    fun touch(file: File) = synchronized(lock) {
        if (isManaged(file) && file.isFile) file.setLastModified(now())
    }

    /** A currently playing file/export can be leased so eviction never removes it mid-use. */
    fun pin(file: File) = synchronized(lock) { require(isManaged(file)); pinned[file.canonicalPath] = (pinned[file.canonicalPath] ?: 0) + 1 }
    fun unpin(file: File) = synchronized(lock) {
        val key = file.canonicalPath
        val remaining = (pinned[key] ?: 0) - 1
        if (remaining <= 0) pinned.remove(key) else pinned[key] = remaining
        Unit
    }

    fun writePinned(category: CacheCategory, relative: String, bytes: ByteArray): File? = synchronized(lock) {
        write(category, relative, bytes)?.also { pin(it) }
    }
    fun findPinned(category: CacheCategory, relative: String): File? = synchronized(lock) {find(category, relative)?.also {pin(it)}}

    fun createWorkFile(relative: String): CacheWorkFile? = synchronized(lock) {
        if (!enabled(CacheCategory.TEMP)) return@synchronized null
        val session = "work/${UUID.randomUUID()}"
        val folder = file(CacheCategory.TEMP, session)
        val file = file(CacheCategory.TEMP, "$session/$relative")
        require(file.canonicalPath.startsWith(folder.canonicalPath+File.separator))
        file.parentFile?.mkdirs()
        file.createNewFile()
        pin(folder)
        CacheWorkFile(this, file, folder)
    }

    internal fun validateWork(folder: File) = synchronized(lock) {
        require(isManaged(folder))
        if (!enabled(CacheCategory.TEMP)) throw IOException("临时缓存已关闭，操作已停止")
        // Work directories are exclusively leased, including metadata libraries' replacement files.
        val protectedCategory = files(CacheCategory.TEMP).filter(::isPinned).sumOf(File::length)
        val protectedTotal = CacheCategory.entries.flatMap(::files).filter(::isPinned).sumOf(File::length)
        if (protectedCategory > limit(CacheCategory.TEMP) || protectedTotal > totalLimit()) throw IOException("临时缓存预算不足，请提高缓存上限或选择更小文件")
        enforceLocked()
    }

    internal fun boundedWrite(file: File, count: Int, operation: () -> Unit) = synchronized(lock) {
        require(isManaged(file))
        val category = CacheCategory.TEMP
        if (!enabled(category)) throw IOException("临时缓存已关闭，操作已停止")
        val proposed = file.length() + count
        val protectedFiles=pinnedFiles().filter {it!=file}
        val categoryPrefix=directory(category).canonicalPath+File.separator
        val protectedCategory = protectedFiles.filter {it.canonicalPath.startsWith(categoryPrefix)}.sumOf(File::length)
        val protectedTotal = protectedFiles.sumOf(File::length)
        if (proposed + protectedCategory > limit(category) || proposed + protectedTotal > totalLimit()) throw IOException("临时缓存预算不足，请提高缓存上限或选择更小文件")
        // Reserve actual disk space before the physical write, then consume only that reservation.
        enforceLocked(category,count.toLong())
        operation()
    }

    fun clear(category: CacheCategory): Long = synchronized(lock) {
        var removed = 0L
        files(category).filterNot { isPinned(it) }.forEach { file ->
            val size = file.length(); if (file.delete()) removed += size
        }
        removed
    }

    fun usage(): List<CacheUsage> = synchronized(lock) {
        CacheCategory.entries.map { CacheUsage(it, files(it).sumOf(File::length), limit(it), enabled(it)) }
    }

    fun enforce() = synchronized(lock) { enforceLocked() }

    fun enforceCategory(category: CacheCategory) = synchronized(lock) { enforceCategoryLocked(category) }

    private fun enforceLocked(reserveCategory:CacheCategory?=null,reserveBytes:Long=0) {
        CacheCategory.entries.forEach {enforceCategoryLocked(it,if(it==reserveCategory)reserveBytes else 0)}
        val files = CacheCategory.entries.flatMap(::files).sortedBy(File::lastModified)
        var bytes = files.sumOf(File::length)
        for (file in files) {
            if (bytes <= (totalLimit()-reserveBytes).coerceAtLeast(0)) break
            if (!isPinned(file)) { val length = file.length(); if (file.delete()) bytes -= length }
        }
    }

    private fun enforceCategoryLocked(category: CacheCategory, reserveBytes:Long=0) {
        val files = files(category).sortedBy(File::lastModified)
        var bytes = files.sumOf(File::length)
        val budget = if (enabled(category)) (limit(category)-reserveBytes).coerceAtLeast(0) else 0L
        for (file in files) {
            if (bytes <= budget) break
            if (!isPinned(file)) { val length = file.length(); if (file.delete()) bytes -= length }
        }
    }

    private fun files(category: CacheCategory): List<File> = directory(category).walkTopDown()
        .onEnter { isManaged(it) }.filter { it.isFile && isManaged(it) }.toList()

    private fun isManaged(file: File): Boolean = file.canonicalPath.startsWith(root.canonicalPath + File.separator)
    private fun isPinned(file: File): Boolean = pinned.keys.any {file.canonicalPath == it || file.canonicalPath.startsWith(it+File.separator)}
    private fun pinnedFiles():List<File> = pinned.keys.flatMap {path ->
        val file=File(path)
        if(file.isDirectory)file.walkTopDown().onEnter(::isManaged).filter {it.isFile && isManaged(it)}.toList() else listOfNotNull(file.takeIf {it.isFile})
    }.distinctBy {it.canonicalPath}

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** A scoped work file; always remove it in finally/use, including conversion failures. */
class CacheWorkFile internal constructor(private val storage: CacheStorage, val file: File, private val folder: File) : AutoCloseable {
    private val opened = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    fun outputStream(): OutputStream {
        check(!closed.get() && opened.compareAndSet(false, true)) { "Work file output can be opened once" }
        val stream = file.outputStream()
        val bounded=object : OutputStream() {
            override fun write(value: Int) { storage.boundedWrite(file, 1) { stream.write(value) } }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { storage.boundedWrite(file, length) { stream.write(bytes, offset, length) } }
            override fun flush() = stream.flush()
            override fun close() = stream.close()
        }
        // Conversion commonly emits 8KB chunks. Buffer in bounded RAM, not on disk; every physical
        // write still reserves/checks the current category, total and concurrent pinned work first.
        return BufferedOutputStream(bounded,1024*1024)
    }
    fun validate() {
        try {storage.validateWork(folder)} catch(failure: IOException) {close();throw failure}
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            storage.unpin(folder)
            folder.walkTopDown().onEnter {it.canonicalPath.startsWith(folder.canonicalPath+File.separator) || it.canonicalPath==folder.canonicalPath}
                .filter {it.canonicalPath.startsWith(folder.canonicalPath+File.separator) || it.canonicalPath==folder.canonicalPath}
                .toList().sortedByDescending {it.path.length}.forEach {it.delete()}
        }
    }
}

class CacheLease internal constructor(private val storage: CacheStorage, val file: File, val persistent: Boolean) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) { storage.unpin(file); if (!persistent) file.delete();storage.enforce() } }
}
