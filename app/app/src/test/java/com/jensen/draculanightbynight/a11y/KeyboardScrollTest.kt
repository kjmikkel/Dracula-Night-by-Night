package com.jensen.draculanightbynight.a11y

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** #114: a keyboard-only reader must be able to move through an entry's text. Part of the ratchet. */
@RunWith(RobolectricTestRunner::class)
class KeyboardScrollTest {
    @get:Rule val compose = createComposeRule()

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @OptIn(ExperimentalTestApi::class)
    @Test fun readerScrollsWithKeyboard() {
        val case = cases.single { it.name == "reader-letter" }
        val app = testApp()
        val moved = try {
            runBlocking { case.seed(app) }
            compose.showInLook(Look.LIGHT) { case.content(app) }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(case.ready, substring = true) or androidx.compose.ui.test.hasContentDescription(case.ready, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            val before = bodyOffset()
            compose.onRoot().performKeyInput { pressKey(Key.PageDown); pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            bodyOffset() > before
        } finally {
            runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        }
        val found = if (moved) emptyList() else listOf(Violation("reader-letter", Look.LIGHT.name, "keyboard-scroll", "body"))
        val result = diff(found, loadBaseline(), "reader-letter", Look.LIGHT.name).let { d ->
            // Only this rule: the rest of reader-letter LIGHT is RatchetTest's.
            d.copy(new = d.new.filter { it.rule == "keyboard-scroll" }, stale = d.stale.filter { it.violation.rule == "keyboard-scroll" })
        }
        assertTrue(result.failureMessage(), result.isEmpty())
    }

    /** How far the entry's text is scrolled. */
    private fun bodyOffset(): Float {
        val lists = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)
        return compose.onAllNodes(lists).fetchSemanticsNodes().sumOf { (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value() ?: 0f).toDouble() }.toFloat()
    }
}
