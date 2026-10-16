package com.jensen.draculanightbynight.reader

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** The reader's bookmark button is a switch: TalkBack hears "Bookmark", its state, and no star. */
@RunWith(RobolectricTestRunner::class)
class ReaderBookmarkTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer
    private val defaultZone = TimeZone.getDefault()

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        app = testApp()
        runBlocking { app.free() }
        compose.showInLook(Look.LIGHT) {
            ReaderScreen(remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) }, onBack = {})
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Bookmark")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    private fun bookmark() = compose.onNodeWithContentDescription("Bookmark")

    @Test fun bookmarkIsASwitchThatStartsOff() {
        bookmark()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(!hasText("☆", substring = true))
    }

    @Test fun tappingTheBookmarkTurnsItOn() {
        bookmark().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)).fetchSemanticsNodes().isNotEmpty()
        }
        bookmark()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(!hasText("★", substring = true))
    }

    private companion object {
        const val ENTRY = "1893-05-03-jonathan_harker-01"
    }
}
