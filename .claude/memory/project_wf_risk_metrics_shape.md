---
name: wf-risk-metrics-shape
description: "WalkForwardResult exposes Sharpe/Sortino/Calmar/SQN/tailRatio nested inside a `RiskMetrics` object, NOT as flat fields. Per-window field is `outOfSampleRiskMetrics.{sharpeRatio,sortinoRatio,calmarRatio,sqn,tailRatio}`; aggregate field is `aggregateOosRiskMetrics.{...}`."
metadata: 
  node_type: memory
  type: project
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

The walk-forward backtest response (`POST /api/backtest/walk-forward`) returns risk-adjusted metrics nested inside a `RiskMetrics` object, NOT as flat top-level fields.

**Correct jq paths**

- Per-window: `.windows[].outOfSampleRiskMetrics.sharpeRatio` (also `.sortinoRatio`, `.calmarRatio`, `.sqn`, `.tailRatio`)
- Aggregate: `.aggregateOosRiskMetrics.sharpeRatio` (same nested keys)

**Wrong paths** (will silently return null):
- `.aggregateOosSharpe`, `.aggregateOosCalmar`, `.outOfSampleSharpe`, etc. — these fields don't exist

**Always-flat fields on each window:**
- `outOfSampleCagr`, `outOfSampleMaxDrawdownPct` (top-level, nullable when un-sized)

**Why:** Reason: The engine consolidates the 5 risk-adjusted metrics into a single `RiskMetrics` data class (see `WalkForwardResult.kt` lines 35 & 55). Spring serializes the nested class as a JSON object.

**How to apply:** When eval scripts, gate evaluators, or analyst sub-agents read Sharpe/Sortino/Calmar from a walk-forward JSON, query the nested path. A flat-field query returns null, which looks identical to "un-sized run, no equity curve" — silent failure mode. The validate-candidate skill's `eval-block.py` and `summarize.py`, the firewall-analyst's trajectory analysis, and the `/tmp/sizer-sweep-*` helpers all need to use the nested path.
