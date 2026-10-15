package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SeasonPromptTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")

    private fun calendar(seasonYear: Int, answered: Int? = null) =
        UserState().withMode(ReadingMode.Calendar(seasonYear)).copy(seasonPromptYear = answered)

    @Test
    fun dueFromTwentySixthAprilAfterAFinishedSeasonUntilAnswered() {
        assertFalse(SeasonPrompt.isDue(calendar(2026), LocalDate.of(2027, 4, 25)))
        assertTrue(SeasonPrompt.isDue(calendar(2026), LocalDate.of(2027, 4, 26)))
        assertTrue(SeasonPrompt.isDue(calendar(2026), LocalDate.of(2027, 6, 1)))
    }

    @Test
    fun notDuringOrRightAfterTheReadersOwnSeason() {
        assertFalse(SeasonPrompt.isDue(calendar(2026), LocalDate.of(2026, 5, 10)))
        assertFalse(SeasonPrompt.isDue(calendar(2026), LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun answeredOnceAYear() {
        assertFalse(SeasonPrompt.isDue(calendar(2026, answered = 2027), LocalDate.of(2027, 5, 1)))
        assertTrue(SeasonPrompt.isDue(calendar(2026, answered = 2027), LocalDate.of(2028, 4, 26)))
    }

    @Test
    fun onlyForCalendarReaders() {
        val today = LocalDate.of(2027, 5, 1)
        assertFalse(SeasonPrompt.isDue(UserState().withMode(ReadingMode.SameTempo(LocalDate.of(2026, 5, 1))), today))
        assertFalse(SeasonPrompt.isDue(UserState().withMode(ReadingMode.Free), today))
        assertFalse(SeasonPrompt.isDue(UserState(), today))
    }

    @Test
    fun theNotificationIsDueAtTenOnTwentySixthApril() {
        assertEquals(ZonedDateTime.of(2027, 4, 26, 10, 0, 0, 0, copenhagen).toInstant(), SeasonPrompt.notifyAt(calendar(2026), copenhagen))
        assertEquals(
            ZonedDateTime.of(2028, 4, 26, 10, 0, 0, 0, copenhagen).toInstant(),
            SeasonPrompt.notifyAt(calendar(2026, answered = 2027), copenhagen),
        )
        assertNull(SeasonPrompt.notifyAt(UserState().withMode(ReadingMode.Free), copenhagen))
    }
}
