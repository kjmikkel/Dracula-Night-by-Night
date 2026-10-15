package com.jensen.draculanightbynight.ui

import com.jensen.draculanightbynight.user.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun assertAllPairsReach(minimum: Double, vararg palettes: Palette) {
        for (palette in palettes) {
            for (pair in palette.textPairs()) {
                val ratio = contrast(pair.foreground, pair.background)
                assertTrue("${palette.name}: ${pair.name} is %.2f:1, needs %.1f:1".format(ratio, minimum), ratio >= minimum)
            }
        }
    }

    @Test
    fun contrastFollowsTheWcagFormula() {
        assertEquals(21.0, contrast(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, contrast(0xFF7A1F24, 0xFF7A1F24), 0.0001)
        assertEquals(4.48, contrast(0xFF777777, 0xFFFFFFFF), 0.01) // the classic "grey on white" that just fails AA
    }

    @Test
    fun theStandardPalettesMeetWcagAA() {
        assertAllPairsReach(4.5, Palettes.Light, Palettes.Dark)
    }

    @Test
    fun theHighContrastPalettesMeetWcagAAA() {
        assertAllPairsReach(7.0, Palettes.LightHighContrast, Palettes.DarkHighContrast)
    }

    @Test
    fun thePaletteFollowsTheThemeAndContrastChoices() {
        assertEquals(Palettes.Light, paletteFor(ThemeChoice.SYSTEM, systemDark = false, highContrast = null, systemHighContrast = false))
        assertEquals(Palettes.Dark, paletteFor(ThemeChoice.SYSTEM, systemDark = true, highContrast = null, systemHighContrast = false))
        assertEquals(Palettes.Dark, paletteFor(ThemeChoice.DARK, systemDark = false, highContrast = false, systemHighContrast = true))
        assertEquals(Palettes.LightHighContrast, paletteFor(ThemeChoice.LIGHT, systemDark = true, highContrast = true, systemHighContrast = false))
    }

    @Test
    fun highContrastFollowsAndroidUntilTheReaderChooses() {
        assertEquals(Palettes.DarkHighContrast, paletteFor(ThemeChoice.DARK, systemDark = false, highContrast = null, systemHighContrast = true))
        assertEquals(Palettes.Dark, paletteFor(ThemeChoice.DARK, systemDark = false, highContrast = false, systemHighContrast = true))
    }
}
