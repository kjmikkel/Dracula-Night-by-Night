package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** TestStory.small in Same tempo from 14 October (UTC): 3 May 22:00 → 14 Oct 22:00, 4 May → 15 Oct 14:45, 5 May → 16 Oct 10:00, 9 May → 20 Oct 10:00. */
class ArrivalsTest {
    private val utc = ZoneOffset.UTC
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))

    private fun between(from: String, to: String, user: UserState = UserState(), settings: ReadingSettings = tempo) =
        Arrivals.between(TestStory.small, user, settings, Instant.parse(from), Instant.parse(to), utc).map { it.id }

    private fun nextAfter(now: String, user: UserState = UserState(), settings: ReadingSettings = tempo) =
        Arrivals.nextAfter(TestStory.small, user, settings, Instant.parse(now), utc)

    @Test
    fun arrivalsAreTheEntriesScheduledInTheWindowExcludingItsStart() {
        assertEquals(listOf("may03", "may04"), between("2026-10-14T21:00:00Z", "2026-10-15T14:45:00Z"))
        assertEquals(listOf("may04"), between("2026-10-14T22:00:00Z", "2026-10-15T14:45:00Z"))
    }

    @Test
    fun entriesAlreadyReadAreNotArrivals() {
        val user = UserState(progress = mapOf("may04" to EntryProgress(readAtMillis = 1)))
        assertEquals(listOf("may03"), between("2026-10-14T21:00:00Z", "2026-10-15T14:45:00Z", user))
    }

    @Test
    fun skipAheadMakesEntriesArriveEarlier() {
        val ahead = tempo.copy(skipAhead = Duration.ofHours(1))
        assertEquals(listOf("may04"), between("2026-10-15T13:00:00Z", "2026-10-15T13:45:00Z", settings = ahead))
    }

    @Test
    fun theNextArrivalIsTheNextUnreadEntry() {
        assertEquals(Instant.parse("2026-10-16T10:00:00Z"), nextAfter("2026-10-15T15:00:00Z"))
        val readFifth = UserState(progress = mapOf("may05" to EntryProgress(readAtMillis = 1)))
        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), nextAfter("2026-10-15T15:00:00Z", readFifth))
    }

    @Test
    fun nothingArrivesAfterTheLastEntryOrInReadFreely() {
        assertNull(nextAfter("2026-10-21T00:00:00Z"))
        assertNull(nextAfter("2026-10-15T15:00:00Z", settings = ReadingSettings(ReadingMode.Free)))
    }
}
