package com.supermite.smp.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Existing validators can keep assigning EditText.error; expose it inline in the M3 field. */
class Md3TextInput(context: Context) : TextInputEditText(context) {
    private fun container(): TextInputLayout? = generateSequence(parent as? View) { it.parent as? View }
        .filterIsInstance<TextInputLayout>().firstOrNull()

    override fun setError(error: CharSequence?, icon: Drawable?) {
        val field = container()
        if (field != null) field.error = error else super.setError(error, icon)
    }

    override fun getError(): CharSequence? = container()?.error ?: super.getError()
}
