package com.jensen.draculanightbynight.user

import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class UserStateTest {
    private fun read(json: String): UserState = runBlocking { UserStateSerializer.readFrom(json.byteInputStream()) }

    private fun write(state: UserState): String =
        ByteArrayOutputStream().also { runBlocking { UserStateSerializer.writeTo(state, it) } }.toString(Charsets.UTF_8)

    @Test
    fun aNewReaderHasNotBeenOnboarded() {
        assertFalse(UserState().isOnboarded)
    }

    @Test
    fun aFileFromPlanOneLoadsWithDefaultsAndCountsAsOnboarded() {
        val state = read("""{"mode": "free", "progress": {"a": {"readAtMillis": 5}}}""")
        assertTrue(state.isOnboarded)
        assertTrue(state.notify)
        assertNull(state.lastNotifiedAt)
        assertEquals(TextSize.NORMAL, state.textSize)
        assertEquals(ReadingFont.SERIF, state.readingFont)
        assertEquals(ThemeChoice.SYSTEM, state.theme)
        assertNull(state.highContrast)
        assertNull(state.seasonPromptYear)
        assertEquals(5L, state.progressOf("a").readAtMillis)
    }

    @Test
    fun unknownDisplayValuesFallBackToTheirDefaults() {
        val state = read("""{"mode": "free", "textSize": "huge", "readingFont": "comic", "theme": "neon"}""")
        assertEquals(TextSize.NORMAL, state.textSize)
        assertEquals(ReadingFont.SERIF, state.readingFont)
        assertEquals(ThemeChoice.SYSTEM, state.theme)
        assertEquals(ReadingMode.Free, state.readingMode)
    }

    @Test
    fun displayChoicesAreStoredByName() {
        val state = UserState(
            onboarded = true, textSize = TextSize.XLARGE, readingFont = ReadingFont.OPEN_DYSLEXIC,
            theme = ThemeChoice.DARK, highContrast = true,
        )
        val json = write(state)
        assertTrue(json, json.contains("\"opendyslexic\"") && json.contains("\"xlarge\"") && json.contains("\"dark\""))
        assertEquals(state, read(json))
    }
}
