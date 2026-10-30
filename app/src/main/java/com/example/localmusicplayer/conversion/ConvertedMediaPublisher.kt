package com.supermite.smp.conversion

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Final converted music is user output, never managed cache. Call on the conversion worker. */
object ConvertedMediaPublisher {
    data class ScanResult(val requested: Boolean, val indexedUri: Uri?, val message: String) {
        val indexed: Boolean get() = indexedUri != null
    }
    fun publish(context: Context, source: File, title: String = source.nameWithoutExtension): Uri {
        require(source.isFile && source.length() > 0) { "转换输出不存在或为空" }
        val mime = if (source.extension.equals("flac", true)) "audio/flac" else "audio/mpeg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, NcmFileNames.sanitize(title) + "." + source.extension.lowercase())
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SMP/ConvertedMusic/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val target = resolver.insert(collection, values) ?: error("无法创建系统音乐条目")
            try {
                val destination = resolver.openOutputStream(target, "w") ?: error("无法写入系统音乐目录")
                destination.use { out -> source.inputStream().use { it.copyTo(out) } }
                check(resolver.update(target, ContentValues().apply {put(MediaStore.Audio.Media.IS_PENDING, 0)}, null, null) > 0) {"无法提交系统音乐条目"}
                resolver.notifyChange(target, null)
                return target
            } catch (error: Throwable) {
                resolver.delete(target, null, null)
                throw error
            }
        }
        require(context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            "Android 8/9 需要先授予存储写入权限，才能发布到系统音乐目录"
        }
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SMP/ConvertedMusic")
        check(directory.isDirectory || directory.mkdirs()) {"无法创建系统音乐目录"}
        val publicName = NcmFileNames.sanitize(title)
        var target = File(directory, "$publicName.${source.extension}")
        var suffix = 1
        while (target.exists()) target = File(directory, "$publicName-${suffix++}.${source.extension}")
        try { source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } } }
        catch (error: Throwable) {target.delete();throw error}
        var indexed: Uri? = null
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime)) { _, uri -> indexed=uri;latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
        return indexed ?: Uri.fromFile(target)
    }

    /** Scan the actual final destination; scanning an Android/data staging file is insufficient. */
    fun requestScan(context: Context, uri: Uri, mimeType: String? = null): ScanResult {
        val path = if (uri.scheme == "file") uri.path else if (uri.scheme == "content") runCatching {
            @Suppress("DEPRECATION")
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: primaryDocumentPath(uri) else null
        if (uri.scheme == "content") runCatching { context.contentResolver.notifyChange(uri, null) }
        if (uri.authority == MediaStore.AUTHORITY && uri.path.orEmpty().contains("/audio/media/") &&
            runCatching {context.contentResolver.getType(uri)?.startsWith("audio/") == true}.getOrDefault(false))
            return ScanResult(false, uri, "系统音乐库已有该音频条目")
        if (path.isNullOrBlank()) return ScanResult(false, null, "已通知文件提供方；无法确定可扫描的本地路径，未确认系统音乐库收录")
        var indexed: Uri? = null
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType)) { _, result -> indexed=result;latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
        return ScanResult(true, indexed, if(indexed==null) "已提交媒体扫描，尚未确认系统音乐库收录" else "系统媒体扫描已完成")
    }

    private fun primaryDocumentPath(uri: Uri): String? = runCatching {
        if(uri.authority != "com.android.externalstorage.documents")return@runCatching null
        val document = DocumentsContract.getDocumentId(uri).split(':', limit=2)
        if(document.size!=2 || !document[0].equals("primary",true) || document[1].isBlank())return@runCatching null
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val candidate = File(root,document[1]).canonicalFile
        candidate.path.takeIf { it.startsWith(root.path + File.separator) }
    }.getOrNull()
}
