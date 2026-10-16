package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StartOverTest {
    private val today = LocalDate.of(2027, 4, 28)
    private val read = EntryProgress(readAtMillis = 1, blockIndex = 3, blockOffset = 40)

    private fun reader(mode: ReadingMode) = UserState(onboarded = true, textSize = TextSize.LARGE).withMode(mode).copy(
        skipAheadMinutes = 90,
        progress = mapOf("a" to read, "b" to read.copy(bookmarked = true)),
    )

    @Test
    fun progressIsClearedAndBookmarksKeptByDefault() {
        val state = reader(ReadingMode.Calendar(2026)).startedOver(removeBookmarks = false, today, tempoStart = today, nowMillis = 7)
        assertEquals(mapOf("b" to EntryProgress(bookmarked = true)), state.progress)
        assertEquals(0L, state.skipAheadMinutes)
        assertEquals(7L, state.lastNotifiedAt)
        assertEquals(TextSize.LARGE, state.textSize)
        assertTrue(state.onboarded)
    }

    @Test
    fun bookmarksGoTooWhenAsked() {
        val state = reader(ReadingMode.Free).startedOver(removeBookmarks = true, today, tempoStart = today, nowMillis = 7)
        assertEquals(emptyMap<String, EntryProgress>(), state.progress)
    }

    @Test
    fun calendarMovesToTheNewSeason() {
        val state = reader(ReadingMode.Calendar(2026)).startedOver(removeBookmarks = false, today, tempoStart = today, nowMillis = 7)
        assertEquals(ReadingMode.Calendar(2027), state.readingMode)
    }

    @Test
    fun sameTempoRestartsFromTheChosenDate() {
        val restart = LocalDate.of(2027, 5, 1)
        val state = reader(ReadingMode.SameTempo(LocalDate.of(2026, 10, 1)))
            .startedOver(removeBookmarks = false, today, tempoStart = restart, nowMillis = 7)
        assertEquals(ReadingMode.SameTempo(restart), state.readingMode)
    }

    @Test
    fun readFreelyStaysReadFreely() {
        val state = reader(ReadingMode.Free).startedOver(removeBookmarks = false, today, tempoStart = today, nowMillis = 7)
        assertEquals(ReadingMode.Free, state.readingMode)
    }

    @Test
    fun keptBookmarksKeepWhenTheyWereMade() {
        val s = UserState(progress = mapOf("may03" to EntryProgress(readAtMillis = 1, bookmarked = true, bookmarkedAtMillis = 42)))
        assertEquals(EntryProgress(bookmarked = true, bookmarkedAtMillis = 42), s.startedOver(false, today, today, 99).progressOf("may03"))
    }
}
