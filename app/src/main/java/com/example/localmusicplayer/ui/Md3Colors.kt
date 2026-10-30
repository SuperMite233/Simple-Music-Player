package com.supermite.smp.ui

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

/**
 * The saved RGB is a seed. All roles use the pinned MDC 1.14.0 implementation.
 * MDC does not expose the complete scheme via public MaterialColors (only four accent roles).
 * Keep this restricted algorithm access isolated here; contrast tests guard dependency upgrades.
 */
@SuppressLint("RestrictedApi")
data class Md3Colors(val dark: Boolean, val seed: Int) {
    private val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
    private val roles = MaterialDynamicColors()
    val primary = roles.primary().getArgb(scheme)
    val onPrimary = roles.onPrimary().getArgb(scheme)
    val primaryContainer = roles.primaryContainer().getArgb(scheme)
    val onPrimaryContainer = roles.onPrimaryContainer().getArgb(scheme)
    val secondaryContainer = roles.secondaryContainer().getArgb(scheme)
    val onSecondaryContainer = roles.onSecondaryContainer().getArgb(scheme)
    val tertiary = roles.tertiary().getArgb(scheme)
    val surface = roles.surface().getArgb(scheme)
    val surfaceContainerLow = roles.surfaceContainerLow().getArgb(scheme)
    val surfaceContainer = roles.surfaceContainer().getArgb(scheme)
    val surfaceContainerHigh = roles.surfaceContainerHigh().getArgb(scheme)
    val surfaceContainerHighest = roles.surfaceContainerHighest().getArgb(scheme)
    val onSurface = roles.onSurface().getArgb(scheme)
    val onSurfaceVariant = roles.onSurfaceVariant().getArgb(scheme)
    val outline = roles.outline().getArgb(scheme)
    val outlineVariant = roles.outlineVariant().getArgb(scheme)
    val error = roles.error().getArgb(scheme)
}
