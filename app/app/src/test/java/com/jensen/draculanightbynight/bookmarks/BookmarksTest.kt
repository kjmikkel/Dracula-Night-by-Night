package com.jensen.draculanightbynight.bookmarks

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.user.BookmarkSort
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BookmarksTest {
    private val utc = ZoneOffset.UTC
    private val free = ReadingSettings(ReadingMode.Free)
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val later = Instant.parse("2026-10-20T00:00:00Z")

    private fun marked(vararg at: Pair<String, Long?>) =
        UserState(progress = at.associate { (id, millis) -> id to EntryProgress(readAtMillis = 1, bookmarked = true, bookmarkedAtMillis = millis) })

    private fun ids(user: UserState, sort: BookmarkSort) = bookmarks(TestStory.small, user, free, later, utc, sort).map { it.row.id }

    @Test
    fun storyOrderFollowsTheStory() {
        assertEquals(listOf("may03", "may09"), ids(marked("may09" to 5L, "may03" to 9L), BookmarkSort.STORY))
    }

    @Test
    fun recentOrderPutsTheNewestFirst() {
        assertEquals(listOf("may03", "may09"), ids(marked("may09" to 5L, "may03" to 9L), BookmarkSort.RECENT))
    }

    @Test
    fun bookmarksWithoutATimeStampComeLastInStoryOrder() {
        assertEquals(listOf("may05", "may03", "may04"), ids(marked("may04" to null, "may05" to 7L, "may03" to null), BookmarkSort.RECENT))
    }

    @Test
    fun aLockedBookmarkIsListedWithWhenItArrives() {
        // After Start over: bookmarked, no longer read; on day 2 at noon 5 May arrives on 16 October at 10:00.
        val user = UserState(progress = mapOf("may05" to EntryProgress(bookmarked = true, bookmarkedAtMillis = 3)))
        val row = bookmarks(TestStory.small, user, tempo, Instant.parse("2026-10-15T12:00:00Z"), utc, BookmarkSort.STORY).single()
        assertEquals(ZonedDateTime.parse("2026-10-16T10:00Z"), row.arrivesAt)
        assertEquals(Instant.ofEpochMilli(3), row.bookmarkedAt)
        assertEquals(LocalDate.of(1893, 5, 5), row.date)
    }

    @Test
    fun aReadableBookmarkHasNoArrivalTime() {
        val row = bookmarks(TestStory.small, marked("may03" to 1L), free, later, utc, BookmarkSort.STORY).single()
        assertNull(row.arrivesAt)
        assertTrue(row.row.bookmarked)
    }

    @Test
    fun bookmarksOfEntriesNoLongerInTheStoryAreIgnored() {
        assertEquals(listOf("may03"), ids(marked("gone" to 1L, "may03" to 2L), BookmarkSort.STORY))
    }

    @Test
    fun aLockedBookmarkIsAnnouncedWithItsEntryAndWhenItArrives() {
        val user = UserState(progress = mapOf("may05" to EntryProgress(bookmarked = true)))
        val row = bookmarks(TestStory.small, user, tempo, Instant.parse("2026-10-15T12:00:00Z"), utc, BookmarkSort.STORY).single()
        assertEquals("Locked, arrives again Friday 16 October, 10:00. 5 May, 10:00, Jonathan Harker, Journal", lockedDescription(row))
    }

    @Test
    fun aReadableBookmarkNeedsNoLockedDescription() {
        assertNull(lockedDescription(bookmarks(TestStory.small, marked("may03" to 1L), free, later, utc, BookmarkSort.STORY).single()))
    }
}
