package com.supermite.smp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar
import android.widget.TextView

/** A small visual track inside the full 48dp native seek target. */
private class PlayerProgressDrawable(private val context: Context, private val compact: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    override fun draw(canvas: Canvas) {
        val inset = context.dp(2).toFloat()
        val left = bounds.left + inset
        val right = bounds.right - inset
        val y = bounds.exactCenterY()
        paint.strokeWidth = context.dp(if (compact) 3 else 4).toFloat()
        paint.color = Palette.OUTLINE_VARIANT
        canvas.drawLine(left, y, right, y, paint)
        if (level > 0) {
            paint.color = Palette.ACCENT
            canvas.drawLine(left, y, left + (right - left) * level / 10000f, y, paint)
        }
    }
    override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicHeight(): Int = context.dp(4)
}

fun stylePlayerSeekBar(context: Context, seek: SeekBar, compact: Boolean = false) {
    seek.tag = if (compact) "miniPlayerProgress" else "playerProgress"
    seek.minimumHeight = context.dp(48)
    seek.setPadding(context.dp(8), 0, context.dp(8), 0)
    seek.progressDrawable = PlayerProgressDrawable(context, compact)
    seek.thumb = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Palette.ACCENT)
        setSize(context.dp(if (compact) 8 else 12), context.dp(if (compact) 8 else 12))
    }
    seek.thumbTintList = ColorStateList.valueOf(Palette.ACCENT)
    seek.progressTintList = ColorStateList.valueOf(Palette.ACCENT)
    seek.progressBackgroundTintList = ColorStateList.valueOf(Palette.OUTLINE_VARIANT)
    seek.thumbOffset = context.dp(if (compact) 4 else 6)
    seek.splitTrack = false
}

/** Preserve native text/accessibility while visually separating elapsed and total time. */
class PlaybackTimeView(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
    override fun onDraw(canvas: Canvas) {
        val values = text.toString().split(" / ", limit = 2)
        if (values.size != 2) { super.onDraw(canvas); return }
        paint.color = currentTextColor
        val baseline = paddingTop - paint.fontMetrics.top
        canvas.drawText(values[0], paddingLeft.toFloat(), baseline, paint)
        canvas.drawText(values[1], width - paddingRight - paint.measureText(values[1]), baseline, paint)
    }
}
