# Who's who — design

*18 October 2026. Issue #204 ("A spoiler-free who's who of the voices").*

## Intent

Readers lose track of people. The owner's own example: Lucy, and the secondary men, Arthur Holmwood and Quincey
Morris. The moment that matters is mid-entry, when a name comes up and you can't place it. What helps then is **who
this person is and how they connect to the people you already know**, said without giving away anything you haven't
read yet.

So the who's who is a page of short profiles that **grow as the story reveals them**. Arthur starts as "a friend of
Lucy's" and becomes "Lucy's fiancé" only once you've read the letter that says so.

## Goals

1. **A page of its own, one tap from the reader.** A "Who's who" button in the reader's top bar, beside Bookmark,
   opens the page. *Reader only is my call.* The issue also suggested the filter sheet, but the owner's moment is
   mid-reading.
2. **It opens on the person who wrote the entry**, unfolded and scrolled into view, if that person has a profile you
   can see. Otherwise it opens at the top. Newspapers, firms and Stoker's note have no profile. *My calls:* it
   matches on the entry's `author` only, not the recipient. It uses the entry the page was opened from, and doesn't
   follow the reader afterwards. In two panes the page opens over the whole window, like Settings, so there's no
   reader beside it to follow.
3. **Anyone who matters to the story (owner)**, not only the 18 voices in `characters.yaml`. Renfield,
   Mrs Westenra, Mr Swales, Mr Hawkins and the Count never write an entry, and they're in. The newspapers and firms
   (Billington, Carter Paterson, the Dailygraph…) are not.
4. **Grouped by who they're connected to (owner; mockup B).** For example "Jonathan and Mina", "Transylvania",
   "Lucy and her suitors". A group shows only when at least one of its people does.
5. **Everything grows with what you've read (owner).**
   - Each line of a profile is tied to one entry, and shows once you've read that entry.
   - A person appears once at least one of their lines shows. Entries only record who wrote them and to whom, so a
     person's lines are the only link between them and the story. The first line goes on the entry where you meet
     them, whether they write it or are only mentioned in it.
   - A group's **name** grows the same way. "Lucy and her suitors" can't exist before 24 May, so before that it
     might be "Lucy and her friends". A person can **move** to another group at a given entry.
   - A person's **displayed name** grows too: Mina is "Mina Murray" until the wedding, Arthur becomes "Lord
     Godalming" when the story makes him so.
   - *My call, for Free mode, where entries can be read out of order:* every line, name, group name and group move
     checks only **its own** entry, never "anything later was read". That's the strictest reading of "only what
     you've read". The name and group shown are the latest whose entry you've read, falling back to the person's
     first. So **every line has to make sense on its own**: "Lucy's fiancé" may be read without the line before it.
6. **Each profile is folded by default.** Folded, it shows the name and the latest unlocked line. Unfolded, it
   shows every unlocked line in story order (by the entry's `order`, not the order you read them in), each with a
   small note saying where it came from ("Lucy to Mina · 24 May").
7. **"New" marks lines you haven't seen.** These are lines unlocked since you last opened the page. *My calls:*
   - The mark is per line. A folded profile with an unseen line shows the mark next to its name.
   - On the very first visit nothing is marked: marking everything would mean nothing.
   - The time of the visit is taken when the page opens. The marks then stay for that whole visit, through
     rotation, and clear on the next one.
   - It's stored as one timestamp in `UserState`, compared with each entry's `readAtMillis`. That way it travels
     with #205's backups (a restored phone has seen what the old one had seen) and is cleared by Start over. A
     missing field decodes to "never visited", so the backup format needs no new version.
8. **An empty page says so.** Before you've met anyone (only Stoker's note read, say): "People appear here as you
   meet them in the story."
9. **Accessibility to the app's usual standard.** Group names are headings. Each profile is a single expandable
   control with its expanded/collapsed state. The "new" mark is spoken ("new"), and the source note is read after
   its line. The accessibility ratchet gets cases for the page: an early state, a later state with a profile
   unfolded, large text, and both themes.

## What "read" means

An entry counts as read when its `EntryProgress.readAtMillis` is set. In the app that's when a page in the reader
comes to rest (`ReaderViewModel.onPageSettled`). It's the same notion the timeline uses. *My call:* unlocked or
arrived entries that haven't been opened don't count. The owner said "the lines for what you've already read".

