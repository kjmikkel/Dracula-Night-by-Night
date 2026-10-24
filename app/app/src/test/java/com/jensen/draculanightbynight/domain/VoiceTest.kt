package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {
    private fun by(author: String) = TestStory.entry("a", 5, 3, "10:00", author = author)

    @Test
    fun theMainVoicesMapFromTheirAuthorIds() {
        assertEquals(Voice.JONATHAN, voiceOf(by("jonathan_harker")))
        assertEquals(Voice.MINA, voiceOf(by("mina_harker")))
        assertEquals(Voice.SEWARD, voiceOf(by("john_seward")))
        assertEquals(Voice.LUCY, voiceOf(by("lucy_westenra")))
        assertEquals(Voice.VAN_HELSING, voiceOf(by("van_helsing")))
        assertEquals(Voice.DEMETER, voiceOf(by("demeter_captain")))
    }

    @Test
    fun everyOtherAuthorIsOthers() {
        assertEquals(Voice.OTHERS, voiceOf(by("dailygraph")))
        assertEquals(Voice.OTHERS, voiceOf(by("someone_new")))
    }

    @Test
    fun filterOffersOnlyVoicesReadSoFarAndOthers() {
        val story = TestStory.small
        assertEquals(listOf(Voice.OTHERS), metVoices(story, UserState()))
        // Bookmarked or arrived isn't met: only reading is (#220).
        assertEquals(listOf(Voice.OTHERS), metVoices(story, UserState(progress = mapOf("may09" to EntryProgress(bookmarked = true)))))
        val read = UserState(progress = mapOf("may09" to EntryProgress(readAtMillis = 1), "may03" to EntryProgress(readAtMillis = 2)))
        assertEquals(listOf(Voice.JONATHAN, Voice.MINA, Voice.OTHERS), metVoices(story, read))
    }

    @Test
    fun keysRoundTripAndUnknownKeysAreNull() {
        Voice.entries.forEach { assertEquals(it, Voice.fromKey(it.key)) }
        assertNull(Voice.fromKey("renfield"))
    }
}
