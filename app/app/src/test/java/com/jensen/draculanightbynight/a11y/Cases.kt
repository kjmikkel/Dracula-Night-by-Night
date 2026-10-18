package com.jensen.draculanightbynight.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.about.AboutScreen
import com.jensen.draculanightbynight.bookmarks.BookmarksScreen
import com.jensen.draculanightbynight.bookmarks.BookmarksViewModel
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.UnlockSchedule
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.settings.SettingsScreen
import com.jensen.draculanightbynight.settings.SettingsViewModel
import com.jensen.draculanightbynight.timeline.TimelineScreen
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import com.jensen.draculanightbynight.timeline.TwoPaneScreen
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.welcome.WelcomeScreen
import com.jensen.draculanightbynight.welcome.WelcomeViewModel
import com.jensen.draculanightbynight.whoswho.WhosWhoScreen
import com.jensen.draculanightbynight.whoswho.WhosWhoViewModel
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One state of one screen the ratchet opens. [covers] names the composables it exercises (the coverage check reads
 * it); [ready] is a text or description that only shows once the state has loaded, so a blank screen can't pass.
 */
data class Case(
    val name: String,
    val covers: Set<String>,
    val ready: String,
    val looks: List<Look> = Look.entries,
    val fonts: List<ReadingFont> = emptyList(),
    val expect: Expectations = Expectations(),
    val seed: suspend AppContainer.() -> Unit = { tempo() },
    /** Opens a dialog or sheet over the screen; [ready] must then be text only that window shows. */
    val open: (ComposeContentTestRule.() -> Unit)? = null,
    /** The window the case runs in, if not the test's default phone: a tablet, say. It holds at large text too. */
    val window: DpSize? = null,
    val content: @Composable (AppContainer) -> Unit,
)

/** Same tempo from 3 May: at [FIXED_NOW] (8 May, noon) the story's 3–7 May entries have arrived, later ones haven't. */
suspend fun AppContainer.tempo() = users.completeOnboarding(ReadingMode.SameTempo(LocalDate.of(2026, 5, 3)), notify = true)

suspend fun AppContainer.free() = users.completeOnboarding(ReadingMode.Free, notify = true)

/** Every entry that has arrived at [FIXED_NOW] in [tempo], oldest first. */
suspend fun AppContainer.arrived(): List<String> {
    val mode = ReadingMode.SameTempo(LocalDate.of(2026, 5, 3))
    return stories.story().entries
        .mapNotNull { e -> UnlockSchedule.unlockInstant(e, mode, ZoneOffset.UTC)?.let { e.id to it } }
        .filter { it.second <= FIXED_NOW }.sortedBy { it.second }.map { it.first }
}

private const val JONATHAN_4_MAY = "1893-05-04-jonathan_harker-01"
private const val JONATHAN_8_MAY = "1893-05-08-jonathan_harker-01"

@Composable private fun Timeline(app: AppContainer) =
    TimelineScreen(remember { TimelineViewModel(app) }, onOpen = { _, _ -> }, onBookmarks = {}, onSettings = {})

@Composable private fun Bookmarks(app: AppContainer) =
    BookmarksScreen(remember { BookmarksViewModel(app.stories, app.users, app.time.ticks()) }, onBack = {}, onOpen = {})

@Composable private fun Settings(app: AppContainer) =
    SettingsScreen(remember { SettingsViewModel(app) }, onBack = {}, onAbout = {})

@Composable private fun WhosWho(app: AppContainer, open: String? = null) =
    WhosWhoScreen(remember { WhosWhoViewModel(app.stories, app.users, open, SavedStateHandle()) }, onBack = {})

@Composable private fun Reader(app: AppContainer, id: String, filtered: Boolean = false) =
    ReaderScreen(remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, id, filtered, SavedStateHandle()) }, onBack = {})

@Composable private fun TwoPanes(app: AppContainer, timelineHidden: Boolean) = TwoPaneScreen(
    timeline = remember { TimelineViewModel(app) },
    reader = remember { ReaderViewModel(app.stories, app.users, app.time.ticks(), app.scope, JONATHAN_4_MAY, false, SavedStateHandle()) },
    timelineHidden = timelineHidden,
    onOpen = { _, _ -> }, onClose = {}, onToggleTimeline = {}, onBookmarks = {}, onSettings = {},
)

private val LANDSCAPE_TABLET = DpSize(1280.dp, 800.dp)
private val PORTRAIT_TABLET = DpSize(800.dp, 1280.dp)

/**
 * Taps the first control showing [label]. Screens load their state in the background, so it waits for the control
 * to be there and enabled (the timeline's Filter button is disabled while loading, and ignores taps) and, while it
 * isn't there, scrolls the screen's list towards it.
 */
