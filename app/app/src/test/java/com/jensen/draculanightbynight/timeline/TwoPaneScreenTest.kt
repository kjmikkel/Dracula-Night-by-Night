package com.jensen.draculanightbynight.timeline

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.InLook
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.reader.ReaderViewModel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/** The timeline and, once an entry is open, the entry beside it (#58). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class TwoPaneScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: AppContainer
    private val defaultZone = TimeZone.getDefault()
    private var hidden by mutableStateOf(false)
    private val opened = mutableListOf<Pair<String, Boolean>>()
    private var closed = 0

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    private fun show(withReader: Boolean) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        app = testApp()
        runBlocking { app.tempo() }
        compose.setContent {
            InLook(Look.LIGHT) {
                TwoPaneScreen(
                    timeline = remember { TimelineViewModel(app) },
                    reader = if (withReader) {
                        remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) }
                    } else {
                        null
                    },
                    timelineHidden = hidden,
                    onOpen = { id, filtered -> opened += id to filtered },
                    onClose = { closed++ },
                    onToggleTimeline = { hidden = !hidden },
                    onBookmarks = {},
                    onSettings = {},
                )
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText(ROW, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        if (withReader) {
            compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Bookmark")).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.waitForIdle()
    }

    private val timelineList = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollToIndexAction()

    /** The timeline's first visible item (lazy lists report 500 per item). */
    private fun firstVisibleItem(): Int =
        compose.onAllNodes(timelineList).onFirst().fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value().toInt() / 500

    @Test fun withoutAReaderItIsTheTimeline() {
        show(withReader = false)
        compose.onNodeWithText("Dracula Night by Night").assertExists()
        compose.onNodeWithContentDescription("Close entry").assertDoesNotExist()
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test fun bothPanesShowWithTheTimelineFirst() {
        show(withReader = true)
        val title = compose.onNodeWithText("Dracula Night by Night").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithContentDescription("Close entry").fetchSemanticsNode().boundsInRoot
        assertTrue("timeline at ${title.left}, reader at ${close.left}", title.left < close.left)
    }

    @Test fun hidingLeavesOnlyTheEntry() {
        hidden = true
        show(withReader = true)
        compose.onNodeWithContentDescription("Filter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close entry").assertExists()
        compose.onNodeWithContentDescription("Show timeline")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    /** Short, so the list scrolls. */
    @Config(qualifiers = "w1280dp-h400dp")
    @Test fun showingTheTimelineAgainKeepsItsPlace() {
        show(withReader = true)
        compose.onAllNodes(timelineList).onFirst().performScrollToIndex(2)
        compose.waitForIdle()
        val before = firstVisibleItem()
        assertNotEquals("the scroll must move the list for this test to mean anything", 0, before)
        compose.onNodeWithContentDescription("Show timeline").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Filter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show timeline").performClick()
        compose.waitForIdle()
        assertEquals(before, firstVisibleItem())
    }

    @Test fun backClosesTheEntry() {
        show(withReader = true)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test fun tappingARowAsksToOpenIt() {
        show(withReader = false)
        compose.onAllNodes(hasText(ROW, substring = true) and hasClickAction()).onFirst().performClick()
        assertEquals(listOf(ENTRY to false), opened)
    }

    private companion object {
        const val ENTRY = "1893-05-04-jonathan_harker-01"
        /** The start of [ENTRY]'s row preview. */
        const val ROW = "4 May.—I found"
    }
}
