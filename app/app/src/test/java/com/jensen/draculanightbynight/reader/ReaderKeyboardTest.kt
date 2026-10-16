package com.jensen.draculanightbynight.reader

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** An entry can be read from the keyboard: its text takes focus, and the arrow and page keys scroll it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderKeyboardTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer
    private val defaultZone = TimeZone.getDefault()

    /** The entry's text: the list that scrolls up and down and can take focus. */
    private val body = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        app = testApp()
        runBlocking { app.free() }
        compose.showInLook(Look.LIGHT) {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            ReaderScreen(remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) }, onBack = {})
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Bistritz", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    private fun scrolled(): Float = compose.onNode(body).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun press(key: Key) {
        compose.onNode(body).requestFocus()
        compose.onNode(body).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test fun theTextCanTakeFocus() {
        compose.onNode(body).requestFocus()
        compose.waitForIdle()
        assertEquals(true, compose.onNode(body).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused))
    }

    @Test fun tabFromTheBookmarkReachesTheText() {
        // Tab, not a focus request: on a phone, Tab went from Bookmark straight to Previous.
        compose.onNode(hasContentDescription("Bookmark")).requestFocus()
        compose.onNode(hasContentDescription("Bookmark")).performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        assertEquals(true, compose.onNode(body).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused))
    }

    @Test fun spaceScrollsLikePageDown() {
        press(Key.Spacebar)
        assertTrue(scrolled() > 0f)
    }

    @Test fun arrowDownScrollsALineAndPageDownAScreen() {
        // A lazy list reports an estimate of how far it has scrolled, so the two are compared, not measured.
        press(Key.DirectionDown)
        val line = scrolled()
        press(Key.MoveHome)
        press(Key.PageDown)
        val page = scrolled()
        assertTrue("line $line, page $page", line > 0f && page > line * 4)
    }

    @Test fun pageUpGoesBack() {
        press(Key.PageDown)
        press(Key.PageDown)
        val down = scrolled()
        press(Key.PageUp)
        assertTrue(scrolled() < down)
    }

    @Test fun homeGoesToTheTop() {
        press(Key.PageDown)
        press(Key.MoveHome)
        assertEquals(0f, scrolled())
    }

    private companion object {
        /** Jonathan's first diary entry: several screens long. */
        const val ENTRY = "1893-05-03-jonathan_harker-01"
    }
}
