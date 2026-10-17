package com.jensen.draculanightbynight.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPanesTest {
    @Test fun tabletsInEitherOrientationGetTwoPanes() {
        assertTrue(twoPanes(1280.dp, 800.dp)); assertTrue(twoPanes(800.dp, 1280.dp)); assertTrue(twoPanes(673.dp, 841.dp))
    }

    @Test fun phonesNeverDo() {
        assertFalse(twoPanes(411.dp, 891.dp)); assertFalse(twoPanes(915.dp, 411.dp)); assertFalse(twoPanes(891.dp, 360.dp))
    }

    @Test fun theBoundaryIsInclusive() {
        assertTrue(twoPanes(600.dp, 480.dp)); assertFalse(twoPanes(599.dp, 480.dp)); assertFalse(twoPanes(600.dp, 479.dp))
    }
}
