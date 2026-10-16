package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TonightTest {
    private val utc = ZoneOffset.UTC
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val free = ReadingSettings(ReadingMode.Free)

    // 2026-10-16 is a Friday.
    private fun h(arrived: String, now: String) = arrivalHeading(ZonedDateTime.parse(arrived), ZonedDateTime.parse(now))

    private fun card(now: String, user: UserState = UserState(), settings: ReadingSettings = tempo, filter: EntryFilter = EntryFilter()) =
        tonightCard(TestStory.small, user, settings, Instant.parse(now), utc, filter)

    private fun read(vararg ids: String) = UserState(progress = ids.associateWith { EntryProgress(readAtMillis = 1) })

    @Test
    fun partsOfTheDay() {
        assertEquals("This Morning", h("2026-10-16T07:00Z", "2026-10-16T10:00Z"))
        assertEquals("This Afternoon", h("2026-10-16T12:00Z", "2026-10-16T13:00Z"))
        assertEquals("This Evening", h("2026-10-16T18:00Z", "2026-10-16T20:00Z"))
        assertEquals("Tonight", h("2026-10-16T21:00Z", "2026-10-16T23:30Z"))
    }

    @Test
    fun anEveningEntryIsStillThisEveningAfterMidnight() {
        assertEquals("This Evening", h("2026-10-16T20:00Z", "2026-10-17T02:00Z"))
    }

    @Test
    fun aSmallHoursEntryIsTonightUntilSix() {
        assertEquals("Tonight", h("2026-10-17T01:30Z", "2026-10-17T02:00Z"))
    }

    @Test
    fun aNightEntryIsLastNightAfterSix() {
        assertEquals("Last night", h("2026-10-17T02:00Z", "2026-10-17T07:30Z"))
    }

    @Test
    fun yesterday() {
        assertEquals("Yesterday afternoon", h("2026-10-16T15:00Z", "2026-10-17T10:00Z"))
        assertEquals("Yesterday morning", h("2026-10-16T06:00Z", "2026-10-17T06:00Z"))
    }

    @Test
    fun earlierThisWeekUsesTheWeekday() {
        assertEquals("Thursday night", h("2026-10-15T22:00Z", "2026-10-17T07:00Z"))
    }

    @Test
    fun aWeekOrMoreUsesTheDate() {
        assertEquals("12 October, evening", h("2026-10-12T19:00Z", "2026-10-20T10:00Z"))
    }

    @Test
    fun oneUnreadEntryIsNamedByWhenItArrived() {
        val c = card("2026-10-14T23:00:00Z")!!
        assertEquals("Tonight", c.heading)
        assertEquals(1, c.waiting)
        assertEquals("may03", c.entryId)
        assertEquals("Jonathan Harker", c.authorName)
    }

    @Test
    fun severalUnreadOpenTheOldest() {
        val c = card("2026-10-15T15:00:00Z")!!
        assertEquals("Read now", c.heading)
        assertEquals(2, c.waiting)
        assertEquals("may03", c.entryId)
    }

    @Test
    fun nothingUnreadNoCard() {
        assertNull(card("2026-10-15T15:00:00Z", read("may03", "may04")))
    }

    @Test
    fun theFilterDecidesWhatCounts() {
        val c = card("2026-10-20T12:00:00Z", filter = EntryFilter(setOf(Voice.MINA)))!!
        assertEquals("may09", c.entryId)
        assertEquals(1, c.waiting)
    }

    @Test
    fun readFreelyAlwaysSaysReadNow() {
        val c = card("2026-10-20T12:00:00Z", read("may03", "may04", "may05"), free)!!
        assertEquals("Read now", c.heading)
        assertEquals("may09", c.entryId)
    }

    @Test
    fun skipAheadCountsAsArrival() {
        val ahead = tempo.copy(skipAhead = Duration.ofHours(2))
        assertEquals("This Evening", card("2026-10-14T20:30:00Z", settings = ahead)!!.heading)
    }

    @Test
    fun theTimelineCarriesTheCard() {
        assertEquals("may03", buildTimeline(TestStory.small, UserState(), tempo, Instant.parse("2026-10-14T23:00:00Z"), utc).tonight?.entryId)
    }
}
