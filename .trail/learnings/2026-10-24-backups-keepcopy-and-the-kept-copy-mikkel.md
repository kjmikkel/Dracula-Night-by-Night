---
title: "Backups: keepCopy and the kept-copy collector store at once; check-and-store is locked"
author: Mikkel
date: 2026-10-24
branch: claude/adoring-volhard-9845cd
cost: { minutes: 18, tokens: 69267 }
id: l-2e5d0722
scope: repo
status: active
---

## Decided
- `user/Backups.kt`: `keepCopy`'s own `updateCopy` and the debounced collector in `init` can finish at the same moment on different threads. `storeTarget` writes prefs then `_target`, so two writers could interleave and leave disk and memory with different `savedAtMillis` (a restart then shows another time), and `updateCopy` could store a target `stopCopy` had just cleared. The check (`_target.value?.uri == target.uri`) and `storeTarget` now run under one `synchronized(lock)`; nothing in it suspends, so `stopCopy` stays non-suspending.
- `BackupsTest.theChoiceIsRememberedAcrossRestarts` builds its instances on a cancelled scope so the collector never runs: it tests persistence, and the collector could store a newer time between the test's two reads even with the lock.

## Tried and ruled out
- A `Thread.sleep` in the test to "let the copy settle": the flake didn't reproduce that way, and the mismatch could be lasting, not just late.
- How it was confirmed: a throwaway probe looping keepCopy 300x with copyDelay 0–7ms on Dispatchers.Default, then comparing a fresh `Backups`' target (read from prefs) with the in-memory one after 500ms. Original code: 3 lasting mismatches in 1,200. With the lock: 0.

## Watch out
- Every `Backups` (the AppContainer's, each test's) shares the "backup_copy" prefs. An instance whose in-memory target is null never writes, which is why the setUp instance doesn't interfere.
