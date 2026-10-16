package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory
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
    fun keysRoundTripAndUnknownKeysAreNull() {
        Voice.entries.forEach { assertEquals(it, Voice.fromKey(it.key)) }
        assertNull(Voice.fromKey("renfield"))
    }
}
