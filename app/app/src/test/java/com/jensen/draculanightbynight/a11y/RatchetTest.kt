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
            case.open(compose)
            val ready = hasText(case.ready, substring = true) or hasContentDescription(case.ready, substring = true)
            compose.waitUntil(READY_TIMEOUT_MS) { compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty() }
            findingsWhileScrolling(case.expect).map { Violation(case.name, lookKey, it.rule, it.node) }
        } finally {
            // The app's background work (rescheduling the alarm) must not outlive the test: Robolectric's
            // environment is gone by then, and the exception would land in the next test.
            runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        }
        val result = diff(found, loadBaseline().filter { it.violation.rule !in OWNED_ELSEWHERE }, case.name, lookKey)
        assertTrue(result.failureMessage(), result.isEmpty())
    }

    /**
     * Rules over every window (dialogs and sheets have their own) at every scroll position of every vertical list.
     * A node rule counts if it fires anywhere; an expectation (a heading, a group, a word) only if it's met nowhere.
     */
    private fun findingsWhileScrolling(expect: Expectations): List<Finding> {
        val snapshots = mutableListOf<List<List<Finding>>>()
        snapshots += snapshot(expect)
        val lists = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)
        val count = compose.onAllNodes(lists).fetchSemanticsNodes().size
        for (i in 0 until count) {
            repeat(MAX_SCROLL_STEPS) {
                val list = compose.onAllNodes(lists)[i]
                val node = list.fetchSemanticsNode()
                val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@repeat
                if (range.value() >= range.maxValue() - 1f) return@repeat
                list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, node.size.height * SCROLL_FRACTION) }
                compose.waitForIdle()
                snapshots += snapshot(expect)
            }
        }
        val all = snapshots.flatten()
        val anywhere = all.flatten().filter { it.rule !in EXPECTATIONS }.distinct()
        val everywhere = all.map { root -> root.filter { it.rule in EXPECTATIONS }.toSet() }.reduce { a, b -> a intersect b }
        return anywhere + everywhere
    }

    /** The rules over each window on screen now. */
    private fun snapshot(expect: Expectations): List<List<Finding>> {
        val merged = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        val unmerged = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().associateBy { it.id }
        return merged.map { root -> checkRules(root, unmerged.getValue(root.id), expect, compose.density) }
    }

    companion object {
        /** Generous: each case loads and parses the whole story first, and CI machines are slow. */
        private const val READY_TIMEOUT_MS = 30_000L
        private const val MAX_SCROLL_STEPS = 40
        private const val SCROLL_FRACTION = 0.7f

        /** Rules other tests check (KeyboardScrollTest), whose baseline lines this test leaves alone. */
        private val OWNED_ELSEWHERE = setOf("keyboard-scroll")

        /** Rules about what a screen should have: met in any window or scroll position is met. */
        private val EXPECTATIONS = setOf("headings", "toggle-state", "group", "words")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> = cases.flatMap { case ->
            case.looks.map { arrayOf<Any>(case.name, it.name) } + case.fonts.map { arrayOf<Any>(case.name, "${Look.LIGHT_LARGE.name}/${it.name}") }
        }
    }
}
