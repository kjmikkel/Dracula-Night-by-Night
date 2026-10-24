package com.jensen.draculanightbynight.a11y

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The rules from the 24 October UI/UX pass: truncated headings (#222), long lines (#221) and spoilers (#220). */
@RunWith(RobolectricTestRunner::class)
class ReadingRulesTest {
    @get:Rule val compose = createComposeRule()

    private fun found(rule: String, expect: Expectations = Expectations(), content: @Composable () -> Unit): List<Finding> {
        compose.showInLook(Look.LIGHT) { content() }
        return checkRules(
            compose.onRoot().fetchSemanticsNode(), compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), expect, compose.density,
        ).filter { it.rule == rule }
    }

    private val long = "How these papers have been placed in sequence will be made manifest in the reading of them."

    @Test fun headingCutShortIsTruncated() {
        val found = found("heading-truncated") {
            Text("Jonathan Harker", Modifier.width(100.dp).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        assertEquals(listOf(Finding("heading-truncated", "Jonathan Harker")), found)
    }

    @Test fun headingThatFitsIsFine() {
        assertTrue(found("heading-truncated") {
            Text("Mina", Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }.isEmpty())
    }

    @Test fun plainTextCutShortIsNotAHeadingFinding() {
        // A one-line preview may end in "…"; only headings must show in full.
        assertTrue(found("heading-truncated") { Text(long, Modifier.width(100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }.isEmpty())
    }

    @Test fun wideTextRunsTooLong() {
        val found = found("line-length") { Text(long + " " + long, Modifier.requiredWidth(900.dp)) }
        assertEquals(1, found.size)
    }

    @Test fun narrowColumnIsFine() {
        assertTrue(found("line-length") { Text(long + " " + long, Modifier.width(300.dp)) }.isEmpty())
    }

    @Test fun unmetNameShownIsASpoiler() {
        val expect = Expectations(unmet = listOf("Van Helsing", "Lucy"))
        assertEquals(listOf(Finding("spoiler", "Van Helsing")), found("spoiler", expect) { Text("Van Helsing") })
    }
}
