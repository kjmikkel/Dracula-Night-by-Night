# Dracula Night by Night — App plan A: Real-time reading

**Status:** Approved in brainstorming, written for review · **Date:** 2026-10-15
**Builds on:** [App design](2026-10-14-app-design.md) (plan 1 implemented: timeline, reader, mode dialog, saved state)
**Followed by:** plan B (filters, bookmarks list, swipe between entries), plan C (gothic theme, About and privacy policy, release build)

## Purpose

Turn the plan 1 foundation into a real-time reading app: a reader chooses how to read on first launch,
controls it from Settings, and is told — quietly — when entries arrive. Plan A delivers the app spec's
success criteria 1 (reading within a minute of first launch) and 2 (entries unlock on time and the reader is
notified, as revised below).

## Decisions (and what they change in the app spec)

| # | Decision | App spec text it replaces |
|---|---|---|
| D1 | Notifications are **silent by default** (channel importance Low). **No in-app quiet hours**: Android's Do Not Disturb and Bedtime cover silencing; Settings links to the channel's system page for sound. | Success criterion 2 "respecting quiet hours"; onboarding "quiet hours (default 22:00–08:00)"; Notifications "If the next unlock falls in quiet hours…" |
| D2 | Wake-ups use an **inexact alarm allowed in Doze** (`AlarmManager.setAndAllowWhileIdle`), not WorkManager. No exact-alarm permission. | Notifications "`UnlockNotifier` (WorkManager, inexact)" |
| D3 | **Once read, always readable**: an entry is unlocked if its scheduled time has come *or* it has been read. Schedules (Next card, notifications) ignore read state. | Reading modes: unlock state follows the schedule only |
| D4 | **Onboarding is one Welcome screen** (idea, mode cards, notify switch, Begin). | Screens §1 three-step onboarding |
| D5 | **Skip ahead**: *Read it now* on the Next card and *Skip to a day* in Settings; *Back to real time* resets. The offset never moves backwards except through Back to real time. | Reading modes "Skipping to a day or entry only changes the offset" (made concrete) |
| D6 | **Reader text size** in four steps and **reading font** in four choices (Serif, Sans-serif, Atkinson Hyperlegible, OpenDyslexic), applied to the story text only. | Settings "text size" (made concrete); fonts are new |
| D7 | **Start over** clears reading progress (bookmarks optionally) and restarts the schedule. | New |
| D8 | **New-season prompt** for Calendar readers from 26 April after a finished season. | Reading modes "everything stays unlocked until the user chooses to restart for next year" (now prompted) |
| D9 | **"Start tomorrow?"** when Same tempo started today would put the first scheduled entry under an hour away or past. | New |
| D10 | **End card** replaces the Next card at the bottom of the timeline once nothing is left to unlock. | New |
| D11 | Theme setting **System / Light / Dark** with a dark palette now; Sepia and the period display font stay in plan C. | Visual design (split across plans A and C) |
| D12 | **High contrast** switch, independent of the theme (light or dark high-contrast palette). Every text/background pair meets WCAG AAA (≥ 7:1). Defaults to on when Android 14+'s Contrast setting is high. Wins over Sepia (plan C). | Accessibility (made concrete) |

## Saved reader state

`UserState` (DataStore JSON, plan 1) gains fields; every field has a default, so files from plan 1 load
unchanged. An unknown or invalid value falls back to the default — never a crash.

| Field | Type, default | Meaning |
|---|---|---|
| `onboarded` | `Boolean`, `false` | Welcome screen shown until Begin. A plan 1 file that already has a mode counts as onboarded. |
| `notify` | `Boolean`, `true` | The reader wants notifications |
| `lastNotifiedAt` | `Long?` epoch ms, `null` | Notifications cover entries scheduled after this instant |
| `textSize` | `small` · `normal` · `large` · `xlarge`, `normal` | Story text size |
| `readingFont` | `serif` · `sans` · `atkinson` · `opendyslexic`, `serif` | Story text font |
| `theme` | `system` · `light` · `dark`, `system` | Plan C adds `sepia` |
| `highContrast` | `Boolean?`, `null` | `null` follows Android's Contrast setting (high on Android 14+ means on, otherwise off); `true`/`false` once the reader sets the switch |
| `seasonPromptYear` | `Int?`, `null` | Year whose new-season prompt has been answered |

`skipAheadMinutes` (plan 1) is now written by skip ahead as well as reset by mode changes.

## Domain (pure Kotlin, JVM-tested)

1. **Unlocked** — `isUnlocked(entry, settings, progress, now, zone)` = scheduled time reached (plan 1
   `UnlockSchedule`, including skip ahead) **or** `progress.readAtMillis != null`. Used by the timeline, the
   reader and Previous/Next. The Next card, arrivals and wake times use the schedule only.
