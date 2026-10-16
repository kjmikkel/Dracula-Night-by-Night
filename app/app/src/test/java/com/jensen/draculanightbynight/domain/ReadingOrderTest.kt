package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReadingOrderTest {
    private val free = ReadingSettings(ReadingMode.Free)
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val mina = EntryFilter(setOf(Voice.MINA))

    private fun ids(
        settings: ReadingSettings = free,
        now: String = "2026-10-20T00:00:00Z",
        user: UserState = UserState(),
        filter: EntryFilter? = null,
        keep: String? = null,
    ) = readingOrder(TestStory.small, user, settings, Instant.parse(now), ZoneOffset.UTC, filter, keep).map { it.id }

    @Test
    fun withoutAFilterItIsEveryReadableEntryInStoryOrder() {
        assertEquals(listOf("may03", "may04", "may05", "may09"), ids())
    }

    @Test
    fun aFilterKeepsOnlyMatchingEntries() {
        assertEquals(listOf("may09"), ids(filter = mina))
    }

    @Test
    fun keepAddsTheCurrentEntryInItsStoryPosition() {
        assertEquals(listOf("may04", "may09"), ids(filter = mina, keep = "may04"))
    }

    @Test
    fun lockedEntriesAreLeftOutAndReadOnesStay() {
        val user = UserState(progress = mapOf("may05" to EntryProgress(readAtMillis = 1)))
        assertEquals(listOf("may03", "may04", "may05"), ids(tempo, "2026-10-15T15:00:00Z", user))
    }

    @Test
    fun keepNeverAddsALockedEntry() {
        assertEquals(listOf("may03"), ids(tempo, "2026-10-14T23:00:00Z", keep = "may09"))
    }
}
