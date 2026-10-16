package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReaderPagerTest {
    private val free = ReadingSettings(ReadingMode.Free)
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val later = Instant.parse("2026-10-20T00:00:00Z")
    private val minaFilter = UserState().withFilter(EntryFilter(setOf(Voice.MINA)))

    private fun pager(
        current: String,
        following: Following,
        user: UserState = minaFilter,
        settings: ReadingSettings = free,
        now: Instant = later,
        switchable: Boolean = true,
    ) = buildPager(TestStory.small, user, settings, now, ZoneOffset.UTC, current, following, switchable)

    @Test
    fun followingAllPagesThroughTheWholeStory() {
        val p = pager("may04", Following.ALL)!!
        assertEquals(listOf("may03", "may04", "may05", "may09"), p.pages)
        assertEquals(1, p.index)
        assertEquals("Mina", p.filterLabel)
    }

    @Test
    fun followingTheFilterPagesThroughMatchesOnly() {
        val p = pager("may09", Following.FILTER)!!
        assertEquals(listOf("may09"), p.pages)
        assertEquals(0, p.index)
        assertEquals(Following.FILTER, p.following)
    }

    @Test
    fun switchingToTheFilterKeepsANonMatchingCurrentPage() {
        val p = pager("may04", Following.FILTER)!!
        assertEquals(listOf("may04", "may09"), p.pages)
        assertEquals(0, p.index)
    }

    @Test
    fun withoutAFilterThereIsNoSwitch() {
        val p = pager("may04", Following.FILTER, user = UserState())!!
        assertNull(p.filterLabel)
        assertEquals(Following.ALL, p.following)
        assertEquals(listOf("may03", "may04", "may05", "may09"), p.pages)
    }

    @Test
    fun openedUnfilteredThereIsNoSwitchEvenWithASavedFilter() {
        val p = pager("may04", Following.ALL, switchable = false)!!
        assertNull(p.filterLabel)
        assertEquals(4, p.pages.size)
    }

    @Test
    fun theCurrentPageKeepsItsEntryWhenAnEarlierOneArrives() {
        // 5 May was read (once read, always readable); 4 May has not arrived yet at 23:00 on day one.
        val user = UserState(progress = mapOf("may05" to EntryProgress(readAtMillis = 1)))
        val before = pager("may05", Following.ALL, user, tempo, Instant.parse("2026-10-14T23:00:00Z"))!!
        assertEquals(listOf("may03", "may05"), before.pages)
        assertEquals(1, before.index)
        val after = pager("may05", Following.ALL, user, tempo, Instant.parse("2026-10-15T15:00:00Z"))!!
        assertEquals(listOf("may03", "may04", "may05"), after.pages)
        assertEquals("may05", after.pages[after.index])
    }

    @Test
    fun aLockedCurrentEntryHasNoPager() {
        assertNull(pager("may09", Following.ALL, UserState(), tempo, Instant.parse("2026-10-14T23:00:00Z")))
    }

    @Test
    fun previousAndNextStopAtTheLockedFrontier() {
        val p = pager("may05", Following.ALL, UserState(), tempo, Instant.parse("2026-10-16T12:00:00Z"))!!
        assertEquals(listOf("may03", "may04", "may05"), p.pages)
        assertEquals(p.pages.lastIndex, p.index)
    }
}
