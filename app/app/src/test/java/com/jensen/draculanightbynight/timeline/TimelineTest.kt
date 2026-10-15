package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TimelineTest {
    private val utc = ZoneOffset.UTC
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))

    private fun timeline(now: String, user: UserState = UserState(), settings: ReadingSettings = tempo) =
        buildTimeline(TestStory.small, user, settings, Instant.parse(now), utc)

    @Test
    fun beforeTheFirstEntryNothingIsUnlockedAndTheTeaserNamesIt() {
        val t = timeline("2026-10-14T21:59:00Z")
        assertTrue(t.days.isEmpty())
        assertEquals(NextTeaser("Jonathan Harker", DocumentType.DIARY, ZonedDateTime.parse("2026-10-14T22:00Z")), t.next)
    }

    @Test
    fun unlockedEntriesAreGroupedByStoryDay() {
        val t = timeline("2026-10-16T12:00:00Z") // day 3 at noon: 3 May, 4 May, 5 May 10:00
        assertEquals(listOf(LocalDate.of(1893, 5, 3), LocalDate.of(1893, 5, 4), LocalDate.of(1893, 5, 5)), t.days.map { it.date })
        assertEquals(3, t.unlockedCount)
        assertEquals(4, t.total)
        assertEquals("Mina Harker", t.next?.authorName)
    }

    @Test
    fun readFreelyUnlocksEverythingAndHasNoTeaser() {
        val t = timeline("2000-01-01T00:00:00Z", settings = ReadingSettings(ReadingMode.Free))
        assertEquals(4, t.unlockedCount)
        assertNull(t.next)
    }

    @Test
    fun rowsShowUnreadAndBookmarkStateAndTheFirstUnread() {
        val user = UserState(progress = mapOf("may03" to EntryProgress(readAtMillis = 1, bookmarked = true)))
        val t = timeline("2026-10-16T12:00:00Z", user)
        val first = t.days.first().rows.single()
        assertFalse(first.unread)
        assertTrue(first.bookmarked)
        assertEquals("may04", t.firstUnreadId)
        assertEquals("22:00", first.timeLabel)
    }
}
