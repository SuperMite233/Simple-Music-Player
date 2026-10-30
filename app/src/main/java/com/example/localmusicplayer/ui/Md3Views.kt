package com.supermite.smp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout

fun materialButton(context: Context, text: String, filled: Boolean = false): MaterialButton = MaterialButton(context).apply {
    this.text = text
    isAllCaps = false
    textSize = 14f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    minHeight = context.dp(48)
    minimumHeight = context.dp(48)
    minimumWidth = context.dp(48)
    insetTop = 0
    insetBottom = 0
    cornerRadius = context.dp(24)
    setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
    iconSize = context.dp(24)
    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    maxLines = 2
    setEmphasis(filled)
}

fun MaterialButton.setEmphasis(filled: Boolean) {
    tag = if (filled) "md3Filled" else "accentButton"
    backgroundTintList = stateColors(if (filled) Palette.ACCENT else Palette.ACCENT_CONTAINER, Palette.PANEL_ALT)
    setTextColor(stateColors(if (filled) Palette.ON_ACCENT else Palette.ON_ACCENT_CONTAINER))
    iconTint = textColors
    rippleColor = ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(currentTextColor, 31))
    elevation = 0f
}

fun materialCheckBox(context: Context): MaterialCheckBox = MaterialCheckBox(context).apply {
    textSize = 16f
    setTextColor(Palette.TEXT)
    minHeight = context.dp(48)
    buttonIconTintList = ColorStateList.valueOf(Palette.ON_ACCENT)
    buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Palette.ACCENT, Palette.OUTLINE))
}

fun materialSeekBar(context: Context): SeekBar = androidx.appcompat.widget.AppCompatSeekBar(context).apply {
    minimumHeight = context.dp(48)
    progressTintList = ColorStateList.valueOf(Palette.ACCENT)
    progressBackgroundTintList = ColorStateList.valueOf(Palette.SELECTED)
    thumbTintList = ColorStateList.valueOf(Palette.ACCENT)
    splitTrack = false
}

/** Applies shared form roles without changing the EditText references used by existing validation. */
fun styleMaterialTree(view: View) {
    if (view is ViewGroup && view !is TextInputLayout) {
        var index = 0
        while (index < view.childCount) {
            val child = view.getChildAt(index)
            if (child is EditText) {
                val oldParams = child.layoutParams
                val requestedHeight = oldParams.height
                val label = child.hint
                view.removeViewAt(index)
                val field = TextInputLayout(view.context, null, com.google.android.material.R.attr.textInputStyle).apply {
                    hint = label
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
                    boxBackgroundColor = Palette.PANEL_ALT
                    setBoxCornerRadii(view.context.dp(4).toFloat(), view.context.dp(4).toFloat(), 0f, 0f)
                    defaultHintTextColor = ColorStateList.valueOf(Palette.MUTED)
                    hintTextColor = ColorStateList.valueOf(Palette.ACCENT)
                    boxStrokeColor = Palette.ACCENT
                    setBoxStrokeColorStateList(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Palette.ACCENT, Palette.OUTLINE)))
                    setErrorTextColor(ColorStateList.valueOf(Palette.DANGER))
                    minimumHeight = context.dp(64)
                }
                child.hint = null
                child.background = null
                child.textSize = 16f
                child.setTextColor(Palette.TEXT)
                child.minimumHeight = view.context.dp(56)
                child.setPadding(view.context.dp(16), view.context.dp(8), view.context.dp(16), view.context.dp(8))
                field.addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    if (requestedHeight > view.context.dp(80)) requestedHeight else ViewGroup.LayoutParams.WRAP_CONTENT))
                oldParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.addView(field, index, oldParams)
            } else styleMaterialTree(child)
            index++
        }
    }
    if (view is android.widget.CheckedTextView) {
        val choiceColors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Palette.ACCENT, Palette.OUTLINE))
        view.checkMarkTintList = choiceColors
        view.compoundDrawablesRelative.forEach { it?.setTintList(choiceColors) }
        view.setTextColor(Palette.TEXT)
        view.minimumHeight = view.context.dp(48)
    }
    if (view is android.widget.ProgressBar) {
        if (view is android.widget.RatingBar) view.minimumHeight = view.context.dp(48)
        view.progressTintList = ColorStateList.valueOf(Palette.ACCENT)
        view.progressBackgroundTintList = ColorStateList.valueOf(Palette.SELECTED)
        view.indeterminateTintList = ColorStateList.valueOf(Palette.ACCENT)
    }
    if (view is LinearLayout && view.layoutParams != null && view.layoutParams.height > 0 &&
        (0 until view.childCount).any { view.getChildAt(it) is Button || view.getChildAt(it) is TextInputLayout }) {
        view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }
    if (view.isClickable) view.minimumHeight = maxOf(view.minimumHeight, view.context.dp(48))
    if (view is android.widget.Spinner) {
        view.background = object : android.graphics.drawable.GradientDrawable() {
            init { setColor(Palette.PANEL_ALT); cornerRadius = view.context.dp(4).toFloat() }
            override fun getPadding(padding: android.graphics.Rect): Boolean { padding.set(view.context.dp(12), view.context.dp(8), view.context.dp(40), view.context.dp(8)); return true }
            override fun draw(canvas: android.graphics.Canvas) {
                super.draw(canvas)
                val x = bounds.right - view.context.dp(22).toFloat()
                val y = bounds.exactCenterY()
                val paint = android.graphics.Paint(3).apply { color = Palette.MUTED; style = android.graphics.Paint.Style.STROKE; strokeWidth = view.context.dp(2).toFloat(); strokeCap = android.graphics.Paint.Cap.ROUND }
                val path = android.graphics.Path().apply { moveTo(x-view.context.dp(4), y-view.context.dp(2)); lineTo(x, y+view.context.dp(2)); lineTo(x+view.context.dp(4),y-view.context.dp(2)) }
                canvas.drawPath(path,paint)
            }
        }
    }
    if (view is Button || view is SeekBar || view is android.widget.Spinner || (view is TextView && view.isClickable)) {
        view.minimumHeight = maxOf(view.minimumHeight, view.context.dp(48))
        if (view is Button) view.layoutParams?.let {
            if (it.width in 1 until view.context.dp(48)) it.width = view.context.dp(48)
            if (it.width > 0 && view.text.isNotBlank() && view.resources.configuration.fontScale > 1.3f) {
                val desired = kotlin.math.ceil(view.paint.measureText(view.text.toString()) + view.compoundPaddingLeft + view.compoundPaddingRight).toInt()
                it.width = maxOf(it.width, minOf(desired, (view.resources.displayMetrics.widthPixels * 0.5f).toInt()))
            }
        }
        view.layoutParams?.let { params ->
            if (params.height > 0 && view !is SeekBar) params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }
    if (view is TextView && view !is EditText && view !is Button) {
        if (view.maxLines < Int.MAX_VALUE && view.ellipsize != android.text.TextUtils.TruncateAt.MARQUEE) view.ellipsize = android.text.TextUtils.TruncateAt.END
        if (view.isClickable) {
            view.isFocusable = true
            if (view.background == null || view.background is android.graphics.drawable.ColorDrawable) view.background = rippleDrawable(view.context, Color.TRANSPARENT)
        }
    }
}
