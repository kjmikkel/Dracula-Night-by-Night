package com.jensen.draculanightbynight.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.showInLook
import com.jensen.draculanightbynight.a11y.testApp
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** In the split view the reader's ✕ and ☰ replace Back; without pane controls it is as it always was (#58). */
@RunWith(RobolectricTestRunner::class)
class ReaderPaneControlsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer
    private val defaultZone = TimeZone.getDefault()

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    private var pxPerDp = 1f

    private fun show(pane: PaneControls?, look: Look = Look.LIGHT, widthDp: Int? = null) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        app = testApp()
        runBlocking { app.free() }
        compose.showInLook(look) {
            pxPerDp = LocalDensity.current.density
            val reader = @Composable {
                ReaderScreen(
                    remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) },
                    onBack = {},
                    pane = pane,
                )
            }
            if (widthDp == null) reader() else Box(Modifier.width(widthDp.dp)) { reader() }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Bookmark")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun withoutPaneControlsTheReaderIsUnchanged() {
        show(null)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close entry").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show timeline").assertDoesNotExist()
    }

    @Test fun paneControlsReplaceBackWithClose() {
        var closed = 0
        show(PaneControls(timelineShown = true, onToggleTimeline = {}, onClose = { closed++ }))
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close entry").assertIsDisplayed().assert(!hasText("✕", substring = true))
        compose.onNodeWithContentDescription("Close entry").performClick()
        assertEquals(1, closed)
    }

    @Test fun theTimelineSwitchSaysItsState() {
        var toggled = 0
        show(PaneControls(timelineShown = true, onToggleTimeline = { toggled++ }, onClose = {}))
        compose.onNodeWithContentDescription("Show timeline")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(!hasText("☰", substring = true))
            .performClick()
        assertEquals(1, toggled)
    }

    @Test fun theTimelineSwitchIsOffWhenTheTimelineIsHidden() {
        show(PaneControls(timelineShown = false, onToggleTimeline = {}, onClose = {}))
        compose.onNodeWithContentDescription("Show timeline")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    /** The right pane on an 800 dp portrait tablet beside a 360 dp timeline is 440 dp wide; at 200 % text all three controls must fit. */
    @Test fun atLargeTextInANarrowPaneAllThreeControlsFit() {
        show(PaneControls(timelineShown = true, onToggleTimeline = {}, onClose = {}), Look.LIGHT_LARGE, widthDp = 440)
        for (name in listOf("Close entry", "Show timeline", "Bookmark")) {
            val node = compose.onNodeWithContentDescription(name)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$name clipped at the right: ${bounds.right}", bounds.right <= 440 * pxPerDp + 0.5f)
            assertTrue("$name clipped at the left: ${bounds.left}", bounds.left >= -0.5f)
        }
    }

    private companion object {
        const val ENTRY = "1893-05-03-jonathan_harker-01"
    }
}
