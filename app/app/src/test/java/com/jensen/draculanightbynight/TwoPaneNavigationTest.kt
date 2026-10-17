package com.jensen.draculanightbynight

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jensen.draculanightbynight.a11y.InLook
import com.jensen.draculanightbynight.a11y.Look
import com.jensen.draculanightbynight.a11y.arrived
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.tempo
import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.content.Story
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/** The whole app's navigation with two panes on wide windows and one on phones (#58). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class TwoPaneNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: AppContainer
    private lateinit var nav: NavHostController
    private lateinit var story: Story
    private lateinit var ids: List<String>
    private val defaultZone = TimeZone.getDefault()

    /** The window size the app sees, in dp. */
    private var windowWidth by mutableStateOf(1280)
    private var windowHeight by mutableStateOf(800)

    /** Entries tapped notifications ask to open, as MainActivity passes them. */
    private val requests = MutableStateFlow<String?>(null)

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        TimeZone.setDefault(defaultZone)
    }

    /** Shows the app on [start]; a [start] of Welcome stands for Begin having finished while the app was away (#110). */
    private fun show(
        width: Int = 1280,
        height: Int = 800,
        restoration: StateRestorationTester? = null,
        start: String = "timeline",
        seed: suspend AppContainer.() -> Unit = {},
    ) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        windowWidth = width
        windowHeight = height
        app = testApp()
        runBlocking {
            app.tempo()
            app.seed()
            ids = app.arrived()
            story = app.stories.story()
        }
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            val window = remember {
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize: IntSize
                        get() = with(density) { IntSize(windowWidth.dp.roundToPx(), windowHeight.dp.roundToPx()) }
                }
            }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                InLook(Look.LIGHT) {
                    Box(Modifier.requiredSize(windowWidth.dp, windowHeight.dp).clipToBounds()) {
                        val controller = rememberNavController()
                        nav = controller
                        // As MainActivity does: nothing until the saved state has loaded.
                        val user by app.users.state.collectAsStateWithLifecycle(initialValue = null)
                        user?.let { AppNavigation(app, requests, start, it.timelineHidden, controller) }
                    }
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
        waitFor(if (start == "welcome") hasText("How would you like to read?") else row(ids.first()))
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun resize(width: Int, height: Int) {
        windowWidth = width
        windowHeight = height
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun preview(id: String): String = story.entries.single { it.id == id }.preview
    private fun author(id: String): String = story.nameOf(story.entries.single { it.id == id }.author) ?: story.entries.single { it.id == id }.author

    /** The timeline's row for [id] (the Tonight card's preview is not clickable; the reader's text is not either). */
    private fun row(id: String) = hasText(preview(id)) and hasClickAction()

    private fun tap(id: String) {
        compose.onNode(row(id)).performClick()
        compose.waitForIdle()
    }

    /** Taps [id]'s row and waits for its entry to show. */
    private fun open(id: String) {
        tap(id)
        waitFor(readerHeading(id))
    }

    private fun readerHeading(id: String) = hasText(author(id)) and isHeading()

    private val split = hasContentDescription("Close entry")
    private val title = hasText("Dracula Night by Night")
    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private fun assertSplitOn(id: String) {
        waitFor(split)
        compose.onNode(title).assertExists()
        compose.onNode(selected and row(id)).assertExists()
    }

    /** The full-width timeline, with nothing under it: Back would leave the app. */
    private fun assertFullTimeline() {
        compose.onNode(split).assertDoesNotExist()
        compose.onNode(title).assertExists()
        assertEquals("timeline", nav.currentDestination?.route)
        assertEquals("the timeline should be all that is on the back stack", null, nav.previousBackStackEntry)
        assertFalse("Back should leave the app", compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    private val timelineList = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollToIndexAction()

    /** The timeline's first visible item (lazy lists report 500 per item). */
    private fun firstVisibleItem(): Int =
        compose.onAllNodes(timelineList).onFirst().fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value().toInt() / 500

    private fun shown(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() && compose.onNode(matcher).isDisplayed()

    /** A notification for [id] is tapped. */
    private fun notify(id: String) {
        requests.value = id
        compose.waitForIdle()
    }

    /**
     * Runs [action] and then the frames after it one at a time until [settled], failing if the timeline's title is
     * on screen in any of them: a hidden timeline must not show for even one frame.
     */
    private fun assertTitleNeverShows(settled: SemanticsMatcher, action: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            action()
            repeat(MAX_FRAMES) { frame ->
                assertTrue("the hidden timeline showed in frame $frame", compose.onAllNodes(title).fetchSemanticsNodes().isEmpty())
                if (compose.onAllNodes(settled).fetchSemanticsNodes().isNotEmpty()) return
                compose.mainClock.advanceTimeByFrame()
            }
            fail("never settled")
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** Two arrived entries by different authors. */
    private fun twoAuthors(): Pair<String, String> {
        val a = ids.first()
        return a to ids.first { author(it) != author(a) }
    }

    @Test fun aWideScreenOpensOnTheFullTimeline() {
        show()
        compose.onNode(title).assertExists()
        compose.onNode(split).assertDoesNotExist()
    }

    @Test fun tappingARowSplitsTheScreen() {
        show()
        open(ids.first())
        assertSplitOn(ids.first())
    }

    @Test fun backClosesTheEntryThenLeavesTheTimeline() {
        show()
        open(ids.first())
        back()
        assertFullTimeline()
    }

    @Test fun twoQuickTapsLeaveTheSecondEntryOpen() {
        show()
        val (a, b) = twoAuthors()
        tap(a)
        tap(b)
        waitFor(readerHeading(b))
        compose.onNode(readerHeading(a)).assertDoesNotExist()
        assertSplitOn(b)
        back()
        assertFullTimeline()
    }

    @Test fun narrowingTurnsTheSplitIntoTheReader() {
        show()
        val x = ids.first()
        open(x)
        resize(400, 800)
        waitFor(hasContentDescription("Back"))
        compose.onNode(split).assertDoesNotExist()
        compose.onNode(readerHeading(x)).assertExists()
        assertEquals(x, nav.currentBackStackEntry?.arguments?.getString("id"))
        back()
        assertFullTimeline()
    }

    @Test fun wideningTurnsTheReaderIntoTheSplit() {
        show(400, 800)
        val x = ids.first()
        open(x)
        compose.onNodeWithContentDescription("Back").assertExists()
        resize(1280, 800)
        assertSplitOn(x)
        back()
        assertFullTimeline()
    }

    @Test fun wideningFromBookmarksDropsBookmarks() {
        val x = "1893-05-04-jonathan_harker-01"
        show(400, 800) { users.toggleBookmark(x) }
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        waitFor(row(x))
        compose.onNode(row(x)).performClick()
        waitFor(hasContentDescription("Back") and hasClickAction())
        waitFor(readerHeading(x))
        resize(1280, 800)
        assertSplitOn(x)
        back()
        assertFullTimeline()
    }

    @Test fun hidingTheTimelineIsRemembered() {
        show()
        open(ids.first())
        compose.onNodeWithContentDescription("Show timeline").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(title).fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { app.users.state.first().timelineHidden })
        compose.onNodeWithContentDescription("Close entry").performClick()
        compose.waitForIdle()
        assertFullTimeline()
    }

    @Test fun theSplitSurvivesStateRestorationOnTheSwipedToEntry() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        val x = ids.first()
        val next = story.entries[story.entries.indexOfFirst { it.id == x } + 1].id
        open(x)
        compose.onNodeWithContentDescription("Next entry in the story").performClick()
        compose.waitForIdle()
        assertSplitOn(next)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertSplitOn(next)
    }

    @Test fun settingsThenBackReturnsToTheSplit() {
        show()
        val x = ids.first()
        open(x)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        compose.onNode(split).assertDoesNotExist()
        assertEquals("settings", nav.currentDestination?.route)
        back()
        assertSplitOn(x)
    }

    @Test fun theTimelineKeepsItsPlaceAcrossTheSplit() {
        show()
        compose.onAllNodes(timelineList).onFirst().performScrollToIndex(4)
        compose.waitForIdle()
        val before = firstVisibleItem()
        assertNotEquals("the scroll must move the list for this test to mean anything", 0, before)
        val x = ids.first { compose.onAllNodes(row(it)).fetchSemanticsNodes().isNotEmpty() && compose.onNode(row(it)).isDisplayed() }
        open(x)
        compose.onNodeWithContentDescription("Close entry").performClick()
        compose.waitForIdle()
        assertEquals(before, firstVisibleItem())

        open(x)
        resize(400, 800)
        waitFor(hasContentDescription("Back"))
        resize(1280, 800)
        assertSplitOn(x)
        compose.onNodeWithContentDescription("Close entry").performClick()
        compose.waitForIdle()
        assertEquals(before, firstVisibleItem())
    }

    @Test fun phonesAreUnchanged() {
        show(400, 800)
        open(ids.first())
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNode(split).assertDoesNotExist()
        back()
        assertFullTimeline()

        resize(915, 411)
        val x = ids.first { compose.onAllNodes(row(it)).fetchSemanticsNodes().isNotEmpty() }
        open(x)
        assertNotNull(nav.currentBackStackEntry?.arguments?.getString("id"))
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNode(split).assertDoesNotExist()
        compose.onNodeWithText("Dracula Night by Night").assertDoesNotExist()
    }

    @Test fun aNotificationOverWelcomeOpensTheSplit() {
        show(start = "welcome")
        val x = ids.first()
        notify(x)
        assertSplitOn(x)
        waitFor(readerHeading(x))
        back()
        assertFullTimeline()
    }

    @Test fun aReaderOverWelcomeWidensIntoTheSplit() {
        show(400, 800, start = "welcome")
        val x = ids.first()
        notify(x)
        waitFor(readerHeading(x))
        assertEquals(x, nav.currentBackStackEntry?.arguments?.getString("id"))
        resize(1280, 800)
        assertSplitOn(x)
        waitFor(readerHeading(x))
        back()
        assertFullTimeline()
    }

    @Test fun aSecondNotificationOverWelcomeOpensItsOwnEntry() {
        show(400, 800, start = "welcome")
        val (a, b) = twoAuthors()
        notify(a)
        waitFor(readerHeading(a))
        notify(b)
        waitFor(readerHeading(b))
        compose.onNode(readerHeading(a)).assertDoesNotExist()
        assertEquals(b, nav.currentBackStackEntry?.arguments?.getString("id"))
    }

    @Test fun aHiddenTimelineDoesNotFlashWhenTheSplitReturns() {
        show(400, 800) { users.setTimelineHidden(true) }
        val x = ids.first()
        open(x)
        assertTitleNeverShows(split and hasClickAction()) {
            windowWidth = 1280
        }
        waitFor(readerHeading(x))
        compose.onNode(title).assertDoesNotExist()
    }

    /** Short, so most rows are off screen. */
    @Config(qualifiers = "w1280dp-h500dp")
    @Test fun showingTheTimelineAgainScrollsToTheHighlightedRow() {
        // Read freely, so every entry has arrived and each Next lands on a row.
        show(1280, 500) { free() }
        val x = story.entries.first { shown(row(it.id)) }.id
        val target = story.entries.dropWhile { it.id != x }.drop(1).take(STEPS).map { it.id }
        open(x)
        assertFalse(
            "the row must start off screen for this test to mean anything",
            shown(row(target.last())),
        )
        compose.onNodeWithContentDescription("Show timeline").performClick() // the switch is on: this hides it
        compose.waitUntil(30_000) { compose.onAllNodes(title).fetchSemanticsNodes().isEmpty() }
        repeat(STEPS) {
            compose.onNodeWithContentDescription("Next entry in the story").performClick()
            compose.waitForIdle()
        }
        waitFor(readerHeading(target.last()))
        compose.onNodeWithContentDescription("Show timeline").performClick()
        waitFor(selected and row(target.last()))
        compose.waitUntil(30_000) { shown(selected and row(target.last())) }
    }

    @Test fun aNotificationWhileWideOpensTheSplit() {
        show()
        val x = ids.first()
        notify(x)
        assertSplitOn(x)
        waitFor(readerHeading(x))
        back()
        assertFullTimeline()
    }

    @Test fun aNotificationOverSettingsOpensTheSplit() {
        show()
        val x = ids.first()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        assertEquals("settings", nav.currentDestination?.route)
        notify(x)
        assertSplitOn(x)
        waitFor(readerHeading(x))
        assertEquals("timeline", nav.currentDestination?.route)
        back()
        assertFullTimeline()
    }

    @Test fun aNotificationOverBookmarksOpensTheSplit() {
        show()
        val x = ids.first()
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.waitForIdle()
        assertEquals("bookmarks", nav.currentDestination?.route)
        notify(x)
        assertSplitOn(x)
        waitFor(readerHeading(x))
        assertEquals("timeline", nav.currentDestination?.route)
        back()
        assertFullTimeline()
    }

    @Test fun aNotificationOnAPhoneOpensTheReaderOverTheTimeline() {
        show(400, 800)
        val x = ids.first()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        notify(x)
        waitFor(readerHeading(x))
        compose.onNode(split).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertExists()
        assertEquals(x, nav.currentBackStackEntry?.arguments?.getString("id"))
        assertEquals("timeline", nav.previousBackStackEntry?.destination?.route)
        back()
        assertFullTimeline()
    }

    @Test fun openingFromBookmarksWhileWideLandsOnTheSplit() {
        val x = "1893-05-04-jonathan_harker-01"
        show { users.toggleBookmark(x) }
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        waitFor(row(x))
        compose.onNode(row(x)).performClick()
        compose.waitForIdle()
        assertSplitOn(x)
        waitFor(readerHeading(x))
        assertEquals("timeline", nav.currentDestination?.route)
        assertEquals("Bookmarks should be gone", null, nav.previousBackStackEntry)
        back()
        assertFullTimeline()
    }

    @Test fun closingThenOpeningAnotherShowsTheOther() {
        show()
        val (a, b) = twoAuthors()
        open(a)
        compose.onNodeWithContentDescription("Close entry").performClick()
        compose.waitForIdle()
        compose.onNode(split).assertDoesNotExist()
        open(b)
        assertSplitOn(b)
        compose.onNode(readerHeading(a)).assertDoesNotExist()
    }

    private companion object {
        /** Frames [assertTitleNeverShows] waits for the screen to settle: 10 s at 60 fps. */
        const val MAX_FRAMES = 600

        /** Times Next is pressed in [showingTheTimelineAgainScrollsToTheHighlightedRow]. */
        const val STEPS = 6
    }
}
