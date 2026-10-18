package com.jensen.draculanightbynight.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

@Serializable
private data class SpanJson(val text: String, val italic: Boolean)

@Serializable
private data class BlockJson(val role: String, val spans: List<SpanJson>, val repeats: List<Int>? = null)

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
    val headerNamesParties: Boolean = false,
    val undated: Boolean = false,
    val body: List<BlockJson>,
)

@Serializable
private data class CharacterJson(val id: String, val name: String, val short: String)

@Serializable
private data class TextTieJson(val from: String, val text: String)

@Serializable
private data class GroupTieJson(val from: String, val id: String)

@Serializable
private data class GroupJson(val id: String, val names: List<TextTieJson>)

@Serializable
private data class PersonJson(val id: String, val groups: List<GroupTieJson>, val names: List<TextTieJson>, val lines: List<TextTieJson>)

@Serializable
private data class WhosWhoJson(val groups: List<GroupJson>, val people: List<PersonJson>)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Decodes data/out/entries.json, characters.json and whos_who.json (the dataset contract) into a [Story]. Without a
 * who's who, the page is simply empty.
 */
fun decodeStory(entriesJson: String, charactersJson: String, whosWhoJson: String? = null): Story {
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
            body = e.body.map { b ->
                Block(BlockRole.valueOf(b.role.uppercase()), b.spans.map { Span(it.text, it.italic) }, b.repeats?.let { (from, to) -> from until to })
            },
            headerNamesParties = e.headerNamesParties,
            undated = e.undated,
        )
    }
    return Story(entries, characters, whosWhoJson?.let(::decodeWhosWho) ?: WhosWho.EMPTY)
}

private fun decodeWhosWho(text: String): WhosWho {
    val data = json.decodeFromString<WhosWhoJson>(text)
    fun List<TextTieJson>.ties() = map { Tie(it.from, it.text) }
    return WhosWho(
        groups = data.groups.map { WhosWhoGroup(it.id, it.names.ties()) },
        people = data.people.map { p -> WhosWhoPerson(p.id, p.groups.map { Tie(it.from, it.id) }, p.names.ties(), p.lines.ties()) },
    )
}
