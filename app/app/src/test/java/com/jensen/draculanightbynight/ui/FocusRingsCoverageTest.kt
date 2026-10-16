package com.jensen.draculanightbynight.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A dialog or sheet is a window of its own, which the app root's [focusRings] can't draw in: each one must pass
 * `Modifier.focusRings()` itself (#113), so a new one can't come without a focus ring.
 */
class FocusRingsCoverageTest {
    /** Each call of a Material dialog or sheet in [source], with its arguments in parentheses. */
    private fun windowCalls(source: String): List<String> =
        Regex("""(?<![\w.])(AlertDialog|DatePickerDialog|ModalBottomSheet|Dialog)\(""").findAll(source)
            .filterNot { source.lastIndexOf('\n', it.range.first).let { line -> source.substring(line + 1, it.range.first).trimStart().startsWith("import") } }
            .filterNot { source.substring(maxOf(0, it.range.first - 4), it.range.first).endsWith("fun ") }
            .map { m ->
                var depth = 0
                var end = m.range.last
                do {
                    when (source[end]) { '(' -> depth++; ')' -> depth-- }
                    end++
                } while (depth > 0 && end < source.length)
                source.substring(m.range.first, end)
            }.toList()

    private fun missing(source: String) = windowCalls(source).filterNot { "focusRings()" in it }

    @Test fun everyDialogAndSheetDrawsFocusRings() {
        val offenders = File("src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { file -> missing(file.readText()).map { "${file.name}: ${it.lineSequence().first()}" } }.toList()
        assertTrue("Pass modifier = Modifier.focusRings() to:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test fun theScanFindsCallsAndSkipsDeclarationsAndImports() {
        val source = """
            import androidx.compose.material3.AlertDialog
            @Composable fun Dialog(x: Int) {}
            fun a() { AlertDialog(onDismissRequest = {}, confirmButton = { Text("Go") }) }
            fun b() { ModalBottomSheet(onDismissRequest = {}, modifier = Modifier.focusRings()) { } }
        """.trimIndent()
        assertEquals(2, windowCalls(source).size)
        assertEquals(listOf("AlertDialog"), missing(source).map { it.substringBefore('(') })
    }
}
