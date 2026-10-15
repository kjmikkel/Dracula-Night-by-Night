package com.jensen.draculanightbynight.user

import androidx.datastore.core.DataStore
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.domain.startedOver
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class UserRepository(private val store: DataStore<UserState>, private val time: TimeSource) {
    val state: Flow<UserState> = store.data

    /** First launch: until onboarding exists, a new reader starts in Same tempo from today. */
    suspend fun ensureMode() {
        store.updateData { s -> if (s.readingMode == null) s.withMode(ReadingMode.SameTempo(time.today())) else s }
    }

    suspend fun setMode(mode: ReadingMode) {
        store.updateData { it.withMode(mode) }
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
}
