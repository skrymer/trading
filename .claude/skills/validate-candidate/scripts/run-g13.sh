#!/usr/bin/env bash
# G13 Parameter Robustness sweep (ADVISORY — does not change the firewall verdict yet).
#
# Run AFTER /validate-candidate returns TRADABLE for a center config. Perturbs each in-scope
# tunable by one step, re-fires Block A + Block B per neighbor, and aggregates the advisory
# outcome per REFERENCE.md "G13 — Parameter Robustness". Fires the ±2 carve-out only at the
# PROVISIONAL boundary.
#
# Usage:
#   run-g13.sh <candidate-name> <request-template-path>
#
# Output:
#   /tmp/g13-<cand>/                                   - neighbor request JSONs + manifest
#   /tmp/g13-<cand>/g13-<cand>-neighbor-<i>-block{A,B}.json   - raw walk-forward per neighbor/block
#   /tmp/g13-<cand>/g13-<cand>-results.json            - per-neighbor result records
#   /tmp/g13-<cand>/g13-<cand>-outcome.json            - advisory G13 outcome
#
# Each neighbor is a full Block A (~25 min) + Block B (~10 min) fire, run SEQUENTIALLY (the engine
# OOMs on concurrent backtests). A 3-tunable strategy = 6 neighbors ≈ 3.5 h. The plan + ETA are
# printed and explicit `confirmed` is required before any backtest fires.

set -uo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <candidate-name> <request-template-path>" >&2
  exit 64
fi

CANDIDATE="$1"
TEMPLATE="$2"
ROOT=/home/skrymer/Development/git/trading
SKILL_DIR="$ROOT/.claude/skills/validate-candidate"
SCRIPTS="$SKILL_DIR/scripts"
WORK="/tmp/g13-${CANDIDATE}"

