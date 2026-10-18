package com.jensen.draculanightbynight.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
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
    /** Not readable yet; [arrivesAt] is when it will be (null for an entry that isn't in the story). */
    data class Locked(val arrivesAt: ZonedDateTime?) : ReaderUi

    /** [pages] holds the reader for the current page and its neighbours, the ones the pager can show. */
    data class Ready(val pager: ReaderPager, val pages: Map<String, ReaderState>) : ReaderUi {
        val reader: ReaderState get() = pages.getValue(pager.pages[pager.index])
    }
}

/** What the locked reader adds about when the entry arrives (#195). */
fun lockedNote(arrivesAt: ZonedDateTime?): String? = arrivesAt?.let { "It arrives on ${it.format(Dates.WEEKDAY_TIME)}." }

/**
 * @param ticks the current time and zone, re-emitted as time passes ([com.jensen.draculanightbynight.domain.TimeSource.ticks]).
 * @param appScope outlives the screen, for saves made as it closes.
 * @param filtered opened from a filtered timeline: Previous, Next and swipe follow the filter until switched to All.
 * @param followingAll opened already switched to All, e.g. when the split view hands its reader over.
 */
class ReaderViewModel(
    stories: StoryRepository,
    private val users: UserRepository,
    ticks: Flow<ZonedDateTime>,
    private val appScope: CoroutineScope,
    startId: String,
    filtered: Boolean,
    private val saved: SavedStateHandle,
    followingAll: Boolean = false,
) : ViewModel() {
    // Kept in the saved state, so a reader whose app Android killed comes back to the page they were on.
    private val current = saved.getStateFlow(KEY_CURRENT, startId)
    private val following = saved.getStateFlow(KEY_FOLLOWING, if (filtered && !followingAll) Following.FILTER.name else Following.ALL.name)

    // In the saved state, not a constructor value read once, so that open() can change it.
    private val switchable = saved.getStateFlow(KEY_SWITCHABLE, filtered)

    /** The entry the pager rests on. */
    val currentId: StateFlow<String> = current

    val state: StateFlow<ReaderUi> =
        combine(flow { emit(stories.story()) }, users.state, ticks, current, combine(following, switchable, ::Pair)) { story, user, now, id, (followName, canSwitch) ->
            val follows = Following.valueOf(followName)
            val settings = user.settings ?: return@combine ReaderUi.Loading
            val pager = buildPager(story, user, settings, now.toInstant(), now.zone, id, follows, switchable = canSwitch)
                ?: return@combine ReaderUi.Locked(story.entry(id)?.let { Arrivals.arrivesAt(it, settings, now.zone) }?.atZone(now.zone))
            val window = pager.pages.subList((pager.index - NEIGHBOURS).coerceAtLeast(0), (pager.index + NEIGHBOURS + 1).coerceAtMost(pager.pages.size))
            ReaderUi.Ready(pager, window.mapNotNull { story.entry(it) }.associate { it.id to readerFor(story, user, it) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReaderUi.Loading)

    /** A page the reader has come to rest on: it becomes current and counts as read (pages dragged past do not). */
    fun onPageSettled(id: String) {
        saved[KEY_CURRENT] = id
        viewModelScope.launch { users.markRead(id) }
    }

    /**
     * Moves the reader to the entry a timeline tap asked for. A request whose [OpenEntry.seq] was already applied
     * is ignored, so running it again after a rotation does not undo the swipes made since.
     */
    fun open(request: OpenEntry) {
        if (saved.get<Long>(KEY_REQUEST) == request.seq) return
        saved[KEY_REQUEST] = request.seq
        saved[KEY_CURRENT] = request.id
        saved[KEY_SWITCHABLE] = request.filtered
        saved[KEY_FOLLOWING] = (if (request.filtered && !request.followingAll) Following.FILTER else Following.ALL).name
    }

    /** Where the reader is now, for handing it to another layout. */
    fun openEntry(): OpenEntry = OpenEntry(
        id = current.value,
        filtered = switchable.value,
        followingAll = Following.valueOf(following.value) == Following.ALL,
    )

    /** The switch: keeps the current entry and rebuilds the order around it. */
    fun follow(order: Following) {
        saved[KEY_FOLLOWING] = order.name
    }

    private val saveOrder = Mutex()

    /**
     * Runs in the app scope: the last save happens as the screen closes, after viewModelScope is cancelled.
     * Saves are written in the order they are made: each takes its place in line before the first suspension,
     * so a debounced save can never overwrite the later one made as the screen closes.
     */
    fun savePosition(id: String, place: SavedPlace) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saveOrder.withLock { users.savePosition(id, place.block, place.offset, place.permille) }
        }
    }

    fun toggleBookmark() {
        val id = current.value
        viewModelScope.launch { users.toggleBookmark(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_CURRENT = "current"
        const val KEY_FOLLOWING = "following"
        const val KEY_SWITCHABLE = "switchable"
        const val KEY_REQUEST = "request"

        /** Pages either side of the current one that the pager may show while it is dragged. */
        const val NEIGHBOURS = 2
    }
}
