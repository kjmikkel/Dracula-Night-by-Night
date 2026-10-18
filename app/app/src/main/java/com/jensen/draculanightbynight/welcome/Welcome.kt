package com.jensen.draculanightbynight.welcome

import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.UnlockSchedule
import com.jensen.draculanightbynight.domain.firstEntryIsClose
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * What the Welcome screen says about the modes today: the Calendar season (and whether it has to wait for
 * 3 May), and whether Same tempo should offer to start tomorrow because the first timed entry is close.
 */
data class WelcomeState(
    val today: LocalDate,
    val seasonYear: Int,
    val calendarWaits: Boolean,
    val firstEntryClose: Boolean,
    val firstEntryAuthor: String?,
    val firstEntryTime: LocalTime?,
)

fun buildWelcome(story: Story, now: ZonedDateTime): WelcomeState {
    val today = now.toLocalDate()
    val seasonYear = UnlockSchedule.seasonYearFor(today)
    val seasonStart = UnlockSchedule.STORY_START.withYear(seasonYear)
    val first = story.entries.firstOrNull { !it.undated }
    return WelcomeState(
        today = today,
        seasonYear = seasonYear,
        calendarWaits = today.isBefore(seasonStart),
        firstEntryClose = firstEntryIsClose(story, today, now.toInstant(), now.zone),
        firstEntryAuthor = first?.let { story.nameOf(it.author) },
        firstEntryTime = first?.time,
    )
}
