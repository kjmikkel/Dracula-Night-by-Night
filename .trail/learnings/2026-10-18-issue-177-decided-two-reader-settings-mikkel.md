---
title: "Issue #177 decided: two reader settings for repeats"
author: Mikkel
date: 2026-10-18
branch: feat/177-skip-repeats
cost: { minutes: 15, tokens: 142629 }
touches:
  - app/app/src/main/java/com/jensen/draculanightbynight/content/Story.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/content/StoryJson.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/Reader.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/Settings.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/SettingsScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/SettingsViewModel.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserRepository.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserState.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/Cases.kt
---

## Decided
- Two settings, both off by default (owner, 2026-10-18): "Skip repeated dates" and "Hide “From … to …” in letters". The owner thinks casual readers would be put off more by text missing by default than by a repeat.
- "Skip repeated dates" follows TalkBack (on while touch exploration is on) until the reader chooses, the same pattern as High contrast following Android's contrast setting (a nullable Boolean in UserState).
- The pipeline marks, never cuts: data/pipeline/repeats.py gives the first non-note block `repeats: [start, end)`, covering a dateline that says only the entry's date and place plus its join ("4 May.—", "“24 May."). The app's Block.withoutRepeat() applies it. An emptied block stays as an empty list item, so saved reading positions keep their block indexes.
- `headerNamesParties` is true when classify_header(header) resolves to the entry's own author and recipient, so overrides that change a party switch it off.
- Hiding "From … to …" applies to letters only. The telegram header never shows Stoker's heading, so on a telegram that line is the only one naming the parties.

## Tried and ruled out
- Putting the two rows in Settings → Display, under High contrast: at large text the a11y ratchet's start-over case then reported duplicate bounds for Serif, Sans-serif and Normal, an artifact of the list's scroll position. They sit in the Reading section instead (which is also where the agreed design put them).

## Watch out
- Some datelines keep the full stop outside the italics ("22 July" + ".—Rough"), so the join regex takes an optional leading . or , before the dash.
- Datelines that add anything (a time word, "Later.", "Telegram, …") are never marked. The rule is "date, then nothing or exactly the entry's place".
