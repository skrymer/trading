#!/usr/bin/env bash
# Run a screen-survivor through the refined firewall (2026-05-28):
# Block A v4 (binding) + Block B v4 (binding) + 25-year aggregate v4 (binding) + Block C (informational).
#
# Usage:
#   run-pipeline.sh <candidate-name> <request-template-path> [inline-script-template-path]
#
# Args:
#   candidate-name           Label used in output paths (e.g. <STRATEGY>-<VARIANT>)
#   request-template-path    A request JSON with the candidate's full strategy config
#                            (entryStrategy/exitStrategy/positionSizing/ranker/etc).
#                            startDate/endDate are overridden per layer. For a promoted
#                            candidate this is the PROMOTED first-class-condition config.
#   inline-script-template-path  OPTIONAL. The original inline-`script` research config the
#                            candidate was promoted FROM. When supplied, the firewall runs the
#                            G14 Implementation Invariance check (via /verify-promotion) BEFORE
#                            Block A. G14 ERROR halts the pipeline (configs not comparable);
#                            G14 DIFFERS voids any reusable inline verdict but the pipeline
#                            still validates the promoted config fully; G14 PASS records that
#                            the inline verdict transfers. See SKILL.md "G14".
#
# Output:
#   /tmp/validate-<cand>-block{A,B,C}.json   - raw walk-forward results (binding + informational)
#   /tmp/validate-<cand>-25y.json            - raw walk-forward result (binding statistical-power layer)
#   /tmp/validate-<cand>-eval-block{A,B,C}.json + eval-25y.json - per-layer gate evals
#   /tmp/validate-<cand>-summary.json        - final summary
#   strategy_exploration/validate-<cand>.md  - human-readable report
#
# Per .claude/skills/validate-candidate/SKILL.md (refined framework). Pipeline stops at
# the first failing BINDING layer (A, B, or 25y). Block C is informational and ALWAYS
# runs if all binding layers pass — its gate failures don't trigger REJECTED.
#
# G10 design-isolation confirmation is required before firing the 25y aggregate run
# (the config must be unchanged from Block A + Block B). Reads `confirmed` from /dev/tty.

set -uo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: $0 <candidate-name> <request-template-path> [inline-script-template-path]" >&2
  exit 64
fi

CANDIDATE="$1"
TEMPLATE="$2"
INLINE_TEMPLATE="${3:-}"
ROOT=/home/skrymer/Development/git/trading
SKILL_DIR="$ROOT/.claude/skills/validate-candidate"
VERIFY_PROMOTION="$ROOT/.claude/skills/verify-promotion/scripts/run-verify-promotion.sh"

