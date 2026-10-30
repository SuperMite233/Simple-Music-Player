package com.supermite.smp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object Palette {
    const val DEFAULT_SEED = 0xFF35C2A1.toInt()
    private var colors = Md3Colors(false, DEFAULT_SEED)
    val BG get() = colors.surface
    val PANEL get() = colors.surfaceContainerLow
    val PANEL_ALT get() = colors.surfaceContainerHighest
    val CONTAINER get() = colors.surfaceContainer
    val DIALOG get() = colors.surfaceContainerHigh
    val ACCENT get() = colors.primary
    val ON_ACCENT get() = colors.onPrimary
    val ACCENT_CONTAINER get() = colors.primaryContainer
    val ON_ACCENT_CONTAINER get() = colors.onPrimaryContainer
    val SELECTED get() = colors.secondaryContainer
    val ON_SELECTED get() = colors.onSecondaryContainer
    val ACCENT_ALT get() = colors.tertiary
    val TEXT get() = colors.onSurface
    val MUTED get() = colors.onSurfaceVariant
    val OUTLINE get() = colors.outline
    val OUTLINE_VARIANT get() = colors.outlineVariant
    val DANGER get() = colors.error
    fun apply(dark: Boolean, accent: Int) { colors = Md3Colors(dark, accent) }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun panelDrawable(color: Int = Palette.PANEL, radiusDp: Int = 12, context: Context): GradientDrawable {
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
    textSize = when { sizeSp >= 24f -> 24f; sizeSp >= 20f -> 22f; sizeSp >= 15f -> 16f; else -> 14f }
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    includeFontPadding = false
}

fun TextView.bodyStyle(sizeSp: Float = 14f, color: Int = Palette.MUTED) {
    setTextColor(color)
    textSize = when { sizeSp >= 15f -> 16f; sizeSp > 12f -> 14f; else -> 12f }
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
    return if (androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, background) >=
        androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK, background)) Color.WHITE else Color.BLACK
}

fun stateColors(foreground: Int, disabled: Int = Palette.MUTED) = android.content.res.ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(disabled, foreground)
)

fun rippleDrawable(context: Context, fill: Int, radiusDp: Int = 12): android.graphics.drawable.RippleDrawable {
    val ripple = androidx.core.graphics.ColorUtils.setAlphaComponent(Palette.TEXT, 31)
    return android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ripple),
        panelDrawable(fill, radiusDp, context), panelDrawable(Color.WHITE, radiusDp, context))
}
