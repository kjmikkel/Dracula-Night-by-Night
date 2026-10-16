package com.jensen.draculanightbynight.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.showInLook
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The "Also remove my bookmarks" box says its state in both directions, not only when it's ticked. */
@RunWith(RobolectricTestRunner::class)
class StartOverDialogTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        compose.showInLook(Look.LIGHT) {
            StartOverDialog(bookmarks = 2, sameTempo = false, today = LocalDate.of(2026, 5, 8), onDismiss = {}, onConfirm = { _, _ -> })
        }
    }

    private fun box() = compose.onNodeWithText("Also remove my 2 bookmarks")

    @Test fun anUntickedBoxSaysNotChecked() {
        box().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not checked"))
    }

    @Test fun aTickedBoxSaysChecked() {
        box().performClick()
        box().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Checked"))
    }
}
