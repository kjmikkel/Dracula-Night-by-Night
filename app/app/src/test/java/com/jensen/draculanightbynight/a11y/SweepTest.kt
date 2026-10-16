package com.jensen.draculanightbynight.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SweepTest {
    @get:Rule val compose = createComposeRule()

    private fun labels() = compose.findingsWhileScrolling(Expectations()).filter { it.rule == "label" }

    @Test fun sweepChecksAboveWhereTheListWas() {
        compose.showInLook(Look.LIGHT) {
            LazyColumn {
                item { Box(Modifier.size(48.dp).clickable(role = Role.Button) {}) }
                items(60) { i -> Text("Row $i", Modifier.height(100.dp)) }
            }
        }
        // A case's tap() scrolls the list to its target; the rows above must still be checked.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(60)
        assertTrue(labels().toString(), labels().isNotEmpty())
    }

    @Test fun sweepReachesTheEndOfALongList() {
        compose.showInLook(Look.LIGHT) {
            LazyColumn {
                items(1000) { i -> Text("Row $i", Modifier.height(100.dp)) }
                item { Box(Modifier.size(48.dp).clickable(role = Role.Button) {}) }
            }
        }
        assertTrue(labels().toString(), labels().isNotEmpty())
    }

    @Test fun sweepCountsRepeatedFindings() {
        compose.showInLook(Look.LIGHT) {
            Column {
                Box(Modifier.size(48.dp).clickable(role = Role.Button) {})
                Box(Modifier.size(48.dp).clickable(role = Role.Button) {})
            }
        }
        // Two unlabelled controls are two problems: a second one must not hide behind the first one's line.
        assertTrue(labels().toString(), labels() == listOf(Finding("label", "unlabelled in screen ×2")))
    }
}
