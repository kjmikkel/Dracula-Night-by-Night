---
title: "UI/UX pass on the emulator: TalkBack speech isn't capturable this way, and the ratchet lets short ellipses through"
author: Mikkel
date: 2026-10-24
branch: feat/204-whos-who
cost: { minutes: 17, tokens: 206067 }
id: l-adf01b27
scope: repo
status: active
touches:
  - docs/ui-ux/inventory.md
  - docs/ui-ux/reports/2026-10-24-whole-app.md
  - docs/audiences.md
---

## Decided
- The audience for UI/UX passes is docs/audiences.md as written (owner confirmed 2026-10-24); no need to re-ask unless that file changes.

## Tried and ruled out
- Capturing TalkBack 17's spoken text on Dracula_Root_API_37 by adding `pref_tts_overlay_key` / `pref_log_level_key` to /data/user_de/0/com.google.android.marvin.talkback/shared_prefs/…_preferences.xml with `sed -i` (as root): the keys never landed and nothing was logged. Focus order can still be walked: `adb shell input keycombination 57 22` (Alt+Right) moves TalkBack focus, and screenshots show the green box.

## Watch out
- a11y/Rules.kt `isCollapsedToEllipsis` only flags an ellipsis when the text is narrower than 3x its font size, so the reader's app-bar title "Jonathan Har…" (ReaderScreen.kt:172) passes the ratchet at normal text size on a 411 dp phone.
- Settings rows shift between modes (Read freely drops Start date and Skip to a day), so tapping by fixed coordinates hits the wrong row; dump the tree first.
- JAVA_HOME in this shell has a trailing ';'; set it to "C:/Program Files/Android/Android Studio/jbr" before ./gradlew.
