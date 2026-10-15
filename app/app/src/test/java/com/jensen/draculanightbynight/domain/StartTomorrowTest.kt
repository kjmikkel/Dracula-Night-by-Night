package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.TestStory.entry
import com.jensen.draculanightbynight.content.Story
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class StartTomorrowTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val utc = ZoneOffset.UTC

    /** The real book's opening: the preface at 00:00 (readable at once), then Jonathan Harker at 22:00. */
    private fun story(firstTimed: String = "22:00") = Story(
        listOf(entry("preface", 5, 3, "00:00", order = 0), entry("harker", 5, 3, firstTimed, order = 1)),
        TestStory.characters,
    )

    private fun at(time: String) = LocalDateTime.of(today, LocalTime.parse(time)).toInstant(utc)

    @Test
    fun closeWhenTheFirstEntryIsUnderAnHourAway() {
        assertTrue(firstEntryIsClose(story(), today, at("21:01"), utc))
    }

    @Test
    fun notCloseAtExactlyAnHour() {
        assertFalse(firstEntryIsClose(story(), today, at("21:00"), utc))
    }

    @Test
    fun closeWhenItHasAlreadyPassed() {
        assertTrue(firstEntryIsClose(story(), today, at("23:30"), utc))
    }

    @Test
    fun notCloseEarlierInTheDay() {
        assertFalse(firstEntryIsClose(story(), today, at("15:00"), utc))
    }

    @Test
    fun followsTheDatasetNotAFixedHour() {
        assertTrue(firstEntryIsClose(story(firstTimed = "20:00"), today, at("19:30"), utc))
    }
}
