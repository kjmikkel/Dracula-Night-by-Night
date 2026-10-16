# Dracula Night by Night — App plan C: Look, About, release-ready

**Status:** Agreed in brainstorming, written for review · **Date:** 2026-10-16
**Builds on:** [App design](2026-10-14-app-design.md), [plan A](2026-10-15-app-plan-a-design.md), [plan B](2026-10-16-app-plan-b-design.md) (all implemented)
**Followed by:** the accessibility spike (#37, including its ratchet tests), publishing on Play (later, separate), two-pane layout (#58)

## Intent

A reader who opens the app — often late, often in the dark — gets into the newest entry at once and reads it
comfortably. The app should feel like Dracula: a quiet gothic look and the character of the novel's documents,
never at the cost of readability. Plan C also makes the app ready to publish (signed, shrunk, with an icon, an
About page and a privacy policy) without publishing it, and clears the small leftovers from plans A and B.

## Goals

- One-tap entry into what has arrived (the **Tonight card**).
- A restrained gothic look in light and dark, with the documents' character inside the reader.
- A launcher icon, an About page and a privacy policy.
- A release build that is signed when a key is present, shrunk with R8, and checked in CI.
- The plan A and plan B leftovers fixed (list below).

## Non-goals

- Publishing: Play Console, store listing, screenshots, Data Safety form — a later step.
- Drop caps, paper textures, animated ornaments (the original spec's "full gothic polish pass").
- The accessibility spike (#37) and the two-pane layout (#58).

## Anti-goals (a failure even if it works)

- Decoration that makes the story text harder to read, or the display face used for story text.
- Any colour pair under the contrast bar (≥ 4.5:1; ≥ 7:1 in high contrast), or meaning carried by colour alone.
- A signing key, keystore or password in the repository.
- A release build that crashes or loses data because R8 stripped something only the release build needs.

## Decisions (and what they change in earlier specs)

| # | Decision | Replaces |
|---|---|---|
| C1 | **Light theme is "restrained gothic"** (mockup A): parchment and oxblood, quiet. | App spec palette (made concrete) |
| C2 | **Dark theme is "candlelight"** (mockup C): near-black, warm bone text, candle-amber and blood-red accents. | Plan A's dark palette |
| C3 | **No sepia theme.** Light is already parchment; the theme choice stays System / Light / Dark. | App spec "sepia reading option"; plan A D11/D12 mentions of sepia |
| C4 | **Display face IM Fell English (OFL) for headings only**: app title, day headers, entry titles, card headings. Story text keeps the reader's chosen reading font. | App spec typography (made concrete) |
| C5 | **One ornament** (❦) between the pinned cards and the list and under reader headers; decorative, hidden from TalkBack. | App spec "ornament" token |
| C6 | **Document touches live in the reader** (from mockup B): telegram strip in a typewriter face, letter dateline and sign-off, newspaper masthead between rules, diary/phonograph author with the source note. The timeline stays plain rows. | Plan 1 reader headers (refined) |
| C7 | **Tonight card** at the top of the timeline (rules below). | New |
| C8 | **Launcher icon: a steel pen nib with a drop of blood** on parchment. | New |
| C9 | **Release-ready, not released.** | App spec "Release" (partly) |
| C10 | **Plan A and B leftovers are fixed in plan C.** | Deferred minors of plans A and B |

## Look

### Palettes

Defined as ARGB data in `ui/Palette.kt`, checked by `PaletteTest` (every text/background pair ≥ 4.5:1; high
contrast ≥ 7:1). Exact values are the builder's, within these intents:

- **Light (C1):** parchment background (mockup: `#F4ECDD`), deep ink text (`#2A1A16`), oxblood primary (`#6E1414`),
  a slightly darker parchment for day headers and cards (`#EADBC0` / `#EADFCB`), a muted rose-brown for the ornament.
- **Dark (C2):** near-black background (`#121011`), warm bone text (`#E8DCC6`), candle amber (`#E0A458`) for labels
  and day headers, blood red (`#B23A2E`) as the primary container for buttons (text on it must still pass), warm
  dark brown for card borders and dividers.
- **High contrast (light and dark):** unchanged in spirit from plan A — wins over the palette, drops the candle
  tones, keeps oxblood or a light rose only where they pass 7:1.
- **Selection colours:** chips, segmented buttons and switches use the palette (Material's default lavender
  `secondaryContainer` disappears) in every theme, including high contrast.

### Type

- IM Fell English (regular and italic) and IM Fell English SC bundled as fonts with their OFL text in
  `assets/licenses/`, never modified.
- A typewriter face for the telegram strip: Special Elite (Apache-2.0) bundled with its licence; the builder may
  instead keep the current monospace style if Special Elite fails contrast or legibility at 200 % text.
- Headings scale with Android's font size like everything else; no text in the display face is smaller than the
  body size it replaces.

### Reader document touches (C6)

Per document type, inside the reader header only: telegram — typewriter strip "TELEGRAM" with from/to; letter —
from/to line, dateline right-aligned, sign-off right-aligned (already in the dataset's block roles); newspaper —
masthead in the display face between two rules, narrower column; diary — the entry's own header (for example
"Jonathan Harker's Journal") in the display face; phonograph — the same with the note "Phonograph diary" (the
document type); memo/log — title in the display face. The body is unchanged.
*Call made in writing:* no "Kept in shorthand" note — the dataset does not record it, and the app should not claim
what the novel does not say.

## Tonight card (C7)

Pinned above the timeline with the other cards whenever at least one **unread entry that matches the filter** is
readable. It is computed in pure Kotlin (`timeline/Tonight.kt`) and tested on the JVM.

- **One unread entry:** heading by the entry's **arrival time** (its scheduled time minus skip ahead, as
  `Arrivals.arrivesAt`), relative to now:
  - arrived today: 06:00–11:59 "This Morning", 12:00–17:59 "This Afternoon", 18:00–20:59 "This Evening",
    21:00–05:59 "Tonight";
  - arrived yesterday: "Yesterday morning", "Yesterday afternoon", "Yesterday evening", "Last night";
  - earlier this week: weekday plus part of day ("Monday evening", "Monday night");
  - more than six days ago: date plus part of day ("12 October, evening").
  - **The day starts at 06:00** for every part of this rule (*call made in writing*, extending the agreed
    night rule consistently): 00:00–05:59 belongs to the day before. So "today" means since 06:00 this morning
    (or since 06:00 yesterday, between midnight and 06:00), and "yesterday" is the 24 hours before that.
    Examples: at 02:00, an entry from 20:00 the evening before is "This Evening"; at 07:00, an entry from 22:00
    two calendar days back is the night of the day before yesterday, so "<weekday> night"; an entry arriving at
    02:00 is "Tonight" until 06:00 and "Last night" after.
  - The card shows the author and document type and the first line, and a **Read** button that opens it.
- **Several unread entries:** heading "Read now", a line "N entries waiting", and a **Read** button that opens
  the **oldest** unread matching entry; the pager then carries on in story order.
- **Nothing unread:** no card.
- **Read freely** has no arrival times: the card is the "Read now" form with the oldest unread entry (or no card).
- The boundaries 06/12/18/21 are constants in one place, easy to change.
- Uses the app clock (`ShiftableClock`), so time travel moves it; dates and weekdays in English (`Dates`).
- Accessibility: heading marked as a heading; the Read button says "Read <author>, <type>".

## Icon (C8)

- Adaptive icon: foreground nib and blood drop inside the 66 dp safe zone, parchment background; monochrome
  layer for Android 13+ themed icons; legacy PNGs generated for older launchers (minSdk 26 supports adaptive).
- Notification small icon redrawn in the same style (white silhouette of the nib).
- A 512 px store icon exported into `docs/` for the later Play listing.
- Fixes lint `MissingApplicationIcon`.

## About page

Reached from Settings ("About"), route `about`:

- App name, version name, one line on what the app is.
- Text: Project Gutenberg eBook #345, public domain; Gutenberg boilerplate removed.
- Code: Apache-2.0, link to `https://github.com/kjmikkel/Dracula-Night-by-Night`.
- Fonts: each bundled font with its licence, the licence text viewable in the app (from `assets/licenses/`).
- Privacy: one sentence ("No data leaves your phone") and a link to the privacy policy at
  `https://github.com/kjmikkel/Dracula-Night-by-Night/blob/main/docs/privacy.md` (*call made in writing*: no
  GitHub Pages; the Play listing can use the same URL later).
- Links open in the browser; the app gains no network permission.

## Release-ready (C9)

- `versionName` 1.0.0, `versionCode` 1.
- **Signing:** a `release` signing config read from `keystore.properties` (git-ignored) or environment
  variables; when neither is present the release build is unsigned. A `keystore.properties.example` documents the
  keys. Nothing secret is committed.
- **R8:** `isMinifyEnabled` and `isShrinkResources` on for release, with keep rules for kotlinx-serialization and
  anything else found missing; a release build must load the story, open an entry, save state, and fire a
  notification (checked on the emulator in the plan).
- **CI:** a new job builds the release bundle and APK (unsigned) and runs `lintRelease`. The plan also makes it a
  required check in the `main` branch protection (`gh api`, as was done for the existing checks), so the
  automerge workflow waits for it.
- **Language:** fix lint `AppBundleLocaleChanges` (the English date-picker configuration) by turning off language
  splits in the bundle.
- **Privacy policy:** `docs/privacy.md` — no data collected, no network access, everything stored on the device,
  notification permission used only for the reader's own arrivals; dated, with a contact via GitHub issues.
- **F-Droid compatible:** no proprietary dependencies (true today); keep it so.

## Leftovers fixed in plan C (C10)

From plan A:
1. `markNotified` can move the notification baseline backwards by milliseconds — never move it back (`maxOf`).
2. The reschedule trigger also fires on display choices, `lastNotifiedAt` and filter writes — narrow it to what
   affects the schedule.
3. Pinned cards (season, ahead, filtered, and now Tonight) can crowd the list at 200 % text in landscape — the
   pinned area must leave room for the list (for example by scrolling with the list when space is short).
4. Clickable rows without a button role (ahead banner, Settings rows, Start over); the "‹ Back" text buttons
   should read as "Back".
5. The Settings Notify switch shows the wrong state for its first frame.
6. A double tap on Begin can push a second timeline.

From plan B:
7. A quick double tap on ★ can put a bookmark back while the snackbar says "Bookmark removed" — removal must be
   idempotent.
8. Fast chip taps can drop a choice — apply each chip change to the saved filter, not to a stale copy.
9. Several quick removals: only the last can be undone, and the snackbar does not say which — name the entry.
10. The Read it now dialog in Bookmarks closes on rotation — keep it across configuration changes.
11. The reader's [filter | All] switch gives TalkBack no context — say what it controls.
12. "Bookmarked 14 October" uses the system zone and the long month — use the app clock's zone and "14 Oct".
13. At 200 % text the timeline's time column wraps ("00:0 / 0") and the reader switch label clips.

## Testing

- **JVM:** palettes (`PaletteTest` over the new values, including selection colours); Tonight card (each part of
  day, today/yesterday/weekday/date, 00:00–05:59 rule, several unread → oldest, filter respected, Read freely,
  skip ahead and time travel); each leftover that is logic (1, 2, 7, 8, 12) gets a test that fails first.
- **Emulator:** light, dark and both high-contrast themes on timeline, reader (every document type), Bookmarks,
  Settings and About; 200 % text in portrait and landscape; the Tonight card under time travel across the day
  boundaries; icon on a light and a dark wallpaper and as a themed icon; a release build installed and used
  (story, entry, bookmark, notification); TalkBack on the Tonight card, the switch and the Back buttons.

## Left to the builder

Exact colour values within the intents and the contrast bar; whether Special Elite is used (see Type); the
ornament glyph's size and spacing; the icon's final drawing; how the pinned area gives way at 200 % in landscape;
the order of tasks.
