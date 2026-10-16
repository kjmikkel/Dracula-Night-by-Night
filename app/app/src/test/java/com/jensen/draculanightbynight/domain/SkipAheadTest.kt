package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.TestStory.entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SkipAheadTest {
    private val utc = ZoneOffset.UTC
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val dayTwoAfternoon = Instant.parse("2026-10-15T15:00:00Z") // 3 and 4 May unlocked; 5 May due 16 Oct 10:00

    private fun unlocked(story: Story, minutes: Long) =
        story.entries.filter { UnlockSchedule.isUnlocked(it, tempo.copy(skipAhead = Duration.ofMinutes(minutes)), dayTwoAfternoon, utc) }.map { it.id }

    @Test
    fun readItNowUnlocksExactlyTheNextEntry() {
        val minutes = SkipAhead.toNext(TestStory.small, UserState(), tempo, dayTwoAfternoon, utc)
        assertEquals(19 * 60L, minutes)
        assertEquals(listOf("may03", "may04", "may05"), unlocked(TestStory.small, minutes))
    }

    @Test
    fun readItNowAddsToTheTimeAlreadySkipped() {
        val ahead = tempo.copy(skipAhead = Duration.ofMinutes(30)) // 5 May now arrives at 09:30
        assertEquals(30 + 18 * 60 + 30L, SkipAhead.toNext(TestStory.small, UserState(), ahead, dayTwoAfternoon, utc))
    }

    @Test
    fun entriesSharingTheNextInstantArriveTogether() {
        val story = Story(listOf(entry("a", 5, 3, "22:00", order = 0), entry("b", 5, 3, "22:00", order = 1)), TestStory.characters)
        val at = Instant.parse("2026-10-14T21:00:00Z")
        val minutes = SkipAhead.toNext(story, UserState(), tempo, at, utc)
        assertTrue(story.entries.all { UnlockSchedule.isUnlocked(it, tempo.copy(skipAhead = Duration.ofMinutes(minutes)), at, utc) })
    }

    @Test
    fun skipToADayUnlocksThatWholeStoryDay() {
        val minutes = SkipAhead.toEndOf(LocalDate.of(1893, 5, 5), TestStory.small, tempo, dayTwoAfternoon, utc)
        assertEquals(listOf("may03", "may04", "may05"), unlocked(TestStory.small, minutes))
        assertFalse("may09" in unlocked(TestStory.small, minutes))
    }

    @Test
    fun skipToADayNeverMovesBackwards() {
        val farAhead = tempo.copy(skipAhead = Duration.ofDays(10))
        assertEquals(10 * 24 * 60L, SkipAhead.toEndOf(LocalDate.of(1893, 5, 5), TestStory.small, farAhead, dayTwoAfternoon, utc))
    }

    @Test
    fun readFreelyHasNothingToSkip() {
        val free = ReadingSettings(ReadingMode.Free)
        assertEquals(0L, SkipAhead.toNext(TestStory.small, UserState(), free, dayTwoAfternoon, utc))
        assertEquals(0L, SkipAhead.toEndOf(LocalDate.of(1893, 5, 9), TestStory.small, free, dayTwoAfternoon, utc))
    }

    @Test
    fun skippingToAnEntryMakesItReadable() {
        val noon = Instant.parse("2026-10-15T12:00:00Z")
        val may05 = TestStory.small.entry("may05")!!
        val minutes = SkipAhead.toEntry(may05, tempo, noon, utc)
        assertEquals(22 * 60L, minutes)
        assertTrue(isReadable(may05, tempo.copy(skipAhead = Duration.ofMinutes(minutes)), UserState(), noon, utc))
    }

    @Test
    fun skippingToAnEntryNeverMovesBackwards() {
        val ahead = tempo.copy(skipAhead = Duration.ofHours(48))
        assertEquals(48 * 60L, SkipAhead.toEntry(TestStory.small.entry("may04")!!, ahead, Instant.parse("2026-10-15T12:00:00Z"), utc))
    }

    @Test
    fun theConfirmationCountsWhatTheSkipUnlocks() {
        val night = Instant.parse("2026-10-14T23:00:00Z")
        val minutes = SkipAhead.toEntry(TestStory.small.entry("may05")!!, tempo, night, utc)
        assertEquals(2, SkipAhead.unlockedBy(minutes, TestStory.small, UserState(), tempo, night, utc))
    }
}
