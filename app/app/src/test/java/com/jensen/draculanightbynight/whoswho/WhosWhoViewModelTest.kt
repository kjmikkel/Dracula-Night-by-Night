package com.jensen.draculanightbynight.whoswho

import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.content.Tie
import com.jensen.draculanightbynight.content.WhosWho
import com.jensen.draculanightbynight.content.WhosWhoGroup
import com.jensen.draculanightbynight.content.WhosWhoPerson
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The who's who page's state: where it opens, and what counts as new during one visit (#204). */
@OptIn(ExperimentalCoroutinesApi::class)
class WhosWhoViewModelTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private val story = Story(
        TestStory.small.entries,
        TestStory.characters,
        WhosWho(
            groups = listOf(WhosWhoGroup("jm", listOf(Tie("may03", "Jonathan and Mina")))),
            people = listOf(
                WhosWhoPerson("jonathan_harker", listOf(Tie("may03", "jm")), listOf(Tie("may03", "Jonathan Harker")), listOf(Tie("may03", "A solicitor."))),
                WhosWhoPerson("mina_harker", listOf(Tie("may09", "jm")), listOf(Tie("may09", "Mina Murray")), listOf(Tie("may09", "Jonathan's fiancée."))),
            ),
        ),
    )

    private class MemoryStore(initial: UserState) : DataStore<UserState> {
        val state = MutableStateFlow(initial)
        override val data: Flow<UserState> = state
        override suspend fun updateData(transform: suspend (t: UserState) -> UserState): UserState =
            transform(state.value).also { state.value = it }
    }

    private fun store(seenAt: Long? = null, vararg read: Pair<String, Long>) =
        MemoryStore(UserState(onboarded = true, whosWhoSeenAt = seenAt, progress = read.associate { (id, at) -> id to EntryProgress(readAtMillis = at) }))

    private fun users(store: MemoryStore) = UserRepository(store, TimeSource(Clock.fixed(now, ZoneOffset.UTC)) { ZoneOffset.UTC })

    private fun viewModel(store: MemoryStore, openOn: String?, saved: SavedStateHandle = SavedStateHandle()) =
        WhosWhoViewModel(StoryRepository { story }, users(store), openOn, saved)

    private suspend fun WhosWhoViewModel.ready(): WhosWhoUi.Ready = ui.first { it is WhosWhoUi.Ready } as WhosWhoUi.Ready

    @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun resetMain() = Dispatchers.resetMain()

    @Test
    fun opensOnTheAuthorWhenTheyAreShown() = runTest {
        val ready = viewModel(store(null, "may03" to 1), openOn = "jonathan_harker").ready()
        assertEquals(setOf("jonathan_harker"), ready.expanded)
        assertEquals("jonathan_harker", ready.scrollTo)
    }

    @Test
    fun doesNotRevealSomeoneNotYetShown() = runTest {
        val ready = viewModel(store(null, "may03" to 1), openOn = "mina_harker").ready()
        assertEquals(emptySet<String>(), ready.expanded)
        assertNull(ready.scrollTo)
        assertEquals(listOf("jonathan_harker"), ready.groups.flatMap { g -> g.people.map { it.id } })
    }

    @Test
    fun anAuthorWithoutAProfileOpensAtTheTop() = runTest {
        val ready = viewModel(store(null, "may03" to 1), openOn = "dailygraph").ready()
        assertNull(ready.scrollTo)
        assertEquals(emptySet<String>(), ready.expanded)
    }

    @Test
    fun openingRecordsTheVisit() = runTest {
        val store = store(null, "may03" to 1)
        viewModel(store, openOn = null).ready()
        assertEquals(now.toEpochMilli(), store.state.value.whosWhoSeenAt)
    }

    @Test
    fun marksStayForTheWholeVisit() = runTest {
        val store = store(100, "may03" to 200)
        val saved = SavedStateHandle()
        assertTrue(viewModel(store, openOn = null, saved).ready().groups.single().people.single().hasNew)
        // Rotation: a new view model on the same saved state, after the visit was recorded.
        assertEquals(now.toEpochMilli(), store.state.value.whosWhoSeenAt)
        assertTrue(viewModel(store, openOn = null, saved).ready().groups.single().people.single().hasNew)
    }

    @Test
    fun togglingFoldsAndUnfolds() = runTest {
        val vm = viewModel(store(null, "may03" to 1), openOn = null)
        assertEquals(emptySet<String>(), vm.ready().expanded)
        vm.toggle("jonathan_harker")
        assertEquals(setOf("jonathan_harker"), vm.ready().expanded)
        vm.toggle("jonathan_harker")
        assertEquals(emptySet<String>(), vm.ready().expanded)
    }

    @Test
    fun eachLineSaysWhereItCameFrom() = runTest {
        val ready = viewModel(store(null, "may03" to 1, "may09" to 1), openOn = null).ready()
        assertEquals("Jonathan · 3 May", ready.sources["may03"])
        assertEquals("Mina · 9 May", ready.sources["may09"])
    }
}
