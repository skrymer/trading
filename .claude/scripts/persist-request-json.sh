#!/usr/bin/env bash
# Persist a funnel candidate's exact request JSON beside its wiki entity (ADR 0017).
#
# A candidate's identity is its exact request JSON (entry stack / exit / ranker / sizer /
# maxPositions / window / cadence / seed for a backtest; the condition stack + screen window
# for a /condition-screen candidate). Until ADR 0017 these lived only in /tmp and were lost on
# reboot / doc-retirement (#129 cost ~half a session reconstructing George's config). This helper
# is the automation: it copies the VALIDATED request that was actually fired into the repo, as a
# sibling of the entity, so the next re-validation is `udgaard-post.sh @<entity>.request.json`,
# not an archaeology project.
#
# Usage:
#   .claude/scripts/persist-request-json.sh <entity-name> <source-request-json>
#
# Args:
#   entity-name           The entity slug — the persisted file is
#                         knowledge/wiki/entities/<entity-name>.request.json and it is the sibling
#                         of knowledge/wiki/entities/<entity-name>.md. Must match [A-Za-z0-9_.-]+
#                         (no slashes / shell metacharacters).
#   source-request-json   Path to the exact request JSON that was fired (e.g. the /strategy-screen
#                         body or the /validate-candidate $TEMPLATE). Must be valid JSON.
#
# Behavior:
#   - jq-normalizes the source (pretty-printed, stable) into the entity path. Idempotent — re-running
#     with the same source is a no-op diff.
#   - Persists the canonical SKELETON only (one file per candidate, ADR 0017). A one-field variant
#     (e.g. the Random baseline = same file with ranker:"Random" + a swept randomSeed) does NOT get
#     its own file — document that edit in the entity's "Reproducing" section instead.
#   - Does NOT edit the entity .md. Adding the frontmatter `request:` pointer + a "Reproducing"
#     section is the skill's job (George is the template) — printed as a reminder below.
#   - Prints "OK: persisted -> <path>" on success.

set -uo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <entity-name> <source-request-json>" >&2
  exit 64
fi

ENTITY="$1"
SOURCE="$2"
ROOT=/home/skrymer/Development/git/trading
ENTITIES_DIR="$ROOT/knowledge/wiki/entities"

if [[ ! "$ENTITY" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "ERROR: entity-name must match [A-Za-z0-9_.-]+ (no slashes, no shell metacharacters): $ENTITY" >&2
  exit 64
fi
if [[ ! -f "$SOURCE" ]]; then
  echo "ERROR: source request JSON not found: $SOURCE" >&2
  exit 2
fi
if ! command -v jq >/dev/null; then
  echo "ERROR: jq required" >&2
  exit 2
fi
if ! jq empty "$SOURCE" 2>/dev/null; then
  echo "ERROR: source is not valid JSON: $SOURCE" >&2
  exit 2
fi

DEST="$ENTITIES_DIR/${ENTITY}.request.json"
mkdir -p "$ENTITIES_DIR"
jq '.' "$SOURCE" > "$DEST"

echo "OK: persisted -> $DEST" >&2
if [[ ! -f "$ENTITIES_DIR/${ENTITY}.md" ]]; then
  echo "NOTE: no entity page $ENTITIES_DIR/${ENTITY}.md yet — add the frontmatter 'request: \"${ENTITY}.request.json\"' pointer + a 'Reproducing' section when the entity is written (george.md is the template)." >&2
else
  if ! grep -q "^request:" "$ENTITIES_DIR/${ENTITY}.md"; then
    echo "REMINDER: $ENTITIES_DIR/${ENTITY}.md has no 'request:' frontmatter pointer — add 'request: \"${ENTITY}.request.json\"' + a 'Reproducing' section (george.md is the template)." >&2
  fi
fi