if [[ ! "$CANDIDATE" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "ERROR: candidate-name must match [A-Za-z0-9_.-]+ : $CANDIDATE" >&2
  exit 64
fi
if [[ ! -f "$TEMPLATE" ]]; then
  echo "ERROR: template not found: $TEMPLATE" >&2
  exit 2
fi
command -v jq >/dev/null || { echo "ERROR: jq required" >&2; exit 2; }

declare -A LAYER_START=( [A]="2000-01-01" [B]="2014-01-01" )
declare -A LAYER_END=(   [A]="2014-01-01" [B]="2021-06-30" )

mkdir -p "$WORK"

# 1. Generate ±1 neighbors.
python3 "$SCRIPTS/g13_neighbors.py" "$TEMPLATE" --out-dir "$WORK" --candidate "$CANDIDATE" >/dev/null
MANIFEST="$WORK/g13-${CANDIDATE}-manifest.json"
N=$(jq 'length' "$MANIFEST")
if [[ "$N" -eq 0 ]]; then
  echo "G13: strategy has zero numeric tunables — gate SKIPPED." >&2
  echo '{"outcome":"SKIPPED","reason":"no_numeric_tunables","binding":false}' > "$WORK/g13-${CANDIDATE}-outcome.json"
  exit 0
fi

# 2. Plan + ETA + explicit confirmation (G13 fires real backtests).
echo "" >&2
echo "==> G13 ADVISORY sweep: $N neighbor(s), each = Block A + Block B (~35 min). ETA ≈ $((N * 35)) min." >&2
jq -r '.[] | "    \(.name) \(.direction): \(.nominal) -> \(.fired)\(if .floor_flag then "  [floor-flag]" else "" end)\(if .subtype_fallback then "  [subtype-fallback: add to map]" else "" end)\(if .no_op_widened then "  [no-op widened]" else "" end)"' "$MANIFEST" >&2
echo "" >&2
echo "G13 is ADVISORY — it will NOT change the verdict. Type 'confirmed' to fire the sweep." >&2
if [[ ! -e /dev/tty ]] || ! { exec 3</dev/tty; } 2>/dev/null; then
  echo "ERROR: G13 sweep requires an interactive tty for confirmation." >&2
  exit 1
fi
read -r answer <&3; exec 3<&-
[[ "$answer" == "confirmed" ]] || { echo "G13 sweep aborted (not confirmed)." >&2; exit 1; }

# fire_neighbor <req-json> <out-prefix> ; echoes the result record JSON on stdout.
fire_neighbor() {
  local req="$1" prefix="$2" meta_json="$3"
  local eval_a eval_b
  for layer in A B; do
    local lreq="${prefix}-req-${layer}.json"
    local lout="${prefix}-block${layer}.json"
    local leval="${prefix}-eval-${layer}.json"
    jq --arg s "${LAYER_START[$layer]}" --arg e "${LAYER_END[$layer]}" \
      '.startDate = $s | .endDate = $e' "$req" > "$lreq"
    bash /tmp/v3-fire.sh "$lreq" "$lout" >&2 || { echo "ERROR: fire failed ($prefix $layer)" >&2; return 2; }
    python3 "$SCRIPTS/eval-block.py" "$lout" --block "$layer" --label "$CANDIDATE" > "$leval" 2>>"$prefix.log"
    [[ "$layer" == "A" ]] && eval_a="$leval" || eval_b="$leval"
  done
  python3 - "$meta_json" "$eval_a" "$eval_b" <<'PY'
import json, sys
import g13_aggregate
meta = json.loads(sys.argv[1])
eval_a = json.loads(open(sys.argv[2]).read())
eval_b = json.loads(open(sys.argv[3]).read())
print(json.dumps(g13_aggregate.neighbor_result_from_evals(meta, eval_a, eval_b)))
PY
}
export PYTHONPATH="$SCRIPTS:${PYTHONPATH:-}"

# 3. Fire each ±1 neighbor, collect result records.
RESULTS="$WORK/g13-${CANDIDATE}-results.json"
echo "[]" > "$RESULTS"
for i in $(seq 0 $((N - 1))); do
  meta=$(jq -c ".[$i] | {tunable, name, direction, step, classification, floor_flag}" "$MANIFEST")
  req=$(jq -r ".[$i].request_path" "$MANIFEST")
  rec=$(fire_neighbor "$req" "$WORK/g13-${CANDIDATE}-neighbor-$i" "$meta") || exit 2
  jq --argjson r "$rec" '. += [$r]' "$RESULTS" > "$RESULTS.tmp" && mv "$RESULTS.tmp" "$RESULTS"
done

# 4. Aggregate; resolve the ±2 carve-out if requested.
OUTCOME="$WORK/g13-${CANDIDATE}-outcome.json"
python3 "$SCRIPTS/g13_aggregate.py" "$RESULTS" > "$OUTCOME"
if [[ $? -eq 2 ]] || [[ "$(jq -r '.outcome' "$OUTCOME")" == "NEEDS_PM2_PROBE" ]]; then
  probe=$(jq -c '.pm2_probe' "$OUTCOME")
  echo "==> G13 ±2 carve-out: probing $(echo "$probe" | jq -r '.name') one step further." >&2
  pm2=$(python3 - "$TEMPLATE" "$probe" <<'PY'
import json, sys
import g13_neighbors
req = json.loads(open(sys.argv[1]).read())
probe = json.loads(sys.argv[2])
nb = g13_neighbors.pm2_neighbor(req, probe)
path = "/tmp/g13-pm2-req.json"
open(path, "w").write(json.dumps(nb["request"]))
print(json.dumps({"meta": {k: nb[k] for k in ("tunable","name","direction","step","classification","floor_flag")}, "path": path}))
PY
)
  pm2_meta=$(echo "$pm2" | jq -c '.meta')
  pm2_req=$(echo "$pm2" | jq -r '.path')
  rec=$(fire_neighbor "$pm2_req" "$WORK/g13-${CANDIDATE}-pm2" "$pm2_meta") || exit 2
  jq --argjson r "$rec" '. += [$r]' "$RESULTS" > "$RESULTS.tmp" && mv "$RESULTS.tmp" "$RESULTS"
  python3 "$SCRIPTS/g13_aggregate.py" "$RESULTS" > "$OUTCOME"
fi

echo "" >&2
echo "==> G13 advisory outcome: $(jq -r '.outcome' "$OUTCOME")  (reason: $(jq -r '.reason // "—"' "$OUTCOME"))" >&2
echo "==> $OUTCOME" >&2
echo "Reminder: G13 is advisory (calibration-pending). It does NOT change the firewall verdict." >&2
