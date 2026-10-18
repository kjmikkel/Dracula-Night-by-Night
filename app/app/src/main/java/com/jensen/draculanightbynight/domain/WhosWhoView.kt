package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Tie
import com.jensen.draculanightbynight.content.WhosWho
import com.jensen.draculanightbynight.user.EntryProgress

/** A line of a profile, from entry [entryId]; [isNew] when read since the page was last opened. */
data class ShownLine(val text: String, val entryId: String, val isNew: Boolean)

data class ShownPerson(val id: String, val name: String, val lines: List<ShownLine>) {
    val hasNew: Boolean get() = lines.any { it.isNew }
}

data class ShownGroup(val id: String, val name: String, val people: List<ShownPerson>)

/**
 * The part of the who's who the reader may see (#204). Every line, name, group name and group move checks only its own
 * entry, so jumping ahead in Free mode never shows what a skipped entry said. A name or group falls back to the
 * person's first (the pipeline checks it comes no later than their earliest line). People show once a line does, and
 * groups once a person does. [seenAtMillis] is when the page was last opened; null (never) marks nothing new.
 */
fun visibleWhosWho(ww: WhosWho, progress: Map<String, EntryProgress>, seenAtMillis: Long?): List<ShownGroup> {
    fun readAt(entryId: String): Long? = progress[entryId]?.readAtMillis
    fun List<Tie>.latest(): String? = lastOrNull { readAt(it.from) != null }?.value ?: firstOrNull()?.value

    val byGroup = ww.people.mapNotNull { person ->
        val lines = person.lines.mapNotNull { line ->
            readAt(line.from)?.let { at -> ShownLine(line.value, line.from, isNew = seenAtMillis != null && at > seenAtMillis) }
        }
        val name = person.names.latest()
        val group = person.groups.latest()
        if (lines.isEmpty() || name == null || group == null) null else group to ShownPerson(person.id, name, lines)
    }.groupBy({ it.first }, { it.second })

    return ww.groups.mapNotNull { group ->
        val people = byGroup[group.id] ?: return@mapNotNull null
        ShownGroup(group.id, group.names.latest() ?: group.id, people)
    }
}
