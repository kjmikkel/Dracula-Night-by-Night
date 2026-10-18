package com.jensen.draculanightbynight.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.SkipAhead
import com.jensen.draculanightbynight.domain.pickMode
import com.jensen.draculanightbynight.user.CopyTarget
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.decodeBackup
import com.jensen.draculanightbynight.user.hasProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

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

    fun setHidePartiesLine(on: Boolean) = update { app.users.setHidePartiesLine(on) }

    fun setSkipRepeatedDates(on: Boolean) = update { app.users.setSkipRepeatedDates(on) }

    fun startOver(removeBookmarks: Boolean, tempoStart: LocalDate) = update { app.users.startOver(removeBookmarks, tempoStart) }

    /** The kept copy and what its row says (#205). */
    val copy: StateFlow<Pair<CopyTarget?, String>> = combine(app.backups.target, app.time.ticks()) { target, now ->
        target to copySummary(target, now.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null to copySummary(null, ZoneOffset.UTC))

    private val _pendingImport = MutableStateFlow<UserState?>(null)

    /** A loaded backup waiting for the reader to agree to replace what this phone has. */
    val pendingImport: StateFlow<UserState?> = _pendingImport.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short confirmations and errors for the snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun backupName(): String = app.backups.suggestedName()

    fun saveTo(uri: Uri) = update { _messages.emit(if (app.backups.save(uri)) "Saved." else "Couldn't save the file.") }

    suspend fun shareIntent(): Intent = app.backups.shareIntent()

    /** A file the reader picked to load: refused, loaded, or held for the replace question when there's progress here. */
    fun load(uri: Uri) = update {
        val text = app.backups.read(uri) ?: return@update _messages.emit("Couldn't read that file.")
        val backup = decodeBackup(text) ?: return@update _messages.emit("That file isn't a Dracula Night by Night backup.")
        if (app.users.state.first().hasProgress) _pendingImport.value = backup else restore(backup)
    }

    fun confirmImport() = update {
        _pendingImport.value?.let { restore(it) }
        _pendingImport.value = null
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    private suspend fun restore(backup: UserState) {
        app.users.restore(backup)
        _messages.emit("Your reading is loaded.")
    }

    fun keepCopy(uri: Uri) = update { app.backups.keepCopy(uri) }

    fun stopCopy() = app.backups.stopCopy()

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    /** The notification baseline may need a fresh alarm after Android's settings changed (blocked or allowed). */
    fun recheckNotifications() = update { app.notifier.reschedule() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
