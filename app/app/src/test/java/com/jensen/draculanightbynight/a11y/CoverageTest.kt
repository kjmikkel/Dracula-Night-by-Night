package com.jensen.draculanightbynight.a11y

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every screen, dialog and sheet in the app has a ratchet case, so a new one can't slip past the checks. */
class CoverageTest {
    /** Composable functions named …Screen, …Dialog or …Sheet in the app's sources (tests run from app/app). */
    private val composables: Set<String> by lazy {
        val pattern = Regex("""fun ([A-Z]\w*(Screen|Dialog|Sheet))\(""")
        File("src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] } }.toSet()
    }

    @Test fun everyScreenDialogAndSheetHasACase() {
        val covered = cases.flatMap { it.covers }.toSet()
        val missing = composables - covered
        assertTrue("No ratchet case (a11y/Cases.kt) opens: $missing", missing.isEmpty())
    }

    @Test fun coverageSeesPrivateDialogs() {
        assertTrue(composables.toString(), "ReadNowDialog" in composables && "LicenceDialog" in composables)
    }
}
