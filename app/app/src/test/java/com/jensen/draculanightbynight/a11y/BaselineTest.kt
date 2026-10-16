package com.jensen.draculanightbynight.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {
    private val back = Violation("settings", "LIGHT", "double-label", "Back")

    @Test fun parsesAnEntry() {
        val entries = parseBaseline("# comment\n\nsettings | LIGHT | double-label | Back | #116 | read twice\n")
        assertEquals(listOf(BaselineEntry(back, 116, "read twice")), entries)
    }

    @Test fun rejectsLineWithoutIssue() {
        val e = assertThrows(IllegalArgumentException::class.java) { parseBaseline("settings | LIGHT | double-label | Back | | read twice") }
        assertTrue(e.message!!, "line 1" in e.message!!)
    }

    @Test fun rejectsLineWithoutReason() {
        assertThrows(IllegalArgumentException::class.java) { parseBaseline("settings | LIGHT | double-label | Back | #116 |") }
    }

    @Test fun rejectsDuplicateKey() {
        val line = "settings | LIGHT | double-label | Back | #116 | read twice\n"
        val e = assertThrows(IllegalArgumentException::class.java) { parseBaseline(line + line) }
        assertTrue(e.message!!, "line 2" in e.message!!)
    }

    @Test fun diffReportsNewViolation() {
        assertEquals(Diff(new = listOf(back), stale = emptyList()), diff(listOf(back), emptyList(), "settings", "LIGHT"))
    }

    @Test fun diffReportsStaleEntry() {
        val entry = BaselineEntry(back, 116, "read twice")
        assertEquals(Diff(new = emptyList(), stale = listOf(entry)), diff(emptyList(), listOf(entry), "settings", "LIGHT"))
    }

    @Test fun diffIgnoresOtherScreensAndLooks() {
        val elsewhere = listOf(BaselineEntry(back.copy(screen = "about"), 116, "x"), BaselineEntry(back.copy(look = "DARK"), 116, "x"))
        assertTrue(diff(emptyList(), elsewhere, "settings", "LIGHT").isEmpty())
    }

    @Test fun failureMessageGivesPasteableLine() {
        val entry = BaselineEntry(back.copy(rule = "headings", node = "Settings"), 115, "not a heading")
        val message = Diff(new = listOf(back), stale = listOf(entry)).failureMessage()
        assertTrue(message, "settings | LIGHT | double-label | Back | #??? | reason?" in message)
        assertTrue(message, "remove: settings | LIGHT | headings | Settings | #115 | not a heading" in message)
    }

    @Test fun nodeKeyIsLabelNotPosition() {
        val other = back.copy(node = "‹ Previous")
        val baseline = parseBaseline(
            "settings | LIGHT | double-label | Back | #116 | x\nsettings | LIGHT | double-label | ‹ Previous | #116 | x\n",
        )
        assertTrue(diff(listOf(back, other), baseline, "settings", "LIGHT").isEmpty())
        assertTrue(diff(listOf(other, back), baseline, "settings", "LIGHT").isEmpty())
    }

    @Test fun repeatedFindingCountsOnce() {
        val baseline = parseBaseline("settings | LIGHT | double-label | Back | #116 | x\n")
        assertTrue(diff(listOf(back, back), baseline, "settings", "LIGHT").isEmpty())
    }

    @Test fun shippedBaselineParses() {
        loadBaseline()
    }
}
