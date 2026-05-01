#!/usr/bin/env bash
# List which top-level projects have uncommitted changes.
#
# Usage: .claude/scripts/changed-projects.sh
#
# Prints one line per project with changes, drawn from {udgaard, midgaard, asgaard}.
# Empty output means no changes under those directories.
#
# Sources of changes:
#   - Tracked changes (staged + unstaged) via `git diff --name-only HEAD`
#   - Untracked files via `git ls-files --others --exclude-standard`

set -euo pipefail

{
  git diff --name-only HEAD
  git ls-files --others --exclude-standard
} | awk -F/ '
  $1 == "udgaard"  { print "udgaard" }
  $1 == "midgaard" { print "midgaard" }
  $1 == "asgaard"  { print "asgaard" }
' | sort -u
