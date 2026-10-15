package com.jensen.draculanightbynight.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ShiftableClockTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val base = MutableClock(Instant.parse("2026-10-15T14:00:00Z"))
    private val clock = ShiftableClock(base)

    @Test
    fun withoutTravelItIsTheDeviceClock() {
        assertEquals(base.now, clock.instant())
    }

    @Test
    fun travellingToAMomentKeepsTheClockRunningFromThere() {
        clock.travelTo(Instant.parse("2026-10-15T21:58:00Z"))
        assertEquals(Instant.parse("2026-10-15T21:58:00Z"), clock.instant())
        base.now = base.now.plusSeconds(90)
        assertEquals(Instant.parse("2026-10-15T21:59:30Z"), clock.instant())
    }

    @Test
    fun travellingByADurationAddsUpAndResetReturnsToTheDevice() {
        clock.travelBy(Duration.ofHours(1))
        clock.travelBy(Duration.ofMinutes(30))
        assertEquals(Instant.parse("2026-10-15T15:30:00Z"), clock.instant())
        assertEquals(Duration.ofMinutes(90), clock.offset)
        clock.reset()
        assertEquals(base.now, clock.instant())
    }

    @Test
    fun theZoneIsTheDevicesAndMillisFollowTheShift() {
        assertEquals(ZoneOffset.UTC, clock.zone)
        clock.travelBy(Duration.ofMinutes(1))
        assertEquals(base.now.plusSeconds(60).toEpochMilli(), clock.millis())
    }
}
