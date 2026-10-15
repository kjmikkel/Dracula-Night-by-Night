package com.jensen.draculanightbynight.settings

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class SettingsTest {
    private val october14 = LocalDate.of(2026, 10, 14)
    private val october15 = LocalDate.of(2026, 10, 15)

    private fun settings(user: UserState, now: String) = buildSettings(TestStory.small, user, ZonedDateTime.parse(now))

    @Test
    fun skipToADayOffersTheStoryDaysThatHaveEntries() {
        val s = settings(UserState().withMode(ReadingMode.SameTempo(october14)), "2026-10-15T12:00Z")
        assertEquals(listOf(3, 4, 5, 9).map { LocalDate.of(1893, 5, it) }, s.storyDays)
    }

    @Test
    fun settingsShowTheStartDateHowFarAheadAndTheBookmarks() {
        val user = UserState().withMode(ReadingMode.SameTempo(october14))
            .copy(skipAheadMinutes = 90, progress = mapOf("may03" to EntryProgress(bookmarked = true)))
        val s = settings(user, "2026-10-15T12:00Z")
        assertEquals(october14, s.tempoStart)
        assertEquals(Duration.ofMinutes(90), s.ahead)
        assertEquals(1, s.bookmarks)
        assertEquals("In the same tempo", s.modeLabel)
    }

    @Test
    fun startTomorrowIsOfferedOnlyForATempoStartedTodayWithTheFirstEntryClose() {
        val today = UserState().withMode(ReadingMode.SameTempo(october15))
        assertTrue(settings(today, "2026-10-15T21:30Z").offerStartTomorrow)
        assertFalse(settings(today, "2026-10-15T12:00Z").offerStartTomorrow)
        assertFalse(settings(UserState().withMode(ReadingMode.SameTempo(october14)), "2026-10-15T21:30Z").offerStartTomorrow)
    }

    @Test
    fun modeLabelsNameTheCalendarSeason() {
        assertEquals("On the actual days (2026)", settings(UserState().withMode(ReadingMode.Calendar(2026)), "2026-10-15T12:00Z").modeLabel)
        val free = settings(UserState().withMode(ReadingMode.Free), "2026-10-15T12:00Z")
        assertEquals("Read freely", free.modeLabel)
        assertNull(free.tempoStart)
    }
}
