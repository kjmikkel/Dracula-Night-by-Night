---
title: "trail-guard: line splitting and root package.json"
author: Mikkel
date: 2026-10-17
branch: guard-line-split-and-package-json
cost: { minutes: 7, tokens: 54285 }
touches:
  - ".github/CODEOWNERS"
  - ".github/scripts/trail-guard.sh"
  - ".github/scripts/trail-guard.test.sh"
---

## Decided
- The fold step in .github/scripts/trail-guard.sh splits on \n only (`.removesuffix("\n").split("\n")`), matching bash `read`. Python's splitlines() also splits on \x1c-\x1e, U+0085 and U+2028/2029, which shifted every later path onto another path's folded form, so `a\x1cb` followed by `.trail/x` passed.
- After folding, the guard fails closed if the path count and the folded count differ.
- Root package.json is protected and CODEOWNERed: .trail/hook.cjs resolves @trailhq/trail-code from the repo root, where a package.json with that name self-references and its "exports" can point at an unguarded dist/claude/hooks.js.
- Same fix as kjmikkel/egypt-mods 72b8c40; the guard there is a copy of this one.

## Tried and ruled out
- Checking the new cases against the old guard with git stash; use `git show HEAD:.github/scripts/trail-guard.sh` into a temp file instead.

## Watch out
- Any new per-line processing in the guard has to split exactly where bash `read` does, or path/folded pairs drift.
- trail-guard.test.sh is slow on Windows (several processes per path); run it in the background.
