package com.jensen.draculanightbynight.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenEntryTest {
    @Test fun aRequestSurvivesEncoding() {
        val e = OpenEntry("1893-05-04-jonathan_harker-01", filtered = true, followingAll = true, seq = 7)
        assertEquals(e, decodeOpenEntry(e.encode()))
    }

    @Test fun theRouteCarriesTheFollowState() =
        assertEquals("reader/x?filtered=true&all=false", OpenEntry("x", filtered = true).route())
}
