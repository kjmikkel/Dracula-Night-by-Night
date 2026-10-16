package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TimelineTest {
    private val utc = ZoneOffset.UTC
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))

    private fun timeline(now: String, user: UserState = UserState(), settings: ReadingSettings = tempo) =
        buildTimeline(TestStory.small, user, settings, Instant.parse(now), utc)

    @Test
    fun beforeTheFirstEntryNothingIsUnlockedAndTheTeaserNamesIt() {
        val t = timeline("2026-10-14T21:59:00Z")
        assertTrue(t.days.isEmpty())
        assertEquals(NextTeaser("Jonathan Harker", DocumentType.DIARY, ZonedDateTime.parse("2026-10-14T22:00Z")), t.next)
    }

    @Test
    fun unlockedEntriesAreGroupedByStoryDay() {
        val t = timeline("2026-10-16T12:00:00Z") // day 3 at noon: 3 May, 4 May, 5 May 10:00
        assertEquals(listOf(LocalDate.of(1893, 5, 3), LocalDate.of(1893, 5, 4), LocalDate.of(1893, 5, 5)), t.days.map { it.date })
        assertEquals(3, t.unlockedCount)
        assertEquals(4, t.total)
        assertEquals("Mina Harker", t.next?.authorName)
    }

    @Test
    fun readFreelyUnlocksEverythingAndHasNoTeaser() {
        val t = timeline("2000-01-01T00:00:00Z", settings = ReadingSettings(ReadingMode.Free))
        assertEquals(4, t.unlockedCount)
        assertNull(t.next)
    }

    @Test
    fun aReadEntryStaysListedWhenTheScheduleNoLongerUnlocksIt() {
        // 5 May was read (say, before Back to real time); on day 2 the schedule has only reached 4 May.
        val user = UserState(progress = mapOf("may05" to EntryProgress(readAtMillis = 1)))
        val t = timeline("2026-10-15T15:00:00Z", user)
        assertEquals(listOf("may03", "may04", "may05"), t.days.flatMap { day -> day.rows.map { it.id } })
        assertEquals("Mina Harker", t.next?.authorName) // the next entry that is not readable yet
    }

    @Test
    fun theNextCardShowsWhenTheEntryWillReallyArriveWhenAhead() {
        val ahead = tempo.copy(skipAhead = Duration.ofHours(1))
        assertEquals(ZonedDateTime.parse("2026-10-15T13:45Z"), timeline("2026-10-15T12:00:00Z", settings = ahead).next?.unlocksAt)
    }

    @Test
    fun theTimelineSaysHowFarAheadTheReaderIs() {
        val ahead = tempo.copy(skipAhead = Duration.ofMinutes(90))
        assertEquals(Duration.ofMinutes(90), timeline("2026-10-15T12:00:00Z", settings = ahead).skipAhead)
        assertEquals(Duration.ZERO, timeline("2026-10-15T12:00:00Z").skipAhead)
    }

    @Test
    fun theEndComesWhenNothingIsLeftToArrive() {
        assertFalse(timeline("2026-10-15T12:00:00Z").finished)
        assertTrue(timeline("2026-10-21T00:00:00Z").finished)
        assertTrue(timeline("2000-01-01T00:00:00Z", settings = ReadingSettings(ReadingMode.Free)).finished)
    }

    @Test
    fun theSeasonCardShowsWhenThePromptIsDue() {
        val calendar = UserState().withMode(ReadingMode.Calendar(2026))
        val settings = calendar.settings!!
        assertTrue(buildTimeline(TestStory.small, calendar, settings, Instant.parse("2027-04-27T12:00:00Z"), utc).seasonPromptDue)
        assertFalse(buildTimeline(TestStory.small, calendar, settings, Instant.parse("2026-10-15T12:00:00Z"), utc).seasonPromptDue)
    }

    @Test
    fun rowsShowUnreadAndBookmarkStateAndTheFirstUnread() {
        val user = UserState(progress = mapOf("may03" to EntryProgress(readAtMillis = 1, bookmarked = true)))
        val t = timeline("2026-10-16T12:00:00Z", user)
        val first = t.days.first().rows.single()
        assertFalse(first.unread)
        assertTrue(first.bookmarked)
        assertEquals("may04", t.firstUnreadId)
        assertEquals("22:00", first.timeLabel)
    }

    private val free = ReadingSettings(ReadingMode.Free)
    private val mina = EntryFilter(setOf(Voice.MINA))

    @Test
    fun aFilterLeavesOutDaysWithNoMatchingEntries() {
        val t = buildTimeline(TestStory.small, UserState(), free, Instant.parse("2026-10-16T12:00:00Z"), utc, mina)
        assertEquals(listOf(LocalDate.of(1893, 5, 9)), t.days.map { it.date })
        assertEquals(3, t.hiddenCount)
        assertEquals(4, t.unlockedCount)
        assertEquals(mina, t.filter)
    }

    @Test
    fun theListOpensAtTheFirstUnreadMatchingEntry() {
        val t = buildTimeline(TestStory.small, UserState(), free, Instant.parse("2026-10-16T12:00:00Z"), utc, mina)
        assertEquals("may09", t.firstUnreadId)
    }

    @Test
    fun aFilterThatHidesEverythingKeepsTheNextCard() {
        val t = buildTimeline(TestStory.small, UserState(), tempo, Instant.parse("2026-10-15T12:00:00Z"), utc, mina)
        assertTrue(t.days.isEmpty())
        assertEquals(1, t.hiddenCount)
        assertEquals("Jonathan Harker", t.next?.authorName)
        assertFalse(t.finished)
    }
}
