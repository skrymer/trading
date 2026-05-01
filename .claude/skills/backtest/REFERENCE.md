# Backtest Reference

Output shape, report template, decision thresholds, and known limitations. See [SKILL.md](SKILL.md) for orchestration rules and [SCENARIOS.md](SCENARIOS.md) for request bodies.

## Output shape

The API returns a `BacktestResponseDto` with pre-computed analytics. Key fields the report uses:

- Scalars: `totalTrades`, `winRate`, `edge`, `profitFactor`, `calmarRatio`
- Equity: `equityCurveData`, `positionSizing.equityCurve` (daily M2M)
- Stability: `edgeConsistencyScore` (0–100 + `yearlyEdges`)
- Regime: `marketConditionStats` (uptrend/downtrend win rates)
- Drawdown depth: `atrDrawdownStats` (percentile distribution)
- Sectors: `sectorPerformance`, `sectorStats`
- Exits: `exitReasonAnalysis`
- Time slices: `timeBasedStats` (byYear/Quarter/Month)

## Example report shape

The numbers below are placeholders — they show structure, not a real strategy.

```
## Backtest Report — <Entry> / <Exit>
Period: <start> to <end> | Trades: N | Position-sized | backtestId: <uuid>

### Headline
- Total return: +X% | CAGR: X% | Max DD: -X%
- Win rate: X% | Edge: X% | Profit factor: X
- Sharpe: X | Sortino: X | Calmar: X

### Stability
- Edge consistency score: N / 100 (interpretation)
- Profitable years: N / M | Tradeable (edge ≥ 1.5%): N / M
- Worst year: -X% (YYYY) | Best: +X% (YYYY)

### Drawdown
- Median ATR-DD: X ATR | p95: X ATR | p99: X ATR
- Longest DD: N months (peak → trough → recovery dates, depth%)  ← from analyst

### Regime sensitivity
- Uptrend  win rate: X% (n=N)
- Downtrend win rate: X% (n=N)

### Top / bottom 3 sectors
| Sector | Trades | Edge | EC |
| ...

### Exit reasons
| Reason | Count | Avg profit | Win rate |
| ...

### SPY correlation (from analyst)
- Correlation: X | Beta: X | Annualized alpha: X%

### Verdict (post-backtest-analyst)
- ✅ / ⚠ / ❌ on edge reality, drawdown sustainability, alpha quality
- 🔬 Recommended next step: /walk-forward and/or /monte-carlo
```

## Decision framework (general systematic-trading thresholds)

These are not strategy-specific — they're the conventional bar for a systematic equity strategy.

| Signal | Threshold | Verdict |
|--------|-----------|---------|
| Edge | ≥ 1.5% | Tradeable after costs |
| Edge consistency score | ≥ 60 | Reliable |
| Calmar | > 1.0 | Healthy risk-adjusted |
| Sharpe | > 1.0 | Decent risk-adjusted |
| Max DD | < 25% | Psychologically tradeable |
| DD duration | < 12 months | Recoverable on retail timescale |
| SPY correlation | < 0.6 | Adds something to a SPY portfolio |

Reject if any of: edge < 1.5%, EC < 40, max DD > 35%, DD duration > 24 months, single sector providing all the edge.

Note: trend-following / breakout strategies legitimately have ~45% win rate with 3:1 W/L ratio. Mean-reversion strategies legitimately have 65–80% win rate with sub-1 W/L. Judge by **edge × stability**, not win rate alone.

## Known limitations

Tracked here so the backend roadmap closes them; the skill works around each in the meantime.

- **Risk-adjusted metrics computed in analyst, not backend.** Sharpe / Sortino / CAGR / SPY-correlation / drawdown-duration come from `post-backtest-analyst`'s post-processing of the equity curve. Values are deterministic per analyst version but recompute every run. Promote into `BacktestReport` so they're testable and consistent across skills.
- **SPY correlation** requires SPY in the symbol set or a separate fetch — the analyst handles the fetch as a workaround.
- **Daily bars only** — no intraday slippage modelling. Edge < 1.5% likely doesn't survive live costs.
- **Survivorship bias** — universe currently excludes most delisted-during-period stocks (V18 mitigates but doesn't eliminate).
- **Assumes perfect fills at close** — `entryDelayDays: 1` partially mitigates.
