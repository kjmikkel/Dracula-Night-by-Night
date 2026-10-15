package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.user.UserState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The skip-ahead offsets behind "Read it now" and "Skip to a day". Both only ever move the offset forwards. */
object SkipAhead {
    private const val MINUTE_MILLIS = 60_000L

    /** Minutes of skip ahead that make the next unread entry (and any arriving with it) readable at [now]. */
    fun toNext(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId): Long {
        val current = settings.skipAhead.toMinutes()
        val next = Arrivals.nextAfter(story, user, settings, now, zone) ?: return current
        return current + ceilMinutes(Duration.between(now, next))
    }

    /** Minutes of skip ahead that unlock every entry up to the end of story day [day]; never less than now. */
    fun toEndOf(day: LocalDate, story: Story, settings: ReadingSettings, now: Instant, zone: ZoneId): Long {
        val current = settings.skipAhead.toMinutes()
        val last = story.entries
            .filter { !it.date.isAfter(day) }
            .mapNotNull { UnlockSchedule.unlockInstant(it, settings.mode, zone) }
            .maxOrNull() ?: return current
        return maxOf(current, ceilMinutes(Duration.between(now, last)))
    }

    private fun ceilMinutes(duration: Duration): Long {
        val millis = duration.toMillis()
        return if (millis <= 0) 0 else (millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS
    }
}
