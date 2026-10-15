package com.jensen.draculanightbynight.user

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UserRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-14T18:00:00Z"))

    private fun file() = File(tmp.root, "user.json")

    private fun TestScope.repository(scope: CoroutineScope = backgroundScope) = UserRepository(
        DataStoreFactory.create(
            serializer = UserStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserState() },
            scope = scope,
            produceFile = ::file,
        ),
        clock,
    )

    @Test
    fun aNewReaderStartsInSameTempoFromToday() = runTest {
        val repo = repository()
        repo.ensureMode()
        assertEquals(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)), repo.state.first().readingMode)
    }

    @Test
    fun ensureModeKeepsAnExistingChoice() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Free)
        repo.ensureMode()
        assertEquals(ReadingMode.Free, repo.state.first().readingMode)
    }

    @Test
    fun changingModeResetsSkipAhead() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Calendar(2026))
        assertEquals(ReadingMode.Calendar(2026), repo.state.first().settings?.mode)
        assertEquals(0L, repo.state.first().skipAheadMinutes)
    }

    @Test
    fun markReadKeepsTheFirstReadTime() = runTest {
        val repo = repository()
        repo.markRead("a")
        clock.now = Instant.parse("2026-10-15T09:00:00Z")
        repo.markRead("a")
        assertEquals(Instant.parse("2026-10-14T18:00:00Z").toEpochMilli(), repo.state.first().progressOf("a").readAtMillis)
    }

    @Test
    fun positionAndBookmarkAreStoredPerEntry() = runTest {
        val repo = repository()
        repo.savePosition("a", 12, 340)
        repo.toggleBookmark("a")
        val a = repo.state.first().progressOf("a")
        assertEquals(12 to 340, a.blockIndex to a.blockOffset)
        assertTrue(a.bookmarked)
        assertFalse(repo.state.first().progressOf("b").bookmarked)
    }

    @Test
    fun aCorruptFileStartsOverInsteadOfCrashing() = runTest {
        file().writeText("{ not json")
        assertNull(repository().state.first().readingMode)
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() = runTest {
        file().writeText("""{"mode": "free", "fontScale": 1.2, "progress": {"gone-id": {"bookmarked": true}}}""")
        val state = repository().state.first()
        assertEquals(ReadingMode.Free, state.readingMode)
        assertTrue(state.progressOf("gone-id").bookmarked)
    }
}
