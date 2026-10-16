package com.jensen.draculanightbynight.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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

/**
 * A way the app can look: its palette, and how big its text is. The large looks are the spike's: font size 200 %
 * with the largest display size, which on its 1080-pixel phone leaves a screen 329 × 731 dp.
 */
enum class Look(val theme: ThemeChoice, val highContrast: Boolean, val fontScale: Float, val textSize: TextSize) {
    LIGHT(ThemeChoice.LIGHT, false, 1f, TextSize.NORMAL),
    DARK(ThemeChoice.DARK, false, 1f, TextSize.NORMAL),
    LIGHT_HC(ThemeChoice.LIGHT, true, 1f, TextSize.NORMAL),
    DARK_HC(ThemeChoice.DARK, true, 1f, TextSize.NORMAL),
    LIGHT_LARGE(ThemeChoice.LIGHT, false, 2f, TextSize.XLARGE),
    DARK_LARGE(ThemeChoice.DARK, false, 2f, TextSize.XLARGE),
}

/** The spike's phone at the largest display size. */
private val LARGEST_DISPLAY = DpSize(329.dp, 731.dp)

/** The instant every ratchet case runs at: 8 May 2026, noon UTC. */
val FIXED_NOW: Instant = Instant.parse("2026-05-08T12:00:00Z")

/** [content] in the app's theme as [look] would show it, with the system font scale [look] asks for. */
@Composable
fun InLook(look: Look, font: ReadingFont = ReadingFont.SERIF, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, look.fontScale)) {
        DraculaTheme(theme = look.theme, highContrast = look.highContrast, readingFont = font, textSize = look.textSize) {
            if (look.fontScale > 1f) {
                Box(Modifier.requiredSize(LARGEST_DISPLAY).clipToBounds()) { content() }
            } else {
                content()
            }
        }
    }
}

fun ComposeContentTestRule.showInLook(look: Look, font: ReadingFont = ReadingFont.SERIF, content: @Composable () -> Unit) {
    setContent { InLook(look, font, content) }
}

/**
 * A fresh app over its own, empty files, its clock at [FIXED_NOW]. Each gets its own folder: DataStore refuses two
 * live stores on one file, and Robolectric reuses the files folder between the cases of a parameterized test.
 */
fun testApp(): AppContainer {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val files = java.nio.file.Files.createTempDirectory("a11y-app").toFile().apply { deleteOnExit() }
    val own = object : android.content.ContextWrapper(context) {
        override fun getFilesDir(): java.io.File = files
    }
    return AppContainer(own).also { it.clock.travelTo(FIXED_NOW) }
}
