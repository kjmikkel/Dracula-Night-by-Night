package com.jensen.draculanightbynight.user

import androidx.datastore.core.DataStore
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.Voice
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

    /** Repair: an onboarded reader whose saved mode cannot be read (say, a broken start date) restarts Same tempo from today. */
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

    /** After a notification run: entries up to [atMillis] have been announced. The baseline never moves back. */
    suspend fun markNotified(atMillis: Long) {
        store.updateData { it.copy(lastNotifiedAt = maxOf(it.lastNotifiedAt ?: atMillis, atMillis)) }
    }

    /** Records the first time an entry was opened; reopening keeps the original time. */
    suspend fun markRead(id: String) {
        store.updateData { s -> s.updateProgress(id) { p -> if (p.readAtMillis != null) p else p.copy(readAtMillis = time.millis()) } }
    }

    suspend fun savePosition(id: String, blockIndex: Int, blockOffset: Int) {
        store.updateData { s -> s.updateProgress(id) { it.copy(blockIndex = blockIndex, blockOffset = blockOffset) } }
    }

    /** Bookmarking stamps the time (for Recently bookmarked); removing clears it. */
    suspend fun toggleBookmark(id: String) {
        store.updateData { s ->
            s.updateProgress(id) { if (it.bookmarked) it.copy(bookmarked = false, bookmarkedAtMillis = null) else it.copy(bookmarked = true, bookmarkedAtMillis = time.millis()) }
        }
    }

    /** Removes a bookmark; removing one that is already gone changes nothing (a double tap). */
    suspend fun removeBookmark(id: String) {
        store.updateData { s -> s.updateProgress(id) { it.copy(bookmarked = false, bookmarkedAtMillis = null) } }
    }

    /** A voice chip: applied to the saved filter itself, so quick taps never work from a stale copy. */
    suspend fun toggleFilterVoice(voice: Voice) {
        store.updateData { s -> s.filter.let { f -> s.withFilter(f.copy(voices = if (voice in f.voices) f.voices - voice else f.voices + voice)) } }
    }

    suspend fun toggleFilterType(type: DocumentType) {
        store.updateData { s -> s.filter.let { f -> s.withFilter(f.copy(types = if (type in f.types) f.types - type else f.types + type)) } }
    }

    /** Undo of a removal: the bookmark comes back with the time it was first made. */
    suspend fun restoreBookmark(id: String, atMillis: Long?) {
        store.updateData { s -> s.updateProgress(id) { it.copy(bookmarked = true, bookmarkedAtMillis = atMillis) } }
    }

    /** The filter only changes what the timeline shows, so the notification baseline stays. */
    suspend fun setFilter(filter: EntryFilter) {
        store.updateData { it.withFilter(filter) }
    }

    suspend fun setBookmarkSort(sort: BookmarkSort) {
        store.updateData { it.copy(bookmarkSort = sort) }
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

    suspend fun setHidePartiesLine(on: Boolean) {
        store.updateData { it.copy(hidePartiesLine = on) }
    }

    /** The reader's own choice; from now on TalkBack no longer decides. */
    suspend fun setSkipRepeatedDates(on: Boolean) {
        store.updateData { it.copy(skipRepeatedDates = on) }
    }

    /** Whether the timeline is hidden beside an open entry (two panes only). */
    suspend fun setTimelineHidden(hidden: Boolean) {
        store.updateData { it.copy(timelineHidden = hidden) }
    }
}
