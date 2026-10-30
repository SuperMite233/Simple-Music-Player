package com.supermite.smp.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class Md3ColorsTest {
    private fun luminance(color: Int): Double = listOf(16, 8, 0).map { shift ->
        val channel = ((color shr shift) and 255) / 255.0
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }.let { it[0] * 0.2126 + it[1] * 0.7152 + it[2] * 0.0722 }

    private fun contrast(a: Int, b: Int): Double = (max(luminance(a), luminance(b)) + 0.05) / (min(luminance(a), luminance(b)) + 0.05)

    @Test fun arbitraryUserSeedsKeepReadableLightAndDarkRoles() {
        val seeds = listOf(0xFF35C2A1, 0xFFF2B84B, 0xFF4A90E2, 0xFF9B6CFF,
            0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00)
        for (seed in seeds) for (dark in listOf(false, true)) {
            val c = Md3Colors(dark, seed.toInt())
            val pairs = listOf(c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondaryContainer to c.secondaryContainer, c.onSurface to c.surface,
                c.onSurfaceVariant to c.surfaceContainerHighest, c.primary to c.surfaceContainerHigh,
                c.tertiary to c.surfaceContainerLow, c.error to c.surfaceContainerHigh)
            pairs.forEach { (text, surface) -> assertTrue("Unreadable seed $seed / dark=$dark", contrast(text, surface) >= 4.49) }
        }
    }

    @Test fun foregroundRolesRemainReadableAcrossAllSurfaceLevels() {
        for (dark in listOf(false, true)) {
            val c = Md3Colors(dark, 0xFF9B6CFF.toInt())
            for (surface in listOf(c.surface, c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)) {
                assertTrue(contrast(c.onSurface, surface) >= 4.5)
                assertTrue(contrast(c.onSurfaceVariant, surface) >= 4.5)
            }
        }
    }
}
