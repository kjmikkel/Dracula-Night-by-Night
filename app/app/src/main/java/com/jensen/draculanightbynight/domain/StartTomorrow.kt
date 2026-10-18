package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Story
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val CLOSE: Duration = Duration.ofHours(1)

/**
 * True when Same tempo started on [today] would put the story's first timed entry (in this dataset Jonathan
 * Harker's, 3 May 22:00 — the preface at 00:00 is readable at once) less than an hour after [now], or already
 * past: the moment to offer "Start tomorrow?".
 */
fun firstEntryIsClose(story: Story, today: LocalDate, now: Instant, zone: ZoneId): Boolean {
    val first = story.firstTimedEntry ?: return false
    val at = UnlockSchedule.unlockInstant(first, ReadingMode.SameTempo(today), zone) ?: return false
    return Duration.between(now, at) < CLOSE
}
