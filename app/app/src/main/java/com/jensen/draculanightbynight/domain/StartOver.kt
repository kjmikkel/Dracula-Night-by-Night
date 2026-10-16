package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import java.time.LocalDate

/**
 * The reader's state after Start over: reading progress cleared (bookmarks kept unless [removeBookmarks]),
 * skip ahead reset, Calendar moved to the season that [today] belongs to, Same tempo restarted from
 * [tempoStart]. Mode kind and display settings are kept. [nowMillis] becomes the notification baseline, so
 * restarting never produces a burst of notifications.
 */
fun UserState.startedOver(removeBookmarks: Boolean, today: LocalDate, tempoStart: LocalDate, nowMillis: Long): UserState {
    val kept = if (removeBookmarks) emptyMap() else progress.filterValues { it.bookmarked }.mapValues { EntryProgress(bookmarked = true, bookmarkedAtMillis = it.value.bookmarkedAtMillis) }
    val restarted = when (readingMode) {
        is ReadingMode.Calendar -> withMode(ReadingMode.Calendar(UnlockSchedule.seasonYearFor(today)))
        is ReadingMode.SameTempo -> withMode(ReadingMode.SameTempo(tempoStart))
        ReadingMode.Free, null -> this
    }
    return restarted.copy(progress = kept, skipAheadMinutes = 0, lastNotifiedAt = nowMillis)
}
