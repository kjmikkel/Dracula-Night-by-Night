package com.jensen.draculanightbynight.whoswho

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.domain.ShownGroup
import com.jensen.draculanightbynight.domain.visibleWhosWho
import com.jensen.draculanightbynight.user.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface WhosWhoUi {
    data object Loading : WhosWhoUi

    /**
     * [expanded] are the unfolded profiles; [scrollTo] the one to bring into view on opening, if any. [sources] says
     * where each shown line came from, by entry id: "Lucy to Mina · 24 May".
     */
    data class Ready(
        val groups: List<ShownGroup>,
        val expanded: Set<String>,
        val scrollTo: String?,
        val sources: Map<String, String>,
    ) : WhosWhoUi
}

/**
 * The who's who page (#204), opened on [openOn] (the author of the entry it was opened from) when that person is
 * shown. A visit is one view model and its saved state: lines read since the previous visit stay marked new for the
 * whole visit, through rotation, even though the visit is recorded as soon as the page opens.
 */
class WhosWhoViewModel(
    private val stories: StoryRepository,
    private val users: UserRepository,
    private val openOn: String?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val ui: StateFlow<WhosWhoUi> = flow {
        val story = stories.story()
        val before = seenBefore()
        if (!saved.contains(KEY_SCROLL_TO)) {
            val shown = visibleWhosWho(story.whosWho, users.state.first().progress, before)
            // Never reveal someone by opening on them: only a person already shown is unfolded.
            val target = openOn?.takeIf { id -> shown.any { group -> group.people.any { it.id == id } } }
            saved[KEY_EXPANDED] = arrayListOf<String>().apply { target?.let(::add) }
            saved[KEY_SCROLL_TO] = target.orEmpty()
        }
        val scrollTo = saved.get<String>(KEY_SCROLL_TO)?.takeIf { it.isNotEmpty() }
        emitAll(
            combine(users.state, saved.getStateFlow(KEY_EXPANDED, arrayListOf<String>())) { user, expanded ->
                val groups = visibleWhosWho(story.whosWho, user.progress, before)
                WhosWhoUi.Ready(groups, expanded.toSet(), scrollTo, sources(story, groups))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WhosWhoUi.Loading)

    /** Folds or unfolds [personId]'s profile. */
    fun toggle(personId: String) {
        val now = saved.get<ArrayList<String>>(KEY_EXPANDED).orEmpty()
        saved[KEY_EXPANDED] = ArrayList(if (personId in now) now - personId else now + personId)
    }

    /** When the page was last opened before this visit (null: never), recording this visit the first time it's asked. */
    private suspend fun seenBefore(): Long? {
        if (saved.contains(KEY_SEEN_BEFORE)) return saved.get<Long>(KEY_SEEN_BEFORE)?.takeIf { it >= 0 }
        val before = users.state.first().whosWhoSeenAt
        saved[KEY_SEEN_BEFORE] = before ?: NEVER
        users.markWhosWhoSeen()
        return before
    }

    private fun sources(story: Story, groups: List<ShownGroup>): Map<String, String> =
        groups.flatMap { g -> g.people.flatMap { p -> p.lines.map { it.entryId } } }.distinct().mapNotNull { id ->
            story.entry(id)?.let { e ->
                fun short(c: String) = story.characters[c]?.short ?: c
                val who = short(e.author) + (e.recipient?.let { " to ${short(it)}" } ?: "")
                id to if (e.undated) who else "$who · ${e.date.format(Dates.DAY)}"
            }
        }.toMap()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_SEEN_BEFORE = "seenBefore"
        const val KEY_EXPANDED = "expanded"
        const val KEY_SCROLL_TO = "scrollTo"
        const val NEVER = -1L
    }
}
