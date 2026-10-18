package com.jensen.draculanightbynight.user

import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.content.DocumentType
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
        assertEquals(false, state.hidePartiesLine)
        assertNull(state.skipRepeatedDates)
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
            theme = ThemeChoice.DARK, highContrast = true, hidePartiesLine = true, skipRepeatedDates = false,
        )
        val json = write(state)
        assertTrue(json, json.contains("\"opendyslexic\"") && json.contains("\"xlarge\"") && json.contains("\"dark\""))
        assertEquals(state, read(json))
    }

    @Test
    fun aPlanAFileLoadsWithNoFilterAndStorySort() {
        val s = read("""{"mode":"free","onboarded":true,"progress":{"may03":{"readAtMillis":1,"bookmarked":true}}}""")
        assertEquals(EntryFilter(), s.filter)
        assertEquals(BookmarkSort.STORY, s.bookmarkSort)
        assertTrue(s.progressOf("may03").bookmarked)
        assertNull(s.progressOf("may03").bookmarkedAtMillis)
    }

    @Test
    fun unknownVoiceAndTypeNamesAreDropped() {
        val s = read("""{"filterVoices":["mina","renfield"],"filterTypes":["letter","postcard"],"bookmarkSort":"alphabetical"}""")
        assertEquals(EntryFilter(setOf(Voice.MINA), setOf(DocumentType.LETTER)), s.filter)
        assertEquals(BookmarkSort.STORY, s.bookmarkSort)
    }

    @Test
    fun aFilterRoundTripsThroughTheSavedFile() {
        val f = EntryFilter(setOf(Voice.VAN_HELSING), setOf(DocumentType.PHONOGRAPH))
        assertEquals(f, read(write(UserState().withFilter(f))).filter)
    }

    @Test
    fun anOldFileShowsTheTimeline() {
        assertFalse(read("""{"onboarded":true}""").timelineHidden)
    }
}
