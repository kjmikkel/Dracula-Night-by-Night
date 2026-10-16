package com.jensen.draculanightbynight.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RulesTest {
    @get:Rule val compose = createComposeRule()

    private fun findings(expect: Expectations = Expectations(), content: @Composable () -> Unit): List<Finding> {
        compose.showInLook(Look.LIGHT) { Column { content() } }
        return checkRules(compose.onRoot().fetchSemanticsNode(), compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), expect, compose.density)
    }

    private fun List<Finding>.rule(rule: String) = filter { it.rule == rule }

    @Composable
    private fun Button(label: String, modifier: Modifier = Modifier) =
        Box(modifier.size(48.dp).clickable(role = Role.Button) {}.semantics { contentDescription = label })

    // label

    @Test fun labelFiresOnUnlabelledClickable() {
        assertEquals(1, findings { Box(Modifier.size(48.dp).clickable(role = Role.Button) {}) }.rule("label").size)
    }

    @Test fun labelPassesWithText() {
        assertTrue(findings { TextButton(onClick = {}) { Text("Begin") } }.rule("label").isEmpty())
    }

    // role

    @Test fun roleFiresOnClickableWithoutRole() {
        assertEquals(listOf(Finding("role", "Open")), findings { Text("Open", Modifier.clickable {}) }.rule("role"))
    }

    @Test fun roleAllowsListRows() {
        val found = findings { LazyColumn { item { Text("22:00 Jonathan Harker", Modifier.clickable {}) } } }
        assertTrue(found.rule("role").isEmpty())
    }

    // target

    @Test fun targetFiresBelow48dp() {
        assertEquals(listOf(Finding("target", "x")), findings { Box(Modifier.size(40.dp).clickable(role = Role.Button) {}.semantics { contentDescription = "x" }) }.rule("target"))
    }

    @Test fun targetPassesAt48dp() {
        assertTrue(findings { Button("x") }.rule("target").isEmpty())
    }

    // headings

    @Test fun headingsFiresWhenPlainText() {
        assertEquals(listOf(Finding("headings", "Settings")), findings(Expectations(headings = listOf("Settings"))) { Text("Settings") }.rule("headings"))
    }

    @Test fun headingsFiresWhenMissing() {
        assertEquals(listOf(Finding("headings", "Settings")), findings(Expectations(headings = listOf("Settings"))) { Text("Other") }.rule("headings"))
    }

    @Test fun headingsPassesOnHeading() {
        assertTrue(findings(Expectations(headings = listOf("Settings"))) { Text("Settings", Modifier.semantics { heading() }) }.rule("headings").isEmpty())
    }

    // double-label

    @Test fun doubleLabelFiresOnDescribedTextButton() {
        val found = findings { TextButton(onClick = {}, Modifier.semantics { contentDescription = "Back" }) { Text("‹ Back") } }
        assertEquals(listOf(Finding("double-label", "Back")), found.rule("double-label"))
    }

    @Test fun doubleLabelIgnoresRowWithDescribedChild() {
        val found = findings {
            Row(Modifier.clickable {}) {
                Box(Modifier.size(8.dp).semantics { contentDescription = "Unread" })
                Text("Jonathan Harker")
            }
        }
        assertTrue(found.rule("double-label").isEmpty())
    }

    // toggle-state

    @Test fun toggleStateFiresOnPlainButton() {
        val found = findings(Expectations(toggles = listOf("Bookmark"))) { TextButton(onClick = {}) { Text("☆ Bookmark") } }
        assertEquals(listOf(Finding("toggle-state", "Bookmark")), found.rule("toggle-state"))
    }

    @Test fun toggleStatePassesOnToggleable() {
        val found = findings(Expectations(toggles = listOf("Bookmark"))) { Text("Bookmark", Modifier.toggleable(false, role = Role.Switch) {}) }
        assertTrue(found.rule("toggle-state").isEmpty())
    }

    // group

    @Test fun groupFiresWithoutSelectableGroup() {
        val found = findings(Expectations(groups = listOf(listOf("Small", "Large")))) {
            Column { Text("Small", Modifier.selectable(true, role = Role.RadioButton) {}); Text("Large", Modifier.selectable(false, role = Role.RadioButton) {}) }
        }
        assertEquals(listOf(Finding("group", "Small/Large")), found.rule("group"))
    }

    @Test fun groupPassesWithSelectableGroup() {
        val found = findings(Expectations(groups = listOf(listOf("Small", "Large")))) {
            Column(Modifier.selectableGroup()) { Text("Small", Modifier.selectable(true, role = Role.RadioButton) {}); Text("Large", Modifier.selectable(false, role = Role.RadioButton) {}) }
        }
        assertTrue(found.rule("group").isEmpty())
    }

    // words

    @Test fun wordsFiresWhenWordMissing() {
        val found = findings(Expectations(words = listOf("17:00" to "about"))) { Row(Modifier.clickable {}) { Text("17:00"); Text("Mina Harker") } }
        assertEquals(listOf(Finding("words", "17:00 → about")), found.rule("words"))
    }

    @Test fun wordsPassesWhenDescriptionSaysIt() {
        val found = findings(Expectations(words = listOf("Mina" to "unread"))) {
            Row(Modifier.clickable {}) { Box(Modifier.size(8.dp).semantics { contentDescription = "Unread" }); Text("Mina Harker") }
        }
        assertTrue(found.rule("words").isEmpty())
    }

    // card-stops

    @Test fun cardStopsFiresAtFiveStops() {
        val found = findings(Expectations(cardTag = "card")) {
            Column(Modifier.testTag("card")) {
                Text("Read now", Modifier.semantics { heading() }); Text("3 waiting"); Text("Mina Harker"); Text("Preview"); TextButton(onClick = {}) { Text("Read") }
            }
        }
        assertEquals(listOf(Finding("card-stops", "card: 5 stops")), found.rule("card-stops"))
    }

    @Test fun cardStopsPassesAtThree() {
        val found = findings(Expectations(cardTag = "card")) {
            Column(Modifier.testTag("card")) {
                Text("Read now", Modifier.semantics { heading() })
                Column(Modifier.semantics(mergeDescendants = true) {}) { Text("3 waiting"); Text("Mina Harker"); Text("Preview") }
                TextButton(onClick = {}) { Text("Read") }
            }
        }
        assertTrue(found.rule("card-stops").isEmpty())
    }

    // redundant-description (ATF's RedundantDescriptionCheck)

    @Test fun redundantDescriptionFiresWhenLabelSaysButton() {
        assertEquals(listOf(Finding("redundant-description", "Back button")), findings { Button("Back button") }.rule("redundant-description"))
    }

    @Test fun redundantDescriptionPassesOnPlainLabel() {
        assertTrue(findings { Button("Back") }.rule("redundant-description").isEmpty())
    }

    // duplicate-bounds (ATF's DuplicateClickableBoundsCheck)

    @Test fun duplicateBoundsFiresOnStackedClickables() {
        val found = findings { Box { Button("One"); Button("Two") } }
        assertEquals(1, found.rule("duplicate-bounds").size)
    }

    @Test fun duplicateBoundsPassesSideBySide() {
        assertTrue(findings { Row { Button("One"); Button("Two") } }.rule("duplicate-bounds").isEmpty())
    }
}
