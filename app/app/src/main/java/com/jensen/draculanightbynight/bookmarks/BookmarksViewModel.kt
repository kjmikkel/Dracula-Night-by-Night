package com.jensen.draculanightbynight.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.domain.SkipAhead
import com.jensen.draculanightbynight.user.BookmarkSort
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

sealed interface BookmarksUi {
    data object Loading : BookmarksUi
    data class Ready(val rows: List<BookmarkRow>, val sort: BookmarkSort) : BookmarksUi
}

/** @param ticks the current time and zone, re-emitted as time passes ([com.jensen.draculanightbynight.domain.TimeSource.ticks]). */
class BookmarksViewModel(
    private val stories: StoryRepository,
    private val users: UserRepository,
    private val ticks: Flow<ZonedDateTime>,
) : ViewModel() {
    /** What the rows were built from, for the Read it now count. */
    private data class Basis(val story: Story, val user: UserState, val now: ZonedDateTime)

    private var basis: Basis? = null

    val state: StateFlow<BookmarksUi> = combine(flow { emit(stories.story()) }, users.state, ticks) { story, user, now ->
        val settings = user.settings ?: return@combine BookmarksUi.Loading
        basis = Basis(story, user, now)
        BookmarksUi.Ready(bookmarks(story, user, settings, now.toInstant(), now.zone, user.bookmarkSort), user.bookmarkSort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BookmarksUi.Loading)

    fun setSort(sort: BookmarkSort) {
        viewModelScope.launch { users.setBookmarkSort(sort) }
    }

    /** Removes the bookmark at once (a second tap changes nothing); [undo] brings it back. */
    fun remove(row: BookmarkRow) {
        viewModelScope.launch { users.removeBookmark(row.row.id) }
    }

    /** Undo for one removal: [row] comes back with the time it was first made. */
    fun undo(row: BookmarkRow) {
        viewModelScope.launch { users.restoreBookmark(row.row.id, row.bookmarkedAt?.toEpochMilli()) }
    }

    /** How many entries Read it now on a locked [row] makes readable, the row's own entry included. */
    fun skipCount(row: BookmarkRow): Int {
        val (story, user, now) = basis ?: return 0
        val settings = user.settings ?: return 0
        val entry = story.entry(row.row.id) ?: return 0
        return SkipAhead.unlockedBy(SkipAhead.toEntry(entry, settings, now.toInstant(), now.zone), story, user, settings, now.toInstant(), now.zone)
    }

    /** Read it now on a locked bookmark: skip ahead to its entry, then open it. */
    fun readNow(row: BookmarkRow, onReady: (id: String) -> Unit) {
        viewModelScope.launch {
            val story = stories.story()
            val settings = users.state.first().settings ?: return@launch
            val entry = story.entry(row.row.id) ?: return@launch
            val now = ticks.first()
            users.setSkipAhead(SkipAhead.toEntry(entry, settings, now.toInstant(), now.zone))
            onReady(entry.id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
