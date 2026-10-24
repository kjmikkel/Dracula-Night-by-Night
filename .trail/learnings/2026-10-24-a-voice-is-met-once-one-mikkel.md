---
title: "A voice is met once one of its entries is read, not when it arrives"
author: Mikkel
date: 2026-10-24
branch: claude/ui-ux-ratchet-fixes
cost: { minutes: 16, tokens: 120464 }
id: l-668864c4
scope: repo
status: active
touches:
  - app/app/src/main/java/com/jensen/draculanightbynight/reader/ReaderScreen.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/domain/Voice.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/timeline/Timeline.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/timeline/FilterSheet.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/timeline/TimelineScreen.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/domain/VoiceTest.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/Cases.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/SpoilerCasesTest.kt
---

## Decided
The owner chose (2026-10-24, #220) that the timeline filter offers a voice chip only once the reader has *read* an entry by that voice (`metVoices` in domain/Voice.kt: `readAtMillis != null`). Others is always offered. An arrived-but-unread entry, or a bookmark, doesn't count. This supersedes the earlier learning that defined "met" by arrival.

## Watch out
- The a11y filter-sheet case seeds `tempo()` plus marking `filterSheetRead` (Jonathan's 3 May entry) read; `SpoilerCasesTest` derives the unmet list from those read entries, so change both together.
- The case's `ready` text must not be on the timeline below the sheet (`DialogReadyTest`): "Jonathan" is, so it stays "Voices".
- The reader uses MediumTopAppBar (#222) except at large text, where the author leads the body text instead (#118/#151). The bar is never given a scrollBehavior: collapsing would move Back/Next for TalkBack.
