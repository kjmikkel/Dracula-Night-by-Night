package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Entry

/**
 * The voices a reader can filter the timeline by: the six who carry the story, and Others for newspapers,
 * firms, one-off writers and any author a later dataset adds. [key] is what the saved state stores.
 */
enum class Voice(val key: String, val label: String) {
    JONATHAN("jonathan", "Jonathan"),
    MINA("mina", "Mina"),
    SEWARD("seward", "Seward"),
    LUCY("lucy", "Lucy"),
    VAN_HELSING("van_helsing", "Van Helsing"),
    DEMETER("demeter", "Demeter"),
    OTHERS("others", "Others"),
    ;

    companion object {
        fun fromKey(key: String): Voice? = entries.firstOrNull { it.key == key }
    }
}

private val voiceByAuthor = mapOf(
    "jonathan_harker" to Voice.JONATHAN,
    "mina_harker" to Voice.MINA,
    "john_seward" to Voice.SEWARD,
    "lucy_westenra" to Voice.LUCY,
    "van_helsing" to Voice.VAN_HELSING,
    "demeter_captain" to Voice.DEMETER,
)

/** The voice an entry is filed under, by its author. */
fun voiceOf(entry: Entry): Voice = voiceByAuthor[entry.author] ?: Voice.OTHERS
