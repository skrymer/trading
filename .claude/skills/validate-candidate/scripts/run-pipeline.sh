#!/usr/bin/env bash
# Run a screen-survivor through the 3-block firewall.
#
# Usage:
#   run-pipeline.sh <candidate-name> <request-template-path>
#
# Args:
#   candidate-name           Label used in output paths (e.g. <STRATEGY>-<VARIANT>)
#   request-template-path    A request JSON with the candidate's full strategy config
#                            (entryStrategy/exitStrategy/positionSizing/ranker/etc).
#                            startDate/endDate are overridden per block.
#                            Typically /tmp/v3-req-<name>.json from /strategy-screen
#                            or a copy with the candidate-frozen config.
#
# Output:
#   /tmp/validate-<cand>-block{A,B,C}.json     - raw walk-forward results
#   /tmp/validate-<cand>-eval-block{A,B,C}.json - per-block gate evals
#   /tmp/validate-<cand>-summary.json          - final summary
#   strategy_exploration/validate-<cand>.md     - human-readable report
#
# Per .claude/skills/validate-candidate/SKILL.md. Stops at first failing block.
# Block C requires explicit user confirmation (G10 - design isolation) - the
# script reads a line from stdin; type 'confirmed' to proceed.

set -uo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <candidate-name> <request-template-path>" >&2
  exit 64
fi

CANDIDATE="$1"
TEMPLATE="$2"
ROOT=/home/skrymer/Development/git/trading
SKILL_DIR="$ROOT/.claude/skills/validate-candidate"

