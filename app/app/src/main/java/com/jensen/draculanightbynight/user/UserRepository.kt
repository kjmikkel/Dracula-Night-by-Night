package com.jensen.draculanightbynight.user

import androidx.datastore.core.DataStore
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.domain.startedOver
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Reads and writes the reader's saved state. Every change to the schedule (mode, start date, skip ahead,
 * turning notifications on, Start over) moves the notification baseline to now: entries that arrive because
 * of something the reader did are never announced afterwards.
 */
class UserRepository(private val store: DataStore<UserState>, private val time: TimeSource) {
    val state: Flow<UserState> = store.data

    /** Until the Welcome screen exists, a reader without a mode starts in Same tempo from today. */
    suspend fun ensureMode() {
        store.updateData { s -> if (s.readingMode == null) s.withMode(ReadingMode.SameTempo(time.today())) else s }
    }

    /** Begin on the Welcome screen. */
    suspend fun completeOnboarding(mode: ReadingMode, notify: Boolean) {
        store.updateData { it.withMode(mode).copy(onboarded = true, notify = notify, lastNotifiedAt = time.millis()) }
    }

    suspend fun setMode(mode: ReadingMode) {
        store.updateData { it.withMode(mode).copy(lastNotifiedAt = time.millis()) }
    }

    suspend fun setTempoStart(date: LocalDate) {
        store.updateData { it.withMode(ReadingMode.SameTempo(date)).copy(lastNotifiedAt = time.millis()) }
    }

    /** Skip ahead (Read it now, Skip to a day) or, with 0, Back to real time. */
    suspend fun setSkipAhead(minutes: Long) {
        store.updateData { it.copy(skipAheadMinutes = minutes, lastNotifiedAt = time.millis()) }
    }

    suspend fun setNotify(on: Boolean) {
        store.updateData { s -> if (on) s.copy(notify = true, lastNotifiedAt = time.millis()) else s.copy(notify = false) }
    }

    /** After a notification run: entries up to [atMillis] have been announced. */
    suspend fun markNotified(atMillis: Long) {
        store.updateData { it.copy(lastNotifiedAt = atMillis) }
    }

    /** Records the first time an entry was opened; reopening keeps the original time. */
    suspend fun markRead(id: String) {
        store.updateData { s -> s.updateProgress(id) { p -> if (p.readAtMillis != null) p else p.copy(readAtMillis = time.millis()) } }
    }

    suspend fun savePosition(id: String, blockIndex: Int, blockOffset: Int) {
        store.updateData { s -> s.updateProgress(id) { it.copy(blockIndex = blockIndex, blockOffset = blockOffset) } }
    }

    suspend fun toggleBookmark(id: String) {
        store.updateData { s -> s.updateProgress(id) { it.copy(bookmarked = !it.bookmarked) } }
    }

    /** Start over: see [startedOver]. [tempoStart] is the Same tempo restart date (ignored in other modes). */
    suspend fun startOver(removeBookmarks: Boolean, tempoStart: LocalDate) {
        store.updateData { it.startedOver(removeBookmarks, time.today(), tempoStart, time.millis()) }
    }

    /** Records that this year's new-season prompt was answered (Start over or Not now). */
    suspend fun answerSeasonPrompt() {
        store.updateData { it.copy(seasonPromptYear = time.today().year) }
    }

    suspend fun setTextSize(size: TextSize) {
        store.updateData { it.copy(textSize = size) }
    }

    suspend fun setReadingFont(font: ReadingFont) {
        store.updateData { it.copy(readingFont = font) }
    }

    suspend fun setTheme(theme: ThemeChoice) {
        store.updateData { it.copy(theme = theme) }
    }

    /** The reader's own choice; from now on Android's contrast setting no longer decides. */
    suspend fun setHighContrast(on: Boolean) {
        store.updateData { it.copy(highContrast = on) }
    }
}
