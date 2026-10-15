package com.jensen.draculanightbynight.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AheadLabelTest {
    @Test
    fun minutesHoursAndDays() {
        assertEquals("1 minute", aheadLabel(Duration.ofMinutes(1)))
        assertEquals("45 minutes", aheadLabel(Duration.ofMinutes(45)))
        assertEquals("1 hour", aheadLabel(Duration.ofHours(1)))
        assertEquals("2 hours, 5 minutes", aheadLabel(Duration.ofMinutes(125)))
        assertEquals("1 day, 3 hours", aheadLabel(Duration.ofHours(27)))
        assertEquals("2 days", aheadLabel(Duration.ofDays(2).plusMinutes(30)))
    }
}
