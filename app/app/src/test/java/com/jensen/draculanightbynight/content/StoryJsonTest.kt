package com.jensen.draculanightbynight.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class StoryJsonTest {
    private val characters = """[{"id": "mina_harker", "name": "Mina Harker", "short": "Mina"}]"""

    private fun entry(id: String, order: Int, date: String, time: String) = """
        {"id": "$id", "order": $order, "chapter": 5, "type": "letter", "author": "mina_harker",
         "recipient": null, "date": "$date", "time": "$time", "timeEstimated": true, "place": null,
         "header": null, "body": [{"role": "note", "spans": [{"text": "(Kept.)", "italic": false}]},
                                  {"role": "para", "spans": [{"text": "9 May.", "italic": true},
                                                             {"text": "—My dearest\nLucy", "italic": false}]}],
         "futureField": 1}
    """

    @Test
    fun decodesEntriesAndIgnoresUnknownFields() {
        val story = decodeStory("[${entry("a", 0, "1893-05-09", "10:00")}]", characters)
        val e = story.entries.single()
        assertEquals(DocumentType.LETTER, e.type)
        assertEquals(LocalDate.of(1893, 5, 9), e.date)
        assertEquals(LocalTime.of(10, 0), e.time)
        assertEquals(BlockRole.PARA, e.body[1].role)
        assertEquals("9 May.—My dearest\nLucy", e.body[1].text)
        assertEquals("Mina Harker", story.nameOf("mina_harker"))
        assertNull(story.nameOf(null))
    }

    @Test
    fun previewSkipsNotesAndFlattensLineBreaks() {
        val story = decodeStory("[${entry("a", 0, "1893-05-09", "10:00")}]", characters)
        assertEquals("9 May.—My dearest Lucy", story.entries.single().preview)
    }

    @Test
    fun aLettersPreviewSkipsItsDatelineAndSalutation() {
        val letter = Entry(
            id = "l", order = 0, chapter = 5, type = DocumentType.LETTER, author = "mina_harker", recipient = null,
            date = LocalDate.of(1893, 5, 9), time = LocalTime.of(10, 0), timeEstimated = true, place = null, header = null,
            body = listOf(
                Block(BlockRole.DATELINE, listOf(Span("“9 May.", false))),
                Block(BlockRole.SALUTATION, listOf(Span("“My dearest Lucy,—", false))),
                Block(BlockRole.PARA, listOf(Span("“Forgive my long delay in writing.", false))),
            ),
        )
        assertEquals("“Forgive my long delay in writing.", letter.preview)
    }

    @Test
    fun entriesAreInStoryOrderNotBookOrder() {
        val json = "[${entry("late", 0, "1893-05-09", "21:00")}, ${entry("early", 1, "1893-05-09", "08:00")}," +
            " ${entry("next-day", 2, "1893-05-10", "07:00")}]"
        assertEquals(listOf("early", "late", "next-day"), decodeStory(json, characters).entries.map { it.id })
    }

    @Test
    fun decodesWhatRepeatsTheHeader() {
        val json = """[{"id": "a", "order": 0, "chapter": 5, "type": "letter", "author": "mina_harker",
            "date": "1893-05-09", "time": "10:00", "timeEstimated": true, "headerNamesParties": true,
            "body": [{"role": "para", "spans": [{"text": "9 May.", "italic": true}, {"text": "—Text", "italic": false}],
                      "repeats": [0, 7]}, {"role": "para", "spans": [{"text": "More", "italic": false}]}]}]"""
        val e = decodeStory(json, characters).entries.single()
        assertEquals(true, e.headerNamesParties)
        assertEquals(0 until 7, e.body[0].repeats)
        assertNull(e.body[1].repeats)
        // Data from before #177 has neither field.
        assertEquals(false, decodeStory("[${entry("b", 0, "1893-05-09", "10:00")}]", characters).entries.single().headerNamesParties)
    }

    @Test
    fun withoutRepeatCutsOnlyTheRepeatedCharacters() {
        val diary = Block(BlockRole.PARA, listOf(Span("4 May.", true), Span("—I found", false)), repeats = 0 until 7)
        assertEquals(listOf(Span("I found", false)), diary.withoutRepeat().spans)
        val telegram = Block(BlockRole.PARA, listOf(Span("“", false), Span("4 September.", true), Span("—Patient.”", false)), repeats = 1 until 14)
        assertEquals("“Patient.”", telegram.withoutRepeat().text)
        val letter = Block(BlockRole.DATELINE, listOf(Span("“", false), Span("24 May.", true)), repeats = 0 until 8)
        assertEquals(Block(BlockRole.DATELINE, emptyList()), letter.withoutRepeat())
        val plain = Block(BlockRole.PARA, listOf(Span("Text", false)))
        assertEquals(plain, plain.withoutRepeat())
    }

    @Test
    fun theRealDatasetDecodes() {
        val out = File("../../data/out")
        val story = decodeStory(File(out, "entries.json").readText(), File(out, "characters.json").readText())
        assertEquals(story.entries.size, story.entries.map { it.id }.toSet().size)
        assertEquals("bram_stoker", story.entries[0].author) // the preface, 3 May 00:00
        assertEquals(listOf(story.entries[0].id), story.entries.filter { it.undated }.map { it.id }) // only the preface (#190)
        assertEquals("1893-05-03-jonathan_harker-01", story.firstTimedEntry?.id) // what Welcome and Settings name (#199)
        assertEquals("1893-05-03-jonathan_harker-01", story.entries[1].id)
        assertEquals("NOTE", story.entries.last().header)
        assertEquals(true, story.entries.all { story.characters.containsKey(it.author) })
        assertEquals("I found that my landlord", story.entry("1893-05-04-jonathan_harker-01")!!.body[0].withoutRepeat().text.take(24))
    }
}
