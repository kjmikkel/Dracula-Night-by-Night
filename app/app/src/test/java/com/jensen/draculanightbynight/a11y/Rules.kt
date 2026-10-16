package com.jensen.draculanightbynight.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** What a screen should expose beyond the general rules: its headings, toggles, option groups and spoken words. */
data class Expectations(
    val headings: List<String> = emptyList(),
    /** Visible texts of controls that switch something on and off. */
    val toggles: List<String> = emptyList(),
    /** Option labels that together form one single-choice group. */
    val groups: List<List<String>> = emptyList(),
    /** (text fragment that finds a node, word its spoken label must contain). */
    val words: List<Pair<String, String>> = emptyList(),
    /** Test tag of the Tonight card, whose TalkBack stops are counted. */
    val cardTag: String? = null,
)

/** One rule broken by one node, named by what TalkBack would read, never by position. */
data class Finding(val rule: String, val node: String)

private const val MAX_CARD_STOPS = 3

/**
 * Every rule broken on one screen. [merged] is what TalkBack walks; [unmerged] is the same tree before merging,
 * so a control's own description can be told apart from its children's text.
 */
fun checkRules(merged: SemanticsNode, unmerged: SemanticsNode, expect: Expectations, density: Density): List<Finding> {
    val nodes = merged.descendants()
    val own = (listOf(unmerged) + unmerged.descendants()).associateBy { it.id }
    val clickables = nodes.filter { it.isClickable() }
    val minTarget = with(density) { 48.dp.toPx() } - 0.5f
    return buildList {
        for (n in clickables) {
            if (n.label().isBlank()) add(Finding("label", "unlabelled in ${n.ancestors().firstOrNull { it.label().isNotBlank() }?.name() ?: "screen"}"))
            if (n.role() == null && n.ancestors().none { it.config.contains(SemanticsProperties.CollectionInfo) }) add(Finding("role", n.name()))
            // The visible size, as ATF and the spike measured it: Compose widens a small control's touch area, but not
            // what a screen reader outlines or what a sighted user aims at.
            if (n.boundsInRoot.width < minTarget || n.boundsInRoot.height < minTarget) add(Finding("target", n.name()))
            val ownDescription = own[n.id]?.descriptions().orEmpty()
            if (ownDescription.isNotEmpty() && n.texts().isNotEmpty()) add(Finding("double-label", n.name()))
            if (n.descriptions().any { "button" in it.lowercase() }) add(Finding("redundant-description", n.name()))
        }
        clickables.groupBy { it.boundsInRoot }.filterKeys { it.width > 0 && it.height > 0 }.values
            .filter { it.size > 1 }.forEach { same -> same.drop(1).forEach { add(Finding("duplicate-bounds", it.name())) } }

        for (h in expect.headings) {
            if (nodes.none { it.texts().joinToString(" ").trim() == h && it.config.contains(SemanticsProperties.Heading) }) add(Finding("headings", h))
        }
        for (t in expect.toggles) {
            val controls = nodes.filter { (it.isClickable()) && t in it.label() }
            if (controls.none { it.config.contains(SemanticsProperties.ToggleableState) || it.config.contains(SemanticsProperties.StateDescription) }) {
                add(Finding("toggle-state", t))
            }
        }
        for (group in expect.groups) {
            val options = group.map { label -> nodes.firstOrNull { it.isClickable() && it.texts().any { text -> text == label } } }
            val shared = options.filterNotNull().map { o -> (own[o.id] ?: o).ancestors().filter { it.config.contains(SemanticsProperties.SelectableGroup) }.map { it.id }.toSet() }
            if (options.any { it == null } || shared.reduceOrNull { a, b -> a intersect b }.isNullOrEmpty()) add(Finding("group", group.joinToString("/")))
        }
        for ((fragment, word) in expect.words) {
            val found = nodes.filter { fragment in it.label() }
            if (found.none { word.lowercase() in it.label().lowercase() }) add(Finding("words", "$fragment → $word"))
        }
        expect.cardTag?.let { tag ->
            val card = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            val stops = card?.descendants()?.count { it.isClickable() || it.label().isNotBlank() } ?: 0
            if (stops > MAX_CARD_STOPS) add(Finding("card-stops", "$tag: $stops stops"))
        }
    }
}

private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }

private fun SemanticsNode.ancestors(): Sequence<SemanticsNode> = generateSequence(parent) { it.parent }

private fun SemanticsNode.isClickable() =
    config.contains(SemanticsActions.OnClick) || config.contains(SemanticsProperties.ToggleableState)

private fun SemanticsNode.role() = config.getOrNull(SemanticsProperties.Role)

private fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

private fun SemanticsNode.descriptions(): List<String> = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

/** What TalkBack would read: descriptions, then texts. */
private fun SemanticsNode.label(): String = (descriptions() + texts()).joinToString(" ")

/** A short, stable name: the node's own description, else its text. */
private fun SemanticsNode.name(): String = (descriptions().firstOrNull() ?: texts().firstOrNull() ?: role()?.toString() ?: "?").take(40)
