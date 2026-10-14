# Android App Foundation Implementation Plan (App plan 1 of 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An installable Android app that loads the bundled story, unlocks entries by reading mode, shows them on a day-by-day timeline with a "next entry" teaser, and opens them in a reader that remembers read state, bookmarks and scroll position.

**Architecture:** One Gradle project in `app/` (module `:app`), Kotlin + Jetpack Compose. Pure-Kotlin logic (`content`, `domain`, `timeline/Timeline.kt`, `reader/Reader.kt`) carries all behaviour and is unit-tested on the JVM; view models and Compose screens are thin glue verified on the emulator. The dataset `data/out/entries.json` + `characters.json` is copied into the APK's assets by a Gradle task and decoded into memory at startup; the reader's own state lives in a separate DataStore JSON file.

**Tech Stack:** AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, Compose BOM 2026.09.00 (Material 3), Navigation Compose 2.10.2, Lifecycle 2.11.0, DataStore 1.2.1, kotlinx-serialization 1.11.0, JUnit 4.13.2, kotlinx-coroutines-test 1.11.0. compileSdk/targetSdk 37 (Android 17), minSdk 26.

**Spec:** `docs/superpowers/specs/2026-10-14-app-design.md` (dataset contract: `docs/superpowers/specs/2026-10-14-dataset-design.md`)

**Prerequisite:** the dataset PR (Task 10 of the dataset plan) is merged, so `data/out/entries.json` and `characters.json` exist on `main`.

## Global Constraints

- Package / applicationId: `com.jensen.draculanightbynight`; display name "Dracula Night by Night".
- minSdk 26; compileSdk and targetSdk 37; versions exactly as in `app/gradle/libs.versions.toml` below.
- Fully offline: no `INTERNET` permission, no analytics, no accounts.
- Unlock state is never stored: it is computed from `UnlockSchedule` with the current time and zone.
- User state is keyed by the dataset's stable entry ids; a dataset update never touches it; progress for ids that no longer exist is ignored, not deleted.
- Season = 3 May – 6 November; story year 1893.
- License Apache-2.0. Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; PR bodies end with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- Gradle needs a full JDK. On this machine `JAVA_HOME` points at a JRE, so every Gradle command below is run as `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew …` from `app/` (Android Studio does this automatically). Do not add `jvmToolchain(21)`: no JDK 21 is installed locally.
- `app/local.properties` (git-ignored) contains `sdk.dir=C:/Users/kjmik/AppData/Local/Android/Sdk` (forward slashes; backslashes break the path).

## Deviations from the spec (rulings, flagged for the maintainer)

