package com.jensen.draculanightbynight.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.showInLook
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.timeline.TimelineScreen
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** At 200 % text the titles give the text room (#151): they scroll away with it, and the bars stay. */
@RunWith(RobolectricTestRunner::class)
class LargeTextBarsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer
    private val defaultZone = TimeZone.getDefault()

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        app = testApp()
    }

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    private fun shown(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() &&
        compose.onAllNodes(matcher)[0].isDisplayed()

    private fun reader(look: Look) {
        runBlocking { app.free() }
        compose.showInLook(look) {
            ReaderScreen(remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) }, onBack = {})
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Bistritz", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp() }
        compose.waitForIdle()
    }

    private fun timeline(look: Look) {
        runBlocking { app.tempo() }
        compose.showInLook(look) { TimelineScreen(remember { TimelineViewModel(app) }, onOpen = { _, _ -> }, onBookmarks = {}, onSettings = {}) }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Read now")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        compose.waitForIdle()
    }

    @Test fun atLargeTextTheAuthorScrollsAwayWithTheText() {
        reader(Look.LIGHT_LARGE)
        assertFalse("author still shown", shown(hasText("Jonathan Harker")))
        assertTrue("the bars stay, for TalkBack too", shown(hasContentDescription("Back")))
    }

    @Test fun atNormalTextTheReadersBarsStay() {
        reader(Look.LIGHT)
        assertTrue(shown(hasContentDescription("Back")))
        assertTrue(shown(hasContentDescription("Next entry in the story")))
    }

    @Test fun atLargeTextTheTimelinesTitleScrollsAway() {
        timeline(Look.LIGHT_LARGE)
        assertFalse(shown(hasText("Dracula Night by Night")))
        assertTrue("the icons stay", shown(hasContentDescription("Settings")))
    }

    @Test fun atNormalTextTheTimelinesTitleStays() {
        timeline(Look.LIGHT)
        assertTrue(shown(hasText("Dracula Night by Night")))
    }

    private companion object {
        const val ENTRY = "1893-05-03-jonathan_harker-01"
    }
}
