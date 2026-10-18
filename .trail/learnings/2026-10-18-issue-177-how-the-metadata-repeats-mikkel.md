---
title: "Issue #177: how the metadata repeats the text's datelines"
author: Mikkel
date: 2026-10-18
branch: feat/179-paragraph-indent
cost: { minutes: 2, tokens: 91743 }
touches:
  - ".idea/.gitignore"
  - ".idea/caches/deviceStreaming.xml"
  - ".idea/dracula-night-by-night.iml"
  - ".idea/misc.xml"
  - ".idea/modules.xml"
  - ".idea/vcs.xml"
---

## Decided
- Stoker's text stays unchanged (owner, 2026-10-18). Repeats are to be handled on the app's side (the header and the "From… to…" line), not by trimming the text in the pipeline.
- The header is for finding your place in the story, so it keeps the date and place. Hiding them from the header was ruled out.

## Tried and ruled out
- (Nothing implemented yet. Still deciding which header lines to soften or drop.)

## Watch out
- The metadata comes from the dateline itself: split.py parse_dateline sets date, time and place, so the two match by construction. About 200 of the 235 entries open with one.
- Diary, phonograph, log and memo datelines are an italic lead inside the first `para`, not a `dateline` block. Only letters and some telegrams use the `dateline` role, often starting with an opening quote mark.
- About one in six datelines add something the header lacks ("Midnight.", "Evening.", "10 p. m.", "Later.", "afternoon"). Header times are mostly estimates ("about 14:45"). The year never appears in the text.
- The reader header is LazyColumn item 0 (ReaderScreen.kt), so it scrolls away and is never pinned. Saved positions count it as item 0, so don't split it into several items.
- Letters and telegrams stack Stoker's heading (entry.header), the app's "From X to Y" line, the date line and the dateline, so they repeat the most.