2. **Skip ahead** — `skipToNext(story, settings, now, zone)`: the offset that unlocks exactly the next
   scheduled entry (or every entry sharing that instant). `skipToDay(day, story, settings, now, zone)`: the
   offset that unlocks everything up to the end of that story day. Both return `max(current, needed)`; both are
   no-ops in Read freely. Back to real time sets the offset to zero.
3. **Arrivals** — `arrivedBetween(story, state, from, to, zone)`: entries whose scheduled unlock is in
   `(from, to]`, excluding entries already read; skip ahead included. `nextUnlockAfter(story, settings, now,
   zone)`: the next scheduled unlock instant, or none (Read freely, or after the last entry).
4. **Wake time** — `nextWake(story, state, now, zone, notificationsAllowed)`: the earlier of `nextUnlockAfter`
   and, for a Calendar reader whose season has ended, the season prompt instant (26 April 10:00 local); none if
   `notify` is false, notifications are blocked, or neither exists (always the case in Read freely). The
   in-app season card does not depend on notifications.
5. **Start over** — `startOver(state, removeBookmarks, today, tempoStart)`: clears progress (bookmarks kept
   unless `removeBookmarks`), resets skip ahead, moves Calendar to `seasonYearFor(today)`, restarts Same tempo
   from `tempoStart` (default today); mode and display settings kept; sets `lastNotifiedAt` to now.
6. **Season prompt** — `seasonPromptDue(state, today)`: true for a Calendar reader whose season year's
   6 November has passed, from 26 April of a later year, unless `seasonPromptYear` is that year. Answering
   (Start over or Not now) records the year.
7. **Start tomorrow** — `firstEntryIsClose(story, startDate, now, zone)`: true when the first entry with a time
   after the start of the story day (Jonathan Harker, 3 May 22:00 in the current dataset) would unlock less than
   one hour after `now`, or already has. Anchored to the dataset, not to a fixed hour.
8. **Mode switches** keep plan 1's `pickMode` (re-picking the current kind keeps it) and reset skip ahead.
9. **No backlog floods** — every action that changes the schedule (Begin, mode switch, start date, skip
   ahead, Back to real time, Start over, turning notify on) sets `lastNotifiedAt` to now.

## Screens and navigation

Routes `welcome`, `timeline`, `reader/{id}`, `settings`. Start destination: `welcome` until `onboarded`,
otherwise `timeline`. Plan 1's double-tap guard applies to every navigation. The timeline's top bar shows a
Settings icon in place of the plan 1 Mode button.

**Welcome** (one scrolling screen): title and two or three sentences on the idea; three mode cards with Same
tempo preselected — Same tempo "Starts today" with *Start today / Start tomorrow* when `firstEntryIsClose`;
Calendar with the season dates and, out of season, "Nothing unlocks until 3 May"; Read freely "Every entry,
now" — a *Notify me when entries arrive* switch (on); **Begin** saves the mode, asks the Android 13+
notification permission if the switch is on, sets `onboarded` and opens the timeline. A refused permission sets
`notify` to false.

**Timeline** additions: *Read it now* on the Next card (a confirmation the first time only); a one-line banner
while ahead ("Reading 2 days, 3 hours ahead · Back to real time"); the new-season card at the top when due
("Dracula begins again on 3 May" · Start over · Not now); the **end card** in the Next card's place once nothing
is left to unlock ("The end. You have read *Dracula* night by night." · Start over).

**Settings** (one screen):

- *Reading:* Mode (the plan 1 dialog with its switching text); Start date (Same tempo; date picker with the
  start-tomorrow hint); Skip to a day (story days 3 May – 6 November); Back to real time (only when ahead).
