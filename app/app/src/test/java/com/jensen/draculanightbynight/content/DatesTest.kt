package com.jensen.draculanightbynight.content

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class DatesTest {
    private val original = Locale.getDefault()

    @Before
    fun danishPhone() = Locale.setDefault(Locale.forLanguageTag("da-DK"))

    @After
    fun restore() = Locale.setDefault(original)

    @Test
    fun datesAreInEnglishWhateverThePhonesLanguage() {
        // The app's text is English; a Danish phone must not get "4 maj" or "torsdag".
        assertEquals("4 May", LocalDate.of(1893, 5, 4).format(Dates.DAY))
        assertEquals("4 May 1893", LocalDate.of(1893, 5, 4).format(Dates.DAY_YEAR))
        assertEquals("Thursday 15 October, 22:00", LocalDateTime.of(2026, 10, 15, 22, 0).format(Dates.WEEKDAY_TIME))
        assertEquals("4 May, 14:45", LocalDateTime.of(1893, 5, 4, 14, 45).format(Dates.DAY_TIME))
    }
}
