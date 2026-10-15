package com.jensen.draculanightbynight.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

@Serializable
private data class SpanJson(val text: String, val italic: Boolean)

@Serializable
private data class BlockJson(val role: String, val spans: List<SpanJson>)

@Serializable
private data class EntryJson(
    val id: String,
    val order: Int,
    val chapter: Int,
    val type: String,
    val author: String,
    val recipient: String? = null,
    val date: String,
    val time: String,
    val timeEstimated: Boolean,
    val place: String? = null,
    val header: String? = null,
    val body: List<BlockJson>,
)

@Serializable
private data class CharacterJson(val id: String, val name: String, val short: String)

private val json = Json { ignoreUnknownKeys = true }

/** Decodes data/out/entries.json and characters.json (the dataset contract) into a [Story]. */
fun decodeStory(entriesJson: String, charactersJson: String): Story {
    val characters = json.decodeFromString<List<CharacterJson>>(charactersJson)
        .associate { it.id to Character(it.id, it.name, it.short) }
    val entries = json.decodeFromString<List<EntryJson>>(entriesJson).map { e ->
        Entry(
            id = e.id,
            order = e.order,
            chapter = e.chapter,
            type = DocumentType.valueOf(e.type.uppercase()),
            author = e.author,
            recipient = e.recipient,
            date = LocalDate.parse(e.date),
            time = LocalTime.parse(e.time),
            timeEstimated = e.timeEstimated,
            place = e.place,
            header = e.header,
            body = e.body.map { b -> Block(BlockRole.valueOf(b.role.uppercase()), b.spans.map { Span(it.text, it.italic) }) },
        )
    }
    return Story(entries, characters)
}
