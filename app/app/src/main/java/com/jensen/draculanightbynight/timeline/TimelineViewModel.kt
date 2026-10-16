package com.jensen.draculanightbynight.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.SkipAhead
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
    data class Ready(val timeline: TimelineState, val mode: ReadingMode, val today: LocalDate, val bookmarks: Int) : TimelineUi
}

class TimelineViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<TimelineUi> = combine(flow { emit(app.stories.story()) }, app.users.state, app.time.ticks()) { story, user, now ->
        val settings = user.settings ?: return@combine TimelineUi.Loading
        TimelineUi.Ready(
            buildTimeline(story, user, settings, now.toInstant(), now.zone, user.filter),
            settings.mode,
            now.toLocalDate(),
            bookmarks = user.progress.values.count { it.bookmarked },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineUi.Loading)

    init {
        viewModelScope.launch { app.users.ensureMode() }
    }

    /** "Read it now": skip ahead just far enough for the next entry to arrive. */
    fun readItNow() {
        viewModelScope.launch {
            val user = app.users.state.first()
            val settings = user.settings ?: return@launch
            val now = app.time.now()
            app.users.setSkipAhead(SkipAhead.toNext(app.stories.story(), user, settings, now.toInstant(), now.zone))
        }
    }

    fun backToRealTime() {
        viewModelScope.launch { app.users.setSkipAhead(0) }
    }

    /** Start over from the end card or the new-season card; either way this year's season prompt is answered. */
    fun startOver(removeBookmarks: Boolean, tempoStart: LocalDate) {
        viewModelScope.launch {
            app.users.startOver(removeBookmarks, tempoStart)
            app.users.answerSeasonPrompt()
        }
    }

    /** "Not now" on the new-season card. */
    fun dismissSeasonPrompt() {
        viewModelScope.launch { app.users.answerSeasonPrompt() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
