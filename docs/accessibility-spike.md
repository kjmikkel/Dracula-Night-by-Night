# Accessibility spike — findings

*16 October 2026, version 1.0.0 (main at a55d749). Issue #37.*

**Question:** can a reader who relies on TalkBack, large text or high contrast read Dracula night by night without
help?

**Answer:** yes, but not comfortably, and a keyboard-only reader can't read at all.
- **TalkBack:** every control has a label and every screen can be operated. Navigation is slow, though: the day
  headers in a 352-item list aren't headings, and many buttons read their label twice.
- **Large text:** large text works, with a few clipped or hidden pieces at the extreme.
- **Keyboard:** a keyboard-only reader can reach every control but can't see the focus, and can't scroll an
  entry's text.

## How it was checked

- **Device:** Android 17 emulator (1080 × 2400, 420 dpi) running TalkBack 17.0, with a debug build of 1.0.0.
- **TalkBack:** every screen was walked swipe by swipe.
  - Swipes and taps were sent as raw touchscreen events, so TalkBack treated them as real gestures.
  - The speech engine was switched off, so TalkBack wrote every utterance to the log. The quotes below are what it
    would have said.
  - TalkBack's hints ("double tap to activate") weren't captured.
- **Mechanical sweep:** a script read each screen's accessibility tree and flagged controls without a label and
  touch targets under 48 dp.
- **Keyboard:** Tab, Enter, the arrow keys, Page Down and Space, with TalkBack off.
- **Large text:** system font size 200 % with the largest display size (density 525). Each reading font was tried
  at Extra large, on a telegram, a newspaper cutting and a letter.
- **Contrast:** light, dark and high-contrast screens were inspected. Text contrast is already enforced by
  `PaletteTest` (≥ 4.5:1, ≥ 7:1 in high contrast). There is no separate sepia palette, because light is the
  parchment palette.
- **Colour:** Android's colour-correction filters can't be captured in screenshots. The check is therefore by
  design: what is shown in colour is also shown by shape or words.
- **Motion:** with all animation scales at 0, the filter sheet, the reader's swipe and Next still work.

## Findings

| # | Finding | Impact | Issue |
|---|---|---|---|
| 1 | The keyboard focus indicator is almost invisible (a faint grey wash) | High | #113 |
| 2 | An entry's text can't be scrolled with a keyboard | High | #114 |
| 3 | Day headers, screen titles and Settings sections aren't headings | High | #115 |
| 4 | Buttons read their label twice ("Back. ‹ Back", "… Black star") | Medium | #116 |
| 5 | The reader's bookmark button has no on/off state and is silent when tapped | Medium | #117 |
| 6 | At 200 % and the largest display size: a clipped ★, the author hidden as "…", little room for text | Medium | #118 |
| 7 | The Tonight card takes five TalkBack stops | Low | #119 |
| 8 | Estimated times are shown only by italics; TalkBack doesn't say "about" | Low | #120 |
| 9 | Radio groups aren't grouped ("1 of 4" missing) | Low | #121 |
| 10 | Start over's checkbox doesn't say "not checked" when off | Low (check on a phone) | #122 |
| 11 | OpenDyslexic shows very wide letter spacing | To check | #123 |

**Impact** describes what a reader meets:
- **High:** blocks reading, or makes moving through the app much slower.
- **Medium:** costs time or hides information.
- **Low:** an annoyance.

## What already works

- **Labels:** every control in the app has a label. The icons are "Filter", "Filter, 1 active", "Bookmarks" and
  "Settings".
- **Timeline rows:** each row is read as one stop, for example "22:00. Unread. Jonathan Harker · Journal ·
  Bistritz. …". The unread dot is said in words.
- **Reader:** the text is read paragraph by paragraph. Document headers ("Journal", the telegram strip, the
  newspaper masthead) are headings. When the reader opens, TalkBack starts at Back.
- **Filter sheet:** its two sections are headings. Each chip says "Selected" or "Not selected". When the sheet
  closes, the filter button says "Filter, 1 active".
- **Reader switch:** reads "Selected. Mina. Radio button. 1 of 2", inside "Previous and Next follow".
- **Bookmarks:** after a removal the snackbar is announced ("Removed Mina Harker, 1 August. Undo"), and Undo can be
  reached and used.
- **Settings:** switches say On or Off. About's sections are headings.
- **Touch targets:** every control on every screen is at least 48 × 48 dp, including at 200 %.
- **Colour:** nothing is shown by colour alone:
  - unread is a dot plus the word "Unread";
  - bookmarks are ★ or ☆;
  - selected chips have a check mark;
  - an active filter shows a dot plus the text "Filtered: …".

  The one exception is estimated times (finding 8).
- **Large text:** at 200 % every text wraps without overlapping, and the Atkinson Hyperlegible, serif and sans-serif
  readers are fine. The issues are listed under finding 6.
- **Motion:** nothing depends on animation.

## Not covered

- **Switch Access:** not run itself. Keyboard focus stands in for it, and findings 1 and 2 apply to it too.
- **Accessibility Scanner:** not on the emulator. The ratchet tests' Accessibility Test Framework checks run the same
  rules.
- **Notifications:** how TalkBack reads them wasn't checked.
- **Bookmarked timeline rows:** not walked with TalkBack. The ★ has the description "Bookmarked", but whether TalkBack
  says it wasn't confirmed.
- **Landscape at 200 %:** only seen in passing. The known card position is #108.

## Ratchet tests (next step)

#37 also asks for ratchet tests, so that what a machine can check never quietly regresses. The app has no Compose UI
tests yet, so this needs Robolectric, `ui-test-junit4` and the Accessibility Test Framework. It gets its own spec and
plan.

From this spike, the mechanical checks would be:
- **Headings:** the heading nodes on each screen (finding 3).
- **Labels:** no clickable whose description repeats its own text (finding 4).
- **State:** the bookmark exposes a toggle state (finding 5), and radio groups are a selectable group (finding 9).
- **Large text:** no clipped text or icons at font scale 2.0 with Extra large text (finding 6).
- **Spoken text:** "about" in the label of an estimated row (finding 8).
- **Already passing:** labels on every clickable and 48 dp targets, held so they stay that way.

Findings 1 and 2 (focus visibility and keyboard scrolling) and 11 (the font) stay manual checks in this report.
