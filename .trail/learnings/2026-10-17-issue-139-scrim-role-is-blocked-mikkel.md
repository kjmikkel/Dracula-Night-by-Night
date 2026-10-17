---
title: "Issue #139 scrim role is blocked upstream"
author: Mikkel
date: 2026-10-17
branch: main
cost: { minutes: 3, tokens: 36880 }
---

## Decided
- #139's scrim half ("Close sheet" with no role) can't be fixed from the app: it lives inside Material 3's ModalBottomSheet. Leave it baselined in the accessibility ratchet. The drag-handle half was fixed by swapping in a decorative handle (FilterSheet.kt).

## Tried and ruled out
- Bumping Compose: as of 2026-10-17, 2026.09.00 is the newest compose-bom on Google Maven, so no update fixes it yet.

## Watch out
- When the Compose BOM is bumped, check whether the scrim gains a role and drop the ratchet baseline if so.
