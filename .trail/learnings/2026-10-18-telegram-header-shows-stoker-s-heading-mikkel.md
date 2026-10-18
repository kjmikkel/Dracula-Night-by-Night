---
title: "Telegram header shows Stoker's heading; From-to setting covers telegrams"
author: Mikkel
date: 2026-10-18
branch: claude/dreamy-fermat-7f95eb
cost: { minutes: 4, tokens: 60808 }
touches:
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/SettingsScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserState.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/Cases.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/reader/ReaderRepeatsTest.kt
---

## Decided
- The telegram header (HeaderStyle.TELEGRAM in ReaderScreen.kt Header()) now renders entry.header in TelegramStyle under the TELEGRAM strip, including second lines such as the 1893-09-17 "(Sent to Carfax, Sussex, … delivered late by twenty-two hours.)" note. Before this, every telegram heading was missing from the reader.
- Owner (2026-10-18): the "Hide “From … to …”" setting now covers letters and telegrams (label "Hide “From … to …” in letters and telegrams"). This supersedes the #177 learning's "letters only", which rested on telegrams having no heading.

## Watch out
- Two telegrams (1893-10-24 jonathan_harker-02, 1893-10-28 john_seward-01, Rufus Smith's) have no header and headerNamesParties false, so their From line always shows.
- The telegram heading is plain text, not semantics heading(); the "TELEGRAM" strip stays the heading node.
