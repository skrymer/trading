# Walk-forward aggregation methodology

The walk-forward endpoint (`POST /api/backtest/walk-forward`) produces a `WalkForwardResult` containing one `WalkForwardWindow` per IS/OOS window plus a single aggregate summary across all OOS windows. This ADR locks in **how the aggregate OOS risk-adjusted metrics — Sharpe, Sortino, Calmar, CAGR, max drawdown — are computed from the per-window data.**

## The choice

Aggregate metrics are computed from a **stitched daily-return series** across all OOS windows, not from a per-window-weighted average of the metrics.

For each window the existing `computeOosRiskMetrics` pipeline builds an OOS-only position-sized equity curve via `PositionSizingService.applyPositionSizing(...)` and a per-window `RiskMetrics` via `RiskMetricsService.compute(...)`. Aggregation then concatenates the per-window `dailyReturns(equityCurve)` series (in calendar order) into one continuous OOS daily-return series, replays it as a synthetic equity curve, and hands it to the same `RiskMetricsService.compute(...)` used by the single-backtest path.

## Why not per-window-weighted aggregates

The intuitive alternative — "aggregate Sharpe = trade-weighted average of per-window Sharpes" — is a textbook proxy and is wrong in two specific ways that bite this project:

1. **It systematically understates cross-window drawdowns.** A drawdown that begins in window 5 and bottoms in window 6 is invisible to a per-window max-DD scan; each window's max-DD only sees its own slice. The v4 gating (max DD <= 25% aggregated, <= 20% per-window) explicitly needs a real cross-window max, and the recently-invalidated VCP showed how cross-window risk can hide inside reported numbers.
2. **It is dominated by a single fat window.** A trade-weighted Sharpe is just `sum(sharpe_w * trades_w) / sum(trades_w)`. A 2003-style outlier window with 60 trades and Sharpe 4.0 pulls the aggregate Sharpe up regardless of how the other 11 windows behaved — the same single-window-dominance failure mode v3 hit with median Calmar (see `/tmp/goal-results-v3.md`).

The stitched series suffers neither: a cross-window drawdown is captured because the equity curve is continuous; outlier-window dominance is bounded because the Sharpe is computed over `sum(days)` not `sum(trades)`.

## Cost

The stitch is mechanically simple. `WalkForwardService.computeOosRiskMetrics` is refactored to return the per-window equity curve and trade list alongside the per-window `RiskMetrics`. `WalkForwardService.aggregateResults` concatenates the per-window equity curves (normalizing each to a growth-multiplier from its window's start so the join is continuous), concatenates the trade lists, and calls `RiskMetricsService.compute(...)` on the stitched inputs. The aggregate `cagr`, `maxDrawdownPct`, and `RiskMetrics` returned are real, not proxies.

## Adjacency assumption and CAGR span

The stitch assumes OOS windows are adjacent in calendar time. `WalkForwardService.generateWindows` already asserts `stepMonths >= outOfSampleMonths`, so windows never overlap. When `stepMonths == outOfSampleMonths` (the common case for v3/v4 configs), OOS windows are exactly adjacent. When `stepMonths > outOfSampleMonths`, gap days exist between adjacent windows.

**CAGR span is wall-clock** — from the first window's first OOS bar to the last window's last OOS bar. The strategy is assumed flat (zero return) during gap days, which is the conservative direction (gap days dilute the annualised rate). This matches the SPY-benchmark convention used by the v4 gates and `RiskMetricsService.cagr` for the single-backtest endpoint: same wall-clock denominator on both sides of any "alpha vs SPY" comparison.

The stitched daily-return series itself does NOT include synthetic zero-return rows for gap days; the gaps are absent from Sharpe / Sortino inputs. This is intentional: a strategy that is unfunded on gap days has no daily-return observation, and adding zeros would understate volatility. The CAGR-wall-clock / Sharpe-active-days asymmetry is documented on `WalkForwardService.aggregateResults`'s KDoc.

## Null semantics

Per-window `outOfSampleRiskMetrics` is null when the run is un-sized (`PositionSizingConfig == null`) — mirrors the existing `outOfSampleCagr` / `outOfSampleMaxDrawdownPct` semantics. Aggregate fields are null when **every** per-window value is null. Mixed runs (some windows sized, others not) are not produced by the engine — `PositionSizingConfig` is a request-level field, applied uniformly to every window.

## What this does NOT decide

- **Benchmark comparison** (vs SPY, correlation, beta, active return). Computable via the same `RiskMetricsService.compute(...)` path, but excluded from the WF response in the PR landing this ADR. Add when a caller needs it.
- **In-sample risk metrics.** The `inSample*` fields on `WalkForwardWindow` remain edge/trades/winRate only. IS risk metrics are diagnostic, not load-bearing for the v4 gates; add when an IS-driven diagnostic needs them.
- **Risk-free rate input.** Threaded from the WF request DTO's new `riskFreeRatePct: Double?` field (default 0.0) through `WalkForwardService.BacktestParams` to `RiskMetricsService.compute(...)`. Backwards-compatible.
