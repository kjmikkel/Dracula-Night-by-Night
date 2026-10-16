# Accessibility Ratchet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every screen, sheet and dialog is checked for accessibility on every PR. Known violations live in a
baseline that can only shrink, and new violations fail CI.

**Architecture:**
- **Robolectric runs the real screens** (real view models over a real `AppContainer`, seeded state, fixed clock)
  inside `DraculaTheme`, in six looks.
- **Two checkers run on each case:**
  - a small rule engine over Compose's semantics tree (`SemanticsNode`);
  - Google's Accessibility Test Framework over the view hierarchy.
- **The baseline compare:** each case's violations are compared with that case's lines in a baseline file. A new
  violation fails; so does a listed one that no longer fires.
- **Coverage test:** scans the sources for every `…Screen`, `…Dialog` and `…Sheet` composable and fails on any
  that the cases don't cover.

**Tech Stack:**
- Kotlin and Compose (BOM 2026.09.00);
- JUnit 4 and Robolectric, with `androidx.compose.ui:ui-test-junit4` and `ui-test-manifest`;
- Accessibility Test Framework for Android (`com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework`);
- AGP 9.4.1.

**Spec:** `docs/superpowers/specs/2026-10-16-accessibility-ratchet-design.md`. The findings it baselines are in
`docs/accessibility-spike.md` and issues #113–#123.

## Global Constraints

- **Nothing in #113–#123 is fixed here.** Today's violations go into the baseline.
- **Production code:** changes must be test-only in effect, such as an added `Modifier.testTag`. No behaviour or
  appearance changes.
- **Where it runs:** on the JVM, inside `./gradlew testDebugUnitTest` (the `app-tests` CI job). No emulator, no
  new CI job.
- **Six looks:**
  - LIGHT, DARK, LIGHT_HC, DARK_HC at font scale 1.0 with `TextSize.NORMAL`;
  - LIGHT_LARGE and DARK_LARGE at font scale 2.0 with `TextSize.EXTRA_LARGE`.
- **Reader fonts:** the reader also runs at 2.0 with Extra large text in each `ReadingFont`.
- **Rule ids:**
  - `label`, `role`, `target`, `headings`, `double-label`, `toggle-state`, `clipped`, `card-stops`, `words`,
    `group`, `keyboard-scroll`;
  - ATF results use `atf:<CheckClassSimpleName>`.
- **Baseline entries:** every entry has an issue link (`#NNN`) and a reason. A line without them is a parse
  failure.
- **Determinism:** the clock is fixed with `app.clock.travelTo(Instant.parse("2026-05-08T12:00:00Z"))`, the zone
  is UTC, and animations are off (`mainClock.autoAdvance` as needed). No sleeps.
- **Run-time budget:** the ratchet adds at most about 2 minutes to `testDebugUnitTest` on CI. If it's over, trim
  looks per screen (keep at least LIGHT and LIGHT_LARGE), never screens.
- **Workflow:** one issue and one PR per task, auto-merge (squash) on green.

## Review Focus

