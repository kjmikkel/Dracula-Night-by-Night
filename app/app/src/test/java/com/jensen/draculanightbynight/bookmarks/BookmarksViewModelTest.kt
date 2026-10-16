package com.jensen.draculanightbynight.bookmarks

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.user.BookmarkSort
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.UserStateSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dayTwoNoon = ZonedDateTime.parse("2026-10-15T12:00Z") // Same tempo from 14 October: 3 May readable, 5 May arrives 16 Oct 10:00

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
        TimeSource(Clock.fixed(dayTwoNoon.toInstant(), ZoneOffset.UTC)) { ZoneOffset.UTC },
    )

    private fun viewModel(users: UserRepository) = BookmarksViewModel(StoryRepository { TestStory.small }, users, flowOf(dayTwoNoon))

    private suspend fun BookmarksViewModel.rows(where: (BookmarksUi.Ready) -> Boolean = { true }): List<String> =
        (state.first { it is BookmarksUi.Ready && where(it) } as BookmarksUi.Ready).rows.map { it.row.id }

    @Test
    fun removingAndUndoingKeepsThePlaceInRecentOrder() = runTest {
        val users = users()
        users.setMode(ReadingMode.Free)
        users.restoreBookmark("may03", 5)
        users.restoreBookmark("may05", 9)
        users.setBookmarkSort(BookmarkSort.RECENT)
        val vm = viewModel(users)
        assertEquals(listOf("may05", "may03"), vm.rows())
        val may03 = (vm.state.first { it is BookmarksUi.Ready } as BookmarksUi.Ready).rows.last()
        vm.remove(may03)
        assertEquals(listOf("may05"), vm.rows { it.rows.size == 1 })
        vm.undo()
        assertEquals(listOf("may05", "may03"), vm.rows { it.rows.size == 2 })
        assertEquals(5L, users.state.first().progressOf("may03").bookmarkedAtMillis)
    }

    @Test
    fun readNowSkipsToTheEntryAndOpensIt() = runTest {
        val users = users()
        users.setMode(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
        users.restoreBookmark("may05", 3) // kept by Start over, not read again yet
        val vm = viewModel(users)
        val row = (vm.state.first { it is BookmarksUi.Ready } as BookmarksUi.Ready).rows.single()
        assertEquals(Instant.parse("2026-10-16T10:00:00Z"), row.arrivesAt?.toInstant())
        assertEquals(2, vm.skipCount(row)) // 5 May, and 4 May (15 Oct 14:45) with it
        val opened = CompletableDeferred<String>()
        vm.readNow(row) { opened.complete(it) }
        assertEquals("may05", opened.await())
        assertEquals(22 * 60L, users.state.first().skipAheadMinutes)
    }

    @Test
    fun theSortChoiceIsSaved() = runTest {
        val users = users()
        users.setMode(ReadingMode.Free)
        val vm = viewModel(users)
        vm.setSort(BookmarkSort.RECENT)
        assertEquals(BookmarkSort.RECENT, users.state.first { it.bookmarkSort == BookmarkSort.RECENT }.bookmarkSort)
    }
}
