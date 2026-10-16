import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The release signing key, from app/keystore.properties (git-ignored) or the DNBN_* environment variables.
 * Null when neither is complete: the release build is then unsigned, which CI uses to check it builds.
 */
val releaseKey: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    val props = Properties().apply { if (file.exists()) file.inputStream().use(::load) }
    val keys = mapOf(
        "storeFile" to "DNBN_STORE_FILE",
        "storePassword" to "DNBN_STORE_PASSWORD",
        "keyAlias" to "DNBN_KEY_ALIAS",
        "keyPassword" to "DNBN_KEY_PASSWORD",
    )
    val values = keys.mapValues { (prop, env) -> props.getProperty(prop) ?: System.getenv(env) }
    if (values.values.all { !it.isNullOrBlank() }) values.mapValues { it.value!! } else null
}

android {
    namespace = "com.jensen.draculanightbynight"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jensen.draculanightbynight"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        releaseKey?.let { key ->
            create("release") {
                storeFile = file(key.getValue("storeFile"))
                storePassword = key.getValue("storePassword")
                keyAlias = key.getValue("keyAlias")
                keyPassword = key.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKey != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    // One bundle for every language: the app is English only and sets an English configuration for the date
    // picker, which language splits would undermine (lint AppBundleLocaleChanges).
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The accessibility ratchet renders real screens under Robolectric: it needs the app's resources and assets,
    // and real text measuring (Robolectric's default graphics make text about a pixel per character).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }

    // The app's text is English; keep only English resources, so library dialogs (the date picker) match.
    androidResources {
        localeFilters += "en"
    }
}

/** Copies the story dataset (data/out/entries.json + characters.json) into the app's assets. */
abstract class StoryAssetsTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        sources.forEach { it.copyTo(File(out, it.name)) }
    }
}

// Only these two files: data/out also holds the git-ignored review page.
val storyAssets = tasks.register<StoryAssetsTask>("storyAssets") {
    sources.from(rootProject.file("../data/out/entries.json"), rootProject.file("../data/out/characters.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(storyAssets, StoryAssetsTask::outputDir)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