if [[ ! "$CANDIDATE" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "ERROR: candidate-name must match [A-Za-z0-9_.-]+ (no slashes, no shell metacharacters): $CANDIDATE" >&2
  exit 64
fi

REPO_REPORT="$ROOT/strategy_exploration/validate-${CANDIDATE}.md"

if [[ ! -f "$TEMPLATE" ]]; then
  echo "ERROR: template not found: $TEMPLATE" >&2
  exit 2
fi
if [[ -n "$INLINE_TEMPLATE" && ! -f "$INLINE_TEMPLATE" ]]; then
  echo "ERROR: inline-script template not found: $INLINE_TEMPLATE" >&2
  exit 2
fi
if ! command -v jq >/dev/null; then
  echo "ERROR: jq required" >&2
  exit 2
fi

# Layer ranges. The 25y aggregate spans the full 2000-2025 history; Block B ends
# 2021-06-30 so the W4 OOS covers 2020; Block C is informational only (single
# OOS window covering 2024 under 36/12/12 cadence — Type I error generator if
# binding, which is why the refined framework demotes it).
declare -A LAYER_START=( [A]="2000-01-01" [B]="2014-01-01" [25y]="2000-01-01" [C]="2021-01-01" )
declare -A LAYER_END=(   [A]="2014-01-01" [B]="2021-06-30" [25y]="2025-12-31" [C]="2025-12-31" )
declare -A LAYER_BLOCK_ARG=( [A]="A" [B]="B" [25y]="25y" [C]="C" )

run_layer() {
  local layer="$1"
  local start="${LAYER_START[$layer]}"
  local end="${LAYER_END[$layer]}"
  local block_arg="${LAYER_BLOCK_ARG[$layer]}"
  local req="/tmp/validate-${CANDIDATE}-req-${layer}.json"
  local out
  local eval_json
  local eval_line
  if [[ "$layer" == "25y" ]]; then
    out="/tmp/validate-${CANDIDATE}-25y.json"
    eval_json="/tmp/validate-${CANDIDATE}-eval-25y.json"
    eval_line="/tmp/validate-${CANDIDATE}-eval-25y.line"
  else
    out="/tmp/validate-${CANDIDATE}-block${layer}.json"
    eval_json="/tmp/validate-${CANDIDATE}-eval-block${layer}.json"
    eval_line="/tmp/validate-${CANDIDATE}-eval-block${layer}.line"
  fi

  jq --arg s "$start" --arg e "$end" '.startDate = $s | .endDate = $e' "$TEMPLATE" > "$req"
  echo "==> Layer $layer: $start -> $end" >&2

  if ! bash /tmp/v3-fire.sh "$req" "$out"; then
    echo "ERROR: Layer $layer fire failed" >&2
    return 2
  fi
  if [[ ! -s "$out" ]]; then
    echo "ERROR: Layer $layer produced empty result file: $out" >&2
    return 2
  fi

  python3 "$SKILL_DIR/scripts/eval-block.py" "$out" \
    --block "$block_arg" --label "$CANDIDATE" \
    > "$eval_json" 2> "$eval_line"
  local rc=$?
  cat "$eval_line" >&2
  if [[ $rc -ne 0 && $rc -ne 1 ]]; then
    echo "ERROR: Layer $layer evaluator crashed (rc=$rc)" >&2
    return 2
  fi
  local verdict
  verdict=$(jq -r '.overall' "$eval_json")
  # Block C is informational — never return failure even if overall=FAIL
  local is_binding
  is_binding=$(jq -r '.binding' "$eval_json")
  if [[ "$verdict" == "FAIL" && "$is_binding" == "true" ]]; then
    return 1
  fi
  return 0
}

g10_confirmation() {
  local prior_layers="$1"  # e.g. "Block A + Block B"
  local next_layer="$2"    # e.g. "25y aggregate"
  echo "" >&2
  echo "==> G10 Design Isolation Check ($next_layer requires explicit confirmation)" >&2
  echo "" >&2
  echo "Candidate config (this MUST be the same config used in $prior_layers):" >&2
  jq '{entryStrategy, exitStrategy, ranker, rankerConfig, maxPositions, entryDelayDays, positionSizing, randomSeed}' "$TEMPLATE" >&2
  echo "" >&2
  echo "Freeze date: $(date -Iseconds)" >&2
  echo "" >&2
  echo "If the config above is UNCHANGED since $prior_layers passed, type 'confirmed' to fire $next_layer." >&2
  echo "Any other input aborts. Modifying the config and re-running is data-mining, not validation." >&2
  if [[ ! -e /dev/tty ]] || ! { exec 3</dev/tty; } 2>/dev/null; then
    echo "ERROR: G10 requires an interactive tty for explicit human confirmation." >&2
    echo "$next_layer refused. Re-run the skill in an interactive terminal." >&2
    return 1
  fi
  local answer
  read -r answer <&3
  exec 3<&-
  if [[ "$answer" != "confirmed" ]]; then
    echo "$next_layer aborted (G10 not confirmed). Run skipped." >&2
    return 1
  fi
  return 0
}

# G14 — Implementation Invariance (fires BEFORE Block A when this is a promoted candidate).
# Diffs the promoted config's trade list against the inline-script research config it was
# promoted from, over the full 25y binding window. Per quant 2026-05-29: G14 DIFFERS does NOT
# auto-REJECT — it voids any reusable inline verdict and forces the promoted config through the
# full firewall (which the pipeline does next regardless). Only ERROR (configs not comparable)
# halts the pipeline. The outcome JSON is passed to summarize.py and surfaced in the report.
G14_OUTCOME="/tmp/validate-${CANDIDATE}-g14.json"
rm -f "$G14_OUTCOME"
G14_ARGS=()
if [[ -n "$INLINE_TEMPLATE" ]]; then
  echo "" >&2
  echo "==> G14 Implementation Invariance: diffing promoted config vs inline-script config (2000-2025)" >&2
  if [[ ! -x "$VERIFY_PROMOTION" ]]; then
    echo "ERROR: verify-promotion orchestrator not found/executable: $VERIFY_PROMOTION" >&2
    exit 2
  fi
  "$VERIFY_PROMOTION" "$CANDIDATE" "$INLINE_TEMPLATE" "$TEMPLATE" 2000-01-01 2025-12-31
  g14_rc=$?
  cp "/tmp/verify-promotion-${CANDIDATE}/diff.json" "$G14_OUTCOME" 2>/dev/null || true
  if [[ $g14_rc -eq 2 ]]; then
    echo "==> G14 ERROR: configs are not the same logical strategy. Firewall halted (methodology fault)." >&2
    echo "    Fix the configs so they differ ONLY in condition representation, then re-run." >&2
    exit 2
  elif [[ $g14_rc -eq 1 ]]; then
    echo "==> G14 DIFFERS: inline-script verdict is VOID. Validating the PROMOTED config from scratch below." >&2
  else
    echo "==> G14 PASS: inline-script verdict transfers. Full firewall below confirms the promoted config." >&2
  fi
  [[ -s "$G14_OUTCOME" ]] && G14_ARGS=(--g14 "$G14_OUTCOME")
fi

# Block A (binding)
if ! run_layer A; then
  rc=$?
  echo "Block A failed (rc=$rc) - skill halts here per firewall rules." >&2
else
  # Block B (binding)
  if ! run_layer B; then
    rc=$?
    echo "Block B failed (rc=$rc) - skill halts here." >&2
  else
    # G10 confirmation before 25y (binding statistical-power layer)
    if g10_confirmation "Block A + Block B" "25-year aggregate"; then
      if ! run_layer 25y; then
        rc=$?
        echo "25y aggregate failed (rc=$rc) - skill halts here." >&2
      else
        # Block C (informational only — always run if binding layers pass)
        echo "" >&2
        echo "==> Firing Block C (informational only — gate failures do not bind verdict)" >&2
        if ! run_layer C; then
          rc=$?
          echo "Block C had a fire/eval error (rc=$rc) — but Block C is informational, continuing to summary." >&2
        fi
      fi
    else
      echo "25y aborted. Summary will be computed from completed layers." >&2
    fi
  fi
fi

# Detect inline-script conditions so summarize.py can flag TRADABLE-pending-promotion.
SCRIPT_CONDITIONS=$(jq '
  [
    (.entryStrategy.conditions // [])[],
    (.exitStrategy.conditions // [])[]
  ] | map(select(.type == "script")) | length
' "$TEMPLATE")

# Pass a prior G13 advisory outcome to summarize if one exists (G13 runs separately via
# run-g13.sh after a TRADABLE; it never changes the verdict, only surfaces as a yellow flag).
G13_OUTCOME="/tmp/g13-${CANDIDATE}/g13-${CANDIDATE}-outcome.json"
G13_ARGS=()
[[ -s "$G13_OUTCOME" ]] && G13_ARGS=(--g13 "$G13_OUTCOME")

# Summarize whatever completed
SUMMARY_JSON="/tmp/validate-${CANDIDATE}-summary.json"
python3 "$SKILL_DIR/scripts/summarize.py" "$CANDIDATE" \
  "/tmp/validate-${CANDIDATE}-eval-blockA.json" \
  "/tmp/validate-${CANDIDATE}-eval-blockB.json" \
  "/tmp/validate-${CANDIDATE}-eval-25y.json" \
  "/tmp/validate-${CANDIDATE}-eval-blockC.json" \
  --script-conditions "$SCRIPT_CONDITIONS" \
  "${G13_ARGS[@]}" \
  "${G14_ARGS[@]}" \
  > "$SUMMARY_JSON" 2> "$REPO_REPORT"

VERDICT=$(jq -r '.verdict' "$SUMMARY_JSON")
echo "" >&2
echo "==> FINAL VERDICT: $VERDICT" >&2
echo "==> Summary JSON: $SUMMARY_JSON" >&2
echo "==> Report:       $REPO_REPORT" >&2

# G13 (advisory) runs as a separate sweep after a TRADABLE center — it fires neighbor backtests
# and needs explicit confirmation, so it is not auto-fired here.
if [[ "$VERDICT" == "TRADABLE" && ! -s "$G13_OUTCOME" ]]; then
  echo "" >&2
  echo "==> Advisory next step: run the G13 parameter-robustness sweep on this TRADABLE center:" >&2
  echo "    $SKILL_DIR/scripts/run-g13.sh $CANDIDATE $TEMPLATE" >&2
  echo "    (fires ±-step neighbor backtests; advisory only — does not change the verdict)" >&2
fi
