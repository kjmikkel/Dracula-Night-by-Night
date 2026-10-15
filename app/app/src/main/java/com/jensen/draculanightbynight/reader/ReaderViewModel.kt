package com.jensen.draculanightbynight.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

sealed interface ReaderUi {
    data object Loading : ReaderUi
    data object Locked : ReaderUi
    data class Ready(val reader: ReaderState) : ReaderUi
}

/**
 * @param ticks the current time and zone, re-emitted as time passes ([com.jensen.draculanightbynight.domain.TimeSource.ticks]).
 * @param appScope outlives the screen, for saves made as it closes.
 */
class ReaderViewModel(
    stories: StoryRepository,
    private val users: UserRepository,
    ticks: Flow<ZonedDateTime>,
    private val appScope: CoroutineScope,
    private val entryId: String,
) : ViewModel() {
    val state: StateFlow<ReaderUi> = combine(flow { emit(stories.story()) }, users.state, ticks) { story, user, now ->
        val settings = user.settings ?: return@combine ReaderUi.Loading
        buildReader(story, user, settings, now.toInstant(), now.zone, entryId)?.let { ReaderUi.Ready(it) } ?: ReaderUi.Locked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReaderUi.Loading)

    init {
        // Only an entry that actually opens counts as read; a locked one keeps its unread dot for when it arrives.
        viewModelScope.launch {
            state.first { it is ReaderUi.Ready }
            users.markRead(entryId)
        }
    }

    private val saveOrder = Mutex()

    /**
     * Runs in the app scope: the last save happens as the screen closes, after viewModelScope is cancelled.
     * Saves are written in the order they are made: each takes its place in line before the first suspension,
     * so a debounced save can never overwrite the later one made as the screen closes.
     */
    fun savePosition(blockIndex: Int, blockOffset: Int) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saveOrder.withLock { users.savePosition(entryId, blockIndex, blockOffset) }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch { users.toggleBookmark(entryId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
