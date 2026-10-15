package com.jensen.draculanightbynight.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderUi {
    data object Loading : ReaderUi
    data object Locked : ReaderUi
    data class Ready(val reader: ReaderState) : ReaderUi
}

class ReaderViewModel(private val app: AppContainer, private val entryId: String) : ViewModel() {
    val state: StateFlow<ReaderUi> = combine(flow { emit(app.stories.story()) }, app.users.state, app.minuteTicks()) { story, user, now ->
        val settings = user.settings ?: return@combine ReaderUi.Loading
        buildReader(story, user, settings, now, app.clock.zone, entryId)?.let { ReaderUi.Ready(it) } ?: ReaderUi.Locked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReaderUi.Loading)

    init {
        viewModelScope.launch { app.users.markRead(entryId) }
    }

    /** Runs in the app scope: the last save happens as the screen closes, after viewModelScope is cancelled. */
    fun savePosition(blockIndex: Int, blockOffset: Int) {
        app.scope.launch { app.users.savePosition(entryId, blockIndex, blockOffset) }
    }

    fun toggleBookmark() {
        viewModelScope.launch { app.users.toggleBookmark(entryId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
