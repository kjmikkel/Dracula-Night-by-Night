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
}
