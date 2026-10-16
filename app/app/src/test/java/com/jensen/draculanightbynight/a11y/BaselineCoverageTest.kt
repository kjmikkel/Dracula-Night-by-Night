package com.jensen.draculanightbynight.a11y

import org.junit.Assert.assertTrue
import org.junit.Test

/** The baseline as a whole, outside the case-by-case ratchet. */
class BaselineCoverageTest {
    /** A line for a case or look that no longer runs would never be checked, so it could never be removed. */
    @Test fun baselineOnlyNamesRunningCases() {
        val running = RatchetTest.parameters().map { (case, look) -> case to look }.toSet()
        val orphans = loadBaseline().filter { (it.violation.screen to it.violation.look) !in running }
        assertTrue("Baseline lines for cases or looks that don't run:\n" + orphans.joinToString("\n") { it.line() }, orphans.isEmpty())
    }

    @Test fun printBaselineCount() {
        println("Accessibility baseline: ${loadBaseline().size} known violations")
    }
}