1. **A case that renders nothing passes every rule.** Each case must assert its `ready` text is displayed before
   checking (Task 5's `case renders its ready text` test).
2. **A baseline key built from positions churns on every layout change.** Node identity must come from the
   label/text, and a test must pin that reordering rows keeps keys stable (Task 4).
3. **Async state (DataStore, `Dispatchers.Default`) not settled when rules run** gives flaky results. Each case
   must wait until the seeded state is visible (`waitUntil` on the ready text), never sleep (Task 5).
4. **Stale baseline lines for a look or screen that no longer runs** would never be detected. A test must fail on
   baseline lines whose screen or look isn't in the case list (Task 4/5).
5. **ATF throwing on the first error** would hide the rest. Results must be collected, not thrown (Task 1).

---

### Task 1: Test harness and ATF feasibility

**Files:**
- Modify: `app/gradle/libs.versions.toml` (robolectric, compose ui-test-junit4, ui-test-manifest, ATF)
- Modify: `app/app/build.gradle.kts` (`testImplementation`s, `debugImplementation(ui-test-manifest)`,
  `testOptions { unitTests { isIncludeAndroidResources = true } }`)
- Create: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/Harness.kt`
- Create: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/Atf.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/HarnessTest.kt`

**Interfaces:**
- Produces:
  - `enum class Look(val theme: ThemeChoice, val highContrast: Boolean, val fontScale: Float, val textSize: TextSize)`
    with LIGHT, DARK, LIGHT_HC, DARK_HC, LIGHT_LARGE, DARK_LARGE.
  - `fun ComposeContentTestRule.showInLook(look: Look, font: ReadingFont = ReadingFont.SERIF, content: @Composable () -> Unit)`.
    It wraps `DraculaTheme(theme, highContrast, readingFont, textSize)` in a `CompositionLocalProvider` of a
    `Density` with `fontScale`.
  - `fun testApp(): AppContainer`. It uses the Robolectric application context and has its clock travelled to the
    fixed instant.
  - `fun atfResults(view: View): List<AtfFinding>`, with `data class AtfFinding(val check: String, val node: String)`.
    Only ERROR results are returned; results are collected, never thrown.

- [ ] **Step 1: Write the failing test `HarnessTest.aboutRendersAndAtfRuns`.** It renders `AboutScreen(onBack = {})` in
  `Look.LIGHT` and asserts:
  - `onNodeWithText("Fonts").assertExists()`;
  - `atfResults(rootView)` returns a list without throwing.

  Also write `HarnessTest.largeLookScalesText`: in LIGHT_LARGE, the "Fonts" node is taller than in LIGHT.
- [ ] **Step 2: Run it.** `./gradlew :app:testDebugUnitTest --tests "*HarnessTest*"`. Expected: FAIL (compilation:
  `Look`/`showInLook`/`atfResults` unresolved).
- [ ] **Step 3: Add the dependencies and implement `Harness.kt` and `Atf.kt`.**
  - **Robolectric:** use `@RunWith(RobolectricTestRunner::class)`, with `@Config(sdk = [36])` if Robolectric
    doesn't support 37 yet.
  - **ATF:** run `AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(LATEST)` over an
    `AccessibilityHierarchyAndroid` built from the root view, and keep `AccessibilityCheckResultType.ERROR`.
  - **Node names:** take the element's text or content description. If it has neither, use its class name plus
    the nearest labelled ancestor's text.
  - **Contrast/image checks:** if they need a screenshot, try `@GraphicsMode(NATIVE)` with an `Image` parameter.
    If that doesn't work under Robolectric, record a ruling and run the non-image checks only (the spec allows it;
    `PaletteTest` still guards text contrast).
- [ ] **Step 4: Run it.** Same command. Expected: PASS. Then run the full suite: `./gradlew :app:testDebugUnitTest`.
  Expected: all green (195 + 2).
- [ ] **Step 5: Commit.** Message: "Ratchet 1: Robolectric harness and ATF".

### Task 2: Semantics rules

**Files:**
- Create: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/Rules.kt`
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/RulesTest.kt`

**Interfaces:**
- Consumes: `showInLook`, `Look` (Task 1).
- Produces:
  - `data class Expectations(val headings: List<String> = emptyList(), val toggles: List<String> = emptyList(), val groups: List<List<String>> = emptyList(), val words: List<Pair<String, String>> = emptyList(), val cardTag: String? = null)`.
    `words` pairs are (text fragment that finds the node, word its spoken label must contain).
  - `fun checkRules(root: SemanticsNode, expect: Expectations, density: Density): List<Finding>`, with
    `data class Finding(val rule: String, val node: String)`.
  - `node` is the node's own text or content description, cut to 40 characters. For list rows it's their first
    text. It's never a position.

Rule definitions. Each operates on the unmerged tree and reads merged text where it says so.

| Rule | Fires when |
|---|---|
| `label` | A node with `OnClick` or `ToggleableState` has no merged text and no content description |
| `role` | A node with `OnClick` has no `Role`, unless it's a direct item of a node with `CollectionInfo` (a list row) |
| `target` | A node with `OnClick` has `touchBoundsInRoot` smaller than 48 × 48 dp |
| `headings` | A text in `expect.headings` isn't on a node with `Heading` |
| `double-label` | A node with `OnClick` has its own (unmerged) `ContentDescription` and also merged text from its children |
| `toggle-state` | The node containing a text in `expect.toggles` has neither `ToggleableState` nor `StateDescription` |
| `group` | The option nodes of a group in `expect.groups` don't share a parent with `SelectableGroup` semantics (`CollectionInfo`) |
| `words` | The merged label of the node containing the fragment lacks the word (case-insensitive) |
| `card-stops` | The node tagged `expect.cardTag` contains more than 3 separately focusable (merged or clickable) descendants |

