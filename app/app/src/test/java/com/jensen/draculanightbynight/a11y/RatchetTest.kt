package com.jensen.draculanightbynight.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.jensen.draculanightbynight.user.ReadingFont
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.TimeZone

/**
 * The accessibility ratchet: every [Case] in each of its looks, checked against `a11y-baseline.txt`. A violation
 * that isn't listed fails, and so does a listed one that no longer happens.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class RatchetTest(private val caseName: String, private val lookKey: String) {
    @get:Rule val compose = createComposeRule()

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @Test fun meetsTheBaseline() {
        val case = cases.single { it.name == caseName }
        val look = Look.valueOf(lookKey.substringBefore('/'))
        val font = lookKey.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let(ReadingFont::valueOf) ?: ReadingFont.SERIF
        val app = testApp()
        val found = try {
            runBlocking { case.seed(app) }
            compose.showInLook(look, font) { case.content(app) }
            compose.waitForIdle()
            case.open?.invoke(compose)
            val ready = hasText(case.ready, substring = true) or hasContentDescription(case.ready, substring = true)
            compose.waitUntil(READY_TIMEOUT_MS) { compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty() }
            compose.findingsWhileScrolling(case.expect).map { Violation(case.name, lookKey, it.rule, it.node) }
        } finally {
            // The app's background work (rescheduling the alarm) must not outlive the test: Robolectric's
            // environment is gone by then, and the exception would land in the next test.
            runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        }
        val result = diff(found, loadBaseline(), case.name, lookKey)
        assertTrue(result.failureMessage(), result.isEmpty())
    }

    companion object {
        /** Generous: each case loads and parses the whole story first, and CI machines are slow. */
        private const val READY_TIMEOUT_MS = 30_000L


        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> = cases.flatMap { case ->
            case.looks.map { arrayOf<Any>(case.name, it.name) } + case.fonts.map { arrayOf<Any>(case.name, "${Look.LIGHT_LARGE.name}/${it.name}") }
        }
    }
}
