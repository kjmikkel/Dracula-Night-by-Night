package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry

/**
 * The timeline filter: chips in one group combine with OR, the two groups with AND, and an empty group lets
 * everything through. It only changes what the list shows, never the schedule.
 */
data class EntryFilter(val voices: Set<Voice> = emptySet(), val types: Set<DocumentType> = emptySet()) {
    val isActive: Boolean get() = voices.isNotEmpty() || types.isNotEmpty()

    val activeCount: Int get() = voices.size + types.size

    fun matches(entry: Entry): Boolean =
        (voices.isEmpty() || voiceOf(entry) in voices) && (types.isEmpty() || entry.type in types)

    /** Every choice, voices then types, each in chip order. */
    private val choices: List<String>
        get() = Voice.entries.filter { it in voices }.map { it.label } +
            DocumentType.entries.filter { it in types }.map(::typeChipLabel)

    /** Short, for the reader's switch: "Jonathan", "Jonathan +1". */
    val label: String
        get() = choices.firstOrNull()?.let { first -> if (choices.size > 1) "$first +${choices.size - 1}" else first }.orEmpty()

    /** Every choice, for the Filtered line: "Jonathan, Mina, Letters". */
    val summary: String get() = choices.joinToString(", ")
}

fun typeChipLabel(type: DocumentType): String = when (type) {
    DocumentType.DIARY -> "Diaries"
    DocumentType.PHONOGRAPH -> "Phonograph"
    DocumentType.LETTER -> "Letters"
    DocumentType.TELEGRAM -> "Telegrams"
    DocumentType.NEWSPAPER -> "Newspapers"
    DocumentType.MEMO -> "Memos"
    DocumentType.LOG -> "Logs"
}

/** What the saved state stores for a document type. */
val DocumentType.key: String get() = name.lowercase()

fun documentTypeFromKey(key: String): DocumentType? = DocumentType.entries.firstOrNull { it.key == key }
