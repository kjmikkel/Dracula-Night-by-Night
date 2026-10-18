package com.jensen.draculanightbynight.reader

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The reader's "Who's who" opens the page on whoever wrote the entry (#204). */
@RunWith(RobolectricTestRunner::class)
class ReaderWhosWhoTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
    }

    private fun opened(look: Look): String? {
        app = testApp()
        runBlocking { app.free() }
        var opened: String? = null
        compose.showInLook(look) {
            ReaderScreen(
                remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, ENTRY, false, SavedStateHandle()) },
                onBack = {},
                onWhosWho = { opened = it },
            )
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Bookmark")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Who's who")).performClick()
        return opened
    }

    @Test fun theButtonOpensOnTheEntrysAuthor() = assertEquals("jonathan_harker", opened(Look.LIGHT))

    @Test fun itIsThereAtLargeTextToo() = assertEquals("jonathan_harker", opened(Look.LIGHT_LARGE))

    private companion object {
        const val ENTRY = "1893-05-04-jonathan_harker-01"
    }
}
