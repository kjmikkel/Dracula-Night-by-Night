package com.jensen.draculanightbynight.a11y

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * A case that opens a dialog or sheet must wait for text only that window shows: otherwise it passes, checking
 * just the screen below, when the dialog never opens.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class DialogReadyTest(private val caseName: String) {
    @get:Rule val compose = createComposeRule()

    @Test fun readyTextIsNotOnTheScreenBelow() {
        val case = cases.single { it.name == caseName }
        val app = testApp()
        try {
            runBlocking { case.seed(app) }
            compose.showInLook(Look.LIGHT) { case.content(app) }
            // Let the screen below load fully, then look for the ready text without opening anything.
            compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Back") or hasContentDescription("Settings")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            val ready = hasText(case.ready, substring = true) or hasContentDescription(case.ready, substring = true)
            assertTrue("'${case.ready}' is already on the screen below ${case.name}'s dialog", compose.onAllNodes(ready).fetchSemanticsNodes().isEmpty())
        } finally {
            runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters(): List<Array<Any>> = cases.filter { it.open != null }.map { arrayOf<Any>(it.name) }
    }
}
