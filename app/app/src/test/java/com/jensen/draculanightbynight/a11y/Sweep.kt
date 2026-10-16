package com.jensen.draculanightbynight.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction

/** Enough for the longest entry at 200 % text; a list still not at its end after this is itself a finding. */
private const val MAX_SCROLL_STEPS = 500
/** Most of a page per step, so consecutive views overlap and no row falls between them. */
private const val SCROLL_FRACTION = 0.9f
private const val TO_TOP = -1_000_000f

/** Rules about what a screen should have: met in any window or scroll position is met. */
private val EXPECTATIONS = setOf("headings", "toggle-state", "group", "words")

/**
 * Rules over every window (dialogs and sheets have their own) at every scroll position of every vertical list.
 * A node rule counts if it fires anywhere; an expectation (a heading, a group, a word) only if it's met nowhere.
 */
fun ComposeContentTestRule.findingsWhileScrolling(expect: Expectations): List<Finding> {
    val lists = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)
    val count = onAllNodes(lists).fetchSemanticsNodes().size
    // Start every list at its top: a case's tap() may have scrolled one to its target, past rows above it.
    for (i in 0 until count) {
        repeat(MAX_SCROLL_STEPS) {
            val list = onAllNodes(lists)[i]
            val range = list.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@repeat
            if (range.value() <= 0f) return@repeat
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, TO_TOP) }
            waitForIdle()
        }
    }
    val snapshots = mutableListOf<List<List<Finding>>>()
    snapshots += snapshot(expect)
    val unfinished = mutableListOf<Finding>()
    for (i in 0 until count) {
        var steps = 0
        while (true) {
            val list = onAllNodes(lists)[i]
            val node = list.fetchSemanticsNode()
            val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: break
            if (range.value() >= range.maxValue() - 1f) break
            if (steps++ == MAX_SCROLL_STEPS) {
                unfinished += Finding("sweep", "list $i not scrolled to its end")
                break
            }
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, node.size.height * SCROLL_FRACTION) }
            waitForIdle()
            snapshots += snapshot(expect)
        }
    }
    val all = snapshots.flatten()
    // A node rule's finding counts once per node: two unlabelled buttons are "×2", so a third shows as new.
    val times = all.flatMap { root -> root.filter { it.rule !in EXPECTATIONS }.groupingBy { it }.eachCount().entries }
        .groupBy({ it.key }, { it.value }).mapValues { (_, n) -> n.max() }
    val anywhere = times.map { (f, n) -> if (n > 1) f.copy(node = "${f.node} ×$n") else f }
    val everywhere = all.map { root -> root.filter { it.rule in EXPECTATIONS }.toSet() }.reduce { a, b -> a intersect b }
    return anywhere + everywhere + unfinished
}

/** The rules over each window on screen now. */
private fun ComposeContentTestRule.snapshot(expect: Expectations): List<List<Finding>> {
    val merged = onAllNodes(isRoot()).fetchSemanticsNodes()
    val unmerged = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().associateBy { it.id }
    return merged.map { root -> checkRules(root, unmerged.getValue(root.id), expect, density) }
}

