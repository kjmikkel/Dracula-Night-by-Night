package com.jensen.draculanightbynight.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TimelineUi {
    data object Loading : TimelineUi
    data class Ready(val timeline: TimelineState, val mode: ReadingMode) : TimelineUi
}

class TimelineViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<TimelineUi> = combine(flow { emit(app.stories.story()) }, app.users.state, app.minuteTicks()) { story, user, now ->
        val settings = user.settings ?: return@combine TimelineUi.Loading
        TimelineUi.Ready(buildTimeline(story, user, settings, now, app.clock.zone), settings.mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineUi.Loading)

    init {
        viewModelScope.launch { app.users.ensureMode() }
    }

    fun setMode(mode: ReadingMode) {
        viewModelScope.launch { app.users.setMode(mode) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
