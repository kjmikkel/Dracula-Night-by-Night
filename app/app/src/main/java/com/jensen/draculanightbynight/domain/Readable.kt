package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/**
 * Once read, always readable: an entry the reader has opened stays available even when the schedule no longer
 * unlocks it (back to real time, a mode switch, a zone moved west, a clock set back). Unread entries follow the
 * schedule. The Next card, arrivals and notifications use the schedule alone.
 */
fun isReadable(entry: Entry, settings: ReadingSettings, user: UserState, now: Instant, zone: ZoneId): Boolean =
    user.progressOf(entry.id).readAtMillis != null || UnlockSchedule.isUnlocked(entry, settings, now, zone)