- [ ] **Step 1: Write the failing tests.** One passing and one failing fixture per rule, each a tiny composable
  shown with `showInLook(LIGHT)`. Examples:
  - `labelFiresOnUnlabelledClickable`: `Box(Modifier.size(48.dp).clickable {})` gives `[Finding("label", …)]`.
  - `doubleLabelFiresOnDescribedTextButton`: `TextButton(onClick = {}, Modifier.semantics { contentDescription = "Back" }) { Text("‹ Back") }`
    gives `Finding("double-label", "Back")`.
  - `doubleLabelIgnoresRowWithDescribedChild`: a clickable `Row` containing `Box(Modifier.semantics { contentDescription = "Unread" })`
    and `Text("x")` gives no finding.
  - `roleAllowsListRows`: a clickable row inside `LazyColumn` with no role gives no `role` finding.
  - `targetFiresBelow48dp`: `Box(Modifier.size(40.dp, 40.dp).clickable(role = Role.Button) {}.semantics { contentDescription = "x" })`
    gives a `target` finding. Note that Material's `minimumInteractiveComponentSize` doesn't apply to raw `Box`es.
  - `headingsFiresWhenPlainText`, `toggleStateFiresOnPlainButton`, `groupFiresWithoutSelectableGroup`,
    `wordsFiresWhenWordMissing`, `cardStopsFiresAtFiveStops`, plus their passing twins.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests "*RulesTest*"`. Expected: FAIL (unresolved
  `checkRules`).
- [ ] **Step 3: Implement `Rules.kt`** to the table above.
- [ ] **Step 4: Run them.** Same command. Expected: PASS. Full suite green.
- [ ] **Step 5: Commit.** Message: "Ratchet 2: semantics rules".

### Task 3: Clipping rule

**Files:**
- Modify: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/Rules.kt` (add `clipped`)
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/ClippedRuleTest.kt`

**Interfaces:**
- Produces: `checkRules` also returns `Finding("clipped", node)`.

`clipped` fires when either of these holds:
- (a) a text or content-described node's clipped bounds (`boundsInWindow`) are smaller than its size by more than
  1 px along an axis its nearest scrollable ancestor does **not** scroll (any axis if there's no scrollable
  ancestor). Also when it lies partly outside the window on such an axis.
- (b) a text node's layout (`GetTextLayoutResult`) ends in an ellipsis and shows fewer than 3 characters before it.

- [ ] **Step 1: Write the failing tests.**
  - `clippedFiresOnIconPushedPastEdge`: a `Row(Modifier.fillMaxWidth())` of a 2000-dp-wide text and then
    `Text("★", Modifier.semantics { contentDescription = "Bookmarked" })` gives `clipped` for "Bookmarked".
  - `clippedFiresOnTitleCollapsedToEllipsis`: `Text("Jonathan Harker", Modifier.width(20.dp), maxLines = 1, overflow = Ellipsis)`
    fires.
  - `clippedIgnoresTwoLinePreviewEllipsis`: a long `Text` with `maxLines = 2, overflow = Ellipsis` at full width
    doesn't fire.
  - `clippedIgnoresRowsScrolledOffVertically`: in a `LazyColumn`, a row half below the viewport doesn't fire.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests "*ClippedRuleTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement (a) and (b) in `Rules.kt`.**
- [ ] **Step 4: Run them.** Expected: PASS. Full suite green.
- [ ] **Step 5: Commit.** Message: "Ratchet 3: clipping rule".

### Task 4: Baseline

**Files:**
- Create: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/Baseline.kt`
- Create: `app/app/src/test/resources/a11y-baseline.txt` (header comment only for now)
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/BaselineTest.kt`

**Interfaces:**
- Produces:
  - `data class Violation(val screen: String, val look: String, val rule: String, val node: String)`.
    `look` is a `Look` name, or `LIGHT_LARGE/<FONT>` for the reader's font runs.
  - `data class BaselineEntry(val violation: Violation, val issue: Int, val reason: String)`.
  - `fun parseBaseline(text: String): List<BaselineEntry>`. It throws `IllegalArgumentException` naming the line
    for: a missing `#issue`, a missing reason, or a duplicate key.
  - `fun loadBaseline(): List<BaselineEntry>`. It reads `/a11y-baseline.txt` from the test classpath.
  - `data class Diff(val new: List<Violation>, val stale: List<BaselineEntry>)` and
    `fun diff(found: List<Violation>, baseline: List<BaselineEntry>, screen: String, look: String): Diff`. It looks
    only at that screen and look.
  - `fun Diff.failureMessage(): String`:
    - each new violation as a ready-to-paste baseline line with `#??? reason?`;
    - each stale one as `remove: <line>`.

Line format, one per entry. Lines starting with `#` and blank lines are ignored:

```
screen | look | rule | node | #issue | reason
```

