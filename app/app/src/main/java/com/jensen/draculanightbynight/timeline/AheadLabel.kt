package com.jensen.draculanightbynight.timeline

import java.time.Duration

/** "2 days, 3 hours", "1 hour, 5 minutes", "45 minutes": how far ahead of real time the reader is. */
fun aheadLabel(ahead: Duration): String {
    fun unit(n: Long, word: String) = "$n $word" + if (n == 1L) "" else "s"
    val days = ahead.toDays()
    val hours = ahead.toHours() % 24
    val minutes = ahead.toMinutes() % 60
    val parts = when {
        days > 0 -> listOf(unit(days, "day")) + if (hours > 0) listOf(unit(hours, "hour")) else emptyList()
        hours > 0 -> listOf(unit(hours, "hour")) + if (minutes > 0) listOf(unit(minutes, "minute")) else emptyList()
        else -> listOf(unit(minutes, "minute"))
    }
    return parts.joinToString(", ")
}
