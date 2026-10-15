package com.jensen.draculanightbynight.welcome

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

class WelcomeViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<WelcomeState?> = combine(flow { emit(app.stories.story()) }, app.time.ticks()) { story, now ->
        buildWelcome(story, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Begin: saves the choices, then [onDone] leaves the Welcome screen. */
    fun begin(mode: ReadingMode, notify: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            app.users.completeOnboarding(mode, notify)
            onDone()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
