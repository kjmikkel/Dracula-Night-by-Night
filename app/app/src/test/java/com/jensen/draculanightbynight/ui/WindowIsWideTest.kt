package com.jensen.draculanightbynight.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Large text must not change the pane decision: only the raw window dp counts. */
@RunWith(RobolectricTestRunner::class)
class WindowIsWideTest {
    @get:Rule val compose = createComposeRule()

    private fun wideAt(widthDp: Int, heightDp: Int): Boolean {
        var wide: Boolean? = null
        compose.setContent {
            val density = Density(1f, fontScale = 2f)
            val window = object : WindowInfo {
                override val isWindowFocused = true
                override val containerSize = IntSize(widthDp, heightDp)
            }
            CompositionLocalProvider(LocalDensity provides density, LocalWindowInfo provides window) { wide = windowIsWide() }
        }
        compose.waitForIdle()
        return wide!!
    }

    @Test fun bigTextStillGetsTwoPanes() = assertTrue(wideAt(700, 600))

    @Test fun aShortWindowStillDoesNot() = assertFalse(wideAt(700, 470))
}
