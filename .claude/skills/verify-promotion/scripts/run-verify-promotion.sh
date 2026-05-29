#!/usr/bin/env bash
# Verify promotion invariance (G14): does a promoted first-class-condition config produce
# the same trade population as the inline-script research candidate it was promoted from?
#
# Runs ONE /api/backtest per config over the diff window (NOT walk-forward — walk-forward
# exposes no per-trade data), pulls each run's full trade list, and diffs them by
# (entry_date, symbol). Emits PASS / DIFFERS / ERROR per quant sign-off 2026-05-29.
#
# Usage:
#   run-verify-promotion.sh <label> <inline-script-config.json> <promoted-condition-config.json>
#                           [diff-start] [diff-end]
#
# Defaults: diff window = full 25y (2000-01-01 -> 2025-12-31), the union of all binding
# firewall layers — maximizes the population-shift signal on historically-thin symbols
# (the Idunn culprit). Both runs use the same window + the configs' own (equal) capital
# settings; config_equivalence.py hard-stops if anything but the conditions differs.
#
# Output (under /tmp/verify-promotion-<label>/):
#   equivalence.json                  - config-equivalence precondition result
#   inline-resp.json / promoted-resp.json   - backtest response DTOs (edge/cagr/totalTrades)
#   inline-trades.json / promoted-trades.json - full per-trade lists
#   diff.json                         - the G14 verdict (also the value reused by the firewall)
#   report.md                         - human-readable diff narrative
#
# Exit code: 0 = PASS, 1 = DIFFERS, 2 = ERROR.

set -uo pipefail

if [[ $# -lt 3 || $# -gt 5 ]]; then
  echo "Usage: $0 <label> <inline-config.json> <promoted-config.json> [diff-start] [diff-end]" >&2
  exit 64
fi

LABEL="$1"
INLINE_CFG="$2"
PROMOTED_CFG="$3"
DIFF_START="${4:-2000-01-01}"
DIFF_END="${5:-2025-12-31}"

ROOT=/home/skrymer/Development/git/trading
SKILL_DIR="$ROOT/.claude/skills/verify-promotion"
HOST="${UDGAARD_HOST:-http://localhost:9080/udgaard}"
API_KEY="${API_KEY:-changeme}"

if [[ ! "$LABEL" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "ERROR: label must match [A-Za-z0-9_.-]+ : $LABEL" >&2
  exit 64
fi
for f in "$INLINE_CFG" "$PROMOTED_CFG"; do
  [[ -f "$f" ]] || { echo "ERROR: config not found: $f" >&2; exit 2; }
done
command -v jq >/dev/null || { echo "ERROR: jq required" >&2; exit 2; }

WORK="/tmp/verify-promotion-${LABEL}"
mkdir -p "$WORK"

# 1. ERROR precondition: configs must be the same logical strategy modulo conditions.
EQUIV="$WORK/equivalence.json"
if ! python3 "$SKILL_DIR/scripts/config_equivalence.py" "$INLINE_CFG" "$PROMOTED_CFG" > "$EQUIV"; then
  echo "" >&2
  echo "==> G14 ERROR: configs are not comparable (see above). No backtest fired." >&2
  echo "==> $EQUIV" >&2
  exit 2
fi

# fire_single <config> <window-applied-req> <resp-out> <trades-out>
# Restarts udgaard for a clean heap (same rationale as /tmp/v3-fire.sh), POSTs a single
# backtest over the diff window, then GETs the full trade list by entry date.
fire_single() {
  local cfg="$1" req="$2" resp="$3" trades="$4"
  jq --arg s "$DIFF_START" --arg e "$DIFF_END" '.startDate = $s | .endDate = $e' "$cfg" > "$req"

  docker compose -f "$ROOT/compose.prod.yaml" restart udgaard >/dev/null 2>&1
  local healthy=false
  for _ in $(seq 1 60); do
    if curl -sf -H "X-API-Key: $API_KEY" "$HOST/api/backtest/conditions" -o /dev/null 2>/dev/null; then
      healthy=true; break
    fi
    sleep 3
  done
  [[ "$healthy" == true ]] || { echo "ERROR: udgaard not serving after restart" >&2; return 2; }

  if ! API_KEY="$API_KEY" "$ROOT/.claude/scripts/udgaard-post.sh" /api/backtest "@$req" "$resp"; then
    echo "ERROR: backtest POST failed" >&2; return 2
  fi
  local backtest_id
  backtest_id=$(jq -r '.backtestId // empty' "$resp")
  [[ -n "$backtest_id" ]] || { echo "ERROR: no backtestId in response" >&2; return 2; }

  curl -sf -H "X-API-Key: $API_KEY" \
    "$HOST/api/backtest/${backtest_id}/trades?startDate=${DIFF_START}&endDate=${DIFF_END}" \
    -o "$trades" || { echo "ERROR: trades GET failed for $backtest_id" >&2; return 2; }
  [[ -s "$trades" ]] || { echo "ERROR: empty trades file for $backtest_id" >&2; return 2; }
}

echo "==> G14 diff window: $DIFF_START -> $DIFF_END" >&2
echo "==> Firing inline-script config..." >&2
fire_single "$INLINE_CFG" "$WORK/inline-req.json" "$WORK/inline-resp.json" "$WORK/inline-trades.json" \
  || exit 2
echo "==> Firing promoted-condition config..." >&2
fire_single "$PROMOTED_CFG" "$WORK/promoted-req.json" "$WORK/promoted-resp.json" "$WORK/promoted-trades.json" \
  || exit 2

# 2. Diff the trade lists. Pass each run's edge/cagr scalar for the diagnostic headline delta.
INLINE_EDGE=$(jq -r '.edge // empty' "$WORK/inline-resp.json")
PROMOTED_EDGE=$(jq -r '.edge // empty' "$WORK/promoted-resp.json")
INLINE_CAGR=$(jq -r '.cagr // empty' "$WORK/inline-resp.json")
PROMOTED_CAGR=$(jq -r '.cagr // empty' "$WORK/promoted-resp.json")

EDGE_ARGS=()
[[ -n "$INLINE_EDGE" ]] && EDGE_ARGS+=(--inline-edge "$INLINE_EDGE")
[[ -n "$PROMOTED_EDGE" ]] && EDGE_ARGS+=(--promoted-edge "$PROMOTED_EDGE")
[[ -n "$INLINE_CAGR" ]] && EDGE_ARGS+=(--inline-cagr "$INLINE_CAGR")
[[ -n "$PROMOTED_CAGR" ]] && EDGE_ARGS+=(--promoted-cagr "$PROMOTED_CAGR")

DIFF_JSON="$WORK/diff.json"
python3 "$SKILL_DIR/scripts/diff_trades.py" \
  "$WORK/inline-trades.json" "$WORK/promoted-trades.json" \
  --inline-label "inline-${LABEL}" --promoted-label "promoted-${LABEL}" \
  "${EDGE_ARGS[@]}" \
  > "$DIFF_JSON" 2> "$WORK/report.md"
RC=$?
cat "$WORK/report.md" >&2

echo "" >&2
echo "==> G14 outcome: $(jq -r '.outcome' "$DIFF_JSON")" >&2
echo "==> $DIFF_JSON" >&2
exit $RC
