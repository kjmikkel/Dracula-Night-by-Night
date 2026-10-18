package com.jensen.draculanightbynight.user

import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.content.DocumentType
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UserRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-14T18:00:00Z"))
    private var zone: ZoneId = ZoneOffset.UTC

    private fun file() = File(tmp.root, "user.json")

    private fun TestScope.repository(scope: CoroutineScope = backgroundScope) = UserRepository(
        DataStoreFactory.create(
            serializer = UserStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserState() },
            scope = scope,
            produceFile = ::file,
        ),
        TimeSource(clock) { zone },
    )

    @Test
    fun restoringABackupReplacesEverythingAndMovesTheBaselineToNow() = runTest {
        val repo = repository()
        repo.setTheme(ThemeChoice.DARK)
        repo.markRead("here")
        val backup = UserState(onboarded = true, progress = mapOf("there" to EntryProgress(readAtMillis = 1))).withMode(ReadingMode.Free)
        repo.restore(backup)
        assertEquals(backup.copy(lastNotifiedAt = clock.now.toEpochMilli()), repo.state.first())
    }

    @Test
    fun aNewReaderStartsInSameTempoFromToday() = runTest {
        val repo = repository()
        repo.ensureMode()
        assertEquals(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)), repo.state.first().readingMode)
    }

    @Test
    fun aNewReadersTodayIsTheDateWhereTheyAreNow() = runTest {
        clock.now = Instant.parse("2026-10-14T23:30:00Z") // already 15 October in Tokyo
        zone = ZoneId.of("Asia/Tokyo")
        val repo = repository()
        repo.ensureMode()
        assertEquals(ReadingMode.SameTempo(LocalDate.of(2026, 10, 15)), repo.state.first().readingMode)
    }

    @Test
    fun ensureModeKeepsAnExistingChoice() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Free)
        repo.ensureMode()
        assertEquals(ReadingMode.Free, repo.state.first().readingMode)
    }

    @Test
    fun changingModeResetsSkipAhead() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Calendar(2026))
        assertEquals(ReadingMode.Calendar(2026), repo.state.first().settings?.mode)
        assertEquals(0L, repo.state.first().skipAheadMinutes)
    }

    @Test
    fun markReadKeepsTheFirstReadTime() = runTest {
        val repo = repository()
        repo.markRead("a")
        clock.now = Instant.parse("2026-10-15T09:00:00Z")
        repo.markRead("a")
        assertEquals(Instant.parse("2026-10-14T18:00:00Z").toEpochMilli(), repo.state.first().progressOf("a").readAtMillis)
    }

    @Test
    fun positionAndBookmarkAreStoredPerEntry() = runTest {
        val repo = repository()
        repo.savePosition("a", 12, 340)
        repo.toggleBookmark("a")
        val a = repo.state.first().progressOf("a")
        assertEquals(12 to 340, a.blockIndex to a.blockOffset)
        assertTrue(a.bookmarked)
        assertFalse(repo.state.first().progressOf("b").bookmarked)
    }

    @Test
    fun startOverAndTheSeasonAnswerAreSaved() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Calendar(2025))
        repo.markRead("a")
        repo.toggleBookmark("b")
        repo.startOver(removeBookmarks = false, tempoStart = LocalDate.of(2026, 10, 14))
        repo.answerSeasonPrompt()
        val state = repo.state.first()
        assertNull(state.progressOf("a").readAtMillis)
        assertTrue(state.progressOf("b").bookmarked)
        assertEquals(ReadingMode.Calendar(2026), state.readingMode)
        assertEquals(2026, state.seasonPromptYear)
    }

    @Test
    fun everyScheduleChangeMovesTheNotificationBaseline() = runTest {
        val repo = repository()
        suspend fun baselineAfter(at: String, change: suspend () -> Unit): Long? {
            clock.now = Instant.parse(at)
            change()
            return repo.state.first().lastNotifiedAt
        }
        fun millis(at: String) = Instant.parse(at).toEpochMilli()
        assertEquals(millis("2026-10-14T18:01:00Z"), baselineAfter("2026-10-14T18:01:00Z") { repo.setMode(ReadingMode.Calendar(2026)) })
        assertEquals(millis("2026-10-14T18:02:00Z"), baselineAfter("2026-10-14T18:02:00Z") { repo.setSkipAhead(90) })
        assertEquals(millis("2026-10-14T18:03:00Z"), baselineAfter("2026-10-14T18:03:00Z") { repo.setTempoStart(LocalDate.of(2026, 10, 1)) })
        // Turning notifications off keeps the baseline; turning them on moves it, so nothing old is announced.
        assertEquals(millis("2026-10-14T18:03:00Z"), baselineAfter("2026-10-14T18:04:00Z") { repo.setNotify(false) })
        assertEquals(millis("2026-10-14T18:05:00Z"), baselineAfter("2026-10-14T18:05:00Z") { repo.setNotify(true) })
    }

    @Test
    fun finishingOnboardingSavesTheChoices() = runTest {
        val repo = repository()
        repo.completeOnboarding(ReadingMode.Free, notify = false)
        val state = repo.state.first()
        assertTrue(state.onboarded)
        assertFalse(state.notify)
        assertEquals(ReadingMode.Free, state.readingMode)
        assertEquals(Instant.parse("2026-10-14T18:00:00Z").toEpochMilli(), state.lastNotifiedAt)
    }

    @Test
    fun displayChoicesAreSaved() = runTest {
        val repo = repository()
        repo.setTextSize(TextSize.LARGE)
        repo.setReadingFont(ReadingFont.ATKINSON)
        repo.setTheme(ThemeChoice.DARK)
        repo.setHighContrast(true)
        repo.setHidePartiesLine(true)
        repo.setSkipRepeatedDates(false)
        val state = repo.state.first()
        assertEquals(listOf(TextSize.LARGE, ReadingFont.ATKINSON, ThemeChoice.DARK, true, true, false),
            listOf(state.textSize, state.readingFont, state.theme, state.highContrast, state.hidePartiesLine, state.skipRepeatedDates))
    }

    @Test
    fun aCorruptFileStartsOverInsteadOfCrashing() = runTest {
        file().writeText("{ not json")
        assertNull(repository().state.first().readingMode)
    }

    @Test
    fun aStartDateItCannotReadCountsAsNoModeInsteadOfCrashing() = runTest {
        // Valid JSON, but not a date: e.g. a backup restored from a newer version onto an older one.
        file().writeText("""{"mode": "tempo", "tempoStart": "2026-10-14T00:00"}""")
        val repo = repository()
        assertNull(repo.state.first().readingMode)
        repo.ensureMode()
        assertEquals(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)), repo.state.first().readingMode)
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() = runTest {
        file().writeText("""{"mode": "free", "fontScale": 1.2, "progress": {"gone-id": {"bookmarked": true}}}""")
        val state = repository().state.first()
        assertEquals(ReadingMode.Free, state.readingMode)
        assertTrue(state.progressOf("gone-id").bookmarked)
    }

    @Test
    fun bookmarkingStampsTheTimeAndRemovingClearsIt() = runTest {
        val repo = repository()
        repo.toggleBookmark("may03")
        assertEquals(EntryProgress(bookmarked = true, bookmarkedAtMillis = clock.millis()), repo.state.first().progressOf("may03"))
        repo.toggleBookmark("may03")
        assertEquals(EntryProgress(), repo.state.first().progressOf("may03"))
    }

    @Test
    fun undoRestoresTheOriginalTime() = runTest {
        val repo = repository()
        val madeAt = clock.millis()
        repo.toggleBookmark("may03")
        repo.toggleBookmark("may03")
        clock.now = Instant.parse("2026-10-15T09:00:00Z")
        repo.restoreBookmark("may03", madeAt)
        assertEquals(EntryProgress(bookmarked = true, bookmarkedAtMillis = madeAt), repo.state.first().progressOf("may03"))
    }

    @Test
    fun theFilterAndSortAreSaved() = runTest {
        val repo = repository()
        val f = EntryFilter(setOf(Voice.LUCY), setOf(DocumentType.LETTER))
        repo.setFilter(f)
        repo.setBookmarkSort(BookmarkSort.RECENT)
        assertEquals(f, repo.state.first().filter)
        assertEquals(BookmarkSort.RECENT, repo.state.first().bookmarkSort)
    }

    @Test
    fun changingTheFilterLeavesTheNotificationBaselineAlone() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Free)
        val baseline = repo.state.first().lastNotifiedAt
        clock.now = Instant.parse("2026-10-15T09:00:00Z")
        repo.setFilter(EntryFilter(setOf(Voice.MINA)))
        assertEquals(baseline, repo.state.first().lastNotifiedAt)
    }

    @Test
    fun theBaselineNeverMovesBack() = runTest {
        val repo = repository()
        repo.markNotified(10_000)
        repo.markNotified(5_000)
        assertEquals(10_000L, repo.state.first().lastNotifiedAt)
    }

    @Test
    fun removingABookmarkTwiceLeavesItRemoved() = runTest {
        val repo = repository()
        repo.toggleBookmark("may03")
        repo.removeBookmark("may03")
        repo.removeBookmark("may03")
        assertEquals(EntryProgress(), repo.state.first().progressOf("may03"))
    }

    @Test
    fun chipTogglesApplyToTheSavedFilter() = runTest {
        val repo = repository()
        repo.setFilter(EntryFilter(setOf(Voice.MINA)))
        repo.toggleFilterType(DocumentType.LETTER)
        assertEquals(EntryFilter(setOf(Voice.MINA), setOf(DocumentType.LETTER)), repo.state.first().filter)
        repo.toggleFilterVoice(Voice.MINA)
        assertEquals(EntryFilter(types = setOf(DocumentType.LETTER)), repo.state.first().filter)
    }

    @Test
    fun theTimelineHiddenChoiceIsSaved() = runTest {
        val users = repository()
        users.setTimelineHidden(true)
        assertTrue(users.state.first().timelineHidden)
        users.setTimelineHidden(false)
        assertFalse(users.state.first().timelineHidden)
    }
}
