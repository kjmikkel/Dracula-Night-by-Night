package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Tie
import com.jensen.draculanightbynight.content.WhosWho
import com.jensen.draculanightbynight.content.WhosWhoGroup
import com.jensen.draculanightbynight.content.WhosWhoPerson
import com.jensen.draculanightbynight.user.EntryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the who's who may show: only what the reader has read, entry by entry (#204). */
class WhosWhoViewTest {
    private val arthur = WhosWhoPerson(
        id = "arthur",
        groups = listOf(Tie("a", "lucy"), Tie("c", "band")),
        names = listOf(Tie("a", "Arthur Holmwood"), Tie("c", "Lord Godalming")),
        lines = listOf(Tie("a", "friend"), Tie("b", "fiancé")),
    )
    private val whosWho = WhosWho(
        groups = listOf(
            WhosWhoGroup("lucy", listOf(Tie("a", "Lucy and her friends"), Tie("b", "Lucy and her suitors"))),
            WhosWhoGroup("band", listOf(Tie("c", "The band"))),
        ),
        people = listOf(arthur),
    )

    private fun read(vararg ids: String, at: Long = 1) = ids.associateWith { EntryProgress(readAtMillis = at) }

    @Test
    fun nobodyBeforeTheirFirstLineIsRead() {
        assertEquals(emptyList<ShownGroup>(), visibleWhosWho(whosWho, emptyMap(), seenAtMillis = null))
        // Bookmarked but never read counts as not read.
        assertEquals(emptyList<ShownGroup>(), visibleWhosWho(whosWho, mapOf("a" to EntryProgress(bookmarked = true)), null))
    }

    @Test
    fun eachLineChecksOnlyItsOwnEntry() {
        val shown = visibleWhosWho(whosWho, read("b"), null)
        assertEquals(listOf("lucy"), shown.map { it.id })
        val person = shown.single().people.single()
        assertEquals(listOf("fiancé"), person.lines.map { it.text })
        assertEquals("Arthur Holmwood", person.name)
    }

    @Test
    fun latestReadNameAndGroupWin() {
        val shown = visibleWhosWho(whosWho, read("a", "c"), null)
        assertEquals(listOf("band"), shown.map { it.id })
        assertEquals("The band", shown.single().name)
        val person = shown.single().people.single()
        assertEquals("Lord Godalming", person.name)
        assertEquals(listOf("friend"), person.lines.map { it.text })
    }

    @Test
    fun groupNameGrows() {
        assertEquals("Lucy and her friends", visibleWhosWho(whosWho, read("a"), null).single().name)
        assertEquals("Lucy and her suitors", visibleWhosWho(whosWho, read("a", "b"), null).single().name)
    }

    @Test
    fun linesKeepStoryOrderAndSayWhereTheyCameFrom() {
        val lines = visibleWhosWho(whosWho, read("b") + read("a", at = 5), null).single().people.single().lines
        assertEquals(listOf("friend" to "a", "fiancé" to "b"), lines.map { it.text to it.entryId })
    }

    @Test
    fun firstVisitMarksNothingNew() {
        val person = visibleWhosWho(whosWho, read("a", "b", at = 200), seenAtMillis = null).single().people.single()
        assertTrue(person.lines.none { it.isNew })
        assertFalse(person.hasNew)
    }

    @Test
    fun linesReadAfterTheLastVisitAreNew() {
        val person = visibleWhosWho(whosWho, read("a", at = 50) + read("b", at = 200), seenAtMillis = 100).single().people.single()
        assertEquals(listOf(false, true), person.lines.map { it.isNew })
        assertTrue(person.hasNew)
    }
}
