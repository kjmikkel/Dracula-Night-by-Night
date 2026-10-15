package com.jensen.draculanightbynight.content

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every date the app shows, in English like the rest of its text, whatever the phone's language. */
object Dates {
    private fun english(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)

    /** "4 May" */
    val DAY: DateTimeFormatter = english("d MMMM")

    /** "4 May 1893" */
    val DAY_YEAR: DateTimeFormatter = english("d MMMM yyyy")

    /** "4 May, 14:45" */
    val DAY_TIME: DateTimeFormatter = english("d MMMM, HH:mm")

    /** "Thursday 15 October, 22:00" */
    val WEEKDAY_TIME: DateTimeFormatter = english("EEEE d MMMM, HH:mm")

    /** "22:00" */
    val TIME: DateTimeFormatter = english("HH:mm")
}
