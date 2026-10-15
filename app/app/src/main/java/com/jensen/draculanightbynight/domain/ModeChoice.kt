package com.jensen.draculanightbynight.domain

/**
 * The mode to store when the reader picks [picked] while in [current]. Picking the kind of mode
 * they are already in keeps it as it is: re-choosing Same tempo must not restart it from today
 * and lock again everything they have already read.
 */
fun pickMode(current: ReadingMode?, picked: ReadingMode): ReadingMode =
    if (current != null && current::class == picked::class) current else picked
