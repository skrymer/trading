# Per-window entry-month trade-stats buckets

The walk-forward response (`WalkForwardWindow`) now carries `outOfSampleStatsByEntryMonth: Map<"yyyy-MM", TradeStatsSummary>` — the OOS trades of each window bucketed by **entry date** (`Trade.entryQuote.date`, per CONTEXT.md's *Entry date*) at **monthly** granularity, with each bucket a `TradeStatsSummary` value object carrying additive raw fields (`trades`, `winners`, `sumWinPercent`, `sumLossPercent`, `grossWinProfit`, `grossLossProfit`) plus computed **Edge**, **Win rate**, and **Profit factor**. This lets any consumer reconstruct all three canonical CONTEXT.md trade-set metrics over an arbitrary contiguous range of months.

## Why monthly + additive, not pre-sliced

The driving need is the firewall's G6a/G6b Block-B split: crash survival (trades entered **Jan–Apr** 2020) vs recovery (**May–Dec** 2020). That boundary is asymmetric and COVID-specific — *not* a calendar half-year (the "2020-H1/H2" labels in issue #51 and SKILL.md are loose; the real cut is Apr/May). Emitting half-year buckets would lose the April/May boundary; emitting exactly the two COVID slices would bake a strategy-specific, single-use cut into the engine.

Monthly buckets keep the **engine slicing-agnostic**: the asymmetric Jan–Apr/May–Dec cut — and any future quarterly / VIX-regime / sector slice — lives in the *evaluator*, which re-aggregates months. Edge and profit factor are non-linear over subsets, so buckets store **additive raw sums** (counts + Σwin%/Σloss% + gross win$/loss$), not pre-computed per-month edges; a consumer sums the raw fields across the months it cares about and computes the metric once. `eval-block.py`'s G6a/G6b is the **first** consumer, not the design driver.

## Boundaries

- **OOS only.** No in-sample equivalent, matching ADR-0005's precedent (IS fields stay edge/trades/winRate; add when an IS diagnostic needs it).
- **Reconciliation.** Buckets are built from `oosReport.trades` (taken winning+losing trades, excluding missed), the same population `outOfSampleEdge`/`outOfSampleTrades` derive from — so the months reconcile exactly to the window aggregate.
- **`TradeStatsSummary` owns its math** via a `fromTrades` factory (ADR-0001 rich domain; precedent `StrategyBreakdownStats.fromPositions`), and is deliberately month-agnostic so it can later summarize by sector/year/regime.
- **Un-sized caveat.** In an un-sized run `profit` is per-share price difference, so per-month **Profit factor** is a relative measure on unsized P&L — the same caveat `BacktestReport.profitFactor` already carries.

## Not decided here

- **Reusing `TradeStatsSummary` inside `BacktestReport`** (which hand-rolls edge/winRate/profitFactor today). A worthwhile follow-up to remove the duplicated formulas, deferred to keep this change scoped.
