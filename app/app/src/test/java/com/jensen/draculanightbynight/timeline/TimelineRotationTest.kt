package com.jensen.draculanightbynight.timeline

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.a11y.InLook
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.arrived
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Rotating to a short window moves the cards into the list; they must still be on screen (#108). */
@RunWith(RobolectricTestRunner::class)
class TimelineRotationTest {
    @get:Rule val compose = createComposeRule()

    /** The window height the timeline sees: tall is portrait, short is landscape (under SHORT_WINDOW_DP). */
    private var windowHeightDp by mutableStateOf(800)

    /** All of the card is on screen, not just a strip of it peeking out at the top of the list. */
    private fun assertWholeCardShows() {
        val card = compose.onNodeWithTag("tonight-card").fetchSemanticsNode()
        assertEquals("visible height of the Tonight card", card.size.height.toFloat(), card.boundsInRoot.height, 0.5f)
    }

    @Test fun theCardsShowAfterRotatingToAShortWindow() {
        val app = testApp()
        // Everything that has arrived is read but the last one, so the list opens scrolled well down to it.
        runBlocking { app.tempo(); app.arrived().dropLast(1).forEach { app.users.markRead(it) } }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val density = LocalDensity.current
            val window = remember {
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize: IntSize
                        get() = with(density) { IntSize(400.dp.roundToPx(), windowHeightDp.dp.roundToPx()) }
                }
            }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                InLook(Look.LIGHT) {
                    TimelineScreen(remember { TimelineViewModel(app) }, onOpen = { _, _ -> }, onBookmarks = {}, onSettings = {})
                }
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("tonight-card").fetchSemanticsNodes().isNotEmpty() }
        assertWholeCardShows()

        // Rotate: the activity is recreated with its saved state, in a short window.
        windowHeightDp = 360
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertWholeCardShows()
    }
}
