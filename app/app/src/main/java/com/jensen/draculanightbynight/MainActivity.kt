package com.jensen.draculanightbynight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.timeline.TimelineScreen
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import com.jensen.draculanightbynight.ui.DraculaTheme
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as DraculaApp).container
        setContent {
            // Display choices apply everywhere at once; until the saved state loads, the defaults are used.
            val user by app.users.state.collectAsStateWithLifecycle(initialValue = null)
            DraculaTheme(
                theme = user?.theme ?: ThemeChoice.SYSTEM,
                highContrast = user?.highContrast,
                readingFont = user?.readingFont ?: ReadingFont.SERIF,
                textSize = user?.textSize ?: TextSize.NORMAL,
            ) { AppNavigation(app) }
        }
    }
}

private fun NavBackStackEntry.isResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

@Composable
private fun AppNavigation(app: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "timeline") {
        // Taps on a screen that is still entering or already leaving (a double tap) are ignored,
        // so a row cannot open two readers and Back cannot pop the timeline as well.
        composable("timeline") { entry ->
            TimelineScreen(
                viewModel { TimelineViewModel(app) },
                onOpen = { if (entry.isResumed()) nav.navigate("reader/$it") { launchSingleTop = true } },
            )
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ReaderScreen(
                viewModel { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, id) },
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onOpen = { next -> if (entry.isResumed()) nav.navigate("reader/$next") { popUpTo("timeline") } },
            )
        }
    }
}
