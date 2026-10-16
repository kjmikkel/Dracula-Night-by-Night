package com.jensen.draculanightbynight.a11y

/** One accessibility rule broken by one node, on one screen, in one look. */
data class Violation(val screen: String, val look: String, val rule: String, val node: String) {
    fun line(issue: String, reason: String) = "$screen | $look | $rule | $node | $issue | $reason"
}

/** A known violation: why it's accepted for now, and the issue that will fix it. */
data class BaselineEntry(val violation: Violation, val issue: Int, val reason: String) {
    fun line() = violation.line("#$issue", reason)
}

/** What changed against the baseline for one screen and look. */
data class Diff(val new: List<Violation>, val stale: List<BaselineEntry>) {
    fun isEmpty() = new.isEmpty() && stale.isEmpty()
}

private const val FIELDS = 6

/**
 * Parses `screen | look | rule | node | #issue | reason` lines. `#` comments and blank lines are skipped. A line
 * without an issue or a reason, or a key listed twice, is an error: the baseline only grows through review.
 */
fun parseBaseline(text: String): List<BaselineEntry> {
    val seen = mutableSetOf<Violation>()
    return text.lines().mapIndexedNotNull { index, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
        val where = "a11y baseline line ${index + 1}: $line"
        val parts = line.split("|").map { it.trim() }
        require(parts.size == FIELDS) { "$where — expected screen | look | rule | node | #issue | reason" }
        val issue = parts[4].removePrefix("#").toIntOrNull()
        require(parts[4].startsWith("#") && issue != null) { "$where — needs an issue link like #115" }
        require(parts[5].isNotEmpty()) { "$where — needs a reason" }
        val violation = Violation(parts[0], parts[1], parts[2], parts[3])
        require(seen.add(violation)) { "$where — listed twice" }
        BaselineEntry(violation, issue, parts[5])
    }
}

/** The baseline shipped with the tests. */
fun loadBaseline(): List<BaselineEntry> =
    parseBaseline(checkNotNull(object {}.javaClass.getResource("/a11y-baseline.txt")) { "a11y-baseline.txt missing" }.readText())

/** [found] against the baseline, for one [screen] and [look] only. */
fun diff(found: List<Violation>, baseline: List<BaselineEntry>, screen: String, look: String): Diff {
    val known = baseline.filter { it.violation.screen == screen && it.violation.look == look }
    val seen = found.toSet()
    return Diff(
        new = seen.filter { v -> known.none { it.violation == v } },
        stale = known.filter { it.violation !in seen },
    )
}

/** What to change in `a11y-baseline.txt`: lines to add (fill in the issue and reason), and lines to remove. */
fun Diff.failureMessage(): String = buildString {
    if (new.isNotEmpty()) {
        appendLine("New accessibility violations. Fix them, or add these lines to app/app/src/test/resources/a11y-baseline.txt with an issue and a reason:")
        new.forEach { appendLine(it.line("#???", "reason?")) }
    }
    if (stale.isNotEmpty()) {
        appendLine("Fixed — remove these lines from a11y-baseline.txt:")
        stale.forEach { appendLine("remove: ${it.line()}") }
    }
}
