package com.jensen.draculanightbynight.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jensen.draculanightbynight.R
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice

// AndroidX's default navigation bar scrims.
private val NAV_LIGHT_SCRIM = AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val NAV_DARK_SCRIM = AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)

/** The period display face (IM Fell English, OFL): headings only, never story text. */
val DisplayFamily = FontFamily(
    Font(R.font.im_fell_english_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.im_fell_english_italic, FontWeight.Normal, FontStyle.Italic),
)

/** IM Fell English small capitals, for day headers and mastheads. */
val DisplayCapsFamily = FontFamily(Font(R.font.im_fell_english_sc))

private val Serif = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = DisplayFamily),
        titleMedium = base.titleMedium.copy(fontFamily = DisplayCapsFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFamily),
    )
}

val TelegramStyle = TextStyle(fontFamily = FontFamily(Font(R.font.special_elite)), fontSize = 15.sp, letterSpacing = 1.sp)

/** How the story text is set: the reader's font and size. Headers and menus keep the theme's fonts. */
data class ReadingStyle(val fontFamily: FontFamily, val scale: Float) {
    /** [base] in the reader's font, scaled; the system font size still applies on top. */
    fun applyTo(base: TextStyle): TextStyle =
        base.copy(fontFamily = fontFamily, fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale)
}

val LocalReadingStyle = staticCompositionLocalOf { ReadingStyle(FontFamily.Serif, 1f) }

/** The palette's ornament colour (decorative only, never text). */
val LocalOrnamentColor = staticCompositionLocalOf { Color.Unspecified }

/** The ornament between sections: decorative, so TalkBack skips it. */
@Composable
fun Ornament(modifier: Modifier = Modifier) {
    Text(
        "❦",
        modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics {},
        color = LocalOrnamentColor.current,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
    )
}

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
        // Selected chips, segmented buttons and switches: the palette's own selection colour, never lavender.
        primaryContainer = Color(selected), onPrimaryContainer = Color(onSelected),
        secondaryContainer = Color(selected), onSecondaryContainer = Color(onSelected),
        tertiary = Color(primary), onTertiary = Color(onPrimary),
        tertiaryContainer = Color(selected), onTertiaryContainer = Color(onSelected),
        surfaceTint = Color(primary),
        // Snackbars and error text: also the palette's own, so no default purple shows anywhere.
        inverseSurface = Color(inverseSurface), inverseOnSurface = Color(inverseOnSurface), inversePrimary = Color(inversePrimary),
        onError = Color(onError),
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
    // The system bars follow the app's palette, not the phone's theme: a dark app on a light phone otherwise gets
    // dark icons on a dark bar. enableEdgeToEdge sets the icons and, on Android 8–9, the matching scrim together.
    val activity = LocalView.current.context as? ComponentActivity
    LaunchedEffect(activity, palette.isDark) {
        val status = if (palette.isDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        // The navigation bar keeps AndroidX's scrims: Android 8.0 cannot draw dark navigation icons.
        val navigation = if (palette.isDark) SystemBarStyle.dark(NAV_DARK_SCRIM) else SystemBarStyle.light(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM)
        activity?.enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = navigation)
    }
    CompositionLocalProvider(
        LocalReadingStyle provides ReadingStyle(readingFont.family(), textSize.scale),
        LocalOrnamentColor provides Color(palette.ornament),
    ) {
        MaterialTheme(colorScheme = palette.colorScheme(), typography = Serif, content = content)
    }
}
