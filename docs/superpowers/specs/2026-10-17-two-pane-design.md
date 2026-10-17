# Two panes on wide screens — design

*17 October 2026. Issue #58 ("Two-pane layout on wide screens: timeline on the left, entry on the right").*

## Intent

On tablets and unfolded foldables, the timeline and the reader each fill the whole screen. Short entries leave most of
it empty, and moving between entries means going back and forth between two screens. The owner uses the timeline to
**jump around**, not to read straight through. So the point of this work is that tapping one row after another shows
each entry right away, next to the list, without leaving it.

Some readers will still want a whole page of text, so the timeline can be hidden.

## Goals

1. **When two panes are used.** The window is at least **600 dp wide and at least 480 dp tall**. That covers tablets
   in either orientation and unfolded foldables. Phones never get two panes, in portrait or held sideways (the owner
   ruled out sideways phones; the height rule is how that's done, because many phones are over 840 dp wide in
   landscape). *Thresholds are my call.* 480 dp matches the app's existing short-window line (`SHORT_WINDOW_DP`).
2. **Opening the app looks the same as today.** The timeline fills the screen with no right-hand pane until an entry
   is opened. This is true on a fresh start, after the app was killed, and after Begin.
3. **Opening an entry splits the screen.** Tapping a timeline row (or a Tonight card, Next card, etc.) moves the
   timeline to the left and shows the entry on the right. Tapping another row swaps the entry in the right pane.
   Entries opened from **Bookmarks** or from a **notification** also open in the right pane when two panes are used
   (Bookmarks closes and you land on the split view).
4. **The right pane is the reader as it is today.** Header, text, bookmark switch, Previous / Next, swiping, the
   filter/All switch, newspaper column, reading font and size, and saved scroll position all work the same.
5. **The timeline follows the entry.** The highlighted row is the entry shown on the right. Previous / Next and swipes
   move the highlight, and the timeline scrolls so the highlighted row stays visible.
6. **Closing the entry.**
   - A **✕ "Close entry"** button sits at the start of the entry's top bar, where the reader's ← Back is today.
     While the timeline is on screen there is nothing "behind" the entry, so it's ✕, not ←.
   - The system **Back** gesture does the same. The entry closes and the timeline fills the screen again. Back from
     the full-width timeline leaves the app, as today.
7. **Hiding the timeline (the owner chose this over a thin strip or a separate full-page screen as the least
   obtrusive).**
   - A **☰ "Show/Hide timeline"** button in the entry's top bar hides the timeline completely. The entry then fills
     the window, laid out as the reader is today (same text column, same newspaper column). The same button brings
     the timeline back, scrolled to show the highlighted row.
   - While the timeline is hidden, ✕ is still there and closes the entry (back to the full-width timeline). Previous,
     Next and swiping still work.
   - Whether the timeline is hidden is **remembered** across rotation, folding and app restarts. It's a reading
     preference: someone who hid it wants a full page next time too.
   - The button only appears when two panes are in use.
8. **Changing size with an entry open.**
   - Two panes → too narrow or short (rotating, folding, resizing in multi-window): the open entry becomes today's
     full-screen reader, at the same position. Back from there goes to the timeline.
   - One pane → wide enough again with the reader open: the split returns with that entry on the right.
   - Neither change loses the open entry, the text's scroll position, or the timeline's scroll position.
9. **Width split, as a starting point.** The timeline keeps its normal phone width (about 360–400 dp; *exact
   value is the builder's call*) and the entry gets the rest. The issue leaves this open to experimentation, so expect
   it to be adjusted once it can be seen on a device.
10. **Accessibility and keyboard, held to the app's existing standard.**
    - Keyboard Tab order: timeline first, then the entry. Within the entry, the order stays as in #114 (text before
      the bottom bar).
    - ✕ and ☰ are labelled for TalkBack ("Close entry", "Hide timeline" / "Show timeline", the latter with its
      state).
    - Large text (font scale 2.0 with Extra large) has to work in the right pane, which can be narrower than a phone.
    - The accessibility ratchet covers the two-pane layout: the split view and the hidden-timeline view, in its usual
      looks, at a tablet-sized window.

## Settled after review (my calls, for the owner to check)

- **Filters.** The timeline's filter is never changed by the reader. If the entry on the right isn't in the filtered
  timeline (the reader is following All, or Previous/Next went past the filter), no row is highlighted. Changing the
  filter while an entry is open leaves that entry open. The next row tapped opens with the new filter state, the same
  way opening from the timeline sets the reader's filter/All switch today.
- **Back stack.** In two panes, whatever path led to the entry, the stack under the split view is just the timeline.
  Back or ✕ goes to the full-width timeline. An entry opened over Bookmarks in one pane drops Bookmarks from the
  stack when the window widens. Going back to one pane, the stack is timeline → reader.
- **Bookmarks and Settings** open full-screen over the split view. Coming back returns to the split view with the same
  entry and the same timeline position.
- **Timeline chrome.** The timeline's top bar, filter sheet and cards all stay inside the left pane.
- **"Same position"** across a size change means the same block and offset within it, as saved positions mean today.
  The text re-wraps to the new width, so the same pixel isn't expected.
- **Locked or not-yet-arrived entries** show in the right pane exactly as the reader shows them today.
- **Window size** is measured in raw window dp, not size-class buckets.
- **Ratchet sizes.** A 1280×800 dp landscape and an 800×1280 dp portrait tablet window, each in the ratchet's six
  usual looks.
- **Code freedom.** The builder may restructure the nav graph (e.g. one adaptive destination holding both panes) and
  change `ReaderViewModel`'s constructor, updating its tests. The behaviour in Goals 4–8 is what has to hold, not the
  current wiring.

## Non-goals

- Nothing changes on phones, in either orientation.
- Bookmarks, Settings and About stay full-screen. They don't get panes of their own.
- No two-pane reopening on launch: the app doesn't remember which entry was open last.
- No drag handle for resizing the split.

## Anti-goals (would count as a failure even if it works)

- A wide screen that looks different from today *before* an entry is opened, such as an empty right pane or a
  placeholder.
- Rotating or folding that drops the open entry, jumps the text to the top, or loses the timeline's place.
- A regression in TalkBack or keyboard use on phones, or new ratchet failures.

## Constraints

- Jetpack Compose, the existing `NavHost` in `MainActivity.AppNavigation`, and the app's own view models. The
  reader's view model is keyed by entry id and filtered flag today.
- The window-size check uses Compose's adaptive APIs (e.g. `currentWindowAdaptiveInfo()`), added through the version
  catalogue under the existing Compose BOM.
- Every new screen state counts as a screen for the ratchet's coverage check (#37's design).
- Notifications currently open `reader/{id}` over the timeline. That has to keep working in both layouts.

## Left to the builder

- How the panes are structured: a list-detail scaffold from Material 3 adaptive, or a hand-made `Row` over the
  existing screens. Pick whichever keeps Back, rotation and the ratchet simplest.
- How the right pane changes entry (a new reader view model per entry, or one that moves to the tapped entry), as
  long as saved positions and the follow switch behave as today.
- Where the "timeline hidden" preference is stored (alongside the other display settings is the obvious place).
- The icons and exact wording, within the labels above.
- The exact timeline width.
