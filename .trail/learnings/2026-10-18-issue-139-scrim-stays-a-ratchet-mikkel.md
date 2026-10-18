---
title: "Issue #139 scrim stays a ratchet exception"
author: Mikkel
date: 2026-10-18
branch: claude/139-scrim-exception
cost: { minutes: 11, tokens: 52204 }
touches:
  - app/app/src/test/resources/a11y-baseline.txt
---

## Decided
- Owner, 2026-10-18: keep the six `filter-sheet | role | Close sheet | #139` lines in a11y-baseline.txt as a documented exception. Their reason now says it was checked through Material 3 1.5.0-beta01. The drag handle half was already fixed in #160.

## Tried and ruled out
- Upgrading: compose-bom 2026.09.00 (still the newest) pins material3 1.4.0, where `Scrim()` in ModalBottomSheet.kt is private: contentDescription "Close sheet" and onClick, no role, no slot. In 1.5.0-beta01 Scrim becomes public with a modifier, but ModalBottomSheet still builds its own with no role and offers no scrim slot, so upgrading won't fix it.
- `properties.shouldDismissOnClickOutside = false` removes the scrim's semantics, but tapping outside then no longer closes the sheet. The owner rejected it, along with adding a Done button to make up for it.
- A custom sheet (Dialog + our own scrim with Role.Button + drag): too much work and risk for a low-impact issue.

## Watch out
- To recheck a new release, download material3-android-<ver>-sources.jar from Google Maven and read Scrim / ModalBottomSheet. Look for a `scrim` slot or a role in its semantics. If either appears, drop the baseline lines.
