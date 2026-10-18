package com.jensen.draculanightbynight.welcome

import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.UnlockSchedule
import com.jensen.draculanightbynight.domain.calendarArrivals
import com.jensen.draculanightbynight.domain.calendarBacklog
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
    /** Entries On the actual days would unlock at once today, out of [total] (#193). */
    val calendarArrived: Int = 0,
    val total: Int = 0,
)

/** What the On the actual days card adds mid-season: how much of the book is already waiting. */
fun calendarBacklog(welcome: WelcomeState): String? =
    if (welcome.calendarWaits) null else calendarBacklog(welcome.today, welcome.calendarArrived, welcome.total)

fun buildWelcome(story: Story, now: ZonedDateTime): WelcomeState {
    val today = now.toLocalDate()
    val seasonYear = UnlockSchedule.seasonYearFor(today)
    val seasonStart = UnlockSchedule.STORY_START.withYear(seasonYear)
    val first = story.firstTimedEntry
    return WelcomeState(
        today = today,
        seasonYear = seasonYear,
        calendarWaits = today.isBefore(seasonStart),
        firstEntryClose = firstEntryIsClose(story, today, now.toInstant(), now.zone),
        firstEntryAuthor = first?.let { story.nameOf(it.author) },
        firstEntryTime = first?.time,
        calendarArrived = calendarArrivals(story.entries, now.toInstant(), now.zone),
        total = story.entries.size,
    )
}
