package com.jensen.draculanightbynight.a11y

import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.domain.voiceOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The spoiler rule's list for the filter sheet matches the story, so the rule can't pass by naming nobody (#220). */
@RunWith(RobolectricTestRunner::class)
class SpoilerCasesTest {
    @Test fun unmetVoicesAreTheVoicesWithNothingArrived() {
        val app = testApp()
        try {
            runBlocking {
                app.tempo()
                val arrived = app.arrived().toSet()
                assertTrue("nothing has arrived at FIXED_NOW", arrived.isNotEmpty())
                val met = app.stories.story().entries.filter { it.id in arrived }.map(::voiceOf).toSet()
                val unmet = (Voice.entries - met - Voice.OTHERS).map { it.label }
                assertEquals(unmet, unmetVoicesInTempo)
            }
        } finally {
            runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
        }
    }
}
