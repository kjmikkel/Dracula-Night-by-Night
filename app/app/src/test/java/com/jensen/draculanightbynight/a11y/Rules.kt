package com.jensen.draculanightbynight.a11y

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
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

/** Test tag of the screen a large look draws on, narrower than the window (see [InLook]). */
const val SCREEN_TAG = "a11y-screen"

/**
 * Every rule broken on one screen. [merged] is what TalkBack walks; [unmerged] is the same tree before merging,
 * so a control's own description can be told apart from its children's text.
 */
fun checkRules(merged: SemanticsNode, unmerged: SemanticsNode, expect: Expectations, density: Density): List<Finding> {
    val nodes = merged.descendants()
    val window = Rect(Offset.Zero, Size(merged.size.width.toFloat(), merged.size.height.toFloat()))
    val own = (listOf(unmerged) + unmerged.descendants()).associateBy { it.id }
    val clickables = nodes.filter { it.isClickable() }
    val minTarget = with(density) { 48.dp.toPx() } - 0.5f
    return buildList {
        for (n in clickables) {
            if (n.label().isBlank()) add(Finding("label", "unlabelled in ${n.ancestors().firstOrNull { it.label().isNotBlank() }?.name() ?: "screen"}"))
            // Only a list's own rows may go without a role (TalkBack offers them as items). A row may sit below plain
            // layout (a background is a node too); below anything labelled or clickable, it's a control in a row.
            val isListRow = n.ancestors().takeWhile { !it.config.contains(SemanticsProperties.CollectionInfo) }
                .none { it.label().isNotBlank() || it.isClickable() } &&
                n.ancestors().any { it.config.contains(SemanticsProperties.CollectionInfo) }
            // An editable field is announced as an edit box, so it needs no role either.
            val isTextField = n.config.contains(SemanticsProperties.EditableText)
            if (n.role() == null && !isListRow && !isTextField) add(Finding("role", n.name(own)))
            // The touch area, which Compose also reports to TalkBack: Material widens a 40 dp button's to 48 dp.
            // Only nodes fully in view: a row half scrolled out of a list has a cut-off touch area, not a small one.
            val whole = n.boundsInRoot.width >= n.size.width - 1f && n.boundsInRoot.height >= n.size.height - 1f
            if (whole && (n.touchBoundsInRoot.width < minTarget || n.touchBoundsInRoot.height < minTarget)) add(Finding("target", n.name(own)))
            val ownDescription = own[n.id]?.descriptions().orEmpty()
            if (ownDescription.isNotEmpty() && n.texts().isNotEmpty()) add(Finding("double-label", n.name(own)))
            if (n.descriptions().any { "button" in it.lowercase() }) add(Finding("redundant-description", n.name(own)))
        }
        clickables.groupBy { it.boundsInRoot }.filterKeys { it.width > 0 && it.height > 0 }.values
            .filter { it.size > 1 }.forEach { same -> same.drop(1).forEach { add(Finding("duplicate-bounds", it.name(own))) } }

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
        // Over the unmerged tree: a clickable row merges its children, but its ★ can be cut off on its own (#118).
        for (n in own.values) {
            if (n.label().isNotBlank() && n.isClippedAcrossItsScroll(window)) add(Finding("clipped", n.name()))
            if (n.isCollapsedToEllipsis(density)) add(Finding("clipped", n.name()))
        }
        expect.cardTag?.let { tag ->
            val card = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            val stops = card?.descendants()?.count { it.isClickable() || it.label().isNotBlank() } ?: 0
            if (stops > MAX_CARD_STOPS) add(Finding("card-stops", "$tag: $stops stops"))
        }
    }
}

/**
 * Cut off along an axis nothing lets the reader scroll: by its parent, or by the edge of the window. Being scrolled
 * out of a list's view is not clipping; a star pushed past the right edge of a vertical list is.
 */
private fun SemanticsNode.isClippedAcrossItsScroll(window: Rect): Boolean {
    val scroller = ancestors().firstOrNull {
        it.config.contains(SemanticsProperties.VerticalScrollAxisRange) || it.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
    }
    val scrollsVertically = scroller?.config?.contains(SemanticsProperties.VerticalScrollAxisRange) == true
    val scrollsHorizontally = scroller?.config?.contains(SemanticsProperties.HorizontalScrollAxisRange) == true
    val whole = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))
    // Squeezed to nothing by its neighbours: there, but not shown at all.
    if (size.width == 0 || size.height == 0) return true
    val shown = boundsInRoot
    // Judged per axis: the visible part against the whole, and the whole against the window (a wholly hidden
    // node's visible part is empty in both directions, so that alone says nothing about which way it went).
    val cutWidth = (!shown.isEmpty && whole.width - shown.width > 1f) || whole.left < window.left - 1f || whole.right > window.right + 1f
    val cutHeight = (!shown.isEmpty && whole.height - shown.height > 1f) || whole.top < window.top - 1f || whole.bottom > window.bottom + 1f
    return (cutWidth && !scrollsHorizontally) || (cutHeight && !scrollsVertically)
}

/** One line ending in "…" with room for fewer than about three characters: the text is effectively gone. */
private fun SemanticsNode.isCollapsedToEllipsis(density: Density): Boolean {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) ?: return false
    val layout = results.firstOrNull() ?: return false
    val fontPx = with(density) { layout.layoutInput.style.fontSize.toPx() }
    // Decided from the layout's inputs rather than isLineEllipsized, which Robolectric's text measuring never sets.
    val input = layout.layoutInput
    val wouldEllipsize = input.maxLines == 1 && input.overflow == TextOverflow.Ellipsis &&
        layout.multiParagraph.intrinsics.maxIntrinsicWidth > layout.size.width
    return wouldEllipsize && size.width < 3 * fontPx
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

/**
 * A short, stable name: the node's own description (from [own], the unmerged tree), else its first two texts, so
 * rows aren't all called by a child's "Unread".
 */
private fun SemanticsNode.name(own: Map<Int, SemanticsNode> = emptyMap()): String =
    ((own[id] ?: this).descriptions().firstOrNull() ?: texts().take(2).joinToString(" ").ifEmpty { null }
        ?: descriptions().firstOrNull() ?: role()?.toString() ?: "?").take(40)
