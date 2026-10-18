---
title: "Fixing the 2026-10-18 review bugs (#189-#193, #200)"
author: Mikkel
date: 2026-10-18
branch: fix/200-saved-place
cost: { minutes: 25, tokens: 158448 }
touches:
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/Reader.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderViewModel.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserRepository.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/user/UserState.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/reader/ReaderTest.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/reader/ReaderViewModelTest.kt
  - ".idea/.gitignore"
  - ".idea/caches/deviceStreaming.xml"
  - ".idea/deviceManager.xml"
---

## Decided
- #189: `finished` still means "nothing left to arrive" (notifier, season prompt use it). The end card needs `TimelineState.allRead` (unreadCount over the whole story, not the filter); otherwise an "Everything has arrived · N unread" card, and nothing in Read freely.
- #190: the dataset marks the preface `"undated": true` (pipeline sets it when an override dates an entry the book left undated; emitted only when true). Display goes through `Entry.storyTime` / `storyDayTime`: exact non-estimated 23:59 is "midnight", undated shows no date or time. The first-timed-entry rule now tests `!undated`, not 00:00.
- #192: `Theme.Dracula` (values / values-night) sets windowBackground to the palette backgrounds; `WindowBackgroundTest` keeps the XML equal to `Palettes.Light/Dark.background`. It follows system day/night only; the reader's own theme choice can't be known before launch.
- #193: `domain/CalendarBacklog.kt` counts what On the actual days unlocks at once and phrases it; used by the Welcome card and the Settings mode dialog. No "start next year" option.
- #200: `EntryProgress.blockPermille` (thousandths of the block's height) is saved alongside the pixel `blockOffset`. On open the reader scrolls to the block, measures it, then moves to the share; it saves nothing until that's done, so the pixel guess never overwrites the place. Old saves (no permille) keep the pixel behaviour.

## Watch out
- `ReaderPlaceTest`: the save runs in the app scope, off the Compose test clock. Wait for the stored offset to change, and assert it changed (the seed's permille alone would pass a lazy test).
