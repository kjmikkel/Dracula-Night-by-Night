package com.jensen.draculanightbynight.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ModeChoiceTest {
    private val october3 = LocalDate.of(2026, 10, 3)
    private val october15 = LocalDate.of(2026, 10, 15)

    @Test
    fun choosingTheModeYouAreAlreadyInKeepsItsStartDate() {
        val tempo = ReadingMode.SameTempo(october3)
        assertEquals(tempo, pickMode(tempo, ReadingMode.SameTempo(october15)))
        assertEquals(ReadingMode.Calendar(2026), pickMode(ReadingMode.Calendar(2026), ReadingMode.Calendar(2027)))
    }

    @Test
    fun choosingAnotherModeSwitches() {
        assertEquals(ReadingMode.Free, pickMode(ReadingMode.SameTempo(october3), ReadingMode.Free))
        assertEquals(ReadingMode.SameTempo(october15), pickMode(ReadingMode.Free, ReadingMode.SameTempo(october15)))
    }
}
