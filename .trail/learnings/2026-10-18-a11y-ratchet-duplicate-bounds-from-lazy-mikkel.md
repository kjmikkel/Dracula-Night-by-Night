---
title: "a11y ratchet: duplicate bounds from lazy rows kept for reuse"
author: Mikkel
date: 2026-10-18
branch: feat/205-export-import
cost: { minutes: 30, tokens: 146812 }
touches:
  - app/app/src/main/AndroidManifest.xml
  - app/app/src/main/java/com/jensen/draculanightbynight/AppContainer.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/content/Dates.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/Settings.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/SettingsScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/settings/SettingsViewModel.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserRepository.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserState.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/Rules.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/user/UserRepositoryTest.kt
---

## Decided
- The duplicate-bounds rule (a11y/Rules.kt) skips clickables whose layout, or any layout above it, isn't placed. After a scroll, a LazyColumn keeps rows that went off screen for reuse without placing them, so their semantics nodes stay in the tree and all report the list's top edge as their position. That gave the spurious "duplicate-bounds" on Serif / Sans-serif / Normal / Light / Dark / System in the start-over case at large text whenever Settings grew longer (#177, then #205's "Your reading" section).

## Tried and ruled out
- Skipping only controls that are partly cut off (boundsInRoot smaller than size): the reported nodes were whole, full-width and stacked at one spot, so that wasn't the cause.
- Moving settings rows elsewhere to dodge it (what #177 did): it just hides the problem until the next row is added.

## Watch out
- The other rules still look at unplaced nodes. If a ratchet case reports something odd for rows that aren't on screen, apply the same isPlaced() filter there.
