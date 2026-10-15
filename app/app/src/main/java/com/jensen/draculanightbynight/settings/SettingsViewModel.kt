package com.jensen.draculanightbynight.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.SkipAhead
import com.jensen.draculanightbynight.domain.pickMode
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<SettingsState?> = combine(flow { emit(app.stories.story()) }, app.users.state, app.time.ticks()) { story, user, now ->
        buildSettings(story, user, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private fun update(change: suspend () -> Unit) {
        viewModelScope.launch { change() }
    }

    /** Re-picking the current kind of mode keeps it; only a different kind is stored. */
    fun setMode(picked: ReadingMode) = update {
        val current = app.users.state.first().readingMode
        val mode = pickMode(current, picked)
        if (mode != current) app.users.setMode(mode)
    }

    fun setTempoStart(date: LocalDate) = update { app.users.setTempoStart(date) }

    /** Unlocks everything up to the end of story [day]; never moves backwards. */
    fun skipToDay(day: LocalDate) = update {
        val user = app.users.state.first()
        val settings = user.settings ?: return@update
        val now = app.time.now()
        app.users.setSkipAhead(SkipAhead.toEndOf(day, app.stories.story(), settings, now.toInstant(), now.zone))
    }

    fun backToRealTime() = update { app.users.setSkipAhead(0) }

    fun setNotify(on: Boolean) = update { app.users.setNotify(on) }

    fun setTextSize(size: TextSize) = update { app.users.setTextSize(size) }

    fun setReadingFont(font: ReadingFont) = update { app.users.setReadingFont(font) }

    fun setTheme(theme: ThemeChoice) = update { app.users.setTheme(theme) }

    fun setHighContrast(on: Boolean) = update { app.users.setHighContrast(on) }

    fun startOver(removeBookmarks: Boolean, tempoStart: LocalDate) = update { app.users.startOver(removeBookmarks, tempoStart) }

    /** The notification baseline may need a fresh alarm after Android's settings changed (blocked or allowed). */
    fun recheckNotifications() = update { app.notifier.reschedule() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
