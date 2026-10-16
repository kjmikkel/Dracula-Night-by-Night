# Dracula Night by Night — App plan B: Filters, bookmarks list, swipe

**Status:** Approved in brainstorming, written for review · **Date:** 2026-10-16
**Builds on:** [App design](2026-10-14-app-design.md) and [plan A](2026-10-15-app-plan-a-design.md) (implemented: Welcome, Settings, notifications, skip ahead, Start over)
**Followed by:** plan C (gothic theme, About and privacy policy, release build)

## Purpose

Let a reader follow one thread of the story, find what they marked, and move between entries without going
back to the list. Plan B delivers the app spec's timeline filter chips, bookmarks list and swipe between
entries, as revised below.

## Decisions (and what they change in the app spec)

| # | Decision | App spec text it replaces |
|---|---|---|
| B1 | **Voice chips for the main voices plus Others**: Jonathan, Mina, Seward, Lucy, Van Helsing, Demeter, Others. Others covers every other author (newspapers, firms, one-off writers) and any author a future dataset adds. Matched on the author only. | Timeline "Filter chips for characters" (made concrete) |
| B2 | Chips combine **OR within a group, AND between groups** (voices × document types); an empty group lets everything through. | New |
| B3 | **The filter only changes the timeline list.** The Next card, end card, ahead banner, season card, arrivals and notifications always use the whole story. A "Filtered" line says so. | New |
| B4 | **The chips live in a bottom sheet** opened from a filter icon, not in a fixed row: with plan A's pinned cards a 14-chip row would take a third of the screen at large text. | Timeline "Filter chips" (placement) |
| B5 | **The reader follows the filter it was opened with**, and a switch **[filter \| All]** (shown only while a filter is on) changes Previous, Next and swipe to the whole story and back. The switch belongs to that reading session; it never changes the saved filter. | Reader "swipe to previous/next unlocked entry" (made concrete) |
| B6 | **Swipe is a pager whose pages follow the finger** (`HorizontalPager`); an entry is marked read only once its page has settled. | Reader "swipe", "opening marks read" (made concrete) |
| B7 | **Bookmarks open from a bookmark icon in the timeline's top bar**, one tap, not an overflow menu. | Screens §4 "reached from the timeline overflow menu" |
| B8 | **Bookmarks sort in story order or most recently bookmarked**, chosen with a saved switch; bookmarks made before plan B count as oldest. | New |
| B9 | **A bookmark is removed from the list with its ★ button**, with Undo. | New |
| B10 | **Bookmarks on entries that are not readable** (after Start over, which keeps bookmarks but clears read state) **stay listed, dimmed, with their arrival time**; tapping one offers *Read it now*. | New |

## Saved reader state

`UserState` gains fields; every field has a default, so files from plan A load unchanged. An unknown value
falls back to the default or is dropped — never a crash.

| Field | Type, default | Meaning |
|---|---|---|
| `filterVoices` | set of `jonathan` · `mina` · `seward` · `lucy` · `van_helsing` · `demeter` · `others`, empty | Selected voice chips; unknown names are dropped |
| `filterTypes` | set of `diary` · `phonograph` · `letter` · `telegram` · `newspaper` · `memo` · `log`, empty | Selected document-type chips; unknown names are dropped |
| `bookmarkSort` | `story` · `recent`, `story` | Order of the bookmarks list |
| `EntryProgress.bookmarkedAtMillis` | `Long?` epoch ms, `null` | When the bookmark was made (app clock). Set on bookmarking, cleared on removing; Undo restores the old value. |

Start over keeps `bookmarkedAtMillis` for the bookmarks it keeps.

## Domain (pure Kotlin, JVM-tested)

1. **Voice** — `enum Voice { JONATHAN, MINA, SEWARD, LUCY, VAN_HELSING, DEMETER, OTHERS }`;
   `voiceOf(entry)` maps the dataset's author ids (`jonathan_harker`, `mina_harker`, `john_seward`,
   `lucy_westenra`, `van_helsing`, `demeter_captain`) and returns `OTHERS` for every other id.
2. **EntryFilter** — `EntryFilter(voices: Set<Voice>, types: Set<DocumentType>)` with `matches(entry)` (B2),
   `isActive`, and a short label for the reader's switch ("Jonathan", "Jonathan +1", "Letters").
