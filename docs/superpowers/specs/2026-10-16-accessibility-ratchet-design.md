# Accessibility ratchet — design

*16 October 2026. Issue #37 ("Requirement: accessibility ratchet tests"). Builds on the spike report
`docs/accessibility-spike.md` and its findings #113–#123.*

## Intent

The spike found eleven accessibility problems. Nothing stops them, or new ones, from growing as the app changes. The
ratchet is a safety net in CI that:

- records the problems known today in one baseline file, so that **fixing them makes the number go down quickly and
  visibly**;
- fails the build when a **new** problem appears anywhere, so that a fix on one screen can't quietly break
  something being watched on another;
- fails the build when a recorded problem is **gone but still listed**, so the baseline can only shrink.

The user's words: implement the ratchet as things are now, so that when the fixing starts the number goes down
quickly, and fixes in one place can't cause problems in places being monitored.

## Goals

1. Every screen, sheet and dialog is opened by a test, in each look, and checked against the rules below.
2. **Looks.** Six for every screen:
   - light, dark, light high contrast and dark high contrast, at default text;
   - light and dark at system font scale 2.0 with the app's Extra large text.

   The reader is also run at 2.0 with Extra large text in each of the four reading fonts (serif, sans-serif,
   Atkinson Hyperlegible, OpenDyslexic), as #37 asks.
3. The checks:
   - **Automated checks:** Google's Accessibility Test Framework (ATF), via Compose's `enableAccessibilityChecks()`.
   - **Our own semantics rules** (the next section).
   - **Contrast:** stays in `PaletteTest`, which already checks every palette (AA 4.5:1, AAA 7:1 in high
     contrast). Unchanged.
   - **Lint:** the `Accessibility` lint category fails the build instead of warning.
4. A baseline file holds every violation known when the ratchet lands, each with a reason and an issue link.
5. A coverage check fails when a screen, sheet or dialog exists in the code but isn't in the test's list.
6. Everything runs on the JVM in the existing `app-tests` CI job (`testDebugUnitTest`), with no emulator. It is
   part of the required checks automatically.

## Non-goals

