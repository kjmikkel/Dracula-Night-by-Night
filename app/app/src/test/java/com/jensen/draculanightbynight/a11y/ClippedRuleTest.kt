package com.jensen.draculanightbynight.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
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

@RunWith(RobolectricTestRunner::class)
class ClippedRuleTest {
    @get:Rule val compose = createComposeRule()

    private fun clipped(content: @Composable () -> Unit): List<Finding> {
        compose.showInLook(Look.LIGHT) { content() }
        return checkRules(
            compose.onRoot().fetchSemanticsNode(), compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), Expectations(), compose.density,
        ).filter { it.rule == "clipped" }
    }

    private val long = "How these papers have been placed in sequence will be made manifest in the reading of them."

    @Test fun clippedFiresOnIconPushedPastEdge() {
        val found = clipped {
            Row(Modifier.fillMaxWidth()) {
                Text(long, Modifier.requiredWidth(2000.dp))
                Text("★", Modifier.semantics { contentDescription = "Bookmarked" })
            }
        }
        assertTrue(found.toString(), Finding("clipped", "Bookmarked") in found)
    }

    @Test fun clippedFiresOnTitleCollapsedToEllipsis() {
        val found = clipped { Text("Jonathan Harker", Modifier.width(20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        assertEquals(listOf(Finding("clipped", "Jonathan Harker")), found)
    }

    @Test fun clippedIgnoresTwoLinePreviewEllipsis() {
        assertTrue(clipped { Text(long + long + long, Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis) }.isEmpty())
    }

    @Test fun clippedIgnoresRowsScrolledOffVertically() {
        val found = clipped {
            LazyColumn(Modifier.height(100.dp)) {
                items(3) { i -> Box(Modifier.height(80.dp)) { Text("Row $i") } }
            }
        }
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test fun clippedSeesIconsInsideClickableRows() {
        // The timeline's row merges its children; its ★ must still be checked on its own (#118).
        val found = clipped {
            Row(Modifier.fillMaxWidth().clickable {}) {
                Text(long, Modifier.requiredWidth(2000.dp))
                Text("★", Modifier.semantics { contentDescription = "Bookmarked" })
            }
        }
        assertTrue(found.toString(), Finding("clipped", "Bookmarked") in found)
    }

    @Test fun clippedUsesTheLargeLooksScreenNotTheWindow() {
        // The large looks draw on a narrower screen inside the window: past its edge is off screen.
        val found = clipped {
            Box(Modifier.testTag(SCREEN_TAG).requiredSize(200.dp).clipToBounds()) {
                Text("Edge", Modifier.offset(x = 300.dp))
            }
        }
        assertTrue(found.toString(), Finding("clipped", "Edge") in found)
    }
}
