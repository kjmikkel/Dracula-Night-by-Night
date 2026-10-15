package com.jensen.draculanightbynight.ui

import com.jensen.draculanightbynight.user.ThemeChoice
import kotlin.math.pow

/** A text colour and the background it is drawn on, as ARGB. */
data class TextPair(val name: String, val foreground: Long, val background: Long)

/**
 * The app's colours as plain ARGB values, so their contrast can be checked on the JVM. [Theme] turns a palette
 * into a Material colour scheme. Text is drawn in on*, primary and error colours on the backgrounds below.
 */
data class Palette(
    val name: String,
    val isDark: Boolean,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val containers: List<Long>, // lowest, low, normal, high, highest
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val outline: Long,
    val error: Long,
) {
    /** Every text colour on every background it appears on. */
    fun textPairs(): List<TextPair> {
        val backgrounds = listOf("background" to background, "surface" to surface, "surfaceVariant" to surfaceVariant) +
            containers.mapIndexed { i, c -> "container$i" to c }
        val texts = listOf("onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "primary" to primary, "error" to error)
        return texts.flatMap { (tn, t) -> backgrounds.map { (bn, b) -> TextPair("$tn on $bn", t, b) } } +
            TextPair("onBackground on background", onBackground, background) +
            TextPair("onPrimary on primary", onPrimary, primary)
    }
}

object Palettes {
    // Restrained gothic baseline: oxblood on parchment, bone on near-black.
    val Light = Palette(
        name = "Light", isDark = false,
        background = 0xFFF4ECDC, onBackground = 0xFF2A1F1A,
        surface = 0xFFFBF6EC, onSurface = 0xFF2A1F1A,
        surfaceVariant = 0xFFEADFCB, onSurfaceVariant = 0xFF5C4D43,
        containers = listOf(0xFFFFFBF3, 0xFFF8F0E2, 0xFFF1E7D6, 0xFFEBE0CD, 0xFFE5D9C4),
        primary = 0xFF7A1F24, onPrimary = 0xFFFBF6EC, secondary = 0xFF75655A, outline = 0xFF8A7A6E, error = 0xFF9C2328,
    )

    val Dark = Palette(
        name = "Dark", isDark = true,
        background = 0xFF141011, onBackground = 0xFFECE2D4,
        surface = 0xFF1D1718, onSurface = 0xFFECE2D4,
        surfaceVariant = 0xFF2A2223, onSurfaceVariant = 0xFFBFAF9F,
        containers = listOf(0xFF0F0C0D, 0xFF1A1516, 0xFF211A1B, 0xFF2B2324, 0xFF362C2D),
        primary = 0xFFDB8280, onPrimary = 0xFF1D1718, secondary = 0xFFA39282, outline = 0xFF7F7066, error = 0xFFF2B8B5,
    )

    // High contrast: near-black on white or white on black; oxblood kept but deepened or lightened to pass 7:1.
    val LightHighContrast = Palette(
        name = "Light high contrast", isDark = false,
        background = 0xFFFFFFFF, onBackground = 0xFF000000,
        surface = 0xFFFFFFFF, onSurface = 0xFF000000,
        surfaceVariant = 0xFFF0EBE3, onSurfaceVariant = 0xFF1F1A17,
        containers = listOf(0xFFFFFFFF, 0xFFFAF8F5, 0xFFF5F2EC, 0xFFF0EBE3, 0xFFEAE4DA),
        primary = 0xFF5A0E12, onPrimary = 0xFFFFFFFF, secondary = 0xFF3B302A, outline = 0xFF000000, error = 0xFF7A0A0A,
    )

    val DarkHighContrast = Palette(
        name = "Dark high contrast", isDark = true,
        background = 0xFF000000, onBackground = 0xFFFFFFFF,
        surface = 0xFF000000, onSurface = 0xFFFFFFFF,
        surfaceVariant = 0xFF1C1919, onSurfaceVariant = 0xFFF2EDE6,
        containers = listOf(0xFF000000, 0xFF0A0909, 0xFF121010, 0xFF1C1919, 0xFF262222),
        primary = 0xFFFFB3B0, onPrimary = 0xFF000000, secondary = 0xFFE8DDD2, outline = 0xFFFFFFFF, error = 0xFFFFC4C1,
    )
}

/**
 * The palette for the reader's choices. [highContrast] null means "follow Android" ([systemHighContrast],
 * Android 14+'s Contrast setting); once the reader sets the switch, their choice wins.
 */
fun paletteFor(theme: ThemeChoice, systemDark: Boolean, highContrast: Boolean?, systemHighContrast: Boolean): Palette {
    val dark = when (theme) {
        ThemeChoice.SYSTEM -> systemDark
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val high = highContrast ?: systemHighContrast
    return when {
        dark && high -> Palettes.DarkHighContrast
        dark -> Palettes.Dark
        high -> Palettes.LightHighContrast
        else -> Palettes.Light
    }
}

/** WCAG 2 contrast ratio between two opaque ARGB colours, from 1 (same) to 21 (black on white). */
fun contrast(a: Long, b: Long): Double {
    val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (lighter + 0.05) / (darker + 0.05)
}

private fun luminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
