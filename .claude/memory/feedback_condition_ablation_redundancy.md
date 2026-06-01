---
name: Check condition redundancy before ablation testing
description: Before testing a new entry condition on top of an existing strategy, inspect the strategy's existing conditions for dimensional overlap (e.g., another regime filter) and skip variants where the new condition would be redundant.
type: feedback
originSessionId: 66ceed94-85a1-4732-b2d1-ab41dcf2ae97
---
Before running an ablation sweep that adds a new entry condition on top of an existing composite strategy, first read the base strategy's condition list and ask: does any existing condition already cover the same *dimension* (market regime, sector regime, trend direction, volatility, etc.) as the one being added?

**Why:** In the 2026-04-20 VCP + `marketBreadthIncreasing` sweep, adding a market-regime filter on top of VCP produced zero edge improvement at any `days` setting (1, 2, 3, 5) — because VCP's existing `marketUptrend` condition (breadth EMA alignment) already encodes the "market is in bull mode" regime. `marketBreadthIncreasing` is a more restrictive version of the same dimension, so it just filters trades without adding information. The same logic applies to `sectorBreadthIncreasing` vs VCP's `sectorUptrend` — no need to run that variant either.

**How to apply:**
- When planning an ablation on a composite strategy (e.g., VCP, PlanAlpha), list the base strategy's conditions by *dimension* before designing variants.
- If the new condition occupies a dimension already covered, skip it or redesign the test (e.g., replace the existing condition instead of stacking, or test the new condition on a bare strategy that lacks that dimension).
- This doesn't mean the new condition is useless — it means the test setup was wrong. Pair it with strategies that don't already filter the same dimension.