So in Free mode, someone who jumps ahead and reads a late entry sees that entry's lines. Start over (which clears
reading) and loading a backup (#205) reset or replace this along with everything else.

## Writing the profiles

**I draft, the owner reviews and corrects (owner).** The drafts ship in the same PR, so the owner can review them
there.

Rules for the text:
- **A line reveals nothing beyond its own entry and the entries before it in story order.** No hints of what's
  coming: no "mysterious", no "little did she know", no "will".
- **Say how the person connects to people the reader already knows**: "Lucy's mother", "Seward's old teacher from
  Amsterdam". That is what the owner needs.
- **One short sentence per line, in plain present tense**, as of that point in the story. A later line can supersede
  an earlier one: "Lucy's fiancé" after "A friend of Lucy's". Earlier lines stay visible, in order. Showing the
  history is part of what helps.
- **No judgements the text doesn't make itself.**

*The cast is my call when drafting, for the owner to correct.* Roughly: Jonathan Harker, Mina, Lucy, Arthur, Seward,
Quincey, Van Helsing, Count Dracula, Renfield, Mrs Westenra, Mr Swales, Mr Hawkins, Sister Agatha, the captain of
the Demeter, and others who come back more than once. Someone named once and never again isn't in.

## Data and pipeline

*Layout is my call.* The profiles go in a **new file, `data/whos_who.yaml`**, not in `characters.yaml`. That file
drives header matching and the filter sheet, and putting non-writers in it would change both. A person who is also a
voice uses the same id as in `characters.yaml`, so the reader can open on the entry's author. Anyone else gets a new
id (`renfield`, `mrs_westenra`), and validation fails if a new id matches a `characters.yaml` id meant for someone
else.

Shape (a sketch, to be settled by the builder):

```yaml
groups:                    # in the order the page shows them
  lucy:
    names:
      - {from: <entry id>, text: Lucy and her friends}
      - {from: <entry id>, text: Lucy and her suitors}
people:
  arthur_holmwood:
    group:                 # one or more, each from an entry on
      - {from: <entry id>, id: lucy}
    names:
      - {from: <entry id>, text: Arthur Holmwood}
      - {from: <entry id>, text: Lord Godalming}
    lines:
      - {from: <entry id>, text: "A friend of Lucy's, the son of Lord Godalming."}
      - {from: <entry id>, text: "Lucy's fiancé."}
```

The pipeline (`python -m pipeline build --strict`) loads it, **validates** it, and emits `data/out/whos_who.json`.
`StoryAssetsTask` copies it into the app's assets next to `entries.json` and `characters.json`, and the
dataset-up-to-date check covers it. Validation fails the build when:
- a `from` isn't an existing entry id;
- a group id is unknown;
- a person has no lines;
- a person's first name or first group comes from an entry later than their earliest line. Since a person shows
  their first name and group until a later one is read, they could otherwise appear without a name or group;
- any text is empty.

Entry ids are stable (the pipeline already checks that an id keeps pointing at the same text), so ties survive
dataset changes. If an id ever changes, validation catches it.

*Review aid (my call; the builder may choose another form):* the pipeline's existing review page
(`python -m pipeline review`) gets a who's who section. It lists each person's lines in story order, each next to
its entry's date and author, so the owner can check every line against the entry it's tied to.

## Two PRs (owner)

1. **The page, the data format, validation, the review aid, and a starter cast**: Jonathan, Mina, Lucy, Arthur,
   Seward and Quincey, with their groups. This is enough to try the page on real data and to check the spoiler
   gating.
2. **The rest of the cast**, text only, reviewed on its own.

## Non-goals

- Tappable names in the text (the owner left these for later; the profiles are built so a later link could open
  them).
- Portraits, a relationship diagram, search within the page.
- A "show everything" switch.
- Profiles for newspapers, firms or Stoker.
- Any change to the privacy policy. Nothing new is stored beyond the "new" timestamp, and nothing leaves the phone.

## Anti-goals

These would make it a failure even if it works:
- **Any spoiler.** A line, a name, a group name or a group move that shows before the entry that reveals it. So
  would a hint of someone's importance or fate. Showing which people exist is a spoiler too, so nobody is listed
  before you've met them.
- A page that's slower to use than flipping back through the timeline.

## Left to the builder

- The button: text or icon, and how it fits beside Bookmark at large text (the title is already hidden there). It
  has to be labelled for TalkBack.
- In two panes, whether the page opens over the whole window (like Settings) or in the right pane.
- How folding, unfolding and scrolling to the writer are animated, if at all.
- How the "new" state is stored (see goal 7).
- The exact JSON shape, and how the app decodes it (alongside `StoryJson`).
