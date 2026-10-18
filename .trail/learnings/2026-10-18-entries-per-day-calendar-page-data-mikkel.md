---
title: Entries-per-day calendar page (data/out/calendar.html)
author: Mikkel
date: 2026-10-18
branch: feat/177-skip-repeats
cost: { minutes: 2, tokens: 38718 }
touches:
  - app/app/src/test/java/com/jensen/draculanightbynight/content/StoryJsonTest.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/user/UserRepositoryTest.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/user/UserStateTest.kt
  - data/out/entries.json
  - data/pipeline/cli.py
  - data/pipeline/emit.py
  - data/pipeline/model.py
  - data/tests/test_emit.py
  - ".idea/.gitignore"
  - ".idea/caches/deviceStreaming.xml"
---

## Decided
- data/out/calendar.html is a hand-written static page next to review.html, not emitted by the pipeline (cli.py only writes review.html). It fetches entries.json at load time, so it reflects the latest pipeline run.
- Year/month range comes from the entries' ISO `date` field (pipeline already fills the estimated year; currently all 1893, 1893-05-03..1893-11-06, 235 entries on 116 days, max 10/day).
- Calendar dates built with Date.UTC to avoid timezone day-shifts; weeks are Monday-first.
- Bar height via barHeight(count, max), linear for now; sqrt/capped are the open alternatives since most days have 1-2 entries.

## Tried and ruled out
- Opening via file:// — fetch of entries.json fails; serve data/out with the "review" launch config (port 8765) instead.

## Watch out
- Running JS checks immediately after navigate can hit the page before the fetch renders; wait a beat.
