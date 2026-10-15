package com.jensen.draculanightbynight.notify

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** TestStory.small in Same tempo from 14 October (UTC): 3 May 22:00 → 14 Oct 22:00, 4 May → 15 Oct 14:45, 5 May → 16 Oct 10:00, 9 May (Mina's letter) → 20 Oct 10:00. */
class NotificationPlanTest {
    private val utc = ZoneOffset.UTC
    private fun tempo(baseline: String?) = UserState().withMode(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
        .copy(lastNotifiedAt = baseline?.let { Instant.parse(it).toEpochMilli() })

    private fun plan(user: UserState, now: String) = planNotifications(TestStory.small, user, Instant.parse(now), utc)

    @Test
    fun oneArrivalNamesTheWriterAndOpensThatEntry() {
        val arrivals = plan(tempo("2026-10-15T12:00:00Z"), "2026-10-15T14:45:00Z").arrivals!!
        assertEquals("Jonathan Harker's journal has arrived", arrivals.title)
        assertEquals("4 May, 14:45", arrivals.text)
        assertEquals("may04", arrivals.openEntryId)
    }

    @Test
    fun aLetterSaysWhoItIsFrom() {
        val arrivals = plan(tempo("2026-10-20T09:00:00Z"), "2026-10-20T10:00:00Z").arrivals!!
        assertEquals("A letter from Mina Harker has arrived", arrivals.title)
    }

    @Test
    fun severalArrivalsAreCountedAndOpenTheTimeline() {
        val arrivals = plan(tempo("2026-10-14T21:00:00Z"), "2026-10-16T10:00:00Z").arrivals!!
        assertEquals("3 new entries", arrivals.title)
        assertEquals(listOf("Jonathan Harker · Journal", "Jonathan Harker · Journal", "Jonathan Harker · Journal"), arrivals.lines)
        assertNull(arrivals.openEntryId)
    }

    @Test
    fun nothingNewSinceTheBaselineMeansNoNotification() {
        assertNull(plan(tempo("2026-10-15T15:00:00Z"), "2026-10-15T16:00:00Z").arrivals)
    }

    @Test
    fun withoutABaselineNothingOldIsAnnounced() {
        assertNull(plan(tempo(null), "2026-10-16T10:00:00Z").arrivals)
    }

    @Test
    fun theSeasonPromptIsPlannedWhenItFallsDue() {
        val finished = UserState().withMode(ReadingMode.Calendar(2026)).copy(lastNotifiedAt = Instant.parse("2027-01-01T00:00:00Z").toEpochMilli())
        val prompt = plan(finished, "2027-04-26T10:00:00Z").seasonPrompt!!
        assertEquals("Dracula begins again on 3 May", prompt.title)
        assertNull(plan(finished, "2027-04-26T09:00:00Z").seasonPrompt)
    }

    @Test
    fun readFreelyAndNotificationsOffPlanNothing() {
        assertEquals(NotificationPlan(null, null), plan(UserState().withMode(ReadingMode.Free), "2026-10-16T10:00:00Z"))
        assertEquals(NotificationPlan(null, null), plan(tempo("2026-10-14T21:00:00Z").copy(notify = false), "2026-10-16T10:00:00Z"))
    }
}
