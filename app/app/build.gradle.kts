plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jensen.draculanightbynight"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jensen.draculanightbynight"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
}
