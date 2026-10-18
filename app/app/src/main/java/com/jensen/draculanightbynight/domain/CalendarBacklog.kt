package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.Entry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * How many of [entries] On the actual days has unlocked by [now] in the season a reader choosing it today follows.
 * Mid-season that is most of the book, all waiting at once (#193).
 */
fun calendarArrivals(entries: List<Entry>, now: Instant, zone: ZoneId): Int {
    val calendar = ReadingSettings(ReadingMode.Calendar(UnlockSchedule.seasonYearFor(now.atZone(zone).toLocalDate())))
    return entries.count { UnlockSchedule.isUnlocked(it, calendar, now, zone) }
}

/** "It is 18 October: 196 of 235 entries have already arrived.", or null when nothing has. */
fun calendarBacklog(today: LocalDate, arrived: Int, total: Int): String? {
    if (arrived == 0) return null
    val count = when (arrived) {
        total -> "all $total entries have"
        1 -> "1 of $total entries has"
        else -> "$arrived of $total entries have"
    }
    return "It is ${today.format(Dates.DAY)}: $count already arrived."
}
