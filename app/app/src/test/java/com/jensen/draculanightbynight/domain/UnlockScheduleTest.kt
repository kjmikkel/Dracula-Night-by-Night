package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UnlockScheduleTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")
    private val harkerArrives = entry("a", 5, 3, "22:00")

    @Test
    fun calendarModeUsesTheSeasonYearAndLocalTime() {
        val at = UnlockSchedule.unlockInstant(harkerArrives, ReadingMode.Calendar(2026), copenhagen)
        assertEquals(Instant.parse("2026-05-03T20:00:00Z"), at) // CEST is UTC+2
    }

    @Test
    fun sameTempoMakesThreeMayTheStartDay() {
        val mode = ReadingMode.SameTempo(LocalDate.of(2026, 10, 14))
        assertEquals(Instant.parse("2026-10-14T22:00:00Z"), UnlockSchedule.unlockInstant(harkerArrives, mode, ZoneOffset.UTC))
        val nineMay = entry("b", 5, 9, "10:00")
        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), UnlockSchedule.unlockInstant(nineMay, mode, ZoneOffset.UTC))
    }

    @Test
    fun readFreelyNeverLocks() {
        assertNull(UnlockSchedule.unlockInstant(harkerArrives, ReadingMode.Free, copenhagen))
        assertTrue(UnlockSchedule.isUnlocked(harkerArrives, ReadingSettings(ReadingMode.Free), Instant.EPOCH, copenhagen))
    }

    @Test
    fun theSameWallTimeIsADifferentInstantInAnotherZone() {
        val mode = ReadingMode.Calendar(2026)
        val tokyo = UnlockSchedule.unlockInstant(harkerArrives, mode, ZoneId.of("Asia/Tokyo"))
        val newYork = UnlockSchedule.unlockInstant(harkerArrives, mode, ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(13), Duration.between(tokyo, newYork))
    }

    @Test
    fun unlocksExactlyAtItsInstantAndNotBefore() {
        val settings = ReadingSettings(ReadingMode.Calendar(2026))
        val at = Instant.parse("2026-05-03T20:00:00Z")
        assertFalse(UnlockSchedule.isUnlocked(harkerArrives, settings, at.minusSeconds(1), copenhagen))
        assertTrue(UnlockSchedule.isUnlocked(harkerArrives, settings, at, copenhagen))
    }

    @Test
    fun skipAheadShiftsNowForward() {
        val settings = ReadingSettings(ReadingMode.Calendar(2026), skipAhead = Duration.ofHours(1))
        assertTrue(UnlockSchedule.isUnlocked(harkerArrives, settings, Instant.parse("2026-05-03T19:00:00Z"), copenhagen))
        assertFalse(UnlockSchedule.isUnlocked(harkerArrives, settings, Instant.parse("2026-05-03T18:59:59Z"), copenhagen))
    }

    @Test
    fun theAmbiguousHourWhenSummerTimeEndsUsesTheEarlierOffset() {
        // 25 October 2026 02:30 happens twice in Copenhagen; the first (CEST, UTC+2) wins.
        val sewardAtNight = entry("s", 10, 25, "02:30")
        val at = UnlockSchedule.unlockInstant(sewardAtNight, ReadingMode.Calendar(2026), copenhagen)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), at)
    }

    @Test
    fun seasonYearRollsOverAfterSixNovember() {
        assertEquals(2026, UnlockSchedule.seasonYearFor(LocalDate.of(2026, 1, 10)))
        assertEquals(2026, UnlockSchedule.seasonYearFor(LocalDate.of(2026, 11, 6)))
        assertEquals(2027, UnlockSchedule.seasonYearFor(LocalDate.of(2026, 11, 7)))
    }
}
