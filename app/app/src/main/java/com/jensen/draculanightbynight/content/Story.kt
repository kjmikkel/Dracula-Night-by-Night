package com.jensen.draculanightbynight.content

import java.time.LocalDate
import java.time.LocalTime

enum class DocumentType { DIARY, PHONOGRAPH, LETTER, TELEGRAM, NEWSPAPER, MEMO, LOG }

enum class BlockRole { NOTE, PARA, DATELINE, SALUTATION, SIGNOFF, VERSE }

data class Span(val text: String, val italic: Boolean)

data class Block(val role: BlockRole, val spans: List<Span>) {
    val text: String get() = spans.joinToString("") { it.text }
}

data class Character(val id: String, val name: String, val short: String)

data class Entry(
    val id: String,
    val order: Int,
    val chapter: Int,
    val type: DocumentType,
    val author: String,
    val recipient: String?,
    val date: LocalDate,
    val time: LocalTime,
    val timeEstimated: Boolean,
    val place: String?,
    val header: String?,
    val body: List<Block>,
) {
    /** First line of real text, for list rows: skips notes, datelines and salutations when it can. */
    val preview: String
        get() = (body.firstOrNull { it.role == BlockRole.PARA || it.role == BlockRole.VERSE }
            ?: body.firstOrNull { it.role != BlockRole.NOTE } ?: body.firstOrNull())
            ?.text?.replace('\n', ' ')?.take(PREVIEW_LENGTH).orEmpty()

    private companion object {
        const val PREVIEW_LENGTH = 140
    }
}

/** The whole novel: entries in story order (in-story date, time, then position in the book). */
class Story(entries: List<Entry>, val characters: Map<String, Character>) {
    val entries: List<Entry> = entries.sortedWith(compareBy<Entry>({ it.date }, { it.time }, { it.order }))
    private val byId = this.entries.associateBy { it.id }

    fun entry(id: String): Entry? = byId[id]

    fun nameOf(characterId: String?): String? = characterId?.let { characters[it]?.name ?: it }
}
