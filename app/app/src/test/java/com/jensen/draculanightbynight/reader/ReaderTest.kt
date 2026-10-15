package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReaderTest {
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val dayThreeNoon = Instant.parse("2026-10-16T12:00:00Z") // 3, 4 and 5 May unlocked

    private fun reader(id: String, user: UserState = UserState()) =
        buildReader(TestStory.small, user, tempo, dayThreeNoon, ZoneOffset.UTC, id)

    @Test
    fun aLockedOrUnknownEntryCannotBeOpened() {
        assertNull(reader("may09"))
        assertNull(reader("nope"))
    }

    @Test
    fun previousAndNextStopAtTheLockedFrontier() {
        val middle = reader("may04")!!
        assertEquals("may03" to "may05", middle.previousId to middle.nextId)
        val last = reader("may05")!!
        assertNull(last.nextId)
    }

    @Test
    fun resumesWhereTheReaderLeftOff() {
        val user = UserState(progress = mapOf("may04" to EntryProgress(blockIndex = 0, blockOffset = 120, bookmarked = true)))
        val r = reader("may04", user)!!
        assertEquals(0 to 120, r.startBlock to r.startOffset)
        assertTrue(r.bookmarked)
    }

    @Test
    fun aSavedPositionPastTheEndIsClamped() {
        val user = UserState(progress = mapOf("may04" to EntryProgress(blockIndex = 99)))
        assertEquals(0, reader("may04", user)!!.startBlock)
    }

    @Test
    fun phonographDiariesShareTheDiaryStyle() {
        assertEquals(HeaderStyle.DIARY, headerStyle(DocumentType.PHONOGRAPH))
        assertEquals(HeaderStyle.TELEGRAM, headerStyle(DocumentType.TELEGRAM))
    }
}
