# Walk-Forward Reference

Output shape, report template, decision thresholds, and known limitations. See [SKILL.md](SKILL.md) for orchestration rules and [SCENARIOS.md](SCENARIOS.md) for request bodies.

## Output shape

The API returns a `WalkForwardResult`. Top-level fields:

- `walkForwardEfficiency` — aggregate WFE (= aggregate OOS edge / simple-average IS edge)
- `aggregateOosEdge`, `aggregateOosTrades`, `aggregateOosWinRate`
- `windows[]` — one entry per IS→OOS window:
  - `inSampleStart`, `inSampleEnd`, `outOfSampleStart`, `outOfSampleEnd`
  - `inSampleEdge`, `outOfSampleEdge`
  - `inSampleTrades`, `outOfSampleTrades`
  - `inSampleWinRate`, `outOfSampleWinRate`
  - `derivedSectorRanking` — IS-optimal sector order (informational; not applied to OOS)

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
| Window | IS edge | OOS edge | OOS/IS | OOS trades | OOS WR | OOS regime* |
| <IS_start>→<IS_end> → <OOS_end> | X.X | X.X | X.XX | N | X% | uptrend / mixed / downtrend |
| ...
* regime is best-effort; backend doesn't expose per-window regime yet

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

- **Per-window regime tagging is best-effort.** The walk-forward result has dates per window but no regime label. The analyst infers regime from the dates by querying SPY/breadth tables — fine but adds latency. Backend should annotate each `WalkForwardWindow` with `oosUptrendPercent` / `oosBreadthAvg` derived from `MarketBreadthDaily`.
- **`derivedSectorRanking` is informational only.** Per the API contract, the IS-derived sector ranking does NOT re-rank OOS trades. Treat as an overfitting signal (does the ranking churn across windows?), not as evidence the strategy uses IS sector data live.
- **Small window counts.** With 10y of data and default 5y IS / 1y OOS / 1y step, you get ~5–6 windows. Aggregate metrics are trade-weighted, so windows with more trades dominate. Per-window WFEs can be wildly dispersed (0 to 1.5+) on small samples.
- **Walk-forward tests parameter durability, not optimization procedure.** Running walk-forward with fixed parameters validates that those parameters survive OOS. It does NOT validate that re-optimizing on each IS window would survive — that requires a different harness.
- Same daily-bar / no-slippage / survivorship-bias caveats as `/backtest` apply.
