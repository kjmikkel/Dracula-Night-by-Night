package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/** When entries arrive by the schedule alone: what notifications announce and what the Next card shows. */
object Arrivals {
    /** The real instant [entry] arrives: its scheduled unlock moved earlier by skip ahead; null in Read freely. */
    fun arrivesAt(entry: Entry, settings: ReadingSettings, zone: ZoneId): Instant? =
        UnlockSchedule.unlockInstant(entry, settings.mode, zone)?.minus(settings.skipAhead)

    /** Unread entries that arrive in (from, to], in story order. */
    fun between(story: Story, user: UserState, settings: ReadingSettings, from: Instant, to: Instant, zone: ZoneId): List<Entry> =
        story.entries.filter { entry ->
            val at = arrivesAt(entry, settings, zone)
            at != null && at.isAfter(from) && !at.isAfter(to) && user.progressOf(entry.id).readAtMillis == null
        }

    /** The next instant after [now] at which an unread entry arrives; null in Read freely or when nothing is left. */
    fun nextAfter(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId): Instant? =
        story.entries
            .filter { user.progressOf(it.id).readAtMillis == null }
            .mapNotNull { arrivesAt(it, settings, zone) }
            .filter { it.isAfter(now) }
            .minOrNull()
}
