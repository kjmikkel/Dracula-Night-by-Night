package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryFilterTest {
    private val jonDiary = TestStory.entry("j", 5, 3, "10:00")
    private val minaLetter = TestStory.entry("m", 5, 4, "10:00", author = "mina_harker", type = DocumentType.LETTER)
    private val minaDiary = TestStory.entry("d", 5, 5, "10:00", author = "mina_harker")

    @Test
    fun anEmptyFilterMatchesEverythingAndIsInactive() {
        assertFalse(EntryFilter().isActive)
        assertTrue(listOf(jonDiary, minaLetter, minaDiary).all { EntryFilter().matches(it) })
    }

    @Test
    fun chipsInOneGroupCombineWithOr() {
        val f = EntryFilter(voices = setOf(Voice.JONATHAN, Voice.MINA))
        assertTrue(f.matches(jonDiary))
        assertTrue(f.matches(minaLetter))
    }

    @Test
    fun theTwoGroupsCombineWithAnd() {
        val f = EntryFilter(voices = setOf(Voice.MINA), types = setOf(DocumentType.LETTER))
        assertTrue(f.matches(minaLetter))
        assertFalse(f.matches(minaDiary))
        assertFalse(f.matches(jonDiary))
    }

    @Test
    fun theLabelNamesTheFirstChoiceAndCountsTheRest() {
        assertEquals("Jonathan", EntryFilter(voices = setOf(Voice.JONATHAN)).label)
        assertEquals("Jonathan +1", EntryFilter(voices = setOf(Voice.JONATHAN), types = setOf(DocumentType.LETTER)).label)
        assertEquals("Letters", EntryFilter(types = setOf(DocumentType.LETTER)).label)
        assertEquals(2, EntryFilter(voices = setOf(Voice.JONATHAN), types = setOf(DocumentType.LETTER)).activeCount)
    }

    @Test
    fun theSummaryListsEveryChoiceInChipOrder() {
        assertEquals("Jonathan, Mina, Letters", EntryFilter(setOf(Voice.MINA, Voice.JONATHAN), setOf(DocumentType.LETTER)).summary)
    }

    @Test
    fun documentTypeKeysRoundTrip() {
        DocumentType.entries.forEach { assertEquals(it, documentTypeFromKey(it.key)) }
        assertNull(documentTypeFromKey("postcard"))
    }
}
