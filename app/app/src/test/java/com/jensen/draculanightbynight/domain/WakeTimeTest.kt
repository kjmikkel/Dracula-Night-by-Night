package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WakeTimeTest {
    private val utc = ZoneOffset.UTC
    private val tempo = UserState().withMode(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val dayTwoAfternoon = Instant.parse("2026-10-15T15:00:00Z")

    private fun wake(user: UserState, now: Instant = dayTwoAfternoon, allowed: Boolean = true) =
        nextWake(TestStory.small, user, now, utc, notificationsAllowed = allowed)

    @Test
    fun wakesForTheNextArrival() {
        assertEquals(Instant.parse("2026-10-16T10:00:00Z"), wake(tempo))
    }

    @Test
    fun neverWakesWhenNotificationsAreOffOrBlocked() {
        assertNull(wake(tempo.copy(notify = false)))
        assertNull(wake(tempo, allowed = false))
    }

    @Test
    fun neverWakesInReadFreely() {
        assertNull(wake(UserState().withMode(ReadingMode.Free)))
    }

    @Test
    fun aFinishedCalendarSeasonWakesForTheSeasonPrompt() {
        val finished = UserState().withMode(ReadingMode.Calendar(2026))
        assertEquals(Instant.parse("2027-04-26T10:00:00Z"), wake(finished, now = Instant.parse("2026-12-01T12:00:00Z")))
        val alreadyNotified = finished.copy(lastNotifiedAt = Instant.parse("2027-04-26T10:05:00Z").toEpochMilli())
        assertNull(wake(alreadyNotified, now = Instant.parse("2027-04-27T12:00:00Z")))
    }

    @Test
    fun theSeasonPromptIsNotifiedOnceWhenItFallsDue() {
        val finished = UserState().withMode(ReadingMode.Calendar(2026))
        assertFalse(seasonPromptToNotify(finished, Instant.parse("2027-04-26T09:59:00Z"), utc))
        assertTrue(seasonPromptToNotify(finished, Instant.parse("2027-04-26T10:00:00Z"), utc))
        val notified = finished.copy(lastNotifiedAt = Instant.parse("2027-04-26T10:00:00Z").toEpochMilli())
        assertFalse(seasonPromptToNotify(notified, Instant.parse("2027-04-26T10:01:00Z"), utc))
    }
}
