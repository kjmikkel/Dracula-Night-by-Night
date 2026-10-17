import java.io.File
import java.util.Properties

/** The four signing settings, each from keystore.properties or its environment variable. */
val RELEASE_KEY_SETTINGS = mapOf(
    "storeFile" to "DNBN_STORE_FILE",
    "storePassword" to "DNBN_STORE_PASSWORD",
    "keyAlias" to "DNBN_KEY_ALIAS",
    "keyPassword" to "DNBN_KEY_PASSWORD",
)

/**
 * The release signing key from [propertiesFile] (keystore.properties, which may be missing) or, for any setting
 * that is missing or blank there, its DNBN_* environment variable from [env].
 *
 * Null when nothing is set: the release build is then unsigned, which CI uses to check it builds. A partial set
 * fails rather than quietly building unsigned. A relative storeFile resolves next to keystore.properties.
 */
fun releaseKey(propertiesFile: File, env: (String) -> String?): Map<String, String>? {
    val props = Properties().apply { if (propertiesFile.exists()) propertiesFile.inputStream().use(::load) }
    val values = RELEASE_KEY_SETTINGS.mapValues { (prop, variable) ->
        props.getProperty(prop)?.trim()?.takeIf { it.isNotEmpty() } ?: env(variable)?.trim()?.takeIf { it.isNotEmpty() }
    }
    val missing = values.filterValues { it == null }.keys
    if (missing.size == values.size) return null
    check(missing.isEmpty()) {
        "Release signing is partly set up. Missing: " +
            missing.joinToString { "$it (${RELEASE_KEY_SETTINGS.getValue(it)})" } +
            ". Set all four in ${propertiesFile.path} or the environment, or none for an unsigned build."
    }
    val dir = propertiesFile.absoluteFile.parentFile
    return values.mapValues { (prop, value) ->
        if (prop == "storeFile") dir.toPath().resolve(value!!).normalize().toFile().absolutePath else value!!
    }
}
