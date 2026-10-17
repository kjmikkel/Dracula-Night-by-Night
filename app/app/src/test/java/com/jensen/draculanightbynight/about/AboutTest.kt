package com.jensen.draculanightbynight.about

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AboutTest {
    @Test
    fun everyBundledLicenceIsListed() {
        val bundled = File("src/main/assets/licenses").list().orEmpty().toSet()
        assertEquals(bundled, Licences.map { it.asset }.toSet())
    }

    @Test
    fun everyBundledFontFamilyIsCredited() {
        // im_fell_english_sc.ttf -> "im fell english sc"; the style suffix is not part of the family.
        val families = File("src/main/res/font").list().orEmpty()
            .map { it.substringBeforeLast('.').removeSuffix("_regular").removeSuffix("_italic").replace('_', ' ') }
            .toSet()
        val credited = Licences.flatMap { it.name.split(", ") }.map { it.lowercase() }.toSet()
        assertEquals(families, credited)
    }
}
