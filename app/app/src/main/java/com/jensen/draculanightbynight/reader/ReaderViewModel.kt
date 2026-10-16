package com.jensen.draculanightbynight.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

sealed interface ReaderUi {
    data object Loading : ReaderUi
    data object Locked : ReaderUi

    /** [pages] holds the reader for the current page and its neighbours, the ones the pager can show. */
    data class Ready(val pager: ReaderPager, val pages: Map<String, ReaderState>) : ReaderUi {
        val reader: ReaderState get() = pages.getValue(pager.pages[pager.index])
    }
}

/**
 * @param ticks the current time and zone, re-emitted as time passes ([com.jensen.draculanightbynight.domain.TimeSource.ticks]).
 * @param appScope outlives the screen, for saves made as it closes.
 * @param filtered opened from a filtered timeline: Previous, Next and swipe follow the filter until switched to All.
 */
class ReaderViewModel(
    stories: StoryRepository,
    private val users: UserRepository,
    ticks: Flow<ZonedDateTime>,
    private val appScope: CoroutineScope,
    startId: String,
    filtered: Boolean = false,
) : ViewModel() {
    private val current = MutableStateFlow(startId)
    private val following = MutableStateFlow(if (filtered) Following.FILTER else Following.ALL)

    val state: StateFlow<ReaderUi> =
        combine(flow { emit(stories.story()) }, users.state, ticks, current, following) { story, user, now, id, follows ->
            val settings = user.settings ?: return@combine ReaderUi.Loading
            val pager = buildPager(story, user, settings, now.toInstant(), now.zone, id, follows, switchable = filtered)
                ?: return@combine ReaderUi.Locked
            val window = pager.pages.subList((pager.index - NEIGHBOURS).coerceAtLeast(0), (pager.index + NEIGHBOURS + 1).coerceAtMost(pager.pages.size))
            ReaderUi.Ready(pager, window.mapNotNull { story.entry(it) }.associate { it.id to readerFor(story, user, it) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReaderUi.Loading)

    /** A page the reader has come to rest on: it becomes current and counts as read (pages dragged past do not). */
    fun onPageSettled(id: String) {
        current.value = id
        viewModelScope.launch { users.markRead(id) }
    }

    /** The switch: keeps the current entry and rebuilds the order around it. */
    fun follow(order: Following) {
        following.value = order
    }

    private val saveOrder = Mutex()

    /**
     * Runs in the app scope: the last save happens as the screen closes, after viewModelScope is cancelled.
     * Saves are written in the order they are made: each takes its place in line before the first suspension,
     * so a debounced save can never overwrite the later one made as the screen closes.
     */
    fun savePosition(id: String, blockIndex: Int, blockOffset: Int) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saveOrder.withLock { users.savePosition(id, blockIndex, blockOffset) }
        }
    }

    fun toggleBookmark() {
        val id = current.value
        viewModelScope.launch { users.toggleBookmark(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Pages either side of the current one that the pager may show while it is dragged. */
        const val NEIGHBOURS = 2
    }
}
