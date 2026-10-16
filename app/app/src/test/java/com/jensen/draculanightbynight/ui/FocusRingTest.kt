package com.jensen.draculanightbynight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.showInLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Keyboard focus is shown by a ring in the theme's primary colour, round Material's controls and the app's own alike. */
@RunWith(RobolectricTestRunner::class)
class FocusRingTest {
    @get:Rule val compose = createComposeRule()

    private var primary = Color.Unspecified

    /** [content] in the app's theme, as a keyboard user sees it. */
    private fun show(content: @Composable () -> Unit) = compose.showInLook(Look.LIGHT) {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
        primary = MaterialTheme.colorScheme.primary
        Box(Modifier.padding(24.dp)) { content() }
    }

    /** The colour 1 dp inside the left edge of [node], halfway down, in [image]. */
    private fun leftEdge(node: SemanticsNodeInteraction, image: ImageBitmap, origin: Rect = Rect.Zero): Int {
        val b = node.fetchSemanticsNode().boundsInWindow
        val inset = with(compose.density) { 1.dp.toPx() }
        return image.toPixelMap()[(b.left - origin.left + inset).toInt(), ((b.top + b.bottom) / 2 - origin.top).toInt()].toArgb()
    }

    @Test fun aFocusedMaterialButtonHasARing() {
        show { TextButton(onClick = {}) { Text("Go") } }
        val go = compose.onNodeWithText("Go")
        go.requestFocus()
        compose.waitForIdle()
        assertEquals(primary.toArgb(), leftEdge(go, compose.onRoot().captureToImage()))
    }

    @Test fun anAppRowHasARingToo() {
        show { Column { Text("Row", Modifier.size(200.dp, 56.dp).clickable(role = Role.Button) {}.semantics {}) } }
        val row = compose.onNodeWithText("Row")
        row.requestFocus()
        compose.waitForIdle()
        assertEquals(primary.toArgb(), leftEdge(row, compose.onRoot().captureToImage()))
    }

    @Test fun nothingFocusedHasNoRing() {
        show { TextButton(onClick = {}) { Text("Go") } }
        val go = compose.onNodeWithText("Go")
        compose.waitForIdle()
        assertNotEquals(primary.toArgb(), leftEdge(go, compose.onRoot().captureToImage()))
    }

    @Test fun somethingFocusedInADialogHasARing() {
        // A dialog is a window of its own, which Robolectric keeps in touch mode, where only some elements can
        // take focus: a plain focusable stands in for the dialog's buttons.
        show {
            AlertDialog(
                onDismissRequest = {},
                // Empty, so the only thing that can paint the edge primary is the ring.
                confirmButton = { Box(Modifier.size(120.dp, 56.dp).testTag("go").focusable()) },
                modifier = Modifier.focusRings(),
            )
        }
        val go = compose.onNodeWithTag("go")
        go.requestFocus()
        compose.waitForIdle()
        val dialog = compose.onNode(isDialog())
        assertEquals(primary.toArgb(), leftEdge(go, dialog.captureToImage(), dialog.fetchSemanticsNode().boundsInWindow))
    }
}
