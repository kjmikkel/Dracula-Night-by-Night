---
title: "trail check on feat/204 flags word matches, not reversals"
author: Mikkel
date: 2026-10-24
branch: feat/204-whos-who
cost: { minutes: 30, tokens: 150479 }
id: l-30083672
scope: repo
status: active
touches:
  - app/app/src/main/java/com/jensen/draculanightbynight/whoswho/WhosWhoViewModel.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/whoswho/WhosWhoViewModelTest.kt
---

## Decided
Who's who: a visit is recorded only when the page shows someone (WhosWhoViewModel.seenBefore), so an early empty open doesn't spend the "first visit marks nothing" rule. Commit 75558ae.

## Watch out
`trail check` on feat/204-whos-who reports 11 "conflicts" that are phrase matches, not reversals: "not yet" (test name doesNotRevealSomeoneNotYetShown, whos_who.py, plans/specs), "open file" (AppContainer.kt:48, specs), "two setting rows" (the Who's who plan/spec). Read each one before acting. Note in the commit or PR why they don't apply instead of rewording code to dodge them.
