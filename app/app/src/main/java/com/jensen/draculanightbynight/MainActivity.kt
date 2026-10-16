package com.jensen.draculanightbynight

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jensen.draculanightbynight.about.AboutScreen
import com.jensen.draculanightbynight.bookmarks.BookmarksScreen
import com.jensen.draculanightbynight.bookmarks.BookmarksViewModel
import com.jensen.draculanightbynight.notify.Notifications
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.settings.SettingsScreen
import com.jensen.draculanightbynight.settings.SettingsViewModel
import com.jensen.draculanightbynight.timeline.TimelineScreen
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import com.jensen.draculanightbynight.ui.DraculaTheme
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import com.jensen.draculanightbynight.welcome.WelcomeScreen
import com.jensen.draculanightbynight.welcome.WelcomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** An entry a notification asked to open, until the navigation has opened it. */
    private val openRequests = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequests.value = intent.getStringExtra(Notifications.EXTRA_ENTRY_ID)
        val app = (application as DraculaApp).container
        setContent {
            // Display choices apply everywhere at once; until the saved state loads, the defaults are used.
            val user by app.users.state.collectAsStateWithLifecycle(initialValue = null)
            DraculaTheme(
                theme = user?.theme ?: ThemeChoice.SYSTEM,
                highContrast = user?.highContrast,
                readingFont = user?.readingFont ?: ReadingFont.SERIF,
                textSize = user?.textSize ?: TextSize.NORMAL,
            ) {
                // Wait for the saved state before choosing the first screen, so a returning reader never sees Welcome flash.
                user?.let { AppNavigation(app, openRequests, startRoute = if (it.isOnboarded) "timeline" else "welcome") }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_ENTRY_ID)?.let { openRequests.value = it }
    }

    override fun onResume() {
        super.onResume()
        Notifications.clearArrivals(this)
    }
}

private fun NavBackStackEntry.isResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

@Composable
private fun AppNavigation(app: AppContainer, openRequests: MutableStateFlow<String?>, startRoute: String) {
    val nav = rememberNavController()
    // The first screen is chosen once; later state changes (Begin) navigate instead.
    val start = rememberSaveable { startRoute }
    // A tapped notification for one entry opens it over the timeline.
    val request by openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        request?.let { id ->
            nav.navigate("reader/$id") { popUpTo("timeline") }
            openRequests.value = null
        }
    }
    NavHost(nav, startDestination = start) {
        composable("welcome") { entry ->
            WelcomeScreen(viewModel { WelcomeViewModel(app) }, onBegun = {
                if (entry.isResumed()) nav.navigate("timeline") { popUpTo("welcome") { inclusive = true } }
            })
        }
        // Taps on a screen that is still entering or already leaving (a double tap) are ignored,
        // so a row cannot open two readers and Back cannot pop the timeline as well.
        composable("timeline") { entry ->
            TimelineScreen(
                viewModel { TimelineViewModel(app) },
                onOpen = { id, filtered -> if (entry.isResumed()) nav.navigate("reader/$id?filtered=$filtered") { launchSingleTop = true } },
                onBookmarks = { if (entry.isResumed()) nav.navigate("bookmarks") { launchSingleTop = true } },
                onSettings = { if (entry.isResumed()) nav.navigate("settings") { launchSingleTop = true } },
            )
        }
        composable("bookmarks") { entry ->
            BookmarksScreen(
                viewModel { BookmarksViewModel(app.stories, app.users, app.time.ticks()) },
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onOpen = { id -> if (entry.isResumed()) nav.navigate("reader/$id") { launchSingleTop = true } },
            )
        }
        composable("settings") { entry ->
            SettingsScreen(
                viewModel { SettingsViewModel(app) },
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onAbout = { if (entry.isResumed()) nav.navigate("about") { launchSingleTop = true } },
            )
        }
        composable("about") { entry ->
            AboutScreen(onBack = { if (entry.isResumed()) nav.popBackStack() })
        }
        composable(
            "reader/{id}?filtered={filtered}",
            arguments = listOf(navArgument("filtered") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val filtered = entry.arguments?.getBoolean("filtered") ?: false
            ReaderScreen(
                viewModel { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, id, filtered, createSavedStateHandle()) },
                onBack = { if (entry.isResumed()) nav.popBackStack() },
            )
        }
    }
}