private fun ComposeContentTestRule.tap(label: String) {
    val matcher = (hasText(label, substring = true) or hasContentDescription(label, substring = true)) and isEnabled()
    fun present() = onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    repeat(TAP_ATTEMPTS) {
        if (present()) return@repeat
        runCatching { onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher) }
        runCatching { waitUntil(TAP_WAIT_MS) { present() } }
    }
    onAllNodes(matcher).onFirst().performClick()
}

private const val TAP_ATTEMPTS = 40
private const val TAP_WAIT_MS = 500L

private val timelineHeadings = listOf("Dracula Night by Night", "3 May")

private val readerTypes = listOf(
    "diary" to "1893-05-03-jonathan_harker-01",
    "letter" to "1893-05-09-mina_harker-01",
    "telegram" to "1893-05-26-arthur_holmwood-01",
    "newspaper" to "1893-08-08-dailygraph-01",
    "phonograph" to "1893-05-25-john_seward-01",
    "memo" to "undated-unknown-01",
    "log" to "1893-08-09-demeter_captain-01",
)

private val readerAuthors = mapOf(
    "diary" to "Jonathan Harker", "letter" to "Mina Harker", "telegram" to "Arthur Holmwood", "newspaper" to "The Dailygraph",
    "phonograph" to "Dr. John Seward", "memo" to "Bram Stoker", "log" to "Captain of the Demeter",
)

