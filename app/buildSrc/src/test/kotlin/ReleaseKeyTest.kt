import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReleaseKeyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val noEnv: (String) -> String? = { null }

    private fun properties(vararg lines: String): File =
        File(tmp.root, "keystore.properties").apply { writeText(lines.joinToString("\n")) }

    private val missing get() = File(tmp.root, "keystore.properties")

    @Test fun nothingSetMeansUnsigned() {
        assertNull(releaseKey(missing, noEnv))
    }

    @Test fun blankValuesCountAsNothingSet() {
        // The copied example has empty passwords: still an unsigned build, as in CI.
        assertNull(releaseKey(properties("storeFile=", "storePassword=", "keyAlias= ", "keyPassword="), noEnv))
    }

    @Test fun allFromTheFile() {
        val key = releaseKey(properties("storeFile=/keys/r.jks", "storePassword=sp", "keyAlias=a", "keyPassword=kp"), noEnv)!!
        assertEquals(mapOf("storeFile" to File("/keys/r.jks").absolutePath, "storePassword" to "sp", "keyAlias" to "a", "keyPassword" to "kp"), key)
    }

    @Test fun allFromTheEnvironment() {
        val env = mapOf("DNBN_STORE_FILE" to "/keys/r.jks", "DNBN_STORE_PASSWORD" to "sp", "DNBN_KEY_ALIAS" to "a", "DNBN_KEY_PASSWORD" to "kp")
        assertEquals("a", releaseKey(missing, env::get)!!["keyAlias"])
    }

    @Test fun aBlankValueInTheFileFallsBackToTheEnvironment() {
        val env = mapOf("DNBN_STORE_PASSWORD" to "from-env")
        val key = releaseKey(properties("storeFile=/keys/r.jks", "storePassword=", "keyAlias=a", "keyPassword=kp"), env::get)!!
        assertEquals("from-env", key["storePassword"])
    }

    @Test fun aPartialConfigFailsAndNamesWhatIsMissing() {
        val error = assertThrows(IllegalStateException::class.java) {
            releaseKey(properties("storeFile=/keys/r.jks", "keyAlias=a"), noEnv)
        }
        assertTrue(error.message, error.message!!.contains("storePassword (DNBN_STORE_PASSWORD)"))
        assertTrue(error.message, error.message!!.contains("keyPassword (DNBN_KEY_PASSWORD)"))
    }

    @Test fun aRelativeStoreFileResolvesNextToKeystoreProperties() {
        val key = releaseKey(properties("storeFile=keys/r.jks", "storePassword=sp", "keyAlias=a", "keyPassword=kp"), noEnv)!!
        assertEquals(File(tmp.root, "keys/r.jks").absolutePath, key["storeFile"])
    }
}
