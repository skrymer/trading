#!/usr/bin/env bash
# Detect backend changes that may invalidate the public-API skills
# (backtest, walk-forward, monte-carlo).
#
# Usage: .claude/scripts/skill-impact-check.sh
#
# Prints a human-readable report mapping changed files to skill files that
# may need review. Empty output = no skill-relevant backend changes.
#
# This is a HEURISTIC. Path matches don't guarantee a public-contract change —
# the reviewer should read the diff and decide whether the corresponding skill
# text is still accurate.

set -euo pipefail

changed=$({
  git diff --name-only HEAD
  git ls-files --others --exclude-standard
} | sort -u)

if [[ -z "$changed" ]]; then
  exit 0
fi

bt='udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting'

report() {
  local pattern="$1" label="$2" skills="$3"
  local matches
  matches=$(echo "$changed" | grep -E "$pattern" || true)
  if [[ -n "$matches" ]]; then
    echo "Changed under: $label"
    echo "$matches" | sed 's/^/  - /'
    echo "Review: $skills"
    echo
  fi
}

report "^$bt/controller/" \
  "backtesting/controller/" \
  ".claude/skills/{backtest,walk-forward,monte-carlo}/SKILL.md (endpoints)"

report "^$bt/dto/" \
  "backtesting/dto/" \
  ".claude/skills/{backtest,walk-forward,monte-carlo}/SCENARIOS.md (request shapes), .../REFERENCE.md (response fields)"

report "^$bt/model/" \
  "backtesting/model/" \
  ".claude/skills/{backtest,walk-forward,monte-carlo}/REFERENCE.md (output shape, decision thresholds)"

report "^$bt/service/sizer/" \
  "backtesting/service/sizer/" \
  ".claude/skills/backtest/SCENARIOS.md §2 (sizer table)"

report "^$bt/strategy/condition/" \
  "backtesting/strategy/condition/" \
  ".claude/skills/backtest/SCENARIOS.md §4 (custom DSL conditions)"

report "^$bt/strategy/(Ranker|StockRanker)" \
  "backtesting/strategy/Ranker*.kt or StockRanker.kt" \
  ".claude/skills/backtest/SCENARIOS.md §3 + .claude/skills/walk-forward/SCENARIOS.md §4 (ranker categories/shape)"

report "^$bt/strategy/StrategyDsl\.kt" \
  "backtesting/strategy/StrategyDsl.kt" \
  ".claude/skills/backtest/SCENARIOS.md §4 (custom DSL syntax)"

report "^$bt/strategy/RegisteredStrategy\.kt" \
  "backtesting/strategy/RegisteredStrategy.kt" \
  ".claude/skills/backtest/SKILL.md (strategy registration mechanism)"

report "^$bt/service/BacktestResultStore\.kt" \
  "backtesting/service/BacktestResultStore.kt" \
  ".claude/skills/monte-carlo/SKILL.md prerequisite + .claude/skills/monte-carlo/REFERENCE.md (in-memory storage limitation)"
