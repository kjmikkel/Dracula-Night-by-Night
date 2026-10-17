#!/usr/bin/env bash
# Fail when someone other than the repo owner changes a path that feeds coding
# agents code or instructions: Trail injects .trail/learnings into context at
# session start, and .trail/hook.cjs imports trail from <repo>/node_modules or
# <repo>/dist/claude; Claude Code also loads .claude/, CLAUDE.md, AGENTS.md
# and .mcp.json.
# Usage: <changed file paths, one per line> | trail-guard.sh <pr-author> <repo-owner>
set -euo pipefail
author=$1
owner=$2

protected='^\.trail/|^node_modules/|^dist/claude/|(^|/)\.claude/|(^|/)claude(\.local)?\.md$|(^|/)agents\.md$|(^|/)\.mcp\.json$'
short_name='(^|/)[^/]*~[0-9][^/]*(/|$)' # 8.3 alias such as TRAIL~1, which Windows may resolve to .trail

# Match each path as a Windows checkout sees it: case-insensitive, with trailing
# dots and spaces dropped from every component.
touched=$(while IFS= read -r f; do
  n=$(printf '%s' "$f" | tr '[:upper:]' '[:lower:]' | sed -E 's#[. ]+(/|$)#\1#g')
  if printf '%s\n' "$n" | grep -qE "$protected|$short_name"; then printf '%s\n' "$f"; fi
done)

if [ -n "$touched" ] && [ "$author" != "$owner" ]; then
  echo "::error::Only @$owner may change agent context and code-loading paths. This PR by @$author touches:"
  echo "$touched"
  exit 1
fi
echo "ok: no protected paths changed, or changed by the owner"
