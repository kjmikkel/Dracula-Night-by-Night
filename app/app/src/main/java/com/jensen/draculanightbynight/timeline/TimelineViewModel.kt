package com.jensen.draculanightbynight.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.pickMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface TimelineUi {
    data object Loading : TimelineUi
    data class Ready(val timeline: TimelineState, val mode: ReadingMode, val today: LocalDate) : TimelineUi
}

class TimelineViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<TimelineUi> = combine(flow { emit(app.stories.story()) }, app.users.state, app.time.ticks()) { story, user, now ->
        val settings = user.settings ?: return@combine TimelineUi.Loading
        TimelineUi.Ready(buildTimeline(story, user, settings, now.toInstant(), now.zone), settings.mode, now.toLocalDate())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineUi.Loading)

    init {
        viewModelScope.launch { app.users.ensureMode() }
    }

    /** Re-picking the current kind of mode keeps it; only a different kind is stored. */
    fun setMode(picked: ReadingMode) {
        viewModelScope.launch {
            val current = app.users.state.first().readingMode
            val mode = pickMode(current, picked)
            if (mode != current) app.users.setMode(mode)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
