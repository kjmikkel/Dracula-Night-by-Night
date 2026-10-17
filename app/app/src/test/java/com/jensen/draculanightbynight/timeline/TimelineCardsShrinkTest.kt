package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.InLook
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.arrived
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.Voice
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Scrolling the timeline shrinks the pinned cards out of the way; back at the top they return (#180). */
@RunWith(RobolectricTestRunner::class)
class TimelineCardsShrinkTest {
    @get:Rule val compose = createComposeRule()

    private var app: AppContainer? = null

    @After fun tearDown() {
        app?.let { runBlocking { it.scope.coroutineContext.job.cancelAndJoin() } }
    }

    /** The timeline in a [windowHeightDp]-tall window, after [seed] on top of Same tempo. */
    private fun show(windowHeightDp: Int = 800, restoration: StateRestorationTester? = null, seed: suspend AppContainer.() -> Unit = {}) {
        val app = testApp().also { this.app = it }
        runBlocking { app.tempo(); app.seed() }
        val content: @Composable () -> Unit = {
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
                    Box(Modifier.size(400.dp, windowHeightDp.dp)) {
                        TimelineScreen(remember { TimelineViewModel(app) }, onOpen = { _, _ -> }, onBookmarks = {}, onSettings = {})
                    }
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("timeline-list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun scrollListTo(index: Int) {
        compose.onNodeWithTag("timeline-list").performScrollToIndex(index)
        compose.waitForIdle()
    }

    private fun shows(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun showsText(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun theTimelineOpensWithTheFullCardEvenAwayFromTheTop() {
        // Everything is read but the last, so the list opens scrolled far down to it.
        show { arrived().dropLast(1).forEach { users.markRead(it) } }
        assertTrue("full card", shows("tonight-card"))
        assertTrue("no strip", !shows("tonight-strip"))
    }

    @Test fun scrollingShrinksTheCardToAStripThatStillReads() {
        show()
        scrollListTo(6)
        assertTrue("strip", shows("tonight-strip"))
        assertTrue("no full card", !shows("tonight-card"))
        // The strip keeps the card's Read button, with the same TalkBack label.
        val read = compose.onAllNodes(hasContentDescription("Read ", substring = true)).fetchSemanticsNodes()
        assertEquals(1, read.size)
    }

    @Test fun backAtTheTopTheCardReturns() {
        show()
        scrollListTo(6)
        scrollListTo(0)
        assertTrue("full card", shows("tonight-card"))
        assertTrue("no strip", !shows("tonight-strip"))
    }

    @Test fun scrollingHidesTheAheadLine() {
        show { users.setSkipAhead(2 * 24 * 60) }
        assertTrue(showsText("Back to real time"))
        scrollListTo(6)
        assertTrue("ahead line gone", !showsText("Back to real time"))
    }

    @Test fun scrollingShrinksTheFilteredLineToOneScrollableLineKeepingClear() {
        show { users.setFilter(EntryFilter(voices = setOf(Voice.JONATHAN, Voice.MINA, Voice.LUCY, Voice.SEWARD))) }
        scrollListTo(3)
        val line = compose.onNodeWithTag("filtered-line")
        line.assertIsDisplayed()
        // The summary scrolls sideways inside the line instead of wrapping.
        val sideways = compose.onAllNodes(hasScrollAction() and SemanticsMatcher("inside the filtered line") { node ->
            generateSequence(node.parent) { it.parent }.any { it.config.getOrElse(SemanticsProperties.TestTag) { "" } == "filtered-line" }
        }).fetchSemanticsNodes()
        assertEquals("one sideways-scrolling summary", 1, sideways.size)
        assertTrue("Clear stays", showsText("Clear"))
    }

    @Test fun inAShortWindowTheCardsScrollAwayAsToday() {
        show(windowHeightDp = 400)
        scrollListTo(6)
        assertTrue("no strip in a short window", !shows("tonight-strip"))
    }

    @Test fun theStripSurvivesRotation() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        scrollListTo(6)
        restoration.emulateSavedInstanceStateRestore()
        // The restored screen has a new view model, which loads the story off the main thread: wait for the list again.
        compose.waitUntil(30_000) { shows("timeline-list") }
        compose.waitForIdle()
        assertTrue("strip after restore", shows("tonight-strip"))
        assertTrue(compose.onAllNodes(hasTestTag("tonight-card")).fetchSemanticsNodes().isEmpty())
    }
}
