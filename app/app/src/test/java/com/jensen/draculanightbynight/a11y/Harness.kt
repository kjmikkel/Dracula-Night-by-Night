package com.jensen.draculanightbynight.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.ui.DraculaTheme
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import java.time.Instant

/** A way the app can look: its palette, and how big its text is. */
enum class Look(val theme: ThemeChoice, val highContrast: Boolean, val fontScale: Float, val textSize: TextSize) {
    LIGHT(ThemeChoice.LIGHT, false, 1f, TextSize.NORMAL),
    DARK(ThemeChoice.DARK, false, 1f, TextSize.NORMAL),
    LIGHT_HC(ThemeChoice.LIGHT, true, 1f, TextSize.NORMAL),
    DARK_HC(ThemeChoice.DARK, true, 1f, TextSize.NORMAL),
    LIGHT_LARGE(ThemeChoice.LIGHT, false, 2f, TextSize.XLARGE),
    DARK_LARGE(ThemeChoice.DARK, false, 2f, TextSize.XLARGE),
}

/** The instant every ratchet case runs at: 8 May 2026, noon UTC. */
val FIXED_NOW: Instant = Instant.parse("2026-05-08T12:00:00Z")

/** [content] in the app's theme as [look] would show it, with the system font scale [look] asks for. */
@Composable
fun InLook(look: Look, font: ReadingFont = ReadingFont.SERIF, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, look.fontScale)) {
        DraculaTheme(theme = look.theme, highContrast = look.highContrast, readingFont = font, textSize = look.textSize, content = content)
    }
}

fun ComposeContentTestRule.showInLook(look: Look, font: ReadingFont = ReadingFont.SERIF, content: @Composable () -> Unit) {
    setContent { InLook(look, font, content) }
}

/** A fresh app over the test's own files, its clock at [FIXED_NOW]. */
fun testApp(): AppContainer =
    AppContainer(ApplicationProvider.getApplicationContext()).also { it.clock.travelTo(FIXED_NOW) }
