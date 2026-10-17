#!/usr/bin/env bash
# Fail when someone other than the repo owner changes .trail/.
# Trail injects .trail/learnings into coding agents' context at session start,
# so outside changes there are a prompt-injection path.
# Usage: <changed file paths, one per line> | trail-guard.sh <pr-author> <repo-owner>
set -euo pipefail
author=$1
owner=$2

touched=$(grep -E '^\.trail/' || true)
if [ -n "$touched" ] && [ "$author" != "$owner" ]; then
  echo "::error::Only @$owner may change .trail/ (agent context). This PR by @$author touches:"
  echo "$touched"
  exit 1
fi
echo "ok: .trail/ unchanged or changed by the owner"
