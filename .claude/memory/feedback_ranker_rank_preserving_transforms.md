---
name: Check rank-preserving transforms before coding new rankers
description: Before building a new stock ranker, verify that its score function differs from an existing ranker under the ranking operation — i.e., that it's not just a monotonic transform of an existing score within a single day's candidate pool.
type: feedback
originSessionId: 66ceed94-85a1-4732-b2d1-ab41dcf2ae97
---
Before implementing a new `StockRanker`, check whether its score function is a *monotonic transform* of an existing ranker's score when restricted to a single day's candidate pool. If it is, both rankers produce identical orderings and therefore identical backtest results — the "new" ranker is redundant.

**Why:** On 2026-04-20 I built `SectorRelativeStrengthRanker` with score = `sectorBull − marketBreadth`, expecting it to produce different results from `SectorStrengthRanker` with score = `sectorBull`. The backtest returned *identical numbers to six decimal places* because `marketBreadth(day)` is a single scalar shared by every candidate on any given day. Subtracting a day-constant preserves rank, so the two rankers produce identical orderings on every day. Wasted a ~7-minute backtest run and the code/test cycle around it.

**How to apply:**
- When the score involves a value that is constant across all candidates on a given day (e.g., market-wide breadth, SPY price, VIX), subtracting or adding it to a per-stock value is rank-preserving.
- Multiplying/dividing by a day-constant is also rank-preserving (as long as the constant is positive).
- Useful non-monotonic transforms: (a) aggregate across time (rolling average over N days), (b) ordinal rank among a fixed peer set (e.g., rank among all 11 sectors), (c) compare the stock to itself historically (momentum/acceleration), (d) combine with a per-stock value where the day-constant enters non-linearly.
- Quick sanity check: write down `score(A, day) > score(B, day)` and simplify. If the day-constants cancel, the ranker is redundant with the one you'd get by removing them.
