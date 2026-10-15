package com.jensen.draculanightbynight.ui

import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.jensen.draculanightbynight.R
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice

private val Serif = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    )
}

val TelegramStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 1.sp)

/** How the story text is set: the reader's font and size. Headers and menus keep the theme's fonts. */
data class ReadingStyle(val fontFamily: FontFamily, val scale: Float) {
    /** [base] in the reader's font, scaled; the system font size still applies on top. */
    fun applyTo(base: TextStyle): TextStyle =
        base.copy(fontFamily = fontFamily, fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale)
}

val LocalReadingStyle = staticCompositionLocalOf { ReadingStyle(FontFamily.Serif, 1f) }

fun ReadingFont.family(): FontFamily = when (this) {
    ReadingFont.SERIF -> FontFamily.Serif
    ReadingFont.SANS -> FontFamily.SansSerif
    ReadingFont.ATKINSON -> FontFamily(
        Font(R.font.atkinson_hyperlegible_regular),
        Font(R.font.atkinson_hyperlegible_italic, style = FontStyle.Italic),
    )
    ReadingFont.OPEN_DYSLEXIC -> FontFamily(
        Font(R.font.opendyslexic_regular),
        Font(R.font.opendyslexic_italic, style = FontStyle.Italic),
    )
}

private fun Palette.colorScheme(): ColorScheme {
    val (lowest, low, normal, high, highest) = containers.map(::Color)
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    // Every surface role is set, otherwise cards, dialogs and bars fall back to Material's lavender.
    return scheme.copy(
        primary = Color(primary), onPrimary = Color(onPrimary), secondary = Color(secondary),
        background = Color(background), onBackground = Color(onBackground),
        surface = Color(surface), onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceContainerLowest = lowest, surfaceContainerLow = low, surfaceContainer = normal,
        surfaceContainerHigh = high, surfaceContainerHighest = highest,
        outline = Color(outline), outlineVariant = Color(outline), error = Color(error),
    )
}

/** Android 14+'s Contrast setting at its highest step; earlier versions have no such setting. */
@Composable
fun systemHighContrast(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val uiMode = LocalContext.current.getSystemService(UiModeManager::class.java) ?: return false
    return uiMode.contrast >= HIGH_CONTRAST_LEVEL
}

private const val HIGH_CONTRAST_LEVEL = 0.75f

@Composable
fun DraculaTheme(
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    highContrast: Boolean? = null,
    readingFont: ReadingFont = ReadingFont.SERIF,
    textSize: TextSize = TextSize.NORMAL,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(theme, isSystemInDarkTheme(), highContrast, systemHighContrast())
    CompositionLocalProvider(LocalReadingStyle provides ReadingStyle(readingFont.family(), textSize.scale)) {
        MaterialTheme(colorScheme = palette.colorScheme(), typography = Serif, content = content)
    }
}
