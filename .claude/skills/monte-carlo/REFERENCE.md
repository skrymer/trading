# Monte Carlo Reference

Output shape, report template, decision thresholds, and known limitations. See [SKILL.md](SKILL.md) for orchestration rules and [SCENARIOS.md](SCENARIOS.md) for request bodies.

## Output shape

The API returns a `MonteCarloResult`. Top-level fields:

- `technique`, `iterations`, `executionTimeMs`
- `statistics` (`MonteCarloStatistics`):
  - **Returns:** `meanReturnPercentage`, `medianReturnPercentage`, `stdDevReturnPercentage`, `returnPercentiles` (p5/p25/p50/p75/p95), `bestCaseReturnPercentage`, `worstCaseReturnPercentage`
  - **Drawdown:** `meanMaxDrawdown`, `medianMaxDrawdown`, `drawdownPercentiles` (p5/p25/p50/p75/p95)
  - **Win rate:** `meanWinRate`, `medianWinRate`, `winRatePercentiles`
  - **Edge:** `meanEdge`, `medianEdge`, `edgePercentiles`
  - **CIs (95%):** `returnConfidenceInterval95`, `drawdownConfidenceInterval95`
  - `probabilityOfProfit` (% of scenarios with positive return)
- `percentileEquityCurves` — pre-computed p5 / p25 / p50 / p75 / p95 equity curves (always returned)
- `scenarios[]` — full per-iteration equity curves (only if `includeAllEquityCurves: true`)
- `originalReturnPercentage`, `originalEdge`, `originalWinRate` — for comparison vs the resampled distribution
- `originalMaxDrawdown` — original position-sized max DD, populated when `positionSizing` is included in the request; `null` for un-sized requests (no portfolio equity curve → no DD)

## Example report shape

Numbers are placeholders. Combined report when both techniques are run.

```
## Monte Carlo Report — <Entry> / <Exit>
backtestId: <uuid> | iterations: 10000 each | techniques: TRADE_SHUFFLING + BOOTSTRAP_RESAMPLING

### Path Risk (trade shuffling)
- Original max DD:  -X%
- Median max DD:    -X%   | Mean: -X%
- p75 max DD:       -X%
- p95 max DD:       -X%   (1-in-20 worst path)
- p99 max DD:       -X%   (1-in-100 worst path)
- 95% CI for max DD: [-X%, -X%]

#### Drawdown threshold probabilities (from analyst)
| P(max DD > X%) | Probability |
| 20%            | X%          |
| 25%            | X%          |
| 30%            | X%          |
| 35%            | X%          |

### Edge Confidence (bootstrap)
- Original edge: X.X%   | Mean: X.X%   | Median: X.X%
- Edge p5:       X.X%   ← worst-case resample
- Edge p95:      X.X%
- Original return: +X%
- 95% CI for return:   [+X%, +X%]
- Probability of profit: X.X%

### Equity Curve Fan
[ASCII or chart of p5/p25/p50/p75/p95 over time]

### Original vs distribution
- Original return: +X%   (sits at p_NN of the resampled distribution)
- Original max DD: -X%   (sits at p_NN of the shuffled distribution)
- Was the backtest lucky? <yes/no/normal range>

### Verdict (monte-carlo-analyst)
- ✅ / ⚠ / ❌ on edge reality, path-risk acceptability
- 🔬 If path risk dominates: reduce sizer risk%, add drawdownScaling, or accept smaller positions
- 🔬 If edge CI straddles 0: do NOT trade live, edge is not statistically significant
```

## Decision framework (general systematic-trading thresholds)

**Edge confidence (bootstrap):**

| Signal | Threshold | Verdict |
|--------|-----------|---------|
| Probability of profit | ≥ 95% | Edge is real, not luck |
| Edge p5 | > 1.5% | Worst-case resampling clears tradeable threshold |
| Original within p25–p75 | yes | Original backtest was neither lucky nor unlucky |
| 95% CI for return | lower bound > 0 | Statistically significant positive edge |

**Path risk (trade shuffling):**

| Signal | Implication |
|--------|-------------|
| Actual DD < MC median | Trade ordering was favorable — got lucky on path |
| Actual DD in p50–p75 | Normal range |
| Actual DD in p75–p95 | Somewhat unlucky path, mild correlation clustering |
| Actual DD > p95 | Significant correlation clustering — actual is worse than shuffled because shuffling destroys temporal correlation. The MC shuffled distribution is a **lower bound** on real-world DD, not an upper bound. |
| `P(max DD > <pain threshold>)` | If the user can't stomach DD beyond X%, this number must be acceptably low. |

Reject if any of: probability of profit < 90%, edge 95% CI lower bound < 0%, p95 max DD > 1.5× user's pain threshold.

## Known limitations

Tracked here so the backend roadmap closes them; the skill works around each in the meantime.

- **Backtest results stored in-memory only, most-recent only.** `BacktestResultStore` keeps a single result; running another `/backtest` (or restarting the backend) invalidates the previous `backtestId`. The skill must run `/backtest` immediately before `/monte-carlo`. Persisting to DB so multiple `backtestId`s can coexist is a separate follow-up — same gap on `/walk-forward` analysis if it ever needs an MC pass.
- **Drawdown-threshold probabilities computed in analyst, not backend.** `MonteCarloStatistics` exposes percentiles but not configurable `P(max DD > X%)`. Analyst computes from `scenarios` array when `includeAllEquityCurves: true`, otherwise from the percentile curves (less precise). Backend should accept `drawdownThresholds: List<Double>` and return per-threshold probabilities directly.
- **Bootstrap assumes IID trades.** Real trades cluster in correlated regimes (multiple longs hit by the same selloff). Bootstrap edge confidence is a useful lower bound but somewhat optimistic — narrower CIs than reality.
- **Trade shuffling destroys temporal correlation.** Shuffled DD distribution is a lower bound on realistic DD. Use it to detect correlation-driven path risk (actual DD >> p95), not to bound worst-case DD.
- **Cache expiry on `backtestId`.** Trade data cached server-side for 1 hour; older `backtestId`s won't resolve. Re-run `/backtest` if needed.
- Same daily-bar / no-slippage caveats as `/backtest` apply.
