package com.jensen.draculanightbynight.content

/** A name, group id or line of the who's who, shown once the entry [from] is read (#204). */
data class Tie(val from: String, val value: String)

/** A group of people on the who's who page; its name grows with the story. */
data class WhosWhoGroup(val id: String, val names: List<Tie>)

/**
 * One person's profile. Every list is in story order. The person shows only once one of their [lines] does, with the
 * latest [names] and [groups] entry whose entry is read (or the first).
 */
data class WhosWhoPerson(val id: String, val groups: List<Tie>, val names: List<Tie>, val lines: List<Tie>)

/** The bundled who's who (data/out/whos_who.json): groups in page order, people in the order they're met. */
data class WhosWho(val groups: List<WhosWhoGroup>, val people: List<WhosWhoPerson>) {
    companion object {
        val EMPTY = WhosWho(emptyList(), emptyList())
    }
}
