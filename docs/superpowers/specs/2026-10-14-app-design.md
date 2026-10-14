# Dracula Night by Night — Sub-project 2: Android App

**Status:** Draft for review · **Date:** 2026-10-14 · **Related:** [Dataset design](2026-10-14-dataset-design.md)

## Purpose

A free, open-source (Apache-2.0) Android app on Google Play that lets readers
experience Bram Stoker's *Dracula* in "real time": each diary entry, letter,
telegram and cutting unlocks at its in-story date and time. Secondary goal: a
clean, modern codebase that serves as a portfolio piece.

## Identity

- Display name: **Dracula Night by Night**
- `applicationId` / package: `com.jensen.draculanightbynight` (the previous app,
  "Dracula Day by Day", was never published, so no continuity is needed)
- Play short description (draft): *Read Stoker's novel in real time, entry by
  entry, as it happened.*
- Positioned as distinct from, and unaffiliated with, the "Dracula Daily"
  newsletter.

## Success criteria

1. A new user can pick a reading mode and read the first available entry within
   a minute of first launch.
2. Entries unlock at their in-story local time in the user's time zone, and the
   user is notified (respecting quiet hours) without exact-alarm permission.
3. Read state, bookmarks and scroll position survive app updates that change
   the dataset.
4. The app needs no network permission and collects no data.

## Reading modes

| Mode | Unlock instant for an entry |
|---|---|
| **Calendar** | `entry.date` with the year replaced by the current season year, at `entry.time`, user's zone |
| **Same tempo** | `startDate + (entry.date − 1893-05-03)` days, at `entry.time`, user's zone |
| **Read freely** | always unlocked; story order |

- Season = 3 May – 6 Nov. The *season year* is stored when Calendar mode is
  chosen: the current year if chosen on or before 6 Nov, else the next year.
  Calendar mode after 6 Nov: everything stays unlocked
  until the user chooses to restart for next year. Before 3 May: nothing is
  unlocked; onboarding suggests Same tempo.
- **Skip ahead** (timed modes): a stored `skipOffset: Duration` added to "now".
  Skipping to a day or entry only changes the offset; it can be reset.
- Switching mode asks for confirmation; read state and bookmarks are kept.

## Architecture

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · WorkManager · manual
DI via an `AppContainer`. minSdk 26, current target SDK. Single `:app` module,
feature-oriented packages.

```
ui/      onboarding · timeline · reader · bookmarks · settings   (Compose + ViewModels)
domain/  UnlockSchedule · ReadingMode · EntryRepository           (pure Kotlin)
data/    content DB · user DB · DataStore prefs · notifications
```

- **Content DB** (read-only): tables `entry`, `entry_block`, `character`, built
  at app build time from `data/out/entries.json` by a Gradle task and loaded with
  `createFromAsset`. Replaced wholesale on update (destructive migration).
- **User DB** (separate file): `entry_progress(entryId PK, readAt, blockIndex,
  blockOffset, bookmarked)`. Keyed by stable entry id; uses real migrations.
  Progress rows whose id no longer exists are ignored, not deleted.
- **Preferences** (DataStore): mode, `startDate`, `skipOffset`, season year,
  filter selection, quiet hours, text size, theme, onboarding done.
- **`UnlockSchedule`**: pure function
  `unlockInstant(entry, mode, settings, zone): Instant?` (null = always unlocked).
  Unlock state is **never stored**; screens compute
  `unlockInstant <= clock.instant() + skipOffset` with an injected
  `java.time.Clock`.

## Screens

1. **Onboarding** (first launch): concept → mode choice (with out-of-season
   hint) → notifications and quiet hours (default 22:00–08:00), with the
   Android 13+ permission request shown in context.
2. **Timeline** (home): unlocked entries in story order under date headers;
   row = type icon, author, place/time, first-line preview, unread dot.
   Bottom teaser for the next locked entry ("Next: Dr. Seward's Diary — tonight,
   23:00"), no text preview, optional *Skip ahead*. Filter chips for characters
   and types (multi-select, persisted); empty dates collapse. Opens scrolled to
   the first unread entry.
3. **Reader:** per-type header — diary/phonograph (author + note such as "Kept
   in shorthand"), letter (from/to, right-aligned dateline, sign-off), telegram
   (typewriter-style strip), newspaper (masthead, narrower column), memo/log.
   Body in one serif reading font with size setting. Each body block is a lazy
   list item; resume position = `(blockIndex, blockOffset)` saved on pause.
   Bookmark toggle; swipe to previous/next unlocked entry; opening marks read.
4. **Bookmarks:** list reached from the timeline overflow menu.
5. **Settings:** mode, start date, skip ahead/reset, notifications and quiet
   hours, text size, theme, About (sources, licenses, GitHub link, privacy policy).

**Accessibility:** content descriptions for type icons, dynamic text size, no
color-only meaning, decorative elements marked decorative.

## Visual design

All styling goes through a single `DraculaTheme` (Material 3 color/typography
tokens plus custom tokens such as `displayFont` and `ornament`). v1 ships a
restrained gothic baseline:

- Palette: oxblood and parchment (light); near-black and bone (dark); sepia
  reading option.
- Typography: an OFL period display face (candidates: IM Fell English,
  Cormorant, UnifrakturMaguntia sparingly) for headings only; a readable serif
  for body text.

A later polish pass (ornaments, drop caps, textures) changes only the theme and
header components.

## Notifications

- `UnlockNotifier` (WorkManager, inexact) is scheduled for the next unlock
  instant. On run it posts one notification summarizing all entries unlocked
  since the last notification, then schedules the next.
- If the next unlock falls in quiet hours, the job is scheduled for the end of
  the quiet window. Entries still unlock on time.
- Rescheduled on app start, settings change, boot, and time/zone change. No
  notifications in Read freely mode. One channel: "New entries".

## Testing

- **Domain (JUnit, fixed Clock):** each mode; season boundaries (2 May, 3 May,
  6 Nov, 7 Nov); DST transitions; zone changes; skip offset; quiet-hours
  scheduling, including windows spanning midnight.
- **Data:** Room tests loading the real prebuilt content DB; every entry and
  block deserializes; user DB migrations.
- **UI:** Compose tests for locked teaser, filters, resume; screenshot tests of
  each entry type in light and dark.
- **CI (GitHub Actions):** run the dataset pipeline and fail if
  `entries.json` differs from the committed one; build; unit and instrumented
  tests.

## Release

- Code: Apache-2.0. Text: public domain (Gutenberg boilerplate stripped).
  Fonts: SIL OFL with license files bundled. The old app's fonts are not reused
  unless their licenses are verified.
- Play: no network permission, Data Safety "no data collected", privacy policy
  page in the repo, App Bundle with R8.
- F-Droid compatible (no proprietary dependencies).

## Out of scope for v1

Full-text search, character guide, home-screen widget, excerpt sharing,
translations, full gothic polish pass, cloud sync.
