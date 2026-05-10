package com.supermite.smp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object Palette {
    const val BG = 0xFF101418.toInt()
    const val PANEL = 0xFF1A2027.toInt()
    const val PANEL_ALT = 0xFF24303A.toInt()
    const val ACCENT = 0xFF35C2A1.toInt()
    const val ACCENT_ALT = 0xFFF2B84B.toInt()
    const val TEXT = 0xFFF4F7F8.toInt()
    const val MUTED = 0xFFAAB5BD.toInt()
    const val DANGER = 0xFFE66A6A.toInt()
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

