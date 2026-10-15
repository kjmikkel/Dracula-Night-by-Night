package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Entry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed interface ReadingMode {
    /** Entries unlock on their calendar day and in-story time, in [seasonYear]. */
    data class Calendar(val seasonYear: Int) : ReadingMode

    /** Day one of the story (3 May) is [startDate]; the gaps between entries are kept. */
    data class SameTempo(val startDate: LocalDate) : ReadingMode

    /** Everything is unlocked. */
    data object Free : ReadingMode
}

data class ReadingSettings(val mode: ReadingMode, val skipAhead: Duration = Duration.ZERO)

object UnlockSchedule {
    val STORY_START: LocalDate = LocalDate.of(1893, 5, 3)
    private val SEASON_END: MonthDay = MonthDay.of(11, 6)

    /** When [entry] unlocks for a reader in [zone]; null means it is always unlocked. */
    fun unlockInstant(entry: Entry, mode: ReadingMode, zone: ZoneId): Instant? = when (mode) {
        ReadingMode.Free -> null
        is ReadingMode.Calendar -> instantOf(entry.date.withYear(mode.seasonYear).atTime(entry.time), zone)
        is ReadingMode.SameTempo -> instantOf(
            mode.startDate.plusDays(ChronoUnit.DAYS.between(STORY_START, entry.date)).atTime(entry.time),
            zone,
        )
    }

    /**
     * A wall-clock time that happens twice (clocks going back) uses the earlier one. One that never happens
     * (clocks jumping forward) unlocks the moment the clock jumps, so entries from that night keep their order.
     */
    private fun instantOf(local: LocalDateTime, zone: ZoneId): Instant {
        val rules = zone.rules
        return if (rules.getValidOffsets(local).isEmpty()) rules.getTransition(local).instant else local.atZone(zone).toInstant()
    }

    fun isUnlocked(entry: Entry, settings: ReadingSettings, now: Instant, zone: ZoneId): Boolean {
        val at = unlockInstant(entry, settings.mode, zone) ?: return true
        return !at.isAfter(now.plus(settings.skipAhead))
    }

    /** The season a reader choosing Calendar mode on [today] will follow: this year, or next year after 6 November. */
    fun seasonYearFor(today: LocalDate): Int =
        if (MonthDay.from(today).isAfter(SEASON_END)) today.year + 1 else today.year
}
