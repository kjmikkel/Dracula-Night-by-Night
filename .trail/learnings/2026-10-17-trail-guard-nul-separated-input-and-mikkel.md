---
title: "trail-guard: NUL-separated input and one-process check"
author: Mikkel
date: 2026-10-17
branch: claude/guard-split-pkg-fix-2af78a
cost: { minutes: 13, tokens: 77796 }
touches:
  - ".github/CODEOWNERS"
  - ".github/scripts/trail-guard.sh"
  - ".github/scripts/trail-guard.test.sh"
  - ".github/workflows/guard.yml"
---

## Decided
- trail-guard.sh reads NUL-separated paths (workflow: `jq -js '... + "\u0000"'`) and splits, folds and matches in one `python3 -I` process, so no path can be paired with another path's folded form. Ported from kjmikkel/PieceCounter 137b66d.
- Any name with a Unicode Cc, Zl or Zp character (newline, \r, U+0085, U+2028, \x1c...) is reported as protected: tools disagree on where such a name ends. Names are printed with unicode_escape.
- Root package.json is protected and CODEOWNERed: .trail/hook.cjs's require.resolve of trail's own package name self-references the nearest package.json. Do not edit hook.cjs; trail init generates it.
- Supersedes PR #173, which split on \n only; a filename containing a newline still bypassed it.

## Tried and ruled out
- Splitting on \n in both bash and Python (PR #173): git allows \n inside a filename, so ".tr\nail/hook.cjs" becomes two harmless lines.

## Watch out
- Write test filenames as $'\xe2\x80\xa8' escapes, never raw bytes; generating them via heredoc+Python or sed turned escapes into raw control bytes twice.
- Keep "\u0000" as an escape in guard.yml; a literal NUL byte there breaks the YAML.
- jq on Windows writes \r\n; the guard then flags the name anyway (\r is Cc).
