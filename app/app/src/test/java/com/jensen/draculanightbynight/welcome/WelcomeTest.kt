package com.jensen.draculanightbynight.welcome

import com.jensen.draculanightbynight.TestStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class WelcomeTest {
    private fun welcome(now: String) = buildWelcome(TestStory.small, ZonedDateTime.parse(now))

    @Test
    fun inSeasonCalendarStartsAtOnce() {
        val w = welcome("2026-10-15T12:00Z")
        assertEquals(2026, w.seasonYear)
        assertFalse(w.calendarWaits)
    }

    @Test
    fun afterSixNovemberCalendarWaitsForNextYear() {
        val w = welcome("2026-11-20T12:00Z")
        assertEquals(2027, w.seasonYear)
        assertTrue(w.calendarWaits)
    }

    @Test
    fun beforeThirdMayCalendarWaits() {
        assertTrue(welcome("2027-02-01T12:00Z").calendarWaits)
        assertFalse(welcome("2027-05-03T12:00Z").calendarWaits)
    }

    @Test
    fun theStartTomorrowChoiceNamesTheFirstEntry() {
        val late = welcome("2026-10-15T21:30Z")
        assertTrue(late.firstEntryClose)
        assertEquals("Jonathan Harker", late.firstEntryAuthor)
        assertEquals(LocalTime.of(22, 0), late.firstEntryTime)
        assertFalse(welcome("2026-10-15T12:00Z").firstEntryClose)
    }
}
