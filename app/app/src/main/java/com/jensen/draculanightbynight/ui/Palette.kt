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
    /** Selected chips, segmented buttons and switch tracks, with the text drawn on them. */
    val selected: Long,
    val onSelected: Long,
    /** The decorative ornament; never text. */
    val ornament: Long,
    /** Snackbars: an inverted bar, its text and its action. */
    val inverseSurface: Long,
    val inverseOnSurface: Long,
    val inversePrimary: Long,
    /** Text on the error colour. */
    val onError: Long,
) {
    /** Every text colour on every background it appears on. */
    fun textPairs(): List<TextPair> {
        val backgrounds = listOf("background" to background, "surface" to surface, "surfaceVariant" to surfaceVariant) +
            containers.mapIndexed { i, c -> "container$i" to c }
        val texts = listOf("onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "primary" to primary, "error" to error)
        return texts.flatMap { (tn, t) -> backgrounds.map { (bn, b) -> TextPair("$tn on $bn", t, b) } } +
            TextPair("onBackground on background", onBackground, background) +
            TextPair("onPrimary on primary", onPrimary, primary) +
            TextPair("onSelected on selected", onSelected, selected) +
            TextPair("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface) +
            TextPair("inversePrimary on inverseSurface", inversePrimary, inverseSurface) +
            TextPair("onError on error", onError, error)
    }
}

object Palettes {
    // Restrained gothic: oxblood and deep ink on parchment.
    val Light = Palette(
        name = "Light", isDark = false,
        background = 0xFFF4ECDD, onBackground = 0xFF2A1A16,
        surface = 0xFFFAF4E8, onSurface = 0xFF2A1A16,
        surfaceVariant = 0xFFEADFCB, onSurfaceVariant = 0xFF5C4D43,
        containers = listOf(0xFFFFFBF3, 0xFFF8F0E2, 0xFFF1E7D6, 0xFFEADBC0, 0xFFE5D6BC),
        primary = 0xFF6E1414, onPrimary = 0xFFF4ECDD, secondary = 0xFF75655A, outline = 0xFF8A7A6E, error = 0xFF9C2328,
        selected = 0xFFE8CFC6, onSelected = 0xFF4A0E0E, ornament = 0xFF9B6B5A,
        inverseSurface = 0xFF2A1A16, inverseOnSurface = 0xFFF4ECDD, inversePrimary = 0xFFE0A458, onError = 0xFFF4ECDD,
    )

    // Candlelight: warm bone on near-black, candle amber for labels, blood red for what is selected.
    val Dark = Palette(
        name = "Dark", isDark = true,
        background = 0xFF121011, onBackground = 0xFFE8DCC6,
        surface = 0xFF1A1617, onSurface = 0xFFE8DCC6,
        surfaceVariant = 0xFF241E1F, onSurfaceVariant = 0xFFC2B39F,
        containers = listOf(0xFF0D0B0C, 0xFF171314, 0xFF1E191A, 0xFF2A1E17, 0xFF342620),
        primary = 0xFFE0A458, onPrimary = 0xFF121011, secondary = 0xFFA39282, outline = 0xFF7F6650, error = 0xFFF2B8B5,
        selected = 0xFFB23A2E, onSelected = 0xFFF6EBDD, ornament = 0xFF6B4A2F,
        inverseSurface = 0xFFE8DCC6, inverseOnSurface = 0xFF121011, inversePrimary = 0xFF6E1414, onError = 0xFF121011,
    )

    // High contrast: near-black on white or white on black; oxblood kept but deepened or lightened to pass 7:1.
    val LightHighContrast = Palette(
        name = "Light high contrast", isDark = false,
        background = 0xFFFFFFFF, onBackground = 0xFF000000,
        surface = 0xFFFFFFFF, onSurface = 0xFF000000,
        surfaceVariant = 0xFFF0EBE3, onSurfaceVariant = 0xFF1F1A17,
        containers = listOf(0xFFFFFFFF, 0xFFFAF8F5, 0xFFF5F2EC, 0xFFF0EBE3, 0xFFEAE4DA),
        primary = 0xFF5A0E12, onPrimary = 0xFFFFFFFF, secondary = 0xFF3B302A, outline = 0xFF000000, error = 0xFF7A0A0A,
        selected = 0xFF5A0E12, onSelected = 0xFFFFFFFF, ornament = 0xFF1F1A17,
        inverseSurface = 0xFF000000, inverseOnSurface = 0xFFFFFFFF, inversePrimary = 0xFFFFB3B0, onError = 0xFFFFFFFF,
    )

    val DarkHighContrast = Palette(
        name = "Dark high contrast", isDark = true,
        background = 0xFF000000, onBackground = 0xFFFFFFFF,
        surface = 0xFF000000, onSurface = 0xFFFFFFFF,
        surfaceVariant = 0xFF1C1919, onSurfaceVariant = 0xFFF2EDE6,
        containers = listOf(0xFF000000, 0xFF0A0909, 0xFF121010, 0xFF1C1919, 0xFF262222),
        primary = 0xFFFFB3B0, onPrimary = 0xFF000000, secondary = 0xFFE8DDD2, outline = 0xFFFFFFFF, error = 0xFFFFC4C1,
        selected = 0xFFFFB3B0, onSelected = 0xFF000000, ornament = 0xFFF2EDE6,
        inverseSurface = 0xFFFFFFFF, inverseOnSurface = 0xFF000000, inversePrimary = 0xFF5A0E12, onError = 0xFF000000,
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