- *Notifications:* Notify me (asks permission when turned on; shows "Notifications are blocked in Android's
  settings · Open settings" when blocked); Sound and vibration… (opens the "New entries" channel settings).
- *Display:* Text size (four steps, live sample); Reading font (each label in its own font); Theme; High contrast
  (switch).
- *Start over* (bottom, error colour): dialog "Start over? Your reading progress is cleared and entries lock
  again on schedule." with ☐ "Also remove my N bookmarks" (off; shown only with bookmarks) and, for Same tempo,
  the restart date (today); Cancel · Start over.
- App version at the very bottom (About is plan C).

**Reader:** story blocks use the chosen text size and reading font; headers keep the theme's fonts.

**Theme:** `DraculaTheme` gains a dark palette (bone text on near-black, muted oxblood accents); the setting
picks system, light or dark. **High contrast** replaces the active palette with its high-contrast variant:
near-black on white (light) or white on near-black (dark); accents keep oxblood, darkened or lightened until
they pass; dividers, outlines and the unread dot are drawn stronger. The palettes are plain data
(`Palette` of named colour pairs) so their contrast can be checked on the JVM.

**Fonts:** Atkinson Hyperlegible and OpenDyslexic are bundled under the SIL Open Font License with their
license files; Serif and Sans-serif are the device's.

## Notifications

| Piece | Kind | Responsibility |
|---|---|---|
| `NotificationText` | pure | One arrival: "**Jonathan Harker's journal has arrived** · 3 May, 22:00" (letters: "A letter from Mina Harker has arrived"). Several: "**3 new entries**" with one line per entry. Names from `characters.json`. |
| `UnlockAlarm` | Android | One alarm (`setAndAllowWhileIdle`, `RTC_WAKEUP`), replaced on every reschedule, or cancelled when `nextWake` is none. |
| `UnlockReceiver` | Android | On the alarm (`goAsync`): load story and state; post the arrivals notification for `arrivedBetween(lastNotifiedAt, now)` if any and allowed; post the season prompt if due; save `lastNotifiedAt = now`; reschedule. |
| `SystemTimeReceiver` | Android | Reschedules on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and the app/channel block-state broadcasts. |

- Rescheduled also on app start and whenever saved state that affects timing changes (mode, start date, skip
  ahead, notify, Start over).
- Tapping a notification opens its entry in the reader (one arrival) or the timeline (several). The arrivals
  notification is cleared when the app comes to the foreground.
- Channel "New entries", importance Low, created at app start.
- Manifest adds `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`. Still no `INTERNET`, no exact-alarm
  permission.

**Edge cases**

- *Blocked in system settings:* `notify` is left as the reader set it; no alarms are set while blocked; the
  block-state broadcasts and app start reschedule; Settings shows the blocked hint. A permission refused on
  Welcome sets `notify` to false.
- *End of the story:* no alarms except the Calendar season prompt; the end card shows.
- *Dataset update:* `MY_PACKAGE_REPLACED` reschedules; an alarm set from old times only wakes, finds nothing
  and reschedules; an entry moved earlier into an already-notified window is not notified (it appears in the
  timeline); an unread entry moved later locks until its new time; progress is keyed by stable ids.

## Testing

- **Unit (JUnit, fixed clocks):** `UserState` defaults and fallbacks, plan 1 files counting as onboarded; once
  read, always readable (Back to real time, mode switch, zone moved west, clock set back); skip ahead (next
  entry or same-instant group, end of day, never backwards, Read freely no-op, reset); arrivals (window bounds,
  same-instant entries, read entries skipped, skip ahead); wake time (earliest of unlock and season prompt;
  none when off, blocked or Read freely); season prompt (from 26 April, once a year, Calendar only); start
  tomorrow (under, exactly, past, early in the day; dataset-anchored); Start over (progress, optional
  bookmarks, skip ahead, Calendar season, Same tempo date, display kept); notification text (one, several,
  letters); end card versus Next card; palette contrast (every text/background pair ≥ 7:1 in both high-contrast
  palettes, ≥ 4.5:1 — WCAG AA — in the standard light and dark palettes); `highContrast` null follows the
  system setting.
- **Emulator:** fresh install shows Welcome; Begin requests permission and a refusal turns notify off; a zone
  change making the next entry a minute away produces a silent notification whose tap opens that entry;
  notifications blocked → no alarm (`dumpsys alarm`) and the Settings hint; fonts and sizes in the reader; dark
  theme; high contrast in light and dark, and following the system Contrast setting; Start over with and without bookmarks; Skip to a day and Back to real time keep read entries listed;
  no crashes in logcat.

## Future spike: accessibility pass

After plan C, when the screens and theme have settled, a time-boxed spike checks the whole app with the tools
people actually use, and turns what it finds into issues (fixes are separate tasks, not part of the spike):

- **TalkBack:** every screen and the reader end to end — reading order, labels for icons and type markers,
  the unread dot and bookmark state announced, headings marked as headings, notifications read sensibly.
- **Switch Access and keyboard:** everything reachable and operable, visible focus.
- **Text and display:** system font size at 200 % and display size at largest, with every reading font and
  text size; nothing clipped or overlapping; landscape.
- **Contrast and colour:** Accessibility Scanner on each screen in light, dark, high contrast and sepia;
  colour-blindness simulation; no meaning carried by colour alone.
- **Touch targets** at least 48 dp; motion (no animation needed to understand anything; respects
  "Remove animations").
- **Output:** a short findings report in `docs/` and one GitHub issue per finding, graded by impact.

The spike's question: *can a reader who relies on TalkBack, large text or high contrast read Dracula night by
night without help?*

## Out of scope (plans B and C)

Filter chips, bookmarks list, swipe between entries (B). Sepia, period display font, ornaments, About page
with sources, licences and privacy policy, release build with R8 and App Bundle, Play listing and Data Safety
(C).
