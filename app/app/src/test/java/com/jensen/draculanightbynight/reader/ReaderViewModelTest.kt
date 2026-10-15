package com.jensen.draculanightbynight.reader

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.UserStateSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dayThreeNoon = ZonedDateTime.parse("2026-10-16T12:00Z") // 3, 4 and 5 May unlocked, 9 May locked

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.users() = UserRepository(
            DataStoreFactory.create(
                serializer = UserStateSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { UserState() },
                scope = backgroundScope,
                produceFile = { File(tmp.root, "user.json") },
            ),
            TimeSource(Clock.fixed(dayThreeNoon.toInstant(), ZoneOffset.UTC)) { ZoneOffset.UTC },
        )

    /** Opens [id] in a reader that started Same tempo on 14 October, and returns the reader's saved state. */
    private suspend fun TestScope.open(id: String): Pair<ReaderUi, UserState> {
        val users = users()
        users.setMode(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
        val viewModel = ReaderViewModel(StoryRepository { TestStory.small }, users, flowOf(dayThreeNoon), backgroundScope, id)
        val ui = viewModel.state.first { it != ReaderUi.Loading }
        advanceUntilIdle()
        return ui to users.state.first()
    }

    @Test
    fun aLockedEntryIsNotMarkedRead() = runTest {
        val (ui, user) = open("may09")
        assertEquals(ReaderUi.Locked, ui)
        assertNull(user.progressOf("may09").readAtMillis)
    }

    /** Runs the newest queued task first: the worst order a multi-threaded dispatcher could produce. */
    private class NewestFirstDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }
        /** Runs everything queued, newest first; true if anything ran. */
        fun runAll(): Boolean {
            val ran = tasks.isNotEmpty()
            while (tasks.isNotEmpty()) tasks.removeLast().run()
            return ran
        }
    }

    /** DataStore kept in memory: writes apply in the order they reach it, like the real one. */
    private class MemoryStore : DataStore<UserState> {
        private val state = MutableStateFlow(UserState())
        private val lock = Mutex()
        override val data: Flow<UserState> = state
        override suspend fun updateData(transform: suspend (t: UserState) -> UserState): UserState =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    @Test
    fun positionsAreSavedInTheOrderTheyWereMade() = runTest {
        val users = UserRepository(MemoryStore(), TimeSource(Clock.fixed(dayThreeNoon.toInstant(), ZoneOffset.UTC)) { ZoneOffset.UTC })
        users.setMode(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
        val dispatcher = NewestFirstDispatcher()
        val viewModel = ReaderViewModel(StoryRepository { TestStory.small }, users, flowOf(dayThreeNoon), CoroutineScope(dispatcher), "may04")
        viewModel.savePosition(1, 10) // e.g. the debounced save while scrolling
        viewModel.savePosition(2, 20) // the save as the screen closes
        while (dispatcher.runAll()) advanceUntilIdle()
        val saved = users.state.first().progressOf("may04")
        assertEquals(2 to 20, saved.blockIndex to saved.blockOffset)
    }

    @Test
    fun anUnlockedEntryIsMarkedReadWhenItOpens() = runTest {
        val (ui, user) = open("may04")
        assertEquals("may04", (ui as ReaderUi.Ready).reader.entry.id)
        assertNotNull(user.progressOf("may04").readAtMillis)
    }
}
