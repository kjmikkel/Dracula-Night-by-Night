package com.jensen.draculanightbynight.reader

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.Shadows.shadowOf

/** What the text says that the header already shows can be skipped, and is with TalkBack, unless chosen (#177). */
@RunWith(RobolectricTestRunner::class)
class ReaderRepeatsTest {
    @get:Rule val compose = createComposeRule()

    private var app: AppContainer? = null

    @After fun tearDown() {
        app?.let { runBlocking { it.scope.coroutineContext.job.cancelAndJoin() } }
    }

    private fun open(id: String, setUp: suspend AppContainer.() -> Unit = {}) {
        val app = testApp().also { this.app = it }
        runBlocking { app.free(); app.setUp() }
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

    private fun talkBack(on: Boolean) {
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setTouchExplorationEnabled(on)
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test fun byDefaultTheTextIsAsPrinted() {
        open(DIARY)
        assertEquals(1, shown("4 May.—I found"))
    }

    @Test fun skippingRepeatedDatesDropsTheDiarysDateline() {
        open(DIARY) { users.setSkipRepeatedDates(true) }
        assertEquals(0, shown("4 May.—"))
        assertEquals(1, shown("I found that my landlord"))
    }

    @Test fun skippingRepeatedDatesDropsTheLettersDatelineLine() {
        open(LETTER) { users.setSkipRepeatedDates(true) }
        assertEquals(0, shown("24 May."))
        assertEquals(1, shown("My dearest Mina"))
    }

    @Test fun talkBackSkipsRepeatedDatesUntilTheReaderChooses() {
        talkBack(true)
        open(DIARY)
        assertEquals(0, shown("4 May.—"))
    }

    @Test fun theReadersChoiceBeatsTalkBack() {
        talkBack(true)
        open(DIARY) { users.setSkipRepeatedDates(false) }
        assertEquals(1, shown("4 May.—I found"))
    }

    @Test fun theFromToLineStaysUnlessChosen() {
        open(LETTER)
        assertEquals(1, shown("From Lucy Westenra"))
        assertEquals(1, shown("24 May."))
    }

    @Test fun theFromToLineGoesWhenTheHeadingNamesBoth() {
        open(LETTER) { users.setHidePartiesLine(true) }
        assertEquals(0, shown("From Lucy Westenra"))
        assertEquals(1, shown("Letter, Lucy Westenra to Mina Murray."))
    }

    @Test fun aTelegramKeepsItsFromToLine() {
        open(TELEGRAM) { users.setHidePartiesLine(true) }
        assertEquals(1, shown("From Arthur Holmwood"))
    }

    private companion object {
        const val DIARY = "1893-05-04-jonathan_harker-01"
        const val LETTER = "1893-05-24-lucy_westenra-01"
        const val TELEGRAM = "1893-05-26-arthur_holmwood-01"
    }
}
