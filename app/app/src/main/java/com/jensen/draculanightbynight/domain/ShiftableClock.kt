package com.jensen.draculanightbynight.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The app's clock: the device's [base] clock moved by [offset]. The offset stays zero except in debug builds,
 * where the time-travel receiver moves it so timed behaviour can be checked in seconds instead of hours.
 */
class ShiftableClock(private val base: Clock) : Clock() {
    @Volatile
    var offset: Duration = Duration.ZERO
        private set

    override fun getZone(): ZoneId = base.zone

    override fun withZone(zone: ZoneId): Clock = ShiftableClock(base.withZone(zone)).also { it.offset = offset }

    override fun instant(): Instant = base.instant().plus(offset)

    /** From now on the clock reads [target] and keeps running from there. */
    fun travelTo(target: Instant) {
        offset = Duration.between(base.instant(), target)
    }

    fun travelBy(duration: Duration) {
        offset = offset.plus(duration)
    }

    fun reset() {
        offset = Duration.ZERO
    }
}
