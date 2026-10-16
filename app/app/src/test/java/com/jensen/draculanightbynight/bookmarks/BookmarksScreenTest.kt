package com.jensen.draculanightbynight.bookmarks

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.showInLook
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** A locked bookmark asks "Read it now?" before it opens; one that has arrived opens straight away. */
@RunWith(RobolectricTestRunner::class)
class BookmarksScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: AppContainer
    private val opened = mutableListOf<String>()
    private val defaultZone = TimeZone.getDefault()

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        app = testApp()
        // Same tempo from 3 May, at 8 May noon: 4 May has arrived, 8 May 20:00 hasn't.
        runBlocking {
            app.tempo()
            app.users.toggleBookmark(ARRIVED)
            app.users.toggleBookmark(LOCKED)
        }
        compose.showInLook(Look.LIGHT) {
            BookmarksScreen(remember { BookmarksViewModel(app.stories, app.users, app.time.ticks()) }, onBack = {}, onOpen = { opened += it })
        }
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Locked", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    @Test fun lockedBookmarkAsksBeforeOpening() {
        compose.onNodeWithContentDescription("Locked", substring = true).performClick()

        compose.onNodeWithText("Read it now?").assertExists()
        assertEquals(emptyList<String>(), opened)
    }

    @Test fun arrivedBookmarkOpensStraightAway() {
        compose.onNodeWithText("4 May, 14:45", substring = true).performClick()

        compose.onNodeWithText("Read it now?").assertDoesNotExist()
        assertEquals(listOf(ARRIVED), opened)
    }

    private companion object {
        const val ARRIVED = "1893-05-04-jonathan_harker-01"
        const val LOCKED = "1893-05-08-jonathan_harker-01"
    }
}