1. **Content is decoded from bundled JSON into memory, not loaded into a prebuilt Room database.** 234 entries / ~1 MB decode in well under a second; this removes build-time database generation and keeps one copy of the schema (the dataset's). The spec's intent — content replaced wholesale on update, user data separate — is kept.
2. **User state uses DataStore with a JSON serializer instead of a Room "user DB".** Room on Android needs a `Context` and cannot run in plain JVM unit tests (verified); DataStore can (verified), and the state is small (mode, skip-ahead, ≤ 234 progress records). Unknown fields are ignored, so later versions can add fields without migrations.
3. **Plan 1 has no onboarding:** a new reader starts in *Same tempo from today* and can switch mode from a small dialog. Onboarding, settings, notifications with quiet hours, filters, the bookmarks list, swipe between entries and the full gothic theme are App plan 2.

## Workflow (every task)

As for the dataset plan: one GitHub issue per task (created in Task 0), branch `app-<N>-<slug>` off `main`, red-green steps, PR `App <N>: <name>` with `Closes #<issue>`, `gh pr merge --auto --squash --delete-branch`, Auto-fix on, wait for the merge before the next task.

## Review Focus

1. **Leaving the reader must not lose the scroll position**, including when the app is killed right after: the last save runs as the screen closes, after `viewModelScope` is cancelled → saves go through `AppContainer.scope` and are also made while scrolling (Task 6 emulator check, step "resume after force-stop").
2. **A time that does not exist or exists twice locally** (daylight-saving changes inside the season, e.g. 25 October) must unlock deterministically → `theAmbiguousHourWhenSummerTimeEndsUsesTheEarlierOffset` (Task 2).
3. **A corrupt or newer-version user-state file** must not crash the app or lose the story → `aCorruptFileStartsOverInsteadOfCrashing`, `unknownFieldsFromANewerVersionAreIgnored` (Task 3).
4. **Opening an entry that is still locked** (stale navigation, clock changed backwards) must show "not arrived yet", never its text → `aLockedOrUnknownEntryCannotBeOpened` (Task 5) and `ReaderUi.Locked` (Task 6).
5. **A saved position beyond the entry's end** (the dataset re-split an entry) must not crash the reader → `aSavedPositionPastTheEndIsClamped` (Task 5).

---

### Task 0: Issues for the app plan (no PR)

- [ ] **Step 1: Create one issue per task**

```bash
for t in "App 1: Gradle project, CI and story decoding" "App 2: Unlock engine" "App 3: Reader's saved state" "App 4: Timeline logic" "App 5: Reader logic" "App 6: App shell and screens"; do
  gh issue create --title "$t" --body "Implements a task of docs/superpowers/plans/2026-10-14-app-foundation.md. Spec: docs/superpowers/specs/2026-10-14-app-design.md"
done
gh issue list --limit 10
```
Expected: six new issues. Note their numbers; each task's PR closes its own issue.

---

### Task 1: Gradle project, CI and story decoding

**Files:**
- Create: `app/settings.gradle.kts`, `app/build.gradle.kts`, `app/gradle.properties`, `app/gradle/libs.versions.toml`, `app/app/build.gradle.kts`, `app/app/src/main/AndroidManifest.xml`, `app/app/src/main/res/values/strings.xml`
- Create (generated): `app/gradlew`, `app/gradlew.bat`, `app/gradle/wrapper/gradle-wrapper.jar`, `app/gradle/wrapper/gradle-wrapper.properties`
- Create: `app/app/src/main/java/com/jensen/draculanightbynight/content/Story.kt`, `.../content/StoryJson.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/content/StoryJsonTest.kt`
- Create: `.github/workflows/app.yml`; Modify: `.gitignore`

**Interfaces:**
- Produces: `DocumentType`, `BlockRole`, `Span(text, italic)`, `Block(role, spans)` with `text`, `Character(id, name, short)`, `Entry(id, order, chapter, type, author, recipient, date: LocalDate, time: LocalTime, timeEstimated, place, header, body)` with `preview`, `Story(entries, characters)` with `entries` (story order), `entry(id)`, `nameOf(characterId)`; `decodeStory(entriesJson: String, charactersJson: String): Story`.

- [ ] **Step 1: Gradle files**

`app/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "DraculaNightByNight"
include(":app")
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`app/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
```

`app/gradle/libs.versions.toml`:
```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
composeBom = "2026.09.00"
activity = "1.13.0"
lifecycle = "2.11.0"
navigation = "2.10.2"
datastore = "1.2.1"
serialization = "1.11.0"
coroutines = "1.11.0"
junit = "4.13.2"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
datastore = { group = "androidx.datastore", name = "datastore", version.ref = "datastore" }
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`app/app/build.gradle.kts` (the `storyAssets` task copies exactly the two dataset files into the APK's assets):
```kotlin
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
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```

`app/app/src/main/AndroidManifest.xml` (no activity yet; Task 6 replaces it):
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true" />

</manifest>
```

`app/app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Dracula Night by Night</string>
</resources>
```

Append to `.gitignore`:
```
# Android
app/local.properties
app/.gradle/
app/.kotlin/
app/build/
app/app/build/
```

- [ ] **Step 2: Gradle wrapper**

```bash
cd app
curl -sL -o /tmp/gradle-9.8.1-bin.zip https://services.gradle.org/distributions/gradle-9.8.1-bin.zip
unzip -q -o /tmp/gradle-9.8.1-bin.zip -d /tmp
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" /tmp/gradle-9.8.1/bin/gradle wrapper --gradle-version 9.8.1
echo 'sdk.dir=C:/Users/kjmik/AppData/Local/Android/Sdk' > local.properties
git update-index --add --chmod=+x gradlew 2>/dev/null || true
```
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` exist; `gradle-wrapper.properties` names `gradle-9.8.1-bin.zip`.

- [ ] **Step 3: Write the failing test**

`app/app/src/test/java/com/jensen/draculanightbynight/content/StoryJsonTest.kt`:
```kotlin
package com.jensen.draculanightbynight.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class StoryJsonTest {
    private val characters = """[{"id": "mina_harker", "name": "Mina Harker", "short": "Mina"}]"""

    private fun entry(id: String, order: Int, date: String, time: String) = """
        {"id": "$id", "order": $order, "chapter": 5, "type": "letter", "author": "mina_harker",
         "recipient": null, "date": "$date", "time": "$time", "timeEstimated": true, "place": null,
         "header": null, "body": [{"role": "note", "spans": [{"text": "(Kept.)", "italic": false}]},
                                  {"role": "para", "spans": [{"text": "9 May.", "italic": true},
                                                             {"text": "—My dearest\nLucy", "italic": false}]}],
         "futureField": 1}
    """

    @Test
    fun decodesEntriesAndIgnoresUnknownFields() {
        val story = decodeStory("[${entry("a", 0, "1893-05-09", "10:00")}]", characters)
        val e = story.entries.single()
        assertEquals(DocumentType.LETTER, e.type)
        assertEquals(LocalDate.of(1893, 5, 9), e.date)
        assertEquals(LocalTime.of(10, 0), e.time)
        assertEquals(BlockRole.PARA, e.body[1].role)
        assertEquals("9 May.—My dearest\nLucy", e.body[1].text)
        assertEquals("Mina Harker", story.nameOf("mina_harker"))
        assertNull(story.nameOf(null))
    }

    @Test
    fun previewSkipsNotesAndFlattensLineBreaks() {
        val story = decodeStory("[${entry("a", 0, "1893-05-09", "10:00")}]", characters)
        assertEquals("9 May.—My dearest Lucy", story.entries.single().preview)
    }

    @Test
    fun aLettersPreviewSkipsItsDatelineAndSalutation() {
        val letter = Entry(
            id = "l", order = 0, chapter = 5, type = DocumentType.LETTER, author = "mina_harker", recipient = null,
            date = LocalDate.of(1893, 5, 9), time = LocalTime.of(10, 0), timeEstimated = true, place = null, header = null,
            body = listOf(
                Block(BlockRole.DATELINE, listOf(Span("“9 May.", false))),
                Block(BlockRole.SALUTATION, listOf(Span("“My dearest Lucy,—", false))),
                Block(BlockRole.PARA, listOf(Span("“Forgive my long delay in writing.", false))),
            ),
        )
        assertEquals("“Forgive my long delay in writing.", letter.preview)
    }

    @Test
    fun entriesAreInStoryOrderNotBookOrder() {
        val json = "[${entry("late", 0, "1893-05-09", "21:00")}, ${entry("early", 1, "1893-05-09", "08:00")}," +
            " ${entry("next-day", 2, "1893-05-10", "07:00")}]"
        assertEquals(listOf("early", "late", "next-day"), decodeStory(json, characters).entries.map { it.id })
    }

    @Test
    fun theRealDatasetDecodes() {
        val out = File("../../data/out")
        val story = decodeStory(File(out, "entries.json").readText(), File(out, "characters.json").readText())
        assertEquals(story.entries.size, story.entries.map { it.id }.toSet().size)
        assertEquals("bram_stoker", story.entries[0].author) // the preface, 3 May 00:00
        assertEquals("1893-05-03-jonathan_harker-01", story.entries[1].id)
        assertEquals("NOTE", story.entries.last().header)
        assertEquals(true, story.entries.all { story.characters.containsKey(it.author) })
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: compilation FAILS with `Unresolved reference 'decodeStory'` (and `Entry`, `Block`, …).

- [ ] **Step 5: Implement the story model and decoder**

`app/app/src/main/java/com/jensen/draculanightbynight/content/Story.kt`:
```kotlin
package com.jensen.draculanightbynight.content

import java.time.LocalDate
import java.time.LocalTime

enum class DocumentType { DIARY, PHONOGRAPH, LETTER, TELEGRAM, NEWSPAPER, MEMO, LOG }

enum class BlockRole { NOTE, PARA, DATELINE, SALUTATION, SIGNOFF, VERSE }

data class Span(val text: String, val italic: Boolean)

data class Block(val role: BlockRole, val spans: List<Span>) {
    val text: String get() = spans.joinToString("") { it.text }
}

data class Character(val id: String, val name: String, val short: String)

data class Entry(
    val id: String,
    val order: Int,
    val chapter: Int,
    val type: DocumentType,
    val author: String,
    val recipient: String?,
    val date: LocalDate,
    val time: LocalTime,
    val timeEstimated: Boolean,
    val place: String?,
    val header: String?,
    val body: List<Block>,
) {
    /** First line of real text, for list rows: skips notes, datelines and salutations when it can. */
    val preview: String
        get() = (body.firstOrNull { it.role == BlockRole.PARA || it.role == BlockRole.VERSE }
            ?: body.firstOrNull { it.role != BlockRole.NOTE } ?: body.firstOrNull())
            ?.text?.replace('\n', ' ')?.take(PREVIEW_LENGTH).orEmpty()

    private companion object {
        const val PREVIEW_LENGTH = 140
    }
}

/** The whole novel: entries in story order (in-story date, time, then position in the book). */
class Story(entries: List<Entry>, val characters: Map<String, Character>) {
    val entries: List<Entry> = entries.sortedWith(compareBy<Entry>({ it.date }, { it.time }, { it.order }))
    private val byId = this.entries.associateBy { it.id }

    fun entry(id: String): Entry? = byId[id]

    fun nameOf(characterId: String?): String? = characterId?.let { characters[it]?.name ?: it }
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/content/StoryJson.kt`:
```kotlin
package com.jensen.draculanightbynight.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

@Serializable
private data class SpanJson(val text: String, val italic: Boolean)

@Serializable
private data class BlockJson(val role: String, val spans: List<SpanJson>)

@Serializable
private data class EntryJson(
    val id: String,
    val order: Int,
    val chapter: Int,
    val type: String,
    val author: String,
    val recipient: String? = null,
    val date: String,
    val time: String,
    val timeEstimated: Boolean,
    val place: String? = null,
    val header: String? = null,
    val body: List<BlockJson>,
)

@Serializable
private data class CharacterJson(val id: String, val name: String, val short: String)

private val json = Json { ignoreUnknownKeys = true }

/** Decodes data/out/entries.json and characters.json (the dataset contract) into a [Story]. */
fun decodeStory(entriesJson: String, charactersJson: String): Story {
    val characters = json.decodeFromString<List<CharacterJson>>(charactersJson)
        .associate { it.id to Character(it.id, it.name, it.short) }
    val entries = json.decodeFromString<List<EntryJson>>(entriesJson).map { e ->
        Entry(
            id = e.id,
            order = e.order,
            chapter = e.chapter,
            type = DocumentType.valueOf(e.type.uppercase()),
            author = e.author,
            recipient = e.recipient,
            date = LocalDate.parse(e.date),
            time = LocalTime.parse(e.time),
            timeEstimated = e.timeEstimated,
            place = e.place,
            header = e.header,
            body = e.body.map { b -> Block(BlockRole.valueOf(b.role.uppercase()), b.spans.map { Span(it.text, it.italic) }) },
        )
    }
    return Story(entries, characters)
}
```

- [ ] **Step 6: Run the tests and build the APK**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `StoryJsonTest` 5 tests, 0 failures; `app/app/build/outputs/apk/debug/app-debug.apk` exists and `unzip -l` on it lists `assets/entries.json` and `assets/characters.json` (and no `review.html`).

- [ ] **Step 7: CI**

`.github/workflows/app.yml`:
```yaml
name: app

on:
  pull_request:
  push:
    branches: [main]

jobs:
  app-tests:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: app
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - uses: android-actions/setup-android@v3
      - run: sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- [ ] **Step 8: Commit, push, PR** (see Workflow)

```bash
git add -A .gitignore .github/workflows/app.yml app
git commit -m "Add Android project, CI and story decoding

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: After the PR has merged, require the app check on `main`**

```bash
gh api -X PUT repos/kjmikkel/Dracula-Night-by-Night/branches/main/protection --input - <<'EOF'
{"required_status_checks": {"strict": false, "contexts": ["data-tests", "app-tests"]},
 "enforce_admins": false, "required_pull_request_reviews": null, "restrictions": null}
EOF
```
Expected: response lists both contexts. (Not before: until this PR merges, `app/` does not exist on other branches.)

---

### Task 2: Unlock engine

**Files:**
- Create: `app/app/src/main/java/com/jensen/draculanightbynight/domain/UnlockSchedule.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/TestStory.kt`, `.../domain/UnlockScheduleTest.kt`

**Interfaces:**
- Consumes: `Entry`, `Story`, `Block`, `BlockRole`, `Span`, `Character`, `DocumentType` (Task 1).
- Produces: `ReadingMode` (`Calendar(seasonYear: Int)`, `SameTempo(startDate: LocalDate)`, `Free`), `ReadingSettings(mode, skipAhead: Duration = ZERO)`, `UnlockSchedule.unlockInstant(entry, mode, zone): Instant?`, `UnlockSchedule.isUnlocked(entry, settings, now: Instant, zone): Boolean`, `UnlockSchedule.seasonYearFor(today: LocalDate): Int`, `UnlockSchedule.STORY_START`; test helper `TestStory.entry(...)`, `TestStory.small`, `TestStory.characters`.

- [ ] **Step 1: Write the test helper and the failing tests**

`app/app/src/test/java/com/jensen/draculanightbynight/TestStory.kt`:
```kotlin
package com.jensen.draculanightbynight

import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.content.Character
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Span
import com.jensen.draculanightbynight.content.Story
import java.time.LocalDate
import java.time.LocalTime

/** Small hand-built stories for tests. */
object TestStory {
    fun entry(
        id: String,
        month: Int,
        day: Int,
        time: String,
        author: String = "jonathan_harker",
        type: DocumentType = DocumentType.DIARY,
        order: Int = 0,
        text: String = "Text of $id.",
    ) = Entry(
        id = id, order = order, chapter = 1, type = type, author = author, recipient = null,
        date = LocalDate.of(1893, month, day), time = LocalTime.parse(time), timeEstimated = true,
        place = null, header = null, body = listOf(Block(BlockRole.PARA, listOf(Span(text, false)))),
    )

    val characters = mapOf(
        "jonathan_harker" to Character("jonathan_harker", "Jonathan Harker", "Jonathan"),
        "mina_harker" to Character("mina_harker", "Mina Harker", "Mina"),
    )

    /** 3 May 22:00, 4 May 14:45, 5 May 10:00 (Jonathan), 9 May 10:00 (Mina's letter). */
    val small = Story(
        listOf(
            entry("may03", 5, 3, "22:00", order = 0),
            entry("may04", 5, 4, "14:45", order = 1),
            entry("may05", 5, 5, "10:00", order = 2),
            entry("may09", 5, 9, "10:00", author = "mina_harker", type = DocumentType.LETTER, order = 3),
        ),
        characters,
    )
}
```

`app/app/src/test/java/com/jensen/draculanightbynight/domain/UnlockScheduleTest.kt`:
```kotlin
package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.TestStory.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UnlockScheduleTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")
    private val harkerArrives = entry("a", 5, 3, "22:00")

    @Test
    fun calendarModeUsesTheSeasonYearAndLocalTime() {
        val at = UnlockSchedule.unlockInstant(harkerArrives, ReadingMode.Calendar(2026), copenhagen)
        assertEquals(Instant.parse("2026-05-03T20:00:00Z"), at) // CEST is UTC+2
    }

    @Test
    fun sameTempoMakesThreeMayTheStartDay() {
        val mode = ReadingMode.SameTempo(LocalDate.of(2026, 10, 14))
        assertEquals(Instant.parse("2026-10-14T22:00:00Z"), UnlockSchedule.unlockInstant(harkerArrives, mode, ZoneOffset.UTC))
        val nineMay = entry("b", 5, 9, "10:00")
        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), UnlockSchedule.unlockInstant(nineMay, mode, ZoneOffset.UTC))
    }

    @Test
    fun readFreelyNeverLocks() {
        assertNull(UnlockSchedule.unlockInstant(harkerArrives, ReadingMode.Free, copenhagen))
        assertTrue(UnlockSchedule.isUnlocked(harkerArrives, ReadingSettings(ReadingMode.Free), Instant.EPOCH, copenhagen))
    }

    @Test
    fun theSameWallTimeIsADifferentInstantInAnotherZone() {
        val mode = ReadingMode.Calendar(2026)
        val tokyo = UnlockSchedule.unlockInstant(harkerArrives, mode, ZoneId.of("Asia/Tokyo"))
        val newYork = UnlockSchedule.unlockInstant(harkerArrives, mode, ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(13), Duration.between(tokyo, newYork))
    }

    @Test
    fun unlocksExactlyAtItsInstantAndNotBefore() {
        val settings = ReadingSettings(ReadingMode.Calendar(2026))
        val at = Instant.parse("2026-05-03T20:00:00Z")
        assertFalse(UnlockSchedule.isUnlocked(harkerArrives, settings, at.minusSeconds(1), copenhagen))
        assertTrue(UnlockSchedule.isUnlocked(harkerArrives, settings, at, copenhagen))
    }

    @Test
    fun skipAheadShiftsNowForward() {
        val settings = ReadingSettings(ReadingMode.Calendar(2026), skipAhead = Duration.ofHours(1))
        assertTrue(UnlockSchedule.isUnlocked(harkerArrives, settings, Instant.parse("2026-05-03T19:00:00Z"), copenhagen))
        assertFalse(UnlockSchedule.isUnlocked(harkerArrives, settings, Instant.parse("2026-05-03T18:59:59Z"), copenhagen))
    }

    @Test
    fun theAmbiguousHourWhenSummerTimeEndsUsesTheEarlierOffset() {
        // 25 October 2026 02:30 happens twice in Copenhagen; the first (CEST, UTC+2) wins.
        val sewardAtNight = entry("s", 10, 25, "02:30")
        val at = UnlockSchedule.unlockInstant(sewardAtNight, ReadingMode.Calendar(2026), copenhagen)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), at)
    }

    @Test
    fun seasonYearRollsOverAfterSixNovember() {
        assertEquals(2026, UnlockSchedule.seasonYearFor(LocalDate.of(2026, 1, 10)))
        assertEquals(2026, UnlockSchedule.seasonYearFor(LocalDate.of(2026, 11, 6)))
        assertEquals(2027, UnlockSchedule.seasonYearFor(LocalDate.of(2026, 11, 7)))
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: compilation FAILS with `Unresolved reference 'UnlockSchedule'` / `'ReadingMode'`.

- [ ] **Step 3: Implement**

`app/app/src/main/java/com/jensen/draculanightbynight/domain/UnlockSchedule.kt`:
```kotlin
package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Entry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed interface ReadingMode {
    /** Entries unlock on their calendar day and in-story time, in [seasonYear]. */
    data class Calendar(val seasonYear: Int) : ReadingMode

    /** Day one of the story (3 May) is [startDate]; the gaps between entries are kept. */
    data class SameTempo(val startDate: LocalDate) : ReadingMode

    /** Everything is unlocked. */
    data object Free : ReadingMode
}

data class ReadingSettings(val mode: ReadingMode, val skipAhead: Duration = Duration.ZERO)

object UnlockSchedule {
    val STORY_START: LocalDate = LocalDate.of(1893, 5, 3)
    private val SEASON_END: MonthDay = MonthDay.of(11, 6)

    /** When [entry] unlocks for a reader in [zone]; null means it is always unlocked. */
    fun unlockInstant(entry: Entry, mode: ReadingMode, zone: ZoneId): Instant? = when (mode) {
        ReadingMode.Free -> null
        is ReadingMode.Calendar -> entry.date.withYear(mode.seasonYear).atTime(entry.time).atZone(zone).toInstant()
        is ReadingMode.SameTempo -> mode.startDate
            .plusDays(ChronoUnit.DAYS.between(STORY_START, entry.date))
            .atTime(entry.time).atZone(zone).toInstant()
    }

    fun isUnlocked(entry: Entry, settings: ReadingSettings, now: Instant, zone: ZoneId): Boolean {
        val at = unlockInstant(entry, settings.mode, zone) ?: return true
        return !at.isAfter(now.plus(settings.skipAhead))
    }

    /** The season a reader choosing Calendar mode on [today] will follow: this year, or next year after 6 November. */
    fun seasonYearFor(today: LocalDate): Int =
        if (MonthDay.from(today).isAfter(SEASON_END)) today.year + 1 else today.year
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `UnlockScheduleTest` 8 tests, 0 failures.

- [ ] **Step 5: Commit, push, PR**

```bash
git add app/app/src
git commit -m "Add the unlock engine for calendar, same-tempo and free reading

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Reader's saved state

**Files:**
- Create: `app/app/src/main/java/com/jensen/draculanightbynight/user/UserState.kt`, `.../user/UserRepository.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/user/UserRepositoryTest.kt`

**Interfaces:**
- Consumes: `ReadingMode`, `ReadingSettings` (Task 2).
- Produces: `EntryProgress(readAtMillis: Long?, blockIndex: Int, blockOffset: Int, bookmarked: Boolean)`; `UserState` with `readingMode: ReadingMode?`, `settings: ReadingSettings?`, `withMode(mode)`, `progressOf(id)`, `updateProgress(id, change)`, `skipAheadMinutes`; `UserStateSerializer` (DataStore `Serializer<UserState>`); `UserRepository(store: DataStore<UserState>, clock: Clock)` with `state: Flow<UserState>`, `suspend ensureMode()`, `setMode(mode)`, `markRead(id)`, `savePosition(id, blockIndex, blockOffset)`, `toggleBookmark(id)`.

- [ ] **Step 1: Write the failing tests**

`app/app/src/test/java/com/jensen/draculanightbynight/user/UserRepositoryTest.kt`:
```kotlin
package com.jensen.draculanightbynight.user

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UserRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-14T18:00:00Z"))

    private fun file() = File(tmp.root, "user.json")

    private fun TestScope.repository(scope: CoroutineScope = backgroundScope) = UserRepository(
        DataStoreFactory.create(
            serializer = UserStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserState() },
            scope = scope,
            produceFile = ::file,
        ),
        clock,
    )

    @Test
    fun aNewReaderStartsInSameTempoFromToday() = runTest {
        val repo = repository()
        repo.ensureMode()
        assertEquals(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)), repo.state.first().readingMode)
    }

    @Test
    fun ensureModeKeepsAnExistingChoice() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Free)
        repo.ensureMode()
        assertEquals(ReadingMode.Free, repo.state.first().readingMode)
    }

    @Test
    fun changingModeResetsSkipAhead() = runTest {
        val repo = repository()
        repo.setMode(ReadingMode.Calendar(2026))
        assertEquals(ReadingMode.Calendar(2026), repo.state.first().settings?.mode)
        assertEquals(0L, repo.state.first().skipAheadMinutes)
    }

    @Test
    fun markReadKeepsTheFirstReadTime() = runTest {
        val repo = repository()
        repo.markRead("a")
        clock.now = Instant.parse("2026-10-15T09:00:00Z")
        repo.markRead("a")
        assertEquals(Instant.parse("2026-10-14T18:00:00Z").toEpochMilli(), repo.state.first().progressOf("a").readAtMillis)
    }

    @Test
    fun positionAndBookmarkAreStoredPerEntry() = runTest {
        val repo = repository()
        repo.savePosition("a", 12, 340)
        repo.toggleBookmark("a")
        val a = repo.state.first().progressOf("a")
        assertEquals(12 to 340, a.blockIndex to a.blockOffset)
        assertTrue(a.bookmarked)
        assertFalse(repo.state.first().progressOf("b").bookmarked)
    }

    @Test
    fun aCorruptFileStartsOverInsteadOfCrashing() = runTest {
        file().writeText("{ not json")
        assertNull(repository().state.first().readingMode)
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() = runTest {
        file().writeText("""{"mode": "free", "fontScale": 1.2, "progress": {"gone-id": {"bookmarked": true}}}""")
        val state = repository().state.first()
        assertEquals(ReadingMode.Free, state.readingMode)
        assertTrue(state.progressOf("gone-id").bookmarked)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: compilation FAILS with `Unresolved reference 'UserRepository'` / `'UserStateSerializer'`.

- [ ] **Step 3: Implement**

`app/app/src/main/java/com/jensen/draculanightbynight/user/UserState.kt` (the mode constants are file-level: a `@Serializable` class cannot have a private companion object, because `serializer()` lives there):
```kotlin
package com.jensen.draculanightbynight.user

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDate

private const val MODE_CALENDAR = "calendar"
private const val MODE_TEMPO = "tempo"
private const val MODE_FREE = "free"

/** Per-entry reading progress, keyed by the dataset's stable entry id. */
@Serializable
data class EntryProgress(
    val readAtMillis: Long? = null,
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val bookmarked: Boolean = false,
)

/**
 * Everything the app remembers about its reader. Stored as JSON, separately from the story,
 * so a dataset update never touches it; progress for ids that no longer exist is simply ignored.
 */
@Serializable
data class UserState(
    val mode: String? = null,
    val seasonYear: Int? = null,
    val tempoStart: String? = null,
    val skipAheadMinutes: Long = 0,
    val progress: Map<String, EntryProgress> = emptyMap(),
) {
    val readingMode: ReadingMode?
        get() = when (mode) {
            MODE_CALENDAR -> seasonYear?.let { ReadingMode.Calendar(it) }
            MODE_TEMPO -> tempoStart?.let { ReadingMode.SameTempo(LocalDate.parse(it)) }
            MODE_FREE -> ReadingMode.Free
            else -> null
        }

    val settings: ReadingSettings?
        get() = readingMode?.let { ReadingSettings(it, Duration.ofMinutes(skipAheadMinutes)) }

    fun withMode(newMode: ReadingMode): UserState = when (newMode) {
        is ReadingMode.Calendar -> copy(mode = MODE_CALENDAR, seasonYear = newMode.seasonYear, skipAheadMinutes = 0)
        is ReadingMode.SameTempo -> copy(mode = MODE_TEMPO, tempoStart = newMode.startDate.toString(), skipAheadMinutes = 0)
        ReadingMode.Free -> copy(mode = MODE_FREE, skipAheadMinutes = 0)
    }

    fun progressOf(id: String): EntryProgress = progress[id] ?: EntryProgress()

    fun updateProgress(id: String, change: (EntryProgress) -> EntryProgress): UserState =
        copy(progress = progress + (id to change(progressOf(id))))
}

object UserStateSerializer : Serializer<UserState> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: UserState = UserState()

    override suspend fun readFrom(input: InputStream): UserState = try {
        json.decodeFromString(UserState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable user state", e)
    }

    override suspend fun writeTo(t: UserState, output: OutputStream) {
        output.write(json.encodeToString(UserState.serializer(), t).encodeToByteArray())
    }
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/user/UserRepository.kt`:
```kotlin
package com.jensen.draculanightbynight.user

import androidx.datastore.core.DataStore
import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

class UserRepository(private val store: DataStore<UserState>, private val clock: Clock) {
    val state: Flow<UserState> = store.data

    /** First launch: until onboarding exists, a new reader starts in Same tempo from today. */
    suspend fun ensureMode() {
        store.updateData { s -> if (s.readingMode == null) s.withMode(ReadingMode.SameTempo(LocalDate.now(clock))) else s }
    }

    suspend fun setMode(mode: ReadingMode) {
        store.updateData { it.withMode(mode) }
    }

    /** Records the first time an entry was opened; reopening keeps the original time. */
    suspend fun markRead(id: String) {
        store.updateData { s -> s.updateProgress(id) { p -> if (p.readAtMillis != null) p else p.copy(readAtMillis = clock.millis()) } }
    }

    suspend fun savePosition(id: String, blockIndex: Int, blockOffset: Int) {
        store.updateData { s -> s.updateProgress(id) { it.copy(blockIndex = blockIndex, blockOffset = blockOffset) } }
    }

    suspend fun toggleBookmark(id: String) {
        store.updateData { s -> s.updateProgress(id) { it.copy(bookmarked = !it.bookmarked) } }
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `UserRepositoryTest` 7 tests, 0 failures.

- [ ] **Step 5: Commit, push, PR**

```bash
git add app/app/src
git commit -m "Store the reader's mode and per-entry progress with DataStore

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Timeline logic

**Files:**
- Create: `app/app/src/main/java/com/jensen/draculanightbynight/timeline/Timeline.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/timeline/TimelineTest.kt`

**Interfaces:**
- Consumes: `Story`, `DocumentType` (Task 1); `ReadingSettings`, `UnlockSchedule` (Task 2); `UserState`, `EntryProgress` (Task 3).
- Produces: `TimelineRow(id, authorName, type, timeLabel, timeEstimated, place, preview, unread, bookmarked)`, `TimelineDay(date, rows)`, `NextTeaser(authorName, type, unlocksAt: ZonedDateTime)`, `TimelineState(days, next, firstUnreadId, unlockedCount, total)`, `buildTimeline(story, user, settings, now: Instant, zone): TimelineState`.

- [ ] **Step 1: Write the failing tests**

`app/app/src/test/java/com/jensen/draculanightbynight/timeline/TimelineTest.kt`:
```kotlin
package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TimelineTest {
    private val utc = ZoneOffset.UTC
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))

    private fun timeline(now: String, user: UserState = UserState(), settings: ReadingSettings = tempo) =
        buildTimeline(TestStory.small, user, settings, Instant.parse(now), utc)

    @Test
    fun beforeTheFirstEntryNothingIsUnlockedAndTheTeaserNamesIt() {
        val t = timeline("2026-10-14T21:59:00Z")
        assertTrue(t.days.isEmpty())
        assertEquals(NextTeaser("Jonathan Harker", DocumentType.DIARY, ZonedDateTime.parse("2026-10-14T22:00Z")), t.next)
    }

    @Test
    fun unlockedEntriesAreGroupedByStoryDay() {
        val t = timeline("2026-10-16T12:00:00Z") // day 3 at noon: 3 May, 4 May, 5 May 10:00
        assertEquals(listOf(LocalDate.of(1893, 5, 3), LocalDate.of(1893, 5, 4), LocalDate.of(1893, 5, 5)), t.days.map { it.date })
        assertEquals(3, t.unlockedCount)
        assertEquals(4, t.total)
        assertEquals("Mina Harker", t.next?.authorName)
    }

    @Test
    fun readFreelyUnlocksEverythingAndHasNoTeaser() {
        val t = timeline("2000-01-01T00:00:00Z", settings = ReadingSettings(ReadingMode.Free))
        assertEquals(4, t.unlockedCount)
        assertNull(t.next)
    }

    @Test
    fun rowsShowUnreadAndBookmarkStateAndTheFirstUnread() {
        val user = UserState(progress = mapOf("may03" to EntryProgress(readAtMillis = 1, bookmarked = true)))
        val t = timeline("2026-10-16T12:00:00Z", user)
        val first = t.days.first().rows.single()
        assertFalse(first.unread)
        assertTrue(first.bookmarked)
        assertEquals("may04", t.firstUnreadId)
        assertEquals("22:00", first.timeLabel)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: compilation FAILS with `Unresolved reference 'buildTimeline'`.

- [ ] **Step 3: Implement**

`app/app/src/main/java/com/jensen/draculanightbynight/timeline/Timeline.kt`:
```kotlin
package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.UnlockSchedule
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class TimelineRow(
    val id: String,
    val authorName: String,
    val type: DocumentType,
    val timeLabel: String,
    val timeEstimated: Boolean,
    val place: String?,
    val preview: String,
    val unread: Boolean,
    val bookmarked: Boolean,
)

data class TimelineDay(val date: LocalDate, val rows: List<TimelineRow>)

/** The next locked entry: who writes, what kind of document, and when it unlocks (no text). */
data class NextTeaser(val authorName: String, val type: DocumentType, val unlocksAt: ZonedDateTime)

data class TimelineState(
    val days: List<TimelineDay>,
    val next: NextTeaser?,
    val firstUnreadId: String?,
    val unlockedCount: Int,
    val total: Int,
)

private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

/** What the timeline shows at [now]: unlocked entries grouped by in-story day, plus the next locked one. */
fun buildTimeline(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId): TimelineState {
    val (unlocked, locked) = story.entries.partition { UnlockSchedule.isUnlocked(it, settings, now, zone) }
    val rows = unlocked.map { e ->
        val progress = user.progressOf(e.id)
        e.date to TimelineRow(
            id = e.id,
            authorName = story.nameOf(e.author) ?: e.author,
            type = e.type,
            timeLabel = e.time.format(HOUR_MINUTE),
            timeEstimated = e.timeEstimated,
            place = e.place,
            preview = e.preview,
            unread = progress.readAtMillis == null,
            bookmarked = progress.bookmarked,
        )
    }
    val days = rows.groupBy({ it.first }, { it.second }).map { (date, dayRows) -> TimelineDay(date, dayRows) }
    val next = locked
        .mapNotNull { e -> UnlockSchedule.unlockInstant(e, settings.mode, zone)?.let { e to it } }
        .minByOrNull { it.second }
        ?.let { (e, at) -> NextTeaser(story.nameOf(e.author) ?: e.author, e.type, at.atZone(zone)) }
    return TimelineState(
        days = days,
        next = next,
        firstUnreadId = rows.firstOrNull { it.second.unread }?.second?.id,
        unlockedCount = unlocked.size,
        total = story.entries.size,
    )
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `TimelineTest` 4 tests, 0 failures.

- [ ] **Step 5: Commit, push, PR**

```bash
git add app/app/src
git commit -m "Build the day-by-day timeline and next-entry teaser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Reader logic

**Files:**
- Create: `app/app/src/main/java/com/jensen/draculanightbynight/reader/Reader.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/reader/ReaderTest.kt`

**Interfaces:**
- Consumes: as Task 4.
- Produces: `HeaderStyle` (`DIARY, LETTER, TELEGRAM, NEWSPAPER, MEMO, LOG`), `headerStyle(type)`, `ReaderState(entry, authorName, recipientName, style, previousId, nextId, startBlock, startOffset, bookmarked)`, `buildReader(story, user, settings, now, zone, id): ReaderState?` (null = unknown or locked).

- [ ] **Step 1: Write the failing tests**

`app/app/src/test/java/com/jensen/draculanightbynight/reader/ReaderTest.kt`:
```kotlin
package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.TestStory
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReaderTest {
    private val tempo = ReadingSettings(ReadingMode.SameTempo(LocalDate.of(2026, 10, 14)))
    private val dayThreeNoon = Instant.parse("2026-10-16T12:00:00Z") // 3, 4 and 5 May unlocked

    private fun reader(id: String, user: UserState = UserState()) =
        buildReader(TestStory.small, user, tempo, dayThreeNoon, ZoneOffset.UTC, id)

    @Test
    fun aLockedOrUnknownEntryCannotBeOpened() {
        assertNull(reader("may09"))
        assertNull(reader("nope"))
    }

    @Test
    fun previousAndNextStopAtTheLockedFrontier() {
        val middle = reader("may04")!!
        assertEquals("may03" to "may05", middle.previousId to middle.nextId)
        val last = reader("may05")!!
        assertNull(last.nextId)
    }

    @Test
    fun resumesWhereTheReaderLeftOff() {
        val user = UserState(progress = mapOf("may04" to EntryProgress(blockIndex = 0, blockOffset = 120, bookmarked = true)))
        val r = reader("may04", user)!!
        assertEquals(0 to 120, r.startBlock to r.startOffset)
        assertTrue(r.bookmarked)
    }

    @Test
    fun aSavedPositionPastTheEndIsClamped() {
        val user = UserState(progress = mapOf("may04" to EntryProgress(blockIndex = 99)))
        assertEquals(0, reader("may04", user)!!.startBlock)
    }

    @Test
    fun phonographDiariesShareTheDiaryStyle() {
        assertEquals(HeaderStyle.DIARY, headerStyle(DocumentType.PHONOGRAPH))
        assertEquals(HeaderStyle.TELEGRAM, headerStyle(DocumentType.TELEGRAM))
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: compilation FAILS with `Unresolved reference 'buildReader'`.

- [ ] **Step 3: Implement**

`app/app/src/main/java/com/jensen/draculanightbynight/reader/Reader.kt`:
```kotlin
package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.UnlockSchedule
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/** How an entry's heading is framed; the body always uses the same reading font. */
enum class HeaderStyle { DIARY, LETTER, TELEGRAM, NEWSPAPER, MEMO, LOG }

fun headerStyle(type: DocumentType): HeaderStyle = when (type) {
    DocumentType.DIARY, DocumentType.PHONOGRAPH -> HeaderStyle.DIARY
    DocumentType.LETTER -> HeaderStyle.LETTER
    DocumentType.TELEGRAM -> HeaderStyle.TELEGRAM
    DocumentType.NEWSPAPER -> HeaderStyle.NEWSPAPER
    DocumentType.MEMO -> HeaderStyle.MEMO
    DocumentType.LOG -> HeaderStyle.LOG
}

data class ReaderState(
    val entry: Entry,
    val authorName: String,
    val recipientName: String?,
    val style: HeaderStyle,
    val previousId: String?,
    val nextId: String?,
    val startBlock: Int,
    val startOffset: Int,
    val bookmarked: Boolean,
)

/** The reader for entry [id], or null if it does not exist or is still locked. */
fun buildReader(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId, id: String): ReaderState? {
    val unlocked = story.entries.filter { UnlockSchedule.isUnlocked(it, settings, now, zone) }
    val index = unlocked.indexOfFirst { it.id == id }
    if (index < 0) return null
    val entry = unlocked[index]
    val progress = user.progressOf(id)
    return ReaderState(
        entry = entry,
        authorName = story.nameOf(entry.author) ?: entry.author,
        recipientName = story.nameOf(entry.recipient),
        style = headerStyle(entry.type),
        previousId = unlocked.getOrNull(index - 1)?.id,
        nextId = unlocked.getOrNull(index + 1)?.id,
        startBlock = progress.blockIndex.coerceIn(0, (entry.body.size - 1).coerceAtLeast(0)),
        startOffset = progress.blockOffset,
        bookmarked = progress.bookmarked,
    )
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `ReaderTest` 5 tests, 0 failures.

- [ ] **Step 5: Commit, push, PR**

```bash
git add app/app/src
git commit -m "Add reader logic: unlocked neighbours, resume position, header styles

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: App shell and screens

**Files:**
- Create: `app/app/src/main/java/com/jensen/draculanightbynight/content/StoryRepository.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/content/StoryRepositoryTest.kt`
- Create: `.../AppContainer.kt`, `.../MainActivity.kt`, `.../ui/Theme.kt`, `.../timeline/TimelineViewModel.kt`, `.../timeline/TimelineScreen.kt`, `.../timeline/ModeDialog.kt`, `.../reader/ReaderViewModel.kt`, `.../reader/ReaderScreen.kt`
- Modify: `app/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: everything above.
- Produces: `StoryRepository(load: suspend () -> Story)` with `suspend story()`; `AppContainer(context)` with `clock`, `scope`, `stories`, `users`, `minuteTicks(): Flow<Instant>`; `DraculaApp`; `TimelineViewModel`, `TimelineUi`; `ReaderViewModel`, `ReaderUi`; `DraculaTheme`; `typeLabel(type)`.

View models and screens are glue over the tested functions; they are verified on the emulator in Step 6 rather than by JVM tests.

- [ ] **Step 1: Write the failing test for the story cache**

`app/app/src/test/java/com/jensen/draculanightbynight/content/StoryRepositoryTest.kt`:
```kotlin
package com.jensen.draculanightbynight.content

import com.jensen.draculanightbynight.TestStory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StoryRepositoryTest {
    @Test
    fun loadsOnceEvenWhenAskedConcurrently() = runTest {
        var loads = 0
        val repo = StoryRepository { loads++; TestStory.small }
        val stories = List(5) { async { repo.story() } }.awaitAll()
        assertEquals(1, loads)
        stories.forEach { assertSame(TestStory.small, it) }
    }
}
```

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: compilation FAILS with `Unresolved reference 'StoryRepository'`.

- [ ] **Step 2: Implement the story cache and the app container**

`app/app/src/main/java/com/jensen/draculanightbynight/content/StoryRepository.kt`:
```kotlin
package com.jensen.draculanightbynight.content

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loads the bundled story once, on first use, and keeps it in memory (234 entries, about 1 MB). */
class StoryRepository(private val load: suspend () -> Story) {
    private val mutex = Mutex()
    private var cached: Story? = null

    suspend fun story(): Story = cached ?: mutex.withLock { cached ?: load().also { cached = it } }
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/AppContainer.kt`:
```kotlin
package com.jensen.draculanightbynight

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.content.decodeStory
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.UserStateSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant

/** Hand-wired dependencies; one per process. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()

    /** Outlives every screen: writes started here finish even if the screen that asked is closed. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val stories = StoryRepository {
        withContext(Dispatchers.IO) {
            val assets = context.assets
            decodeStory(
                assets.open("entries.json").bufferedReader().use { it.readText() },
                assets.open("characters.json").bufferedReader().use { it.readText() },
            )
        }
    }

    val users = UserRepository(
        DataStoreFactory.create(
            serializer = UserStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserState() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(context.filesDir, "user_state.json") },
        ),
        clock,
    )

    /** The current instant now and at the start of every following minute, so locked entries open on time. */
    fun minuteTicks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(MINUTE_MILLIS - clock.millis() % MINUTE_MILLIS)
        }
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

class DraculaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `StoryRepositoryTest` 1 test, 0 failures.

- [ ] **Step 3: View models**

`app/app/src/main/java/com/jensen/draculanightbynight/timeline/TimelineViewModel.kt`:
```kotlin
package com.jensen.draculanightbynight.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TimelineUi {
    data object Loading : TimelineUi
    data class Ready(val timeline: TimelineState, val mode: ReadingMode) : TimelineUi
}

class TimelineViewModel(private val app: AppContainer) : ViewModel() {
    val state: StateFlow<TimelineUi> = combine(flow { emit(app.stories.story()) }, app.users.state, app.minuteTicks()) { story, user, now ->
        val settings = user.settings ?: return@combine TimelineUi.Loading
        TimelineUi.Ready(buildTimeline(story, user, settings, now, app.clock.zone), settings.mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimelineUi.Loading)

    init {
        viewModelScope.launch { app.users.ensureMode() }
    }

    fun setMode(mode: ReadingMode) {
        viewModelScope.launch { app.users.setMode(mode) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderViewModel.kt` (`savePosition` runs in `app.scope`: the final save happens as the screen closes, after `viewModelScope` has been cancelled — saving in `viewModelScope` silently loses it):
```kotlin
package com.jensen.draculanightbynight.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jensen.draculanightbynight.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderUi {
    data object Loading : ReaderUi
    data object Locked : ReaderUi
    data class Ready(val reader: ReaderState) : ReaderUi
}

class ReaderViewModel(private val app: AppContainer, private val entryId: String) : ViewModel() {
    val state: StateFlow<ReaderUi> = combine(flow { emit(app.stories.story()) }, app.users.state, app.minuteTicks()) { story, user, now ->
        val settings = user.settings ?: return@combine ReaderUi.Loading
        buildReader(story, user, settings, now, app.clock.zone, entryId)?.let { ReaderUi.Ready(it) } ?: ReaderUi.Locked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReaderUi.Loading)

    init {
        viewModelScope.launch { app.users.markRead(entryId) }
    }

    /** Runs in the app scope: the last save happens as the screen closes, after viewModelScope is cancelled. */
    fun savePosition(blockIndex: Int, blockOffset: Int) {
        app.scope.launch { app.users.savePosition(entryId, blockIndex, blockOffset) }
    }

    fun toggleBookmark() {
        viewModelScope.launch { app.users.toggleBookmark(entryId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
```

- [ ] **Step 4: Theme and screens**

`app/app/src/main/java/com/jensen/draculanightbynight/ui/Theme.kt` (all `surfaceContainer*` roles are set, otherwise cards, dialogs and bars fall back to Material's lavender):
```kotlin
package com.jensen.draculanightbynight.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Restrained gothic baseline: oxblood on parchment, bone on near-black. Plan 2 adds fonts and ornaments.
private val Light = lightColorScheme(
    primary = Color(0xFF7A1F24),
    onPrimary = Color(0xFFFBF6EC),
    secondary = Color(0xFF75655A),
    background = Color(0xFFF4ECDC),
    onBackground = Color(0xFF2A1F1A),
    surface = Color(0xFFFBF6EC),
    onSurface = Color(0xFF2A1F1A),
    surfaceVariant = Color(0xFFEADFCB),
    onSurfaceVariant = Color(0xFF5C4D43),
    surfaceContainerLowest = Color(0xFFFFFBF3),
    surfaceContainerLow = Color(0xFFF8F0E2),
    surfaceContainer = Color(0xFFF1E7D6),
    surfaceContainerHigh = Color(0xFFEBE0CD),
    surfaceContainerHighest = Color(0xFFE5D9C4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD06A6A),
    onPrimary = Color(0xFF1D1718),
    secondary = Color(0xFFA39282),
    background = Color(0xFF141011),
    onBackground = Color(0xFFECE2D4),
    surface = Color(0xFF1D1718),
    onSurface = Color(0xFFECE2D4),
    surfaceVariant = Color(0xFF2A2223),
    onSurfaceVariant = Color(0xFFBFAF9F),
    surfaceContainerLowest = Color(0xFF0F0C0D),
    surfaceContainerLow = Color(0xFF1A1516),
    surfaceContainer = Color(0xFF211A1B),
    surfaceContainerHigh = Color(0xFF2B2324),
    surfaceContainerHighest = Color(0xFF362C2D),
)

private val Serif = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    )
}

val TelegramStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 1.sp)

@Composable
fun DraculaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Serif, content = content)
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/timeline/TimelineScreen.kt`:
```kotlin
package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.DocumentType
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("d MMMM")
private val UNLOCKS = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")

fun typeLabel(type: DocumentType): String = when (type) {
    DocumentType.DIARY -> "Journal"
    DocumentType.PHONOGRAPH -> "Phonograph diary"
    DocumentType.LETTER -> "Letter"
    DocumentType.TELEGRAM -> "Telegram"
    DocumentType.NEWSPAPER -> "Newspaper"
    DocumentType.MEMO -> "Memorandum"
    DocumentType.LOG -> "Ship's log"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: TimelineViewModel, onOpen: (String) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var choosingMode by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dracula Night by Night") },
                actions = { TextButton(onClick = { choosingMode = true }) { Text("Mode") } },
            )
        },
    ) { padding ->
        when (val state = ui) {
            TimelineUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is TimelineUi.Ready -> {
                Timeline(state.timeline, onOpen, Modifier.padding(padding))
                if (choosingMode) {
                    ModeDialog(current = state.mode, onDismiss = { choosingMode = false }) {
                        viewModel.setMode(it)
                        choosingMode = false
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(state: TimelineState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Item index of each row: one header item per day, then its rows.
    val firstUnreadIndex = remember(state.firstUnreadId, state.days) {
        var index = 0
        var found = -1
        for (day in state.days) {
            index++
            for (row in day.rows) {
                if (row.id == state.firstUnreadId) found = index
                index++
            }
        }
        found
    }
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(firstUnreadIndex) {
        if (!scrolled && firstUnreadIndex > 0) listState.scrollToItem((firstUnreadIndex - 1).coerceAtLeast(0))
        scrolled = true
    }

    LazyColumn(modifier.fillMaxSize(), state = listState) {
        if (state.days.isEmpty()) {
            item { Text("The story has not begun yet.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        for (day in state.days) {
            item(key = "day-${day.date}") {
                Text(
                    day.date.format(DAY),
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(day.rows, key = { it.id }) { row ->
                EntryRow(row, onClick = { onOpen(row.id) })
                HorizontalDivider()
            }
        }
        item(key = "next") { NextCard(state) }
    }
}

@Composable
private fun EntryRow(row: TimelineRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                row.timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (row.timeEstimated) FontStyle.Italic else FontStyle.Normal,
                color = if (row.timeEstimated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.unread) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = "Unread" })
                }
                Text("${row.authorName} · ${typeLabel(row.type)}${row.place?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelLarge)
                if (row.bookmarked) Text("★", color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "Bookmarked" })
            }
            Text(row.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NextCard(state: TimelineState) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            val next = state.next
            if (next == null) {
                Text("${state.unlockedCount} of ${state.total} entries unlocked.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${next.authorName} · ${typeLabel(next.type)}", style = MaterialTheme.typography.titleMedium)
                Text(next.unlocksAt.format(UNLOCKS), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/timeline/ModeDialog.kt`:
```kotlin
package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.UnlockSchedule
import java.time.LocalDate

/** Minimal mode picker; plan 2 replaces it with onboarding and a settings screen. */
@Composable
fun ModeDialog(current: ReadingMode, onDismiss: () -> Unit, onChoose: (ReadingMode) -> Unit) {
    val today = LocalDate.now()
    val options = listOf(
        "On the actual days" to ReadingMode.Calendar(UnlockSchedule.seasonYearFor(today)),
        "In the same tempo, starting today" to ReadingMode.SameTempo(today),
        "Read freely" to ReadingMode.Free,
    )
    var selected by remember { mutableStateOf(options.indexOfFirst { it.second::class == current::class }.coerceAtLeast(0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to read") },
        text = {
            Column {
                options.forEachIndexed { i, (label, _) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = i == selected, role = Role.RadioButton) { selected = i }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Changing mode keeps what you have read.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(options[selected].second) }) { Text("Choose") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderScreen.kt`:
```kotlin
package com.jensen.draculanightbynight.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.timeline.typeLabel
import com.jensen.draculanightbynight.ui.TelegramStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.time.format.DateTimeFormatter

private val STORY_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy")
private const val SAVE_DEBOUNCE_MILLIS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val ready = ui as? ReaderUi.Ready
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ready?.reader?.authorName.orEmpty()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
                actions = {
                    if (ready != null) {
                        TextButton(onClick = viewModel::toggleBookmark) { Text(if (ready.reader.bookmarked) "★ Bookmarked" else "☆ Bookmark") }
                    }
                },
            )
        },
        bottomBar = {
            if (ready != null) {
                BottomAppBar {
                    TextButton(onClick = { ready.reader.previousId?.let(onOpen) }, enabled = ready.reader.previousId != null) { Text("‹ Previous") }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { ready.reader.nextId?.let(onOpen) }, enabled = ready.reader.nextId != null) { Text("Next ›") }
                }
            }
        },
    ) { padding ->
        when (val state = ui) {
            ReaderUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            ReaderUi.Locked -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("This entry has not arrived yet.") }
            is ReaderUi.Ready -> Body(state.reader, viewModel, Modifier.padding(padding))
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun Body(reader: ReaderState, viewModel: ReaderViewModel, modifier: Modifier) {
    // Item 0 is the header; block i is item i + 1.
    val listState = rememberLazyListState(reader.startBlock + if (reader.startBlock > 0 || reader.startOffset > 0) 1 else 0, reader.startOffset)
    // Save while reading (debounced) and once more on leaving, so a killed app still resumes close by.
    LaunchedEffect(reader.entry.id) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(SAVE_DEBOUNCE_MILLIS)
            .collect { (index, offset) -> viewModel.savePosition((index - 1).coerceAtLeast(0), offset) }
    }
    DisposableEffect(reader.entry.id) {
        onDispose { viewModel.savePosition((listState.firstVisibleItemIndex - 1).coerceAtLeast(0), listState.firstVisibleItemScrollOffset) }
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), state = listState) {
        item { Header(reader) }
        itemsIndexed(reader.entry.body) { _, block -> BlockText(block) }
    }
}

@Composable
private fun Header(reader: ReaderState) {
    val e = reader.entry
    val time = if (e.timeEstimated) "about ${e.time}" else "${e.time}"
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        when (reader.style) {
            HeaderStyle.TELEGRAM -> Text(
                "TELEGRAM",
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                style = TelegramStyle,
                textAlign = TextAlign.Center,
            )
            HeaderStyle.NEWSPAPER -> {
                HorizontalDivider()
                Text(e.header?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Newspaper",
                    Modifier.fillMaxWidth().padding(vertical = 6.dp), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                HorizontalDivider()
            }
            else -> Text(e.header ?: typeLabel(e.type), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (reader.style == HeaderStyle.LETTER || reader.style == HeaderStyle.TELEGRAM) {
            Text("From ${reader.authorName}${reader.recipientName?.let { " to $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text("${e.date.format(STORY_DATE)} · $time${e.place?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun annotated(block: Block): AnnotatedString = buildAnnotatedString {
    for (span in block.spans) {
        if (span.italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) } else append(span.text)
    }
}

@Composable
private fun BlockText(block: Block) {
    val body = MaterialTheme.typography.bodyLarge
    when (block.role) {
        BlockRole.NOTE -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        BlockRole.DATELINE, BlockRole.SIGNOFF -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = body, textAlign = TextAlign.End)
        BlockRole.VERSE -> Text(annotated(block), Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 12.dp), style = body, fontStyle = FontStyle.Italic)
        BlockRole.SALUTATION, BlockRole.PARA -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = body)
    }
}
```

`app/app/src/main/java/com/jensen/draculanightbynight/MainActivity.kt`:
```kotlin
package com.jensen.draculanightbynight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.timeline.TimelineScreen
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import com.jensen.draculanightbynight.ui.DraculaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as DraculaApp).container
        setContent { DraculaTheme { AppNavigation(app) } }
    }
}

@Composable
private fun AppNavigation(app: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "timeline") {
        composable("timeline") {
            TimelineScreen(viewModel { TimelineViewModel(app) }, onOpen = { nav.navigate("reader/$it") })
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ReaderScreen(
                viewModel(key = id) { ReaderViewModel(app, id) },
                onBack = { nav.popBackStack() },
                onOpen = { next -> nav.navigate("reader/$next") { popUpTo("timeline") } },
            )
        }
    }
}
```

Replace `app/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".DraculaApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 5: Build and run all tests**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; 30 tests, 0 failures.

- [ ] **Step 6: Verify on the emulator**

```bash
SDK="C:/Users/kjmik/AppData/Local/Android/Sdk"; ADB="$SDK/platform-tools/adb.exe"
"$SDK/emulator/emulator.exe" -avd Medium_Phone_API_37.0 -no-snapshot-save -no-boot-anim &   # leave running
"$ADB" wait-for-device
"$ADB" uninstall com.jensen.draculanightbynight || true
"$ADB" install app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -W -n com.jensen.draculanightbynight/.MainActivity
```
Check each, with `"$ADB" exec-out screencap -p > shot.png` as evidence:
1. **First launch:** the timeline shows only the preface (Bram Stoker · Memorandum, 3 May, 00:00) and a *Next* card naming Jonathan Harker's journal today at 22:00 (Same tempo from today). Card and bars are parchment, not lavender.
2. **Mode → Read freely → Choose:** the timeline lists 3 May, 4 May, … ; estimated times are grey italic, "23:59" (Midnight) is not; Mina's 9 May letter previews "“Forgive my long delay…", not "“9 May.".
3. **Open 3 May:** header "JONATHAN HARKER'S JOURNAL", "3 May 1893 · about 22:00 · Bistritz" line, the note "(Kept in shorthand.)" centred in italics; *Previous* disabled.
4. **Back:** 3 May's unread dot is gone.
5. **Resume after force-stop:** open 4 May, scroll three screens down, wait 1 s, Back, `"$ADB" shell am force-stop com.jensen.draculanightbynight`, relaunch, scroll the timeline to the top, open 4 May → it opens mid-entry where you left it.
6. `"$ADB" logcat -d -s AndroidRuntime:E` prints nothing.

- [ ] **Step 7: Commit, push, PR**

```bash
git add app/app/src
git commit -m "Add the app shell: timeline and reader screens, mode picker, theme

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## App plan 2 (next plan, not part of this one)

Onboarding (mode choice, notifications permission in context); settings screen (mode, start date, skip ahead/reset, text size, theme); WorkManager notifications with quiet hours, rescheduled on boot and time/zone change; filter chips (characters, types); bookmarks list; swipe between entries; full gothic polish (OFL display font, ornaments); About page (sources, licenses, privacy policy); release build (R8, App Bundle, Data Safety).
