package com.jensen.draculanightbynight.user

import com.jensen.draculanightbynight.domain.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Saving and loading the reader's state as a file (#205). */
class BackupTest {
    private val reading = UserState(textSize = TextSize.LARGE, bookmarkSort = BookmarkSort.RECENT)
        .withMode(ReadingMode.SameTempo(LocalDate.of(2026, 9, 1)))
        .copy(onboarded = true, lastNotifiedAt = 1, progress = mapOf("may03" to EntryProgress(readAtMillis = 5, blockIndex = 2, blockOffset = 40, blockPermille = 120, bookmarked = true)))

    @Test
    fun everythingTheAppRemembersRoundTrips() {
        assertEquals(reading, decodeBackup(encodeBackup(reading)))
    }

    @Test
    fun theFileSaysWhoseItIs() {
        assertTrue(encodeBackup(reading).contains("\"app\":\"dracula-night-by-night\""))
    }

    @Test
    fun anotherAppsJsonOrADamagedFileIsRefused() {
        assertNull(decodeBackup("""{"mode": "free"}"""))
        assertNull(decodeBackup("""{"app": "something-else", "version": 1, "state": {}}"""))
        assertNull(decodeBackup(encodeBackup(reading).dropLast(10)))
        assertNull(decodeBackup("not json at all"))
    }

    @Test
    fun aFileFromANewerVersionLoadsWhatThisOneUnderstands() {
        val newer = """{"app": "dracula-night-by-night", "version": 9, "state": {"mode": "free", "futureField": [1, 2], "textSize": "HUGE"}, "more": true}"""
        val state = decodeBackup(newer)!!
        assertEquals(ReadingMode.Free, state.readingMode)
        assertEquals(TextSize.NORMAL, state.textSize)
    }

    @Test
    fun progressMeansSomethingReadOrBookmarked() {
        assertFalse(UserState().withMode(ReadingMode.Free).hasProgress)
        assertTrue(reading.hasProgress)
        assertTrue(UserState(progress = mapOf("a" to EntryProgress(bookmarked = true))).hasProgress)
        assertFalse(UserState(progress = mapOf("a" to EntryProgress(blockIndex = 3))).hasProgress)
    }

    @Test
    fun restoringReplacesEverythingButAnnouncesNothingThatAlreadyArrived() {
        assertEquals(reading.copy(onboarded = true, lastNotifiedAt = 99), restored(reading.copy(onboarded = false), nowMillis = 99))
    }
}
