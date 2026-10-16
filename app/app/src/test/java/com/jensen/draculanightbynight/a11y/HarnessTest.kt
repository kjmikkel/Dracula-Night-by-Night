package com.jensen.draculanightbynight.a11y

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jensen.draculanightbynight.about.AboutScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HarnessTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aboutRendersInTheAppTheme() {
        compose.showInLook(Look.LIGHT) { AboutScreen(onBack = {}) }
        compose.onNodeWithText("Fonts").assertExists()
    }

    @Test
    fun largeLookScalesText() {
        compose.setContent {
            Column {
                InLook(Look.LIGHT) { Text("normal", style = MaterialTheme.typography.bodyLarge) }
                InLook(Look.LIGHT_LARGE) { Text("large", style = MaterialTheme.typography.bodyLarge) }
            }
        }
        val normal = compose.onNodeWithText("normal").getBoundsInRoot().let { it.bottom - it.top }
        val large = compose.onNodeWithText("large").getBoundsInRoot().let { it.bottom - it.top }
        // Font scale 2.0 (Android scales large sizes less than linearly): clearly taller.
        assertTrue("$large should be over 1.5 x $normal", large > normal * 1.5f)
    }

    @Test fun largeLookAppliesToDialogs() {
        // A dialog is its own window with its own density: it must see the 200 % font size too.
        compose.showInLook(Look.LIGHT_LARGE) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Text("scale ${LocalDensity.current.fontScale}") })
        }
        compose.onNodeWithText("scale 2.0").assertExists()
    }
}
