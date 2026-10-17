#!/usr/bin/env bash
# Tests for trail-guard.sh. Run: bash .github/scripts/trail-guard.test.sh
set -u
guard="$(dirname "$0")/trail-guard.sh"
failures=0

expect() { # expect <pass|fail> <description> <author> <owner> <changed files...>
  local want=$1 desc=$2 author=$3 owner=$4; shift 4
  if printf '%s\n' "$@" | bash "$guard" "$author" "$owner" >/dev/null 2>&1; then got=pass; else got=fail; fi
  if [ "$got" = "$want" ]; then echo "ok   $desc"; else echo "FAIL $desc (wanted $want, got $got)"; failures=$((failures + 1)); fi
}

expect pass "owner may change .trail/"            kjmikkel kjmikkel .trail/learnings/a.md
expect pass "others may change files outside"     someone  kjmikkel app/build.gradle.kts README.md
expect pass "others may touch a look-alike path"  someone  kjmikkel docs/.trail/x.md .trailer
expect pass "no changed files"                    someone  kjmikkel ""
expect fail "others may not add a learning"       someone  kjmikkel README.md .trail/learnings/a.md
expect fail "others may not change the hook"      someone  kjmikkel .trail/hook.cjs
expect fail "a look-alike login is not the owner" KJMIKKEL-fake kjmikkel .trail/hook.cjs

[ "$failures" -eq 0 ] && echo "all passed" || { echo "$failures failed"; exit 1; }
