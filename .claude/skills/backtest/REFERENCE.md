# Backtest Reference

Output shape, report template, decision thresholds, and known limitations. See [SKILL.md](SKILL.md) for orchestration rules and [SCENARIOS.md](SCENARIOS.md) for request bodies.

## Output shape

The API returns a `BacktestResponseDto` with pre-computed analytics. Key fields the report uses:

- Scalars: `totalTrades`, `winRate`, `edge`, `profitFactor`, `cagr`
- Risk-adjusted ratios: `riskMetrics.{sharpeRatio, sortinoRatio, calmarRatio, sqn, tailRatio}` (only populated for position-sized backtests; null otherwise)
- Benchmark vs SPY: `benchmarkComparison.{benchmarkSymbol, correlation, beta, activeReturnVsBenchmark}` (null when overlap < 60 days or sized backtest absent)
- Drawdown episodes: `drawdownEpisodes[]` — top-10 (peak/trough/recoveryDate, maxDrawdownPct, declineDays/recoveryDays/totalDays)
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
- Longest DD: N days (peak → trough → recovery dates, depth%) — from `drawdownEpisodes[0]`

### Regime sensitivity
- Uptrend  win rate: X% (n=N)
- Downtrend win rate: X% (n=N)

### Top / bottom 3 sectors
| Sector | Trades | Edge | EC |
| ...

### Exit reasons
| Reason | Count | Avg profit | Win rate |
| ...

### Benchmark comparison (from `benchmarkComparison`)
- Symbol: SPY | Correlation: X | Beta: X | Active return vs benchmark: X%

(Note: `activeReturnVsBenchmark` is `r_p_ann − β·r_b_ann` — NOT Jensen's alpha. Jensen's α requires a non-zero RF and is not yet computed.)

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
| Calmar (CAGR / max DD) | > 1.0 | Healthy risk-adjusted (industry standard; values prior to the formula fix used totalReturn/maxDD and were inflated by ~N years) |
| Sharpe | > 1.0 | Decent risk-adjusted |
| Max DD | < 25% | Psychologically tradeable |
| DD duration | < 12 months | Recoverable on retail timescale |
| SPY correlation | < 0.6 | Adds something to a SPY portfolio |

Reject if any of: edge < 1.5%, EC < 40, max DD > 35%, DD duration > 24 months, single sector providing all the edge.

Note: trend-following / breakout strategies legitimately have ~45% win rate with 3:1 W/L ratio. Mean-reversion strategies legitimately have 65–80% win rate with sub-1 W/L. Judge by **edge × stability**, not win rate alone.

## Known limitations

Tracked here so the backend roadmap closes them; the skill works around each in the meantime.

- **USD-only equity curves.** `RiskMetricsService` assumes a USD-denominated portfolio. Multi-currency portfolios (e.g. AUD-base / USD-trade) are out of scope; their Sharpe / Sortino / CAGR would conflate strategy performance with FX vol, and benchmark beta vs SPY (USD) is essentially nonsense.
- **Survivorship bias.** All risk-adjusted metrics inherit survivorship bias from the underlying universe. Sharpe and active-return-vs-benchmark are inflated by an estimated 1–2pp annualized relative to a survivorship-free universe; Calmar is less affected (denominator survives). V18 mitigates but doesn't eliminate.
- **Daily bars only** — no intraday slippage modelling. Edge < 1.5% likely doesn't survive live costs.
- **Survivorship bias** — universe currently excludes most delisted-during-period stocks (V18 mitigates but doesn't eliminate).
- **Assumes perfect fills at close** — `entryDelayDays: 1` partially mitigates.
