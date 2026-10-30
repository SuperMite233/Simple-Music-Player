package com.supermite.smp

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.supermite.smp.ui.Palette
import com.supermite.smp.ui.bodyStyle
import com.supermite.smp.ui.dp
import java.util.Locale

/** File-manager entry only: confirmation and conversion stay in MainActivity's existing workflow. */
class NcmOpenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val mode = prefs.getInt("darkMode", 2)
        val dark = mode == 1 || (mode == 2 && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
        super.onCreate(savedInstanceState)
        Palette.apply(dark, prefs.getInt("themeColor", Palette.DEFAULT_SEED))
        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(Palette.BG)
            addView(ProgressBar(this@NcmOpenActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(this@NcmOpenActivity).apply {
                text = "正在读取 NCM 文件…"; bodyStyle(16f, Palette.TEXT); setPadding(0, dp(16), 0, 0)
            })
        }
        setContentView(loading)
        val input = NcmInput.fromIntent(intent)
        Thread {
            val result = runCatching { requireNotNull(input) { "没有收到可读取的 NCM 文件" }; NcmInput.validate(this, input) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    val incoming = requireNotNull(input)
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        action = ACTION_CONVERT_NCM; data = incoming
                        clipData = ClipData.newRawUri("NCM", incoming)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    finish()
                }.onFailure {
                    MaterialAlertDialogBuilder(this).setTitle("无法转换文件")
                        .setMessage(it.message ?: "文件不可读取，请在文件管理器中重新选择 .ncm 文件。")
                        .setPositiveButton("关闭") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
                }
            }
        }.start()
    }

    companion object { const val ACTION_CONVERT_NCM = "com.supermite.smp.action.CONVERT_NCM" }
}

object NcmInput {
    fun fromIntent(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        else -> null
    } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

    fun displayName(context: Context, uri: Uri): String {
        require(uri.scheme in setOf("content", "file")) { "仅支持本地文件或文件提供方 URI" }
        val providerName = if (uri.scheme == "content") runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() else null
        return (providerName ?: uri.lastPathSegment.orEmpty()).substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "").take(240)
    }

    fun validate(context: Context, uri: Uri): String {
        val name = displayName(context, uri)
        require(name.lowercase(Locale.ROOT).endsWith(".ncm")) { "请选择 .ncm 格式的文件" }
        sizeBytes(context, uri)?.let { require(it >= 10L) { "NCM 文件内容不完整" } }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(8)
            var offset = 0
            while (offset < header.size) {
                val read = stream.read(header, offset, header.size - offset)
                require(read > 0) { "NCM 文件内容不完整" }; offset += read
            }
            require(header.contentEquals("CTENFDAM".toByteArray(Charsets.US_ASCII))) { "不是有效的 NCM 文件" }
        } ?: error("无法读取 NCM 文件，请重新授予文件访问权限")
        return name
    }

    fun sizeBytes(context: Context, uri: Uri): Long? {
        if (uri.scheme == "file") return uri.path?.let { java.io.File(it) }?.takeIf { it.isFile }?.length()
        if (uri.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
            }
        }.getOrNull()
    }
}