3. **Reading order** — `readingOrder(story, user, settings, now, zone, filter: EntryFilter?, keep: String?)`:
   readable entries (plan A's `isReadable`) in story order, filtered when a filter is given. `keep` is an entry
   id included in its story position even if it does not match, so switching the reader back to the filter never
   throws the reader off the page they are on. The timeline, the reader and the pager all use this one function.
4. **Filtered timeline** — `buildTimeline` takes the filter: `days` contain only matching readable entries
   (dates with none are left out); new fields `hiddenCount` (readable entries the filter hides) and
   `filter`. `firstUnreadId` becomes the first unread *matching* entry. Next, finished, skip ahead and season
   prompt are computed from the whole story, unchanged.
5. **Bookmarks** — `bookmarks(story, user, settings, now, zone, sort): List<BookmarkRow>`: every bookmarked
   entry that exists in the story. A row carries the timeline row fields plus `bookmarkedAt: Instant?` and
   `arrivesAt: ZonedDateTime?` (null when readable; the scheduled unlock otherwise). `story` sorts in story
   order; `recent` sorts newest `bookmarkedAt` first, then rows without one in story order. Locked rows sort
   in the same way as readable ones.
6. **Skip to an entry** — `SkipAhead.toEntry(entry, story, settings, now, zone)`: the minutes of skip ahead that
   make that entry readable (and with it every entry scheduled before it); never less than the current offset.
   `entriesUnlockedBy(...)` counts how many entries the skip would make readable, for the confirmation text.

## Screens and navigation

### Timeline

- **Top bar:** title, then three icons: Filter, Bookmarks, Settings, each with a TalkBack label. The Filter
  icon shows a dot while a filter is on, and its label becomes "Filter, N active".
- **Filter sheet** (modal bottom sheet): heading "Voices" with the seven voice chips, heading "Documents" with
  the seven type chips (Diaries, Phonograph, Letters, Telegrams, Newspapers, Memos, Logs), and *Clear all*.
  Chips wrap onto several lines (no horizontal scrolling) and apply at once; there is no Apply button. Chips
  are toggleable with a selected state for TalkBack.
- **Filtered line**, pinned above the list with plan A's cards while a filter is on:
  "Filtered: Jonathan, Letters · 41 hidden · Clear". *Clear* is its own button.
- **Nothing matches:** "No entries match yet." with a *Clear filter* button, in place of the list. The Next
  card or end card still shows.
- Opens scrolled to the first unread matching entry.
- Opening an entry from a filtered list opens the reader with the filter (`reader/{id}?filtered=true`).

### Reader

- `ReaderViewModel(startId, filtered)` holds the order (ids from `readingOrder`) and shows it in a
  `HorizontalPager`, one page per entry; each page is plan A's reader (header and blocks in a lazy list) with
  its own scroll state. The order is recomputed as time passes; the current page keeps its entry id, never its
  index, so a newly arrived entry never moves the page.
- **Switch** in the bottom bar between Previous and Next, shown only when opened filtered:
  `‹ Previous   [ Jonathan | All ]   Next ›`. Switching keeps the current entry and rebuilds the order around it
  (B5, `keep`). Default: the filter side when opened from a filtered timeline; All otherwise (bookmarks list,
  notifications, unfiltered timeline).
- **Previous / Next** animate the pager. TalkBack labels say which order: "Next Jonathan entry", "Next entry in
  the story". At either end the button is disabled and the pager stops.
- **Marking read** happens when a page settles. Scroll position is saved for the page being left and on pause,
  as today. The bookmark toggle in the top bar acts on the current page.
- **Back** returns to where the reader was opened from (timeline or bookmarks); swiping adds no history.
- `buildReader`'s own previous/next computation is replaced by `readingOrder`.

### Bookmarks

- **Route** `bookmarks`; top bar "‹ Back · Bookmarks".
- **Sort switch** at the top: `[ Story order | Recently bookmarked ]`, saved to `bookmarkSort`.
- **Rows** like the timeline's (type icon, author, in-story date and time, preview) with a ★ button (48 dp,
  label "Remove bookmark, <author>, <date>"). *Recently bookmarked* also shows "Bookmarked 14 Oct" (English).
- **Locked rows** (B10): a lighter row background and a lock icon, text at the palette's normal contrast
  (≥ 4.5:1, ≥ 7:1 in high contrast), and "Arrives again Thu 22 Oct, 23:00" in the row. TalkBack reads
  "Locked, arrives again Thursday 22 October, 23:00". Tap: a dialog "This entry arrives again Thu 22 Oct,
  23:00. Reading it now also makes N earlier entries readable and puts you ahead of real time." with
  *Read it now* / *Cancel*; Read it now applies `SkipAhead.toEntry`, then opens the entry. Read freely never
  has locked rows.
- **Removing:** ★ removes the bookmark at once; a snackbar "Bookmark removed · Undo" restores it with its old
  `bookmarkedAtMillis`.
- **Opening** a readable row opens the reader with All.
- **Empty:** "No bookmarks yet. Tap ☆ Bookmark while reading to keep an entry here."

## Testing

- **JVM:** `voiceOf` (every main voice, an unknown author is Others); `EntryFilter.matches` (OR within, AND
  between, empty groups); filter label; `readingOrder` (all, filtered, `keep` of a non-matching entry, locked
  entries excluded, read entries kept); filtered `buildTimeline` (empty dates left out, `hiddenCount`,
  `firstUnreadId` among matches, Next card and finished unchanged by the filter); `bookmarks` (story order,
  recent order, entries without a time stamp last, locked rows with `arrivesAt`, ids missing from the story
  ignored); `SkipAhead.toEntry` and the count; `UserState` (plan A file loads with defaults, unknown voice or
  type dropped, unknown sort falls back); `startedOver` keeps `bookmarkedAtMillis`; repository toggle sets and
  clears the time stamp, Undo restores it.
- **Emulator** (debug time travel and state seeding as in plan A): filter sheet and Filtered line at 200 %
  text; nothing-matches state; swipe marks read only on settle (a page dragged past and released back stays
  unread); the switch keeps the current entry; bookmarks list in both orders; Start over, then a dimmed
  bookmark → Read it now; Undo; TalkBack on the chips, the switch and the ★ button.

## Out of scope

Notes on bookmarks; filters that affect notifications or the Next card; searching the text; two-pane layout
on wide screens (issue #58); sepia, display font, ornaments, About page, release build (plan C); the
accessibility pass (#37).
