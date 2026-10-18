package com.jensen.draculanightbynight.content

import java.time.LocalDate
import java.time.LocalTime

enum class DocumentType { DIARY, PHONOGRAPH, LETTER, TELEGRAM, NEWSPAPER, MEMO, LOG }

enum class BlockRole { NOTE, PARA, DATELINE, SALUTATION, SIGNOFF, VERSE, DIVIDER }

data class Span(val text: String, val italic: Boolean)

/** [repeats] holds the characters of [text] that only repeat the entry's header, an opening dateline (#177). */
data class Block(val role: BlockRole, val spans: List<Span>, val repeats: IntRange? = null) {
    val text: String get() = spans.joinToString("") { it.text }

    /** The block without what repeats the header. One that only repeated it comes back empty, keeping its place. */
    fun withoutRepeat(): Block {
        val cut = repeats ?: return this
        var at = 0
        val kept = spans.mapNotNull { span ->
            val start = at
            at += span.text.length
            span.text.filterIndexed { i, _ -> start + i !in cut }.takeIf { it.isNotEmpty() }?.let { Span(it, span.italic) }
        }
        return Block(role, kept)
    }
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
    /** The [header] names both the author and the recipient, as the source wrote them. */
    val headerNamesParties: Boolean = false,
    /** The book gives no date (the preface): [date] and [time] only place it in the schedule, and are never shown. */
    val undated: Boolean = false,
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

/** The book's "midnight": the dataset stores it as 23:59, so it stays on its own day (#190). */
private val MIDNIGHT_IN_BOOK: LocalTime = LocalTime.of(23, 59)

/** The entry's time as shown: "midnight" where the book says so, or null when the book gives no date (#190). */
val Entry.storyTime: String?
    get() = when {
        undated -> null
        time == MIDNIGHT_IN_BOOK && !timeEstimated -> "midnight"
        else -> time.format(Dates.TIME)
    }

/** "8 May, 22:00", or null when the book gives no date. */
val Entry.storyDayTime: String?
    get() = storyTime?.let { "${date.format(Dates.DAY)}, $it" }

/** The whole novel: entries in story order (in-story date, time, then position in the book). */
class Story(entries: List<Entry>, val characters: Map<String, Character>, val whosWho: WhosWho = WhosWho.EMPTY) {
    val entries: List<Entry> = entries.sortedWith(compareBy<Entry>({ it.date }, { it.time }, { it.order }))
    private val byId = this.entries.associateBy { it.id }

    fun entry(id: String): Entry? = byId[id]

    fun nameOf(characterId: String?): String? = characterId?.let { characters[it]?.name ?: it }

    /** The story's first entry with a date and time of its own: Harker's 3 May, after the undated preface (#199). */
    val firstTimedEntry: Entry? get() = entries.firstOrNull { !it.undated }
}
