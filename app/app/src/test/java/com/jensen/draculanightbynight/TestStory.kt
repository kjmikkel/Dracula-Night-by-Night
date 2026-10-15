package com.jensen.draculanightbynight

import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.content.Character
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Span
import com.jensen.draculanightbynight.content.Story
import java.time.LocalDate
import java.time.LocalTime

/** Small hand-built stories for tests. */
object TestStory {
    fun entry(
        id: String,
        month: Int,
        day: Int,
        time: String,
        author: String = "jonathan_harker",
        type: DocumentType = DocumentType.DIARY,
        order: Int = 0,
        text: String = "Text of $id.",
    ) = Entry(
        id = id, order = order, chapter = 1, type = type, author = author, recipient = null,
        date = LocalDate.of(1893, month, day), time = LocalTime.parse(time), timeEstimated = true,
        place = null, header = null, body = listOf(Block(BlockRole.PARA, listOf(Span(text, false)))),
    )

    val characters = mapOf(
        "jonathan_harker" to Character("jonathan_harker", "Jonathan Harker", "Jonathan"),
        "mina_harker" to Character("mina_harker", "Mina Harker", "Mina"),
    )

    /** 3 May 22:00, 4 May 14:45, 5 May 10:00 (Jonathan), 9 May 10:00 (Mina's letter). */
    val small = Story(
        listOf(
            entry("may03", 5, 3, "22:00", order = 0),
            entry("may04", 5, 4, "14:45", order = 1),
            entry("may05", 5, 5, "10:00", order = 2),
            entry("may09", 5, 9, "10:00", author = "mina_harker", type = DocumentType.LETTER, order = 3),
        ),
        characters,
    )
}
