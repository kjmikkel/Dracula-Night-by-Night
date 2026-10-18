---
title: "App review 2026-10-18: bugs filed and remaining findings"
author: Mikkel
date: 2026-10-18
branch: fix/112-gutenberg-credit
cost: { minutes: 1, tokens: 7825 }
touches:
  - ".idea/.gitignore"
  - ".idea/caches/deviceStreaming.xml"
  - ".idea/deviceManager.xml"
  - ".idea/dracula-night-by-night.iml"
  - ".idea/misc.xml"
  - ".idea/modules.xml"
  - ".idea/vcs.xml"
  - data/out/calendar.html
---

## Decided
- Four bugs from a full app review were filed: #189 (end card claims "You have read" when nothing is left to arrive, including Read freely), #190 (midnight entries stored as 23:59, preface shown as "about 00:00"), #191 (privacy.md Links leaves out the Gutenberg link), #192 (cold start flashes @android:style/Theme.Material.NoActionBar before Compose).
- Not filed yet (rework/additions): Calendar mode picked mid-season unlocks the backlog with no warning on Welcome; single-arrival notification text is the date, not the preview; Locked reader doesn't say when the entry arrives; TimelineState.unlockedCount/total are computed but never shown; the "first timed entry" rule (time != MIDNIGHT) is copied in Welcome.kt, Settings.kt, StartTomorrow.kt; README is two lines; data/out/calendar.html and .idea/ are untracked and neither committed nor ignored.

## Watch out
- Entry ids are progress keys in user_state.json: don't rename odd ones like undated-unknown-01 or 1893-11-06-unknown-01 (author is jonathan_harker).
- 215 of 235 entry times are estimated; only 9 entries have a place.