- **Fixing findings.** Nothing in #113–#123 is fixed here; they land later, each with its baseline entries removed.
- **Testing what a machine can't judge:**
  - focus visibility (#113);
  - whether OpenDyslexic looks right (#123);
  - TalkBack's own wording quirks (#122 — the semantics are already correct there);
  - Switch Access;
  - notifications.

  These stay manual checks in the spike report.
- **Instrumented (emulator) tests in CI.**

## Anti-goals

- **A baseline that can grow silently.** An entry can only be added together with an issue link, so it shows in
  review.
- **A ratchet that's flaky or slow enough that people stop trusting it.** It must be deterministic: fixed clock,
  seeded state, no animations. Its run time should stay small next to the current `app-tests` job.
- **Checks that pass because they look at nothing.** Each screen test first asserts that the screen actually
  rendered, for example by finding a known text.

## Our semantics rules

Each rule has a short id used in the baseline file.

| Id | Rule | Pins finding |
|---|---|---|
| `label` | Every clickable or toggleable node has a non-empty label (text or content description) | (passes today) |
| `role` | Every clickable node has a role, or is a list row (rows are allowed without a role) | (passes today) |
| `target` | Every clickable node is at least 48 × 48 dp | (passes today) |
| `headings` | Each screen exposes its expected heading nodes (the list per screen is part of the test) | #115 |
| `double-label` | No clickable has a content description *and* merged visible text, so nothing is read twice | #116 |
| `toggle-state` | Controls that switch something on and off expose a toggle or state description (the reader bookmark included) | #117 |
| `clipped` | At font scale 2.0 with Extra large text, no text or icon node is clipped by its parent or the window | #118 |
| `card-stops` | The Tonight card is a heading plus one merged stop plus its button | #119 |
| `words` | Unread, Bookmarked, Locked and estimated ("about …") states are in the row's spoken label, not only in shape, colour or italics | #120 |
| `group` | Single-choice option sets are a `selectableGroup` | #121 |
| `keyboard-scroll` | In the reader, Page Down (and arrow down) moves the text | #114 |

## The screens

The coverage list:

- Welcome
- Timeline, unfiltered and filtered, with:
  - the "ahead" banner;
  - a locked row;
  - the Tonight card in its "Tonight" state and in its "Read now" state.
- The timeline's dialogs: the mode dialog and the dialog in `TimelineScreen`.
- Filter sheet.
- Reader, once per document header: diary/journal, letter, telegram, newspaper, phonograph, memo, log. Filtered
  (with the switch) and unfiltered.
- Bookmarks, with a normal row and a locked row, plus the Read it now dialog.
- Settings, plus its dialogs (skip day and the others in `SettingsScreen`).
- Start over dialog.
- About, plus the licence dialog.

**Coverage check:** the test scans the main source set for `@Composable` functions whose names end in `Screen`,
`Dialog` or `Sheet`. Any it finds that the list doesn't name is a failure.

## The baseline

- **Location:** one plain-text file next to the tests (exact path left to the builder).
- **Entries:** one per known violation, identified by screen + look + rule + node. The node is found by its label
  or test tag, not by position, so moving things around doesn't churn it.
- **Each entry carries:**
  - a reason;
  - a link to an issue. The existing #113–#123 cover the semantics rules. Whatever ATF reports on day one is
    grouped into new issues by kind, one issue per kind, not per node.
- **Failure rules:**
  - A violation that isn't in the baseline fails, naming the screen, look, rule and node.
  - A baseline entry that no longer fires fails with "remove this line".
- **Showing progress:** the test output prints the total number of baseline entries, so a PR shows the count going
  down.

## Decided by the user

- Build the ratchet now with today's findings in the baseline, without fixing them first.
- The scope is the list in #37's requirement section, played back and confirmed:
  - every screen in light, dark and high contrast, at 2.0;
  - ATF checks;
  - the semantics rules;
  - large-text clipping;
  - lint as errors;
  - the baseline that can only shrink;
  - the coverage check.

## My calls

- **Six looks, not the full matrix of eight.** High contrast at 2.0 adds little. Colour only matters to ATF's
  contrast checks, which the default-size high-contrast looks already cover. Size only matters to clipping and
  targets, which light and dark at 2.0 cover.
- **Run-time budget:** the ratchet should add no more than about two minutes to `app-tests`. If it adds more, trim
  the looks per screen, never the screens.
- **Lint needs no baseline.** It has no accessibility warnings today, so it goes straight to errors.
- **Keyboard scrolling is mechanical.** The spike report called #114 manual, but a test can press Page Down and
  check the scroll position, so it's included.
- **Rows are allowed without a role.** TalkBack still offers "double tap to activate" for them, and the spike heard
  rows read well.
- **New baseline entries need an issue link.** Adding one is allowed, but only with that link.
- **Robolectric with Compose UI tests**, so it runs in the existing JVM job.

## Left to the builder

- **Libraries and versions:** Robolectric, Compose `ui-test-junit4`, the accessibility-checks artifact, and the
  graphics mode ATF's contrast/image checks need.
  - If ATF can't run under Robolectric, say so and fall back to our own rules plus ATF's non-image checks. Don't
    silently drop it.
  - A short feasibility step comes first in the plan.
- **How screens get their state:** real view models over a test container with seeded user state and a fixed
  clock, or the screens' stateless content composables with fixed state. Whichever needs fewer changes to
  production code. Any production change must be test-only in effect, such as an added `testTag` or a split-out
  stateless composable.
- **File format** of the baseline and the exact node identifiers.
- **The heading list per screen**, from the spike report and #115.
- **How "clipped" is measured**, for example node bounds against the parent's bounds and the window, or text
  layout overflow.
