package com.jensen.draculanightbynight.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TimeSourceTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private var zone: ZoneId = ZoneOffset.UTC
    private val time = TimeSource(Clock.fixed(Instant.parse("2026-10-14T23:30:00Z"), ZoneOffset.UTC)) { zone }

    @Test
    fun todayFollowsTheZoneTheReaderIsInNow() {
        assertEquals(LocalDate.of(2026, 10, 14), time.today())
        zone = tokyo
        assertEquals(LocalDate.of(2026, 10, 15), time.today())
    }

    @Test
    fun everyMinuteReadsTheZoneAfresh() = runTest {
        val seen = mutableListOf<ZonedDateTime>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { time.ticks().toList(seen) }
        zone = tokyo
        advanceTimeBy(60_001)
        assertEquals(listOf<ZoneId>(ZoneOffset.UTC, tokyo), seen.map { it.zone })
    }

    @Test
    fun aTimeChangeIsEmittedAtOnceWithoutWaitingForTheNextMinute() = runTest {
        val seen = mutableListOf<ZonedDateTime>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { time.ticks().toList(seen) }
        zone = tokyo
        time.timeChanged()
        assertEquals(listOf<ZoneId>(ZoneOffset.UTC, tokyo), seen.map { it.zone })
    }
}
