package com.jensen.draculanightbynight.whoswho

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.testApp
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What TalkBack hears on a profile: the name, the line and the state, not the fold arrow (#204). */
@RunWith(RobolectricTestRunner::class)
class WhosWhoScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun theFoldArrowIsNotSpoken() {
        app = testApp()
        runBlocking {
            app.free()
            app.users.markRead("1893-05-03-jonathan_harker-01")
        }
        val viewModel = WhosWhoViewModel(app.stories, app.users, null, SavedStateHandle())
        compose.setContent { WhosWhoScreen(viewModel, onBack = {}) }
        val profile = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
        compose.waitUntil(30_000) { compose.onAllNodes(profile).fetchSemanticsNodes().isNotEmpty() }
        val spoken = compose.onAllNodes(profile).fetchSemanticsNodes().first().config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }
        assertTrue(spoken, "Jonathan Harker" in spoken)
        assertFalse(spoken, "▾" in spoken || "▴" in spoken)
    }
}
