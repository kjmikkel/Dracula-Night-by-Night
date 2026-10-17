package com.jensen.draculanightbynight.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.InLook
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.free
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

/** The book's lines of dots become the ornament inside an entry, and go at its start or end (#178). */
@RunWith(RobolectricTestRunner::class)
class ReaderDividerTest {
    @get:Rule val compose = createComposeRule()

    private var app: AppContainer? = null

    @After fun tearDown() {
        app?.let { runBlocking { it.scope.coroutineContext.job.cancelAndJoin() } }
    }

    /** The reader on [id], in a window tall enough that every block of these short entries is laid out. */
    private fun open(id: String) {
        val app = testApp().also { this.app = it }
        runBlocking { app.free() }
        compose.setContent {
            InLook(Look.LIGHT) {
                Box(Modifier.size(400.dp, 20_000.dp)) {
                    ReaderScreen(remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, id, false, SavedStateHandle()) }, onBack = {})
                }
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Next", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun ornaments() = compose.onAllNodesWithTag("divider").fetchSemanticsNodes().size
    private fun dots() = compose.onAllNodesWithText(". . .", substring = true).fetchSemanticsNodes().size

    @Test fun aBreakInsideAnEntryIsTheOrnament() {
        open("1893-10-31-mina_harker-02")
        assertEquals(1, ornaments())
        assertEquals(0, dots())
    }

    @Test fun aBreakAtTheEndOfAnEntryIsDropped() {
        open("1893-10-16-jonathan_harker-01")
        assertEquals(0, ornaments())
        assertEquals(0, dots())
    }
}
