package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/** "Dracula begins again on 3 May": offered to Calendar readers a week before a new season, once a year. */
object SeasonPrompt {
    private val OPENS: MonthDay = MonthDay.of(4, 26)
    private val NOTIFY_TIME: LocalTime = LocalTime.of(10, 0)

    /** True from 26 April of a year after the reader's season until they answer (Start over or Not now). */
    fun isDue(user: UserState, today: LocalDate): Boolean {
        val mode = user.readingMode as? ReadingMode.Calendar ?: return false
        return today.year > mode.seasonYear && !MonthDay.from(today).isBefore(OPENS) && user.seasonPromptYear != today.year
    }

    /** When the prompt's notification is due: 26 April, 10:00, of the first year it has not been answered in. */
    fun notifyAt(user: UserState, zone: ZoneId): Instant? {
        val mode = user.readingMode as? ReadingMode.Calendar ?: return null
        val year = maxOf(mode.seasonYear + 1, (user.seasonPromptYear ?: 0) + 1)
        return OPENS.atYear(year).atTime(NOTIFY_TIME).atZone(zone).toInstant()
    }
}
