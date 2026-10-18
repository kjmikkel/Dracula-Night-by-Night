package com.jensen.draculanightbynight

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jensen.draculanightbynight.about.AboutScreen
import com.jensen.draculanightbynight.bookmarks.BookmarksScreen
import com.jensen.draculanightbynight.bookmarks.BookmarksViewModel
import com.jensen.draculanightbynight.notify.Notifications
import com.jensen.draculanightbynight.reader.OpenEntry
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.reader.decodeOpenEntry
import com.jensen.draculanightbynight.reader.encode
import com.jensen.draculanightbynight.reader.route
import com.jensen.draculanightbynight.settings.SettingsScreen
import com.jensen.draculanightbynight.settings.SettingsViewModel
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import com.jensen.draculanightbynight.timeline.TwoPaneScreen
import com.jensen.draculanightbynight.ui.DraculaTheme
import com.jensen.draculanightbynight.ui.windowIsWide
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import com.jensen.draculanightbynight.welcome.WelcomeScreen
import com.jensen.draculanightbynight.welcome.WelcomeViewModel
import com.jensen.draculanightbynight.whoswho.WhosWhoScreen
import com.jensen.draculanightbynight.whoswho.WhosWhoViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
                user?.let { AppNavigation(app, openRequests, startRoute = if (it.isOnboarded) "timeline" else "welcome", timelineHidden = it.timelineHidden) }
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

/** The timeline entry's saved-state key for the entry open beside it ([OpenEntry.encode]), or null for none. */
private const val OPEN_ENTRY = "openEntry"

/**
 * Opens [request]: [wide], beside the timeline, dropping anything stacked over it; otherwise as the full-screen reader.
 * [popUpToTimeline] also drops what is over the timeline on the narrow path (a notification). Each request gets a
 * fresh [OpenEntry.seq], so the split's reader applies it even when it is the same entry again.
 */
private fun NavController.openEntry(request: OpenEntry, wide: Boolean, popUpToTimeline: Boolean = false) {
    val fresh = request.copy(seq = SystemClock.elapsedRealtimeNanos())
    if (wide) {
        // Still on Welcome although Begin finished (it ended while the app was away, #110): a notification, or a
        // reader opened over Welcome on a phone, goes to the timeline as Begin would have, so the split has one.
        val timeline = try {
            getBackStackEntry("timeline")
        } catch (_: IllegalArgumentException) {
            navigate("timeline") { popUpTo("welcome") { inclusive = true } }
            getBackStackEntry("timeline")
        }
        timeline.savedStateHandle[OPEN_ENTRY] = fresh.encode()
        popBackStack("timeline", inclusive = false)
    } else {
        navigate(fresh.route()) {
            // A notification always opens a new reader, as before #58: over Welcome there is no timeline to pop to,
            // and a reused reader would stay on its old entry.
            launchSingleTop = !popUpToTimeline
            if (popUpToTimeline) popUpTo("timeline")
        }
    }
}

/**
 * @param timelineHidden the saved choice, passed in from state already loaded: collecting it here would start as
 * "shown" each time the timeline enters composition, and a hidden timeline would flash for a frame.
 */
@Composable
internal fun AppNavigation(
    app: AppContainer,
    openRequests: MutableStateFlow<String?>,
    startRoute: String,
    timelineHidden: Boolean,
    nav: NavHostController = rememberNavController(),
) {
    // The first screen is chosen once; later state changes (Begin) navigate instead.
    val start = rememberSaveable { startRoute }
    // Two panes or one (#58), read once here so every destination agrees.
    val wide = windowIsWide()
    // A tapped notification for one entry opens it over the timeline, or beside it.
    val request by openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        request?.let { id ->
            nav.openEntry(OpenEntry(id, filtered = false), wide, popUpToTimeline = true)
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
            val handle = entry.savedStateHandle
            val open = handle.getStateFlow<String?>(OPEN_ENTRY, null).collectAsStateWithLifecycle().value?.let(::decodeOpenEntry)
            // One reader for the right pane, moved to each entry asked for; it outlives the entry closing.
            val pane = open?.let { first ->
                viewModel(key = "pane-reader") {
                    ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, first.id, first.filtered, createSavedStateHandle(), first.followingAll)
                }
            }
            if (open != null && pane != null) {
                LaunchedEffect(open, wide) {
                    pane.open(open)
                    if (!wide) {
                        // Too narrow now: the entry becomes the full-screen reader, where the right pane had got to.
                        handle[OPEN_ENTRY] = null
                        nav.navigate(pane.openEntry().route()) { launchSingleTop = true }
                    }
                }
            }
            TwoPaneScreen(
                timeline = viewModel { TimelineViewModel(app) },
                reader = pane.takeIf { wide },
                timelineHidden = timelineHidden,
                onOpen = { id, filtered -> if (entry.isResumed()) nav.openEntry(OpenEntry(id, filtered), wide) },
                onClose = { handle[OPEN_ENTRY] = null },
                onToggleTimeline = { app.scope.launch { app.users.setTimelineHidden(!timelineHidden) } },
                onBookmarks = { if (entry.isResumed()) nav.navigate("bookmarks") { launchSingleTop = true } },
                onSettings = { if (entry.isResumed()) nav.navigate("settings") { launchSingleTop = true } },
            )
        }
        composable("bookmarks") { entry ->
            BookmarksScreen(
                viewModel { BookmarksViewModel(app.stories, app.users, app.time.ticks()) },
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onOpen = { id -> if (entry.isResumed()) nav.openEntry(OpenEntry(id, filtered = false), wide) },
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
            "whos-who?open={open}",
            arguments = listOf(navArgument("open") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val open = entry.arguments?.getString("open")
            WhosWhoScreen(
                viewModel { WhosWhoViewModel(app.stories, app.users, open, createSavedStateHandle()) },
                onBack = { if (entry.isResumed()) nav.popBackStack() },
            )
        }
        composable(
            "reader/{id}?filtered={filtered}&all={all}",
            arguments = listOf(
                navArgument("filtered") { type = NavType.BoolType; defaultValue = false },
                navArgument("all") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val filtered = entry.arguments?.getBoolean("filtered") ?: false
            val all = entry.arguments?.getBoolean("all") ?: false
            val vm = viewModel { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, id, filtered, createSavedStateHandle(), all) }
            // Wide enough again: the reader closes itself into the split, dropping anything between it and the timeline.
            LaunchedEffect(wide) { if (wide) nav.openEntry(vm.openEntry(), wide = true) }
            ReaderScreen(vm, onBack = { if (entry.isResumed()) nav.popBackStack() })
        }
    }
}
