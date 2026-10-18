package com.jensen.draculanightbynight.reader

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.showInLook
import com.jensen.draculanightbynight.a11y.testApp
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A saved place is a share of its block, so it means the same sentence at any text size or font (#200). */
@RunWith(RobolectricTestRunner::class)
class ReaderPlaceTest {
    @get:Rule val compose = createComposeRule()

    private var app: AppContainer? = null

    @After fun tearDown() {
        app?.let { runBlocking { it.scope.coroutineContext.job.cancelAndJoin() } }
    }

    /** Opens [ENTRY] saved halfway into its long paragraph [BLOCK] (no pixel offset), in [look]; returns what it saves. */
    private fun reopenedHalfway(look: Look): Triple<Int, Int, Int?> {
        val app = testApp().also { this.app = it }
        runBlocking {
            app.free()
            app.users.savePosition(ENTRY, BLOCK, blockOffset = 0, blockPermille = 500)
        }
        compose.showInLook(look) {
            ReaderScreen(remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) }, onBack = {})
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("When it grew dark there seemed", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // Past the save debounce, so the place the reader opened at is saved again.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        // The save itself runs in the app's scope, off the test's clock.
        runCatching { compose.waitUntil(10_000) { runBlocking { app.users.state.first().progressOf(ENTRY).blockOffset > 0 } } }
        val saved = runBlocking { app.users.state.first().progressOf(ENTRY) }
        return Triple(saved.blockIndex, saved.blockOffset, saved.blockPermille)
    }

    private fun assertHalfway(saved: Triple<Int, Int, Int?>) {
        assertEquals(BLOCK, saved.first)
        // Saved again from where it opened: the seed had no pixel offset at all.
        assertTrue("saved ${saved.second} px", saved.second > 0)
        val permille = saved.third ?: error("no share of the block saved")
        assertTrue("saved at $permille‰", permille in 495..505)
    }

    @Test fun atNormalTextItOpensHalfwayIntoTheBlock() = assertHalfway(reopenedHalfway(Look.LIGHT))

    @Test fun atLargeTextItOpensHalfwayIntoTheSameBlock() = assertHalfway(reopenedHalfway(Look.LIGHT_LARGE))

    private companion object {
        const val ENTRY = "1893-05-05-jonathan_harker-01"

        /** A paragraph of some 2,600 characters, many screens tall at large text. */
        const val BLOCK = 7
    }
}