val cases: List<Case> = listOf(
    Case(
        "welcome", setOf("WelcomeScreen"), ready = "How would you like to read?", seed = {},
        expect = Expectations(
            headings = listOf("Dracula Night by Night", "How would you like to read?"),
            groups = listOf(listOf("In the same tempo", "On the actual days", "Read freely")),
        ),
    ) { WelcomeScreen(remember { WelcomeViewModel(it) }, onBegun = {}) },
    Case(
        "timeline", setOf("TimelineScreen"), ready = "Bistritz",
        seed = { tempo(); users.markRead("undated-unknown-01"); users.toggleBookmark(JONATHAN_4_MAY) },
        expect = Expectations(
            headings = timelineHeadings,
            words = listOf("Bistritz" to "Unread", "4 May.—I found" to "Bookmarked", "14:45" to "about"),
            cardTag = "tonight-card",
        ),
    ) { Timeline(it) },
    Case(
        "timeline-tonight", setOf("TimelineScreen"), ready = "Jonathan Harker",
        seed = { tempo(); arrived().dropLast(1).forEach { id -> users.markRead(id) } },
        expect = Expectations(headings = timelineHeadings, cardTag = "tonight-card"),
    ) { Timeline(it) },
    Case(
        "timeline-filtered", setOf("TimelineScreen"), ready = "Filtered",
        seed = { tempo(); users.setFilter(EntryFilter(voices = setOf(Voice.JONATHAN))) },
        expect = Expectations(headings = listOf("Dracula Night by Night")),
    ) { Timeline(it) },
    Case(
        "timeline-ahead", setOf("TimelineScreen"), ready = "Back to real time",
        seed = { tempo(); users.setSkipAhead(2 * 24 * 60) },
    ) { Timeline(it) },
    // Scrolled, the cards shrink to one line each (#180). In the large looks they are in the list, so nothing shrinks.
    Case(
        "timeline-scrolled", setOf("TimelineScreen"), ready = " · Jonathan Harker",
        looks = listOf(Look.LIGHT, Look.DARK, Look.LIGHT_HC, Look.DARK_HC),
        seed = { tempo(); users.setFilter(EntryFilter(voices = setOf(Voice.JONATHAN))) },
        open = {
            waitUntil(TAP_WAIT_MS * TAP_ATTEMPTS) { onAllNodesWithTag("timeline-list").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("timeline-list").performScrollToIndex(3)
        },
    ) { Timeline(it) },
    Case(
        "read-it-now", setOf("TimelineScreen"), ready = "Read it now?",
        open = { tap("Read it now") },
    ) { Timeline(it) },
    Case(
        "filter-sheet", setOf("FilterSheet"), ready = "Voices",
        open = { tap("Filter") },
        expect = Expectations(headings = listOf("Voices", "Documents")),
    ) { Timeline(it) },
) + readerTypes.map { (type, id) ->
    Case(
        "reader-$type", setOf("ReaderScreen"), ready = "Next", seed = { free() },
        fonts = if (type == "letter") ReadingFont.entries else emptyList(),
        expect = Expectations(headings = listOf(readerAuthors.getValue(type)), toggles = listOf("Bookmark")),
    ) { Reader(it, id) }
} + listOf(
    // A break inside the entry, drawn as the ornament (#178).
    Case(
        "reader-divider", setOf("ReaderScreen"), ready = "Next", seed = { free() },
        expect = Expectations(headings = listOf("Mina Harker"), toggles = listOf("Bookmark")),
    ) { Reader(it, "1893-10-31-mina_harker-02") },
    Case(
        "reader-filtered", setOf("ReaderScreen"), ready = "Next Mina entry",
        seed = { free(); users.setFilter(EntryFilter(voices = setOf(Voice.MINA))) },
        expect = Expectations(groups = listOf(listOf("Mina", "All"))),
    ) { Reader(it, "1893-05-09-mina_harker-01", filtered = true) },
    Case(
        "bookmarks", setOf("BookmarksScreen"), ready = "Locked",
        seed = { tempo(); users.toggleBookmark(JONATHAN_4_MAY); users.toggleBookmark(JONATHAN_8_MAY) },
        expect = Expectations(headings = listOf("Bookmarks"), groups = listOf(listOf("Story order", "Recently bookmarked"))),
    ) { Bookmarks(it) },
    Case(
        "read-now-dialog", setOf("ReadNowDialog"), ready = "Read it now?",
        seed = { tempo(); users.toggleBookmark(JONATHAN_8_MAY) },
        open = { tap("Locked") },
    ) { Bookmarks(it) },
    Case(
        "settings", setOf("SettingsScreen"), ready = "Reading",
        expect = Expectations(
            headings = listOf("Settings", "Reading", "Notifications", "Display", "Text size", "Reading font", "Theme"),
            groups = listOf(
                listOf("Small", "Normal", "Large", "Extra large"),
                listOf("Serif", "Sans-serif", "Atkinson Hyperlegible", "OpenDyslexic"),
                listOf("System", "Light", "Dark"),
            ),
            toggles = listOf("Skip repeated dates", "Hide “From … to …” in letters and telegrams"),
        ),
    ) { Settings(it) },
    Case("settings-mode", setOf("ModeDialog"), ready = "How to read", open = { tap("Mode") }) { Settings(it) },
    Case("settings-start-date", setOf("SettingsScreen"), ready = "Choose", open = { tap("Start date") }) { Settings(it) },
    Case("settings-skip-day", setOf("SettingsScreen"), ready = "Cancel", open = { tap("Skip to a day") }) { Settings(it) },
    Case(
        "start-over", setOf("StartOverDialog"), ready = "Start over?",
        seed = { tempo(); users.toggleBookmark(JONATHAN_4_MAY) },
        open = { tap("Start over") },
        expect = Expectations(toggles = listOf("Also remove")),
    ) { Settings(it) },
    Case("whos-who-empty", setOf("WhosWhoScreen"), ready = "People appear here") { WhosWho(it) },
    Case(
        "whos-who-open", setOf("WhosWhoScreen"), ready = "Proposed to three times",
        // Everything up to Lucy's letters of 24 May read, opened from one of them.
        seed = {
            tempo()
            listOf(
                "1893-05-03-jonathan_harker-01", "1893-05-05-jonathan_harker-02", "1893-05-09-mina_harker-01",
                "1893-05-09-lucy_westenra-01", "1893-05-24-lucy_westenra-01", "1893-05-24-lucy_westenra-02",
            ).forEach { users.markRead(it) }
        },
        expect = Expectations(
            headings = listOf("Who's who", "Jonathan and Mina", "Lucy and her suitors"),
            toggles = listOf("Jonathan Harker", "Mina Murray", "Lucy Westenra", "Arthur Holmwood", "Dr John Seward", "Quincey P. Morris"),
        ),
    ) { WhosWho(it, open = "lucy_westenra") },
    Case(
        "about", setOf("AboutScreen"), ready = "The text", seed = {},
        expect = Expectations(headings = listOf("About", "The text", "The app", "Fonts")),
    ) { AboutScreen(onBack = {}) },
    Case("licence", setOf("LicenceDialog"), ready = "January 2004", seed = {}, open = { tap("Special Elite") }) { AboutScreen(onBack = {}) },
) + listOf("landscape" to LANDSCAPE_TABLET, "portrait" to PORTRAIT_TABLET).flatMap { (orientation, window) ->
    listOf(false, true).map { hidden ->
        Case(
            if (hidden) "two-pane-hidden-$orientation" else "two-pane-$orientation", setOf("TwoPaneScreen"), ready = "Close entry",
            window = window,
            expect = Expectations(
                headings = listOfNotNull("Dracula Night by Night".takeUnless { hidden }, "Jonathan Harker"),
                toggles = listOf("Bookmark", "Show timeline"),
            ),
        ) { TwoPanes(it, hidden) }
    }
}
