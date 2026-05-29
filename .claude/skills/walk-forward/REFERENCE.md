# Walk-Forward Reference

Output shape, report template, decision thresholds, and known limitations. See [SKILL.md](SKILL.md) for orchestration rules and [SCENARIOS.md](SCENARIOS.md) for request bodies.

## Output shape

The API returns a `WalkForwardResult`. Top-level fields:

- `walkForwardEfficiency` — aggregate WFE (= aggregate OOS edge / simple-average IS edge)
- `aggregateOosEdge`, `aggregateOosTrades`, `aggregateOosWinRate`
- `aggregateOosRiskMetrics` (`RiskMetrics?`) — Sharpe / Sortino / Calmar / SQN / tailRatio computed from the **stitched** daily-return series across all OOS windows (per ADR-0005). Captures cross-window drawdowns that per-window-max-DD cannot. `null` for un-sized runs.
- `aggregateOosCagr`, `aggregateOosMaxDrawdownPct` (`Double?`) — CAGR (wall-clock-annualised) and peak-to-trough max DD from the stitched synthetic continuous-compounding equity curve. Same null semantics.
- `windows[]` — one entry per IS→OOS window:
  - `inSampleStart`, `inSampleEnd`, `outOfSampleStart`, `outOfSampleEnd`
  - `inSampleEdge`, `outOfSampleEdge`
  - `inSampleTrades`, `outOfSampleTrades`
  - `inSampleWinRate`, `outOfSampleWinRate`
  - `outOfSampleCagr`, `outOfSampleMaxDrawdownPct` (`Double?`) — OOS-segment CAGR and max drawdown, derived by applying position sizing to each window's OOS trades. The per-window inputs for a Calmar-based walk-forward objective. Both `null` for un-sized walk-forward runs (no daily equity curve).
  - `outOfSampleRiskMetrics` (`RiskMetrics?`) — per-window Sharpe / Sortino / Calmar / SQN / tailRatio from the position-sized OOS equity curve via `RiskMetricsService.compute`. `null` for un-sized runs.
  - `inSampleBreadthUptrendPercent`, `inSampleBreadthAvg` — IS-half regime metrics derived from `MarketBreadthDaily.isInUptrend()` (`breadthPercent > ema10`)
  - `outOfSampleBreadthUptrendPercent`, `outOfSampleBreadthAvg` — OOS-half regime metrics (same definition)
  - `outOfSampleStatsByEntryMonth` (`Map<String, TradeStatsSummary>`) — OOS trades bucketed by entry-date month (key `"yyyy-MM"`). Each `TradeStatsSummary` carries **additive raw fields** (`trades`, `winners`, `sumWinPercent`, `sumLossPercent`, `grossWinProfit`, `grossLossProfit`) plus derived `winRate` / `edge` / `profitFactor`. Because Edge / Profit factor are non-linear over subsets, re-aggregate an arbitrary contiguous month range by summing the raw fields and recomputing the metrics once — do NOT average per-month Edge. Empty map for un-sized vs sized is irrelevant (always populated when there are OOS trades). Per ADR-0006. Used by `/validate-candidate` to split Block-B G6 into G6a (Jan–Apr 2020 crash survival) + G6b (May–Dec 2020 recovery).
  - `derivedSectorRanking` — IS-optimal sector order (informational; not applied to OOS)

**Stitched-aggregate semantics** (ADR-0005): aggregate Sharpe / Sortino / Calmar / CAGR / max DD are NOT trade-weighted averages of per-window values. The per-window equity curves are normalised to their window-start values and chained multiplicatively into one continuous synthetic curve; daily returns are concatenated for Sharpe/Sortino; trades are concatenated for SQN/tailRatio. CAGR annualises against wall-clock span (matches SPY-benchmark convention). When `stepMonths > outOfSampleMonths` the strategy is assumed flat during gap days (conservative direction).

**Regime divergence**: `outOfSampleBreadthUptrendPercent − inSampleBreadthUptrendPercent` positive ⇒ OOS more bullish than IS (strategy got tailwind on unseen data). Large negative ⇒ OOS regime is harder than IS — OOS edge degradation may be regime-driven, not curve-fitting.

## Example report shape

Numbers below are placeholders.

```
## Walk-Forward Report — <Entry> / <Exit>
Range: <start> to <end> | Cadence: <Iy>y IS / <Oy>y OOS / <Sy>y step | Windows: N

### Headline
- Walk-Forward Efficiency: X.XX  (>0.50 persistent, <0.30 likely curve-fit)
- Aggregate OOS edge: X.X%   (vs IS: X.X%)
- Aggregate OOS trades: N    | OOS win rate: X%

### Per-window
| Window | IS edge | OOS edge | OOS/IS | OOS trades | OOS WR | IS uptrend% | OOS uptrend% | Δ (OOS−IS) |
| <IS_start>→<IS_end> → <OOS_end> | X.X | X.X | X.XX | N | X% | X% | X% | +/-X% |
| ...

### Stability (from analyst)
- OOS-positive windows: N / M
- Std-dev of OOS edge across windows: X
- Worst window: <date range> (OOS edge X.X%, regime <regime>)
- Best window:  <date range> (OOS edge X.X%, regime <regime>)

### Sector ranking stability
- Top-3 IS-derived sectors per window: <list>
- Stability: do the same sectors lead across windows, or churn? (informational only)

### Verdict (walk-forward-analyst)
- ✅ / ⚠ / ❌ on edge persistence, regime-robustness, overfitting risk
- 🔬 Recommended next step: /monte-carlo on the position-sized run, or stop here
```

## Decision framework (general systematic-trading thresholds)

| WFE | Verdict |
|-----|---------|
| < 0.30 | Likely curve-fit — reject or rebuild |
| 0.30 – 0.50 | Marginal — proceed only after `/monte-carlo` confirms edge confidence |
| 0.50 – 0.80 | Robust — meaningful edge survives OOS |
| > 0.80 | Excellent (suspiciously high may indicate insufficient OOS challenge) |

Additional checks beyond aggregate WFE:

| Check | Threshold | Why it matters |
|-------|-----------|----------------|
| OOS-positive windows | ≥ 70% | "Every window 0.6" vs "half 1.2 / half 0.0" both give WFE 0.6 — only the first is tradeable |
| Std-dev of OOS edge | < IS edge | High dispersion across windows means edge is regime-dependent |
| Smallest OOS window trade count | ≥ 30 | Single-window OOS metrics below this are noise |
| Aggregate OOS edge | ≥ 1.5% | Trades-after-costs threshold — OOS edge above this clears the live-trading bar |

Reject if any of: aggregate WFE < 0.30, < 50% OOS-positive windows, OOS edge < 0% in the most recent window with `n ≥ 30`.

## Known limitations

Tracked here so the backend roadmap closes them; the skill works around each in the meantime.

- **`derivedSectorRanking` is informational only.** Per the API contract, the IS-derived sector ranking does NOT re-rank OOS trades. Treat as an overfitting signal (does the ranking churn across windows?), not as evidence the strategy uses IS sector data live.
- **Small window counts.** With 10y of data and default 5y IS / 1y OOS / 1y step, you get ~5–6 windows. Aggregate metrics are trade-weighted, so windows with more trades dominate. Per-window WFEs can be wildly dispersed (0 to 1.5+) on small samples.
- **Walk-forward tests parameter durability, not optimization procedure.** Running walk-forward with fixed parameters validates that those parameters survive OOS. It does NOT validate that re-optimizing on each IS window would survive — that requires a different harness.
- Same daily-bar / no-slippage / survivorship-bias caveats as `/backtest` apply.
