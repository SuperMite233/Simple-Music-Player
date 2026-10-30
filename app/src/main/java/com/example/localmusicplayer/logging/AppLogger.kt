package com.supermite.smp.logging

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import com.supermite.smp.cache.CacheCategory
import com.supermite.smp.cache.CacheManager
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class LogLevel(val label: String, val code: Char) {
    VERBOSE("详细", 'V'), DEBUG("调试", 'D'), INFO("信息", 'I'), WARN("警告", 'W'), ERROR("错误", 'E')
}
data class LogFileInfo(val id: String, val bytes: Long, val modifiedAt: Long)

/** Process-wide, bounded and sanitized. Captures this app UID only, never device-wide logs. */
object AppLogger {
    private val lock = Any()
    @Volatile private var initialized = false
    private lateinit var preferences: SharedPreferences
    private lateinit var cache: CacheManager
    private var current: File? = null
    @Volatile private var collector: java.lang.Process? = null
    @Volatile private var captureThread: Thread? = null
    private var originalCrashHandler: Thread.UncaughtExceptionHandler? = null
    private var crashHandlerInstalled = false
    private var lastPrunedAt = 0L
    private var lastGlobalTrimAt = 0L
    private var captureGeneration = 0L

    @Synchronized fun initialize(context: Context, manager: CacheManager = CacheManager(context)) {
        if (initialized) { refreshCapture(); return }
        cache = manager
        preferences = context.applicationContext.getSharedPreferences("runtime_logging", Context.MODE_PRIVATE)
        initialized = true
        if (!crashHandlerInstalled) {
            originalCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                log(LogLevel.ERROR, "CRASH", "Uncaught exception on ${thread.name}", error)
                originalCrashHandler?.uncaughtException(thread, error) ?: Process.killProcess(Process.myPid())
            }
            crashHandlerInstalled = true
        }
        prune()
        log(LogLevel.INFO, "APP", "Process started; SDK=${Build.VERSION.SDK_INT}; pid=${Process.myPid()}; UID-only diagnostics")
        refreshCapture()
    }

    var enabled: Boolean
        get() = !initialized || preferences.getBoolean("enabled", true)
        set(value) { if (initialized) { preferences.edit().putBoolean("enabled", value).apply(); refreshCapture() } }
    var level: LogLevel
        get() = if (initialized) LogLevel.entries.firstOrNull { it.name == preferences.getString("level", "VERBOSE") } ?: LogLevel.VERBOSE else LogLevel.VERBOSE
        set(value) { if (initialized) preferences.edit().putString("level", value.name).apply() }
    var retentionDays: Int
        get() = if (initialized) preferences.getInt("retentionDays", 7).coerceIn(1, 365) else 7
        set(value) { if (initialized) { preferences.edit().putInt("retentionDays", value.coerceIn(1, 365)).apply(); prune() } }
    val isRecording: Boolean get() = initialized && enabled && cache.isEnabled(CacheCategory.LOGS)

    fun verbose(tag: String, message: String) = log(LogLevel.VERBOSE, tag, message)
    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String, error: Throwable? = null) = log(LogLevel.WARN, tag, message, error)
    fun error(tag: String, message: String, error: Throwable? = null) = log(LogLevel.ERROR, tag, message, error)

    fun log(level: LogLevel, tag: String, message: String, error: Throwable? = null) {
        if (!isRecording || level.ordinal < this.level.ordinal) return
        synchronized(lock) {
            runCatching {
                val time = System.currentTimeMillis()
                if (time - lastPrunedAt > 60_000L) pruneLocked(time)
                val detail = if (error == null) message else "$message\n${error.stackTraceToString()}"
                val sanitized = LogRedactor.redact(detail).take(32 * 1024)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(time))
                val safeTag = tag.replace(Regex("[\\r\\n\\[\\]]"), "_").take(64)
                val bytes = "[$timestamp][${level.code}][$safeTag] $sanitized\n".toByteArray()
                if (bytes.size > cache.limitBytes(CacheCategory.LOGS)) return@synchronized
                val active = current?.takeIf { it.isFile && it.length() + bytes.size <= ROTATE_BYTES }
                    ?: cache.file(CacheCategory.LOGS, "runtime-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(time))}-${UUID.randomUUID().toString().take(8)}.log").also { current = it }
                active.parentFile?.mkdirs()
                active.appendBytes(bytes)
                cache.storage.enforceCategory(CacheCategory.LOGS)
                if (time - lastGlobalTrimAt >= 1000L) {cache.enforce();lastGlobalTrimAt=time}
            }
        }
    }

    /** Call after a cache toggle/clear to update capture immediately without another process. */
    @Synchronized fun refreshCapture() {
        if (!initialized) return
        captureGeneration++
        if (!isRecording) { collector?.destroy(); collector = null; return }
        if (captureThread?.isAlive == true) return
        val startedGeneration = captureGeneration
        captureThread = Thread({
            var process: java.lang.Process? = null
            try {
                // --uid is supported from API 24; -T 1 avoids importing prior launches/foreign history.
                val running = ProcessBuilder("logcat", "--uid=${Process.myUid()}", "-v", "threadtime", "-T", "1").redirectErrorStream(false).start()
                process = running
                synchronized(this) { collector = running }
                running.inputStream.bufferedReader().use { reader ->
                    var continuationLevel = LogLevel.DEBUG
                    while (isRecording && !Thread.currentThread().isInterrupted) {
                        val line = reader.readLine() ?: break
                        val code = THREADTIME_LEVEL.find(line)?.groupValues?.get(1)?.firstOrNull()
                        if (code != null) continuationLevel = when (code) { 'V' -> LogLevel.VERBOSE; 'I' -> LogLevel.INFO; 'W' -> LogLevel.WARN; 'E', 'F' -> LogLevel.ERROR; else -> LogLevel.DEBUG }
                        log(continuationLevel, "LOGCAT", line.take(64 * 1024))
                    }
                }
            } catch (failure: Exception) {
                warn("LOGGER", "UID logcat unavailable; application events and uncaught exceptions remain recorded", failure)
            } finally {
                process?.destroy()
                synchronized(this) {
                    if (collector === process) collector = null
                    captureThread = null
                    if (captureGeneration != startedGeneration && isRecording) refreshCapture()
                }
            }
        }, "SMP-UID-Logs").apply { isDaemon = true; start() }
    }

    fun files(): List<LogFileInfo> = synchronized(lock) {
        if (!initialized) return@synchronized emptyList()
        pruneLocked(System.currentTimeMillis())
        logFiles().sortedByDescending(File::lastModified).map { LogFileInfo(logId(it), it.length(), it.lastModified()) }
    }

    fun preview(selectedIds: Set<String> = emptySet(), maxChars: Int = 80_000): String = synchronized(lock) {
        if (!initialized) return@synchronized ""
        val remainingLimit = maxChars.coerceIn(1000, 200_000)
        val result = StringBuilder()
        for (file in selectedFiles(selectedIds).sortedByDescending(File::lastModified)) {
            if (result.length >= remainingLimit) break
            result.append("── ${file.name} ──\n")
            val remaining=(remainingLimit-result.length).coerceAtLeast(1)
            runCatching {result.append(LogTail.read(file,remaining))}
        }
        result.take(remainingLimit).toString()
    }

    /** Writes directly to a user-selected destination. No unbounded temporary copy is created. */
    fun export(output: OutputStream, selectedIds: Set<String>) {
        check(initialized) { "Logger is not initialized" }
        val files = synchronized(lock) { selectedFiles(selectedIds).map { it to it.length() } }
        require(files.isNotEmpty()) { "没有可导出的日志" }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("SMP diagnostic logs. Application UID only; credentials are redacted. Retention and cache quotas may remove older records.\n".toByteArray())
            zip.closeEntry()
            for ((file, _) in files) {
                // Clearing cache while an export starts may remove a file; the export skips it safely.
                val reader = runCatching {file.bufferedReader()}.getOrNull() ?: continue
                zip.putNextEntry(ZipEntry(logId(file)))
                reader.use { input -> input.forEachLine { zip.write((LogRedactor.redact(it)+"\n").toByteArray()) } }
                zip.closeEntry()
            }
        }
    }

    fun prune() = synchronized(lock) { if (initialized) pruneLocked(System.currentTimeMillis()) }
    private fun pruneLocked(now: Long) {
        val cutoff = now - retentionDays * 24L * 60L * 60L * 1000L
        logFiles().filter { it.lastModified() < cutoff }.forEach(File::delete)
        cache.enforce()
        lastPrunedAt = now
    }
    private fun logFiles(): List<File> {
        val root = cache.directory(CacheCategory.LOGS).canonicalFile
        return root.walkTopDown().onEnter {it.canonicalPath == root.path || it.canonicalPath.startsWith(root.path+File.separator)}
            .filter {it.isFile && it.extension=="log" && it.canonicalPath.startsWith(root.path+File.separator)}.toList()
    }
    private fun selectedFiles(ids: Set<String>): List<File> {
        return logFiles().filter { ids.isEmpty() || logId(it) in ids }
    }
    private fun logId(file: File): String = file.canonicalFile.relativeTo(cache.directory(CacheCategory.LOGS).canonicalFile).invariantSeparatorsPath
    private const val ROTATE_BYTES = 512L * 1024L
    private val THREADTIME_LEVEL = Regex("^\\d{2}-\\d{2}\\s+\\S+\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s")
}