# Reject candidate names with shell metacharacters or path traversal — every
# output path interpolates this into /tmp and strategy_exploration, so a `..`
# or `/` would write outside the intended dirs.
if [[ ! "$CANDIDATE" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "ERROR: candidate-name must match [A-Za-z0-9_.-]+ (no slashes, no shell metacharacters): $CANDIDATE" >&2
  exit 64
fi

REPO_REPORT="$ROOT/strategy_exploration/validate-${CANDIDATE}.md"

if [[ ! -f "$TEMPLATE" ]]; then
  echo "ERROR: template not found: $TEMPLATE" >&2
  exit 2
fi
if ! command -v jq >/dev/null; then
  echo "ERROR: jq required" >&2
  exit 2
fi

declare -A BLOCK_START=( [A]="2000-01-01" [B]="2014-01-01" [C]="2021-01-01" )
# Block B extends to 2021-06-30 (not 2020-12-31) so the W4 OOS window
# (2020-01-02 to 2021-01-01 under 36/12/12 cadence) actually covers 2020 —
# otherwise G6 ("2020 COVID OOS positive") is structurally unreachable. Quant-
# verified 2026-05-28. 6-month overlap with Block C is contained in C's IS
# warm-up (C's first OOS is 2024 under 36/12/12), so the firewall stays intact.
# G6a/G6b half-year split (crash survival vs recovery) deferred — needs engine
# change to expose per-trade data in WF response. See issue #51.
declare -A BLOCK_END=(   [A]="2014-01-01" [B]="2021-06-30" [C]="2025-12-31" )

EVAL_PATHS=()

run_block() {
  local block="$1"
  local start="${BLOCK_START[$block]}"
  local end="${BLOCK_END[$block]}"
  local req="/tmp/validate-${CANDIDATE}-req-block${block}.json"
  local out="/tmp/validate-${CANDIDATE}-block${block}.json"
  local eval_json="/tmp/validate-${CANDIDATE}-eval-block${block}.json"
  local eval_line="/tmp/validate-${CANDIDATE}-eval-block${block}.line"

  jq --arg s "$start" --arg e "$end" '.startDate = $s | .endDate = $e' "$TEMPLATE" > "$req"
  echo "==> Block $block: $start -> $end" >&2

  if ! bash /tmp/v3-fire.sh "$req" "$out"; then
    echo "ERROR: Block $block fire failed" >&2
    return 2
  fi
  if [[ ! -s "$out" ]]; then
    echo "ERROR: Block $block produced empty result file: $out" >&2
    return 2
  fi

  python3 "$SKILL_DIR/scripts/eval-block.py" "$out" \
    --block "$block" --label "$CANDIDATE" \
    > "$eval_json" 2> "$eval_line"
  local rc=$?
  EVAL_PATHS+=("$eval_json")
  cat "$eval_line" >&2
  if [[ $rc -ne 0 && $rc -ne 1 ]]; then
    echo "ERROR: Block $block evaluator crashed (rc=$rc)" >&2
    return 2
  fi
  local verdict
  verdict=$(jq -r '.overall' "$eval_json")
  if [[ "$verdict" == "FAIL" ]]; then
    return 1
  fi
  return 0
}

# Block A
if ! run_block A; then
  rc=$?
  echo "Block A failed (rc=$rc) - skill halts here per firewall rules." >&2
else
  # Block B
  if ! run_block B; then
    rc=$?
    echo "Block B failed (rc=$rc) - skill halts here." >&2
  else
    # G10: design-isolation confirmation before Block C
    echo "" >&2
    echo "==> G10 Design Isolation Check (Block C requires explicit confirmation)" >&2
    echo "" >&2
    echo "Candidate config (this MUST be the same config used in Blocks A + B):" >&2
    jq '{entryStrategy, exitStrategy, ranker, rankerConfig, maxPositions, entryDelayDays, positionSizing, randomSeed}' "$TEMPLATE" >&2
    echo "" >&2
    echo "Freeze date: $(date -Iseconds)" >&2
    echo "" >&2
    echo "If the config above is UNCHANGED since Block B passed, type 'confirmed' to fire Block C." >&2
    echo "Any other input aborts. Modifying the config and re-running is data-mining, not validation." >&2
    # G10 requires a real human at a tty — refuse pipe / redirected stdin to
    # prevent the gate being silently bypassed by `echo confirmed | run-pipeline...`.
    if [[ ! -e /dev/tty ]] || ! { exec 3</dev/tty; } 2>/dev/null; then
      echo "ERROR: G10 requires an interactive tty for explicit human confirmation." >&2
      echo "Block C refused. Re-run the skill in an interactive terminal." >&2
      exit 3
    fi
    read -r answer <&3
    exec 3<&-
    if [[ "$answer" != "confirmed" ]]; then
      echo "Block C aborted (G10 not confirmed). Run skipped." >&2
    else
      if ! run_block C; then
        rc=$?
        echo "Block C failed (rc=$rc)." >&2
      fi
    fi
  fi
fi

# Detect inline-script conditions so summarize.py can flag TRADABLE-pending-promotion.
# Per SKILL.md "Script-condition promotion": a TRADABLE verdict on a candidate with
# inline `{"type":"script"}` conditions is conditional until promoted via /create-condition.
SCRIPT_CONDITIONS=$(jq '
  [
    (.entryStrategy.conditions // [])[],
    (.exitStrategy.conditions // [])[]
  ] | map(select(.type == "script")) | length
' "$TEMPLATE")

# Summarize whatever completed
SUMMARY_JSON="/tmp/validate-${CANDIDATE}-summary.json"
python3 "$SKILL_DIR/scripts/summarize.py" "$CANDIDATE" \
  "/tmp/validate-${CANDIDATE}-eval-blockA.json" \
  "/tmp/validate-${CANDIDATE}-eval-blockB.json" \
  "/tmp/validate-${CANDIDATE}-eval-blockC.json" \
  --script-conditions "$SCRIPT_CONDITIONS" \
  > "$SUMMARY_JSON" 2> "$REPO_REPORT"

VERDICT=$(jq -r '.verdict' "$SUMMARY_JSON")
echo "" >&2
echo "==> FINAL VERDICT: $VERDICT" >&2
echo "==> Summary JSON: $SUMMARY_JSON" >&2
echo "==> Report:       $REPO_REPORT" >&2
