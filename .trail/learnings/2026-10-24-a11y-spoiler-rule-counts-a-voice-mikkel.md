---
title: "a11y spoiler rule counts a voice as met once an entry has arrived, not read"
author: Mikkel
date: 2026-10-24
branch: feat/204-whos-who
cost: { minutes: 9, tokens: 74941 }
id: l-d847595a
scope: repo
status: active
touches:
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/Cases.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/ReadingRulesTest.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/Rules.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/a11y/SpoilerCasesTest.kt
  - app/app/src/test/resources/a11y-baseline.txt
  - docs/accessibility-spike.md
  - docs/ui-ux/inventory.md
  - docs/ui-ux/reports/2026-10-24-whole-app.md
  - docs/ui-ux/reports/2026-10-24-whole-app/shots/d1-mode-actual.png
  - docs/ui-ux/reports/2026-10-24-whole-app/shots/d1-mode-actual.xml
---

## Decided
- The ratchet's `spoiler` rule (a11y/Rules.kt, `Expectations.unmet`) and #220 treat a voice as met once one of its entries has *arrived*. Who's who (WhosWhoView) is stricter: a person shows only after an entry is *read*. Not yet confirmed by the owner; if chips should follow Who's who, change `unmetVoicesInTempo` (a11y/Cases.kt) and the check in SpoilerCasesTest together.
- `line-length` reports once per window, not per paragraph, so a long entry is one baseline line per case and look.

## Watch out
- In a Robolectric rule test, `Modifier.width(900.dp)` on a 411 dp test window is clamped to the window; use `requiredWidth` to get text wider than the screen.
