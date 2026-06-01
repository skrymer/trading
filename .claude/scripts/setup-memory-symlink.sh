#!/usr/bin/env bash
# Point Claude Code's per-project auto-memory at the in-repo .claude/memory/ store.
#
# Claude Code writes auto-memory to a machine-local path derived from the repo
# path (~/.claude/projects/<slug>/memory/). We keep memory version-controlled in
# the repo instead, so it is backed up and travels with the code. The committed
# autoMemoryDirectory setting in .claude/settings.json is not honored on its own
# (writes still hit the default path), so this symlinks the default path to the
# repo store. The symlink is machine-local — re-run once per machine after clone.
#
# Idempotent: safe to run repeatedly.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
repo_mem="$repo_root/.claude/memory"

# Replicate Claude Code's project-slug derivation: absolute path, '/' -> '-'.
slug="${repo_root//\//-}"
global_mem="$HOME/.claude/projects/${slug}/memory"

if [[ ! -d "$repo_mem" ]]; then
  echo "error: repo memory store not found at $repo_mem" >&2
  exit 1
fi

mkdir -p "$(dirname "$global_mem")"

# Already correctly linked?
if [[ -L "$global_mem" && "$(readlink "$global_mem")" == "$repo_mem" ]]; then
  echo "ok: $global_mem already -> $repo_mem"
  exit 0
fi

# A real directory here means machine-local memory the repo store may not have.
# Refuse to clobber it silently — back it up first.
if [[ -e "$global_mem" && ! -L "$global_mem" ]]; then
  backup="${global_mem}.bak-$(date +%Y%m%d%H%M%S)"
  echo "warn: $global_mem is a real directory; backing up to $backup" >&2
  mv "$global_mem" "$backup"
fi

# Replace any existing (wrong) symlink.
[[ -L "$global_mem" ]] && rm -f "$global_mem"

ln -s "$repo_mem" "$global_mem"
echo "linked: $global_mem -> $repo_mem"
