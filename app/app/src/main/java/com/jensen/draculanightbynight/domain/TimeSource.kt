package com.jensen.draculanightbynight.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The reader's current time and time zone. The zone is read afresh every time, never captured,
 * so a reader who travels sees entries unlock by the clock where they are now.
 */
class TimeSource(private val clock: Clock, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun now(): ZonedDateTime = clock.instant().atZone(zone())

    fun today(): LocalDate = now().toLocalDate()

    fun millis(): Long = clock.millis()

    /** Call when the device's time zone or clock changes, so [ticks] does not wait for the next minute. */
    fun timeChanged() {
        changes.tryEmit(Unit)
    }

    /** Now, at the start of every following minute, and whenever [timeChanged] is called. */
    fun ticks(): Flow<ZonedDateTime> = merge(
        flow {
            while (true) {
                emit(now())
                delay(MINUTE_MILLIS - clock.millis() % MINUTE_MILLIS)
            }
        },
        changes.map { now() },
    )

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
