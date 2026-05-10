package com.supermite.smp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object Palette {
    var BG = 0xFF101418.toInt()
    var PANEL = 0xFF1A2027.toInt()
    var PANEL_ALT = 0xFF24303A.toInt()
    var ACCENT = 0xFF35C2A1.toInt()
    var ACCENT_ALT = 0xFFF2B84B.toInt()
    var TEXT = 0xFFF4F7F8.toInt()
    var MUTED = 0xFFAAB5BD.toInt()
    var DANGER = 0xFFE66A6A.toInt()

    fun apply(dark: Boolean, accent: Int) {
        ACCENT = accent
        ACCENT_ALT = if (dark) 0xFFF2B84B.toInt() else 0xFFB46A00.toInt()
        DANGER = if (dark) 0xFFE66A6A.toInt() else 0xFFC43D3D.toInt()
        if (dark) {
            BG = 0xFF101418.toInt()
            PANEL = 0xFF1A2027.toInt()
            PANEL_ALT = 0xFF24303A.toInt()
            TEXT = 0xFFF4F7F8.toInt()
            MUTED = 0xFFAAB5BD.toInt()
        } else {
            BG = 0xFFF6F8FA.toInt()
            PANEL = 0xFFFFFFFF.toInt()
            PANEL_ALT = 0xFFE8EEF2.toInt()
            TEXT = 0xFF111820.toInt()
            MUTED = 0xFF5D6975.toInt()
        }
    }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun panelDrawable(color: Int = Palette.PANEL, radiusDp: Int = 8, context: Context): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
    }
}

fun View.setMargins(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.setMargins(left, top, right, bottom)
    layoutParams = params
}

fun TextView.titleStyle(sizeSp: Float = 17f) {
    setTextColor(Palette.TEXT)
    textSize = sizeSp
    typeface = Typeface.DEFAULT_BOLD
    includeFontPadding = false
}

fun TextView.bodyStyle(sizeSp: Float = 13f, color: Int = Palette.MUTED) {
    setTextColor(color)
    textSize = sizeSp
    includeFontPadding = true
}

fun stars(rating: Int): String {
    val safe = rating.coerceIn(0, 5)
    if (safe <= 0) return "未评分"
    return "★".repeat(safe) + "☆".repeat(5 - safe)
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val minutes = total / 60L
    val seconds = total % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun contrastTextColor(background: Int): Int {
    val r = Color.red(background)
    val g = Color.green(background)
    val b = Color.blue(background)
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
    return if (luminance > 150) Color.BLACK else Color.WHITE
}

