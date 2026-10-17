package com.jensen.draculanightbynight.about

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** With no browser installed, a link says so instead of crashing the app. */
@RunWith(RobolectricTestRunner::class)
class AboutLinksTest {
    @get:Rule val compose = createComposeRule()

    @Before fun noBrowser() {
        // Robolectric then throws ActivityNotFoundException for an intent nothing handles, as a device does.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)
        compose.setContent { AboutScreen(onBack = {}) }
    }

    @Test fun sourceLinkWithoutBrowserShowsMessage() {
        compose.onNodeWithText("Source code").performClick()
        compose.onNodeWithText(NO_BROWSER).assertExists()
    }

    @Test fun privacyLinkWithoutBrowserShowsMessage() {
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText(NO_BROWSER).assertExists()
    }
}