- [ ] **Step 1: Write the failing tests.**
  - `parsesAnEntry`, `rejectsLineWithoutIssue`, `rejectsDuplicateKey`.
  - `diffReportsNewViolation`, `diffReportsStaleEntry`, `diffIgnoresOtherScreensAndLooks`.
  - `failureMessageGivesPasteableLine`.
  - `nodeKeyIsLabelNotPosition`: two runs whose findings come in different order give an empty diff against the
    same baseline.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests "*BaselineTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement `Baseline.kt`.**
- [ ] **Step 4: Run them.** Expected: PASS. Full suite green.
- [ ] **Step 5: Commit.** Message: "Ratchet 4: baseline file and diff".

### Task 5: Screen cases and the ratchet test

**Files:**
- Create: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/Cases.kt`
- Create: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/RatchetTest.kt` (parameterized over case × look)
- Modify: `app/app/src/test/resources/a11y-baseline.txt` (today's violations)
- Modify: `app/app/src/main/java/com/jensen/draculanightbynight/timeline/TimelineScreen.kt` (add
  `Modifier.testTag("tonight-card")` on the card only)
- Modify: `docs/accessibility-spike.md` ("Ratchet tests" section: how to run, how to read a failure, how to remove
  a line)

**Interfaces:**
- Consumes: `testApp`, `showInLook`, `Look`, `atfResults`, `checkRules`, `Expectations`, `Violation`,
  `loadBaseline`, `diff`.
- Produces:
  - `data class Case(val name: String, val covers: Set<String>, val ready: String, val looks: List<Look> = Look.entries, val fonts: List<ReadingFont> = emptyList(), val expect: Expectations = Expectations(), val seed: suspend AppContainer.() -> Unit = {}, val open: ComposeContentTestRule.() -> Unit = {}, val content: @Composable (AppContainer) -> Unit)`;
  - `val cases: List<Case>`.

**Seed:**
- Fixed instant 2026-05-08T12:00Z.
- `completeOnboarding(<in the same tempo>, notify = true)`.
- `setTempoStart(2026-05-03)`, so the story's 3–8 May entries are readable and later ones are locked.

**Cases.** `covers` names the composable functions each case exercises:

| Case | covers | open / state | expect |
|---|---|---|---|
| `welcome` | WelcomeScreen | fresh app, no onboarding | headings "Dracula Night by Night", "How would you like to read?"; group of the three mode cards |
| `timeline` | TimelineScreen | seeded; one entry read, one bookmarked | headings: app title, "3 May"; words: unread row → "Unread", bookmarked row → "Bookmarked", an estimated row → "about"; cardTag "tonight-card" |
| `timeline-tonight` | TimelineScreen | every readable entry read except the newest, so the card shows its "Tonight"-style heading, not "Read now" | cardTag "tonight-card" |
| `timeline-filtered` | TimelineScreen | filter Mina | headings: app title |
| `timeline-ahead` | TimelineScreen | `setSkipAhead` > 0 | ready: the ahead banner text |
| `read-it-now` | TimelineScreen | tap the next card's Read it now | ready "Read it now?" |
| `filter-sheet` | FilterSheet | tap "Filter" | headings "Voices", "Documents"; the chips expose state |
| `reader-<type>` × 7 | ReaderScreen | one entry id per `DocumentType` (diary `1893-05-03-jonathan_harker-01`, letter `1893-05-09-mina_harker-01`, telegram `1893-05-26-arthur_holmwood-01`, newspaper `1893-08-08-dailygraph-01`, phonograph `1893-05-25-john_seward-01`, memo `undated-unknown-01`, log `1893-08-09-demeter_captain-01`), read freely so all are readable; fonts = all four for the letter | headings: author name, document header; toggles "Bookmark" |
| `reader-filtered` | ReaderScreen | filter Mina, `filtered = true` | group "Mina", "All" |
| `bookmarks` | BookmarksScreen | one readable and one locked bookmark (bookmark, then `startOver(removeBookmarks = false, …)`) | headings "Bookmarks"; words: locked row → "Locked" (or the app's own word for it today, read from the screen) |
| `read-now-dialog` | ReadNowDialog | tap the locked row | |
| `settings` | SettingsScreen | seeded | headings "Settings", "Reading", "Notifications", "Display", "Text size", "Reading font", "Theme"; groups: the text sizes, the fonts, the themes |
| `settings-mode` / `-skip-day` / `-start-date` | ModeDialog, SettingsScreen | open each dialog from its row | |
| `start-over` | StartOverDialog | tap Start over | toggles "Also remove" |
| `about` | AboutScreen | | headings "About", "The text", "The app", "Fonts" |
| `licence` | LicenceDialog | tap "Special Elite" | |

**Per case and look, `RatchetTest`:**
1. seeds a fresh `testApp()`;
2. shows `content` in the look;
3. runs `open`;
4. waits for `ready` (`waitUntil { onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty() }`);
5. collects `checkRules` and `atfResults` into `Violation`s;
6. asserts `diff(...)` is empty, otherwise fails with `failureMessage()`.

**Tests outside the case × look loop:**
- `baselineOnlyNamesRunningCases`: every baseline line's screen and look exists among the cases.
- `printBaselineCount`: prints `Accessibility baseline: N known violations`.

- [ ] **Step 1: Write `RatchetTest`, `Cases.kt` and the two extra tests.** Leave the baseline file empty.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests "*RatchetTest*"`. Expected: FAIL. Every
  failure message lists ready-to-paste lines. This is today's state: the RED that proves the rules see the spike's
  findings. Check that #115 (headings), #116 (double-label on Back/Previous/Next/Read/Remove), #117 (toggle-state
  on Bookmark), #118 (clipped in the LARGE looks), #119 (card-stops), #120 (words "about") and #121 (group) all
  appear. If any doesn't, the rule or the case is wrong: fix it, not the expectation.
- [ ] **Step 3: Write the baseline.**
  - Paste the lines into `a11y-baseline.txt`, linking each to its spike issue (#115–#121).
  - Group the ATF results by check, and file one new `accessibility` issue per check (impact in the body, as
    #113–#123 do). Link them.
  - Add `testTag("tonight-card")`.
- [ ] **Step 4: Run them.** Same command. Expected: PASS. Note the baseline count. Run the full suite. Check the
  added time against the budget: compare `testDebugUnitTest` before and after on CI.
- [ ] **Step 5: Commit.** Message: "Ratchet 5: every screen in six looks against the baseline".

### Task 6: Keyboard scrolling in the reader

**Files:**
- Modify: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/RatchetTest.kt` (add `readerScrollsWithKeyboard`)
- Modify: `app/app/src/test/resources/a11y-baseline.txt`

**Interfaces:**
- Consumes: `Case`, `Violation`, `diff`.

- [ ] **Step 1: Write the test.** `readerScrollsWithKeyboard` opens `reader-letter` in LIGHT, then:
  - records the first visible paragraph's `boundsInRoot.top`;
  - sends `performKeyInput { pressKey(Key.PageDown) }` (and `Key.DirectionDown`) to the root;
  - if the top didn't move, it produces `Violation("reader-letter", "LIGHT", "keyboard-scroll", "body")` and
    diffs it against the baseline.
- [ ] **Step 2: Run it.** Expected: FAIL with the paste line for `keyboard-scroll` (#114 is real today).
- [ ] **Step 3: Add the baseline line** with `#114`.
- [ ] **Step 4: Run it.** Expected: PASS. Full suite green.
- [ ] **Step 5: Commit.** Message: "Ratchet 6: keyboard scrolling in the reader".

### Task 7: Coverage check

**Files:**
- Test: `app/app/src/test/java/com/jensen/draculanightbynight/a11y/CoverageTest.kt`

**Interfaces:**
- Consumes: `cases` (Task 5).

- [ ] **Step 1: Write the test.** `everyScreenDialogAndSheetHasACase`:
  - reads every `.kt` file under `src/main/java` (path from the Gradle working directory, `app/app`);
  - collects names matching `fun ([A-Z]\w*(Screen|Dialog|Sheet))\(`;
  - asserts that every name is in `cases.flatMap { it.covers }`, failing with the missing names.

  Also write `coverageSeesPrivateDialogs`, which asserts the scan finds `ReadNowDialog` and `LicenceDialog`.
- [ ] **Step 2: Run it** with a case's `covers` temporarily removed. Expected: FAIL naming it. Restore it.
- [ ] **Step 3:** No production code. If the scan finds a composable with no case, add the case.
- [ ] **Step 4: Run it.** Expected: PASS. Full suite green.
- [ ] **Step 5: Commit.** Message: "Ratchet 7: coverage check".

### Task 8: Accessibility lint as errors

**Files:**
- Modify: `app/app/build.gradle.kts` (`lint { error += <the Accessibility category's issue ids> }`, or
  `checkAllWarnings` plus `error` for that category)

- [ ] **Step 1: Prove it fails on a violation.** Temporarily add an `ImageView` without `contentDescription` to a
  throwaway layout and run `./gradlew :app:lintDebug`. Expected: FAIL with `ContentDescription` as an error.
  Remove the layout.
- [ ] **Step 2: Run `./gradlew :app:lintDebug :app:lintRelease`.** Expected: PASS (no accessibility warnings
  today).
- [ ] **Step 3: Commit.** Message: "Ratchet 8: accessibility lint fails the build".
