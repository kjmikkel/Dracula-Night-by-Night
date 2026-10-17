package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Span
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
    fun aReadEntryCanBeOpenedBeforeItsTime() {
        val user = UserState(progress = mapOf("may09" to EntryProgress(readAtMillis = 1)))
        assertEquals("may09", reader("may09", user)!!.entry.id)
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
    fun aHalfScrolledHeaderResumesAtTheTopNotInsideTheFirstBlock() {
        // List item 0 is the header; scrolled 50 px into it is still the top of the entry.
        assertEquals(0 to 0, savedPosition(firstVisibleItem = 0, offset = 50))
    }

    @Test
    fun positionsInTheBodyRoundTrip() {
        assertEquals(3 to 120, savedPosition(firstVisibleItem = 4, offset = 120))
        assertEquals(4 to 120, listPosition(block = 3, offset = 120))
        assertEquals(0 to 0, listPosition(block = 0, offset = 0))
        assertEquals(1 to 40, listPosition(block = 0, offset = 40))
    }

    @Test
    fun phonographDiariesShareTheDiaryStyle() {
        assertEquals(HeaderStyle.DIARY, headerStyle(DocumentType.PHONOGRAPH))
        assertEquals(HeaderStyle.TELEGRAM, headerStyle(DocumentType.TELEGRAM))
    }

    @Test
    fun aNewspaperHeaderSplitsIntoMastheadAndHeadline() {
        val paper = TestStory.entry("n", 8, 8, "10:00", type = DocumentType.NEWSPAPER)
        assertEquals("THE DAILYGRAPH" to "A STORM AT WHITBY", mastheadOf(paper.copy(header = "THE DAILYGRAPH\nA STORM AT WHITBY")))
        assertEquals("Newspaper" to null, mastheadOf(paper.copy(header = null)))
        assertEquals("THE PALL MALL GAZETTE" to null, mastheadOf(paper.copy(header = "\nTHE PALL MALL GAZETTE\n")))
    }

    @Test
    fun onlyAParagraphThatFollowsAParagraphIsIndented() {
        // As in print (#179): the first paragraph, and one after a dateline, salutation, verse, note or break, starts flush.
        val roles = listOf(BlockRole.DATELINE, BlockRole.SALUTATION, BlockRole.PARA, BlockRole.PARA, BlockRole.VERSE,
            BlockRole.PARA, BlockRole.DIVIDER, BlockRole.PARA, BlockRole.PARA, BlockRole.SIGNOFF)
        val body = roles.map { Block(it, listOf(Span("x", false))) }
        assertEquals(listOf(3, 8), body.indices.filter { indentsFirstLine(body, it) })
        assertFalse(indentsFirstLine(body, -1))
        assertFalse(indentsFirstLine(body, body.size))
    }
}
