package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.a11y.InLook
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.arrived
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.AppContainer
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

/** The row for the entry open in the other pane is marked and kept in view (#58). */
@RunWith(RobolectricTestRunner::class)
class TimelineSelectionTest {
    @get:Rule val compose = createComposeRule()

    private var selected by mutableStateOf<String?>(null)
    private var app: AppContainer? = null

    @After fun tearDown() {
        app?.let { runBlocking { it.scope.coroutineContext.job.cancelAndJoin() } }
    }

    private val may3 = "1893-05-03-jonathan_harker-01"
    private val may4 = "1893-05-04-jonathan_harker-01"

    /** Everything that has arrived is read but the last, so the list opens far below the 3 May entry. */
    private fun show() {
        val app = testApp().also { this.app = it }
        runBlocking { app.tempo(); app.arrived().dropLast(1).forEach { app.users.markRead(it) } }
        compose.setContent {
            val density = LocalDensity.current
            val window = remember {
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize: IntSize
                        get() = with(density) { IntSize(400.dp.roundToPx(), 800.dp.roundToPx()) }
                }
            }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                InLook(Look.LIGHT) {
                    // A short real viewport, so the 3 May row starts well off screen.
                    Box(Modifier.size(400.dp, 300.dp)) { TimelineScreen(
                        remember { TimelineViewModel(app) }, onOpen = { _, _ -> }, onBookmarks = {}, onSettings = {},
                        selectedId = selected,
                    ) }
                }
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("tonight-card").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun selectedRows() = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

    @Test fun theSelectedRowSaysSo() {
        selected = may4
        show()
        compose.waitUntil(10_000) { selectedRows().fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, selectedRows().fetchSemanticsNodes().size)
        // Every other row is not selected: it carries no selected flag at all.
        val flagged = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes()
        assertEquals(1, flagged.size)
    }

    @Test fun aSelectedRowOffScreenIsScrolledIntoView() {
        show()
        assertTrue(selectedRows().fetchSemanticsNodes().isEmpty())
        selected = may3
        compose.waitUntil(10_000) { selectedRows().fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue(selectedRows().onFirst().isDisplayed())
    }

    @Test fun noSelectionKeepsTodaysOpeningPosition() {
        show()
        compose.waitForIdle()
        assertTrue(selectedRows().fetchSemanticsNodes().isEmpty())
        // Opens just above the first unread entry (the last one that arrived), not at the top where 3 May is.
        val firstDay = compose.onAllNodes(hasText("3 May")).fetchSemanticsNodes()
        assertTrue("the list did not scroll to the top", firstDay.isEmpty())
    }
}
