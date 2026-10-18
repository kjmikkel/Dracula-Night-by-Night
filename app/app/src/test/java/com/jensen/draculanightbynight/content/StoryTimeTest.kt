package com.jensen.draculanightbynight.content

import com.jensen.draculanightbynight.TestStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** How an entry's in-story time is shown (#190). */
class StoryTimeTest {
    private val timed = TestStory.entry("a", 5, 8, "22:00")

    @Test
    fun aTimeIsShownAsTheClockReadsIt() {
        assertEquals("22:00", timed.storyTime)
        assertEquals("8 May, 22:00", timed.storyDayTime)
    }

    @Test
    fun midnightIsMidnightNot2359() {
        val midnight = timed.copy(time = LocalTime.of(23, 59), timeEstimated = false)
        assertEquals("midnight", midnight.storyTime)
        assertEquals("8 May, midnight", midnight.storyDayTime)
    }

    @Test
    fun anEstimateThatHappensToBe2359StaysAClockTime() {
        assertEquals("23:59", timed.copy(time = LocalTime.of(23, 59), timeEstimated = true).storyTime)
    }

    @Test
    fun anUndatedEntryShowsNoDateOrTime() {
        val preface = timed.copy(time = LocalTime.MIDNIGHT, undated = true)
        assertNull(preface.storyTime)
        assertNull(preface.storyDayTime)
    }
}
