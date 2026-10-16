package com.jensen.draculanightbynight.notify

import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.user.BookmarkSort
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleKeyTest {
    private val base = UserState(onboarded = true, progress = mapOf("may03" to EntryProgress(readAtMillis = 1)))
        .withMode(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))

    @Test
    fun displayFilterAndBaselineChangesDoNotReschedule() {
        val key = scheduleKey(base)
        listOf(
            base.copy(textSize = TextSize.LARGE),
            base.copy(theme = ThemeChoice.DARK),
            base.withFilter(EntryFilter(setOf(Voice.MINA))),
            base.copy(bookmarkSort = BookmarkSort.RECENT),
            base.copy(lastNotifiedAt = 42),
            base.updateProgress("may04") { it.copy(bookmarked = true) },
            base.updateProgress("may03") { it.copy(blockIndex = 7, blockOffset = 30) },
        ).forEach { assertEquals(key, scheduleKey(it)) }
    }

    @Test
    fun scheduleChangesDo() {
        val key = scheduleKey(base)
        listOf(
            base.copy(skipAheadMinutes = 90),
            base.withMode(ReadingMode.Free),
            base.copy(notify = false),
            base.updateProgress("may04") { it.copy(readAtMillis = 2) },
        ).forEach { assertNotEquals(key, scheduleKey(it)) }
    }
}
