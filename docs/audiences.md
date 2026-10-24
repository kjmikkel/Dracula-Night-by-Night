# Audiences — Dracula Night by Night

*24 October 2026.*

Who the app is for, and how that settles design questions. Readers are described in three layers:

1. **One axis:** how attached the reader is to Stoker's text, from casual to purist.
2. **Situations:** circumstances any reader on the axis can be in, such as reading it for the first time or
   catching up.
3. **Access needs:** needs that are each their own spectrum and combine freely. They are not an axis.

An audience earns a place here only if it decides something, so each one names what puts it off and the
decisions it has already shaped.

## The axis: attachment to the text

Everyone on the axis wants the same thing, *Dracula* night by night. What changes along it is how much they let
the app change what they see.

| | Casual reader | Purist |
|---|---|---|
| **Who** | Thinks reading *Dracula* like this is a neat idea; no strong attachment to the text | Wants to read it like this, and cares about the text as Stoker wrote it |
| **Wants** | An easy read: less friction, help keeping track of who's who | Every word, in Stoker's order, and nothing that stands in for it |
| **Put off by** | Dense Victorian headers, repeated datelines, losing track of the cast | Anything that looks like the text was edited, cut or summarised |
| **Accepts help when** | It's there | It's optional, clearly separate from Stoker's text, and never replaces it |

**The rule this gives:** defaults protect the purist; settings serve the casual reader. Hiding a purist's text by
default costs more than asking a casual reader to switch something on.

- Stoker's text stays unchanged; the pipeline marks, never cuts (#177).
- "Skip repeated dates" and "Hide “From … to …” in letters" are both off by default (#177).
- Who's who is a page of its own, outside the text, opened from the reader (#204).

## Situations

These don't sit on the axis. A first-time reader can be casual or purist, and so can someone catching up.

| Situation | Wants | Put off by | Shapes |
|---|---|---|---|
| **First-time reader** (doesn't know the plot) | To meet the story as it unfolds | Any spoiler: a line, a name, a hint of someone's importance or fate, or even who exists | Who's who shows nobody before the entry that introduces them (#204) |
| **Re-reader** | The pleasure of the story in real time; knows how it ends | Being made to wait for help they don't need | Could be offered the full cast, but only by choice: the default is the first-time reader |
| **Late joiner / catching up** | To start after 3 May, or come back after missing days, without losing the thread | Being stranded behind the calendar, or told they've read what they skipped | Reading modes (calendar, same tempo, freely); the end card must not claim "You have read" what was skipped (#189) |
| **Group reader** (book club, friends) | To read the same entry on the same day as others | Drifting out of step | Calendar mode keeps everyone on the same date |
| **Non-native English reader** | To follow archaic words, Van Helsing's English, Mr Swales' Yorkshire | Getting stuck, or help that clutters the page | A glossary or word lookup would fit, as long as it stays optional (the axis rule) |
| **Student or teacher** | To find a passage, map entries to chapters, cite | Not being able to find a passage again | Chapter references, search, bookmarks |

When situations pull against each other, the stricter one sets the default: the first-time reader over the
re-reader, the purist over the casual reader.

## Access needs: not an axis

Accessibility could look like a second axis, but it isn't one. Each need is its own spectrum, and needs combine:
a reader can use TalkBack *and* large text, or high contrast *and* a keyboard. Listing every combination as an
audience would never end, so the app is designed around these principles instead:

- **One setting per need, each independent.** No "accessibility mode" that bundles them. Any combination has to
  work.
- **Follow the system first, let the reader decide.** High contrast follows Android's contrast setting, and "Skip
  repeated dates" follows TalkBack, until the reader chooses (#177). The phone already knows what the reader
  needs.
- **A need isn't an attachment level.** A TalkBack purist still wants every word. A setting turned on because of
  TalkBack must stay something the reader can switch off.
- **Check combinations, not just single needs.** The accessibility ratchet runs its cases (`a11y/Cases.kt`)
  across text sizes and settings, and a combination can fail where neither part does on its own (#177: rows under
  High contrast at large text).

The needs the app knows about so far (from the accessibility spike, #37):

| Need | What matters |
|---|---|
| **Screen reader (TalkBack)** | Everything labelled once, headings to jump by, nothing read twice, including repeated datelines read aloud |
| **Low vision** | Large text without clipping, high contrast |
| **Keyboard / switch access** | Visible focus, every control reachable, entry text scrollable without touch |
| **Motor** | Large touch targets, no gestures that need precision |
| **Reading fatigue / dyslexia** | Font choice, spacing, entries that can be read in short sittings |

## Not for

- **Scholars who want a critical edition**, with textual variants, footnotes or apparatus. The app follows one
  text and doesn't compare editions.
- **Readers who want an adaptation**: abridged, modernised or retold. The text stays Stoker's.

Naming these lets a feature request be turned down for a reason, not a preference.
