---
name: monte-carlo
description: Run Monte Carlo simulations against the Udgaard API on a saved backtest and delegate percentile / risk-of-ruin interpretation to the monte-carlo-analyst sub-agent. Use to quantify path risk and edge confidence before sizing up a strategy.
argument-hint: "[backtestId] [technique]"
---

# Monte Carlo Validation

Answers one question: **"What's the probability the live path is worse than I can stomach?"**

Quantifies path risk (max-drawdown distribution under random trade ordering) and edge confidence (return / win-rate / edge distribution under resampling). For initial edge measurement use `/backtest`. For OOS persistence use `/walk-forward`.

This skill is strategy-neutral. Substitute the user's actual `backtestId` / sizing in every example.

## Prerequisite

`/monte-carlo` requires an existing `backtestId` from a recent `/backtest` run. **The backend only retains the most recent backtest in memory** (see [Known limitations](#known-limitations)) — run `/backtest` immediately before this skill.

## Scenarios

Pick one technique or run both back-to-back. Replace `<BACKTEST_ID>` with the UUID returned from the prior `/backtest` call.

### 1. Trade shuffling (path risk / drawdown distribution)

Resamples trade **order**. Distribution of max drawdowns answers "how unlucky could the path be?" Almost always run with `positionSizing` — un-sized shuffling on large trade sets produces meaningless compounded returns.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/monte-carlo/simulate \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "backtestId": "<BACKTEST_ID>",
    "technique": "TRADE_SHUFFLING",
    "iterations": 10000,
    "positionSizing": {
      "startingCapital": <dollars>,
      "sizer": <SIZER>,
      "leverageRatio": 1.0
    }
  }' > /tmp/mc-shuffling.json
```

### 2. Bootstrap resampling (edge confidence)

Resamples trades **with replacement**. Distribution of return / edge / win-rate answers "is the observed edge real, or could I have gotten this lucky from a no-edge sample?"

```bash
curl -s -X POST http://localhost:9080/udgaard/api/monte-carlo/simulate \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "backtestId": "<BACKTEST_ID>",
    "technique": "BOOTSTRAP_RESAMPLING",
    "iterations": 10000
  }' > /tmp/mc-bootstrap.json
```

### 3. Both techniques back-to-back

The typical full validation. Run shuffling + bootstrap; the analyst combines them into one report.

```bash
# 1. shuffling
curl -s -X POST http://localhost:9080/udgaard/api/monte-carlo/simulate \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"backtestId": "<BACKTEST_ID>", "technique": "TRADE_SHUFFLING", "iterations": 10000, "positionSizing": {...}}' \
  > /tmp/mc-shuffling.json

# 2. bootstrap (sequentially — see "How to run")
curl -s -X POST http://localhost:9080/udgaard/api/monte-carlo/simulate \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"backtestId": "<BACKTEST_ID>", "technique": "BOOTSTRAP_RESAMPLING", "iterations": 10000}' \
  > /tmp/mc-bootstrap.json
```

### 4. Position-sized MC

Pass `positionSizing` (same shape as `/backtest` §2) so percentile equity curves are dollar-comparable to the original run. Required for shuffling to be meaningful; optional but recommended for bootstrap.

```jsonc
{
  "backtestId": "<BACKTEST_ID>",
  "technique": "TRADE_SHUFFLING",
  "iterations": 10000,
  "positionSizing": {
    "startingCapital": <dollars>,
    "sizer": {"type": "atrRisk", "riskPercentage": <r>, "nAtr": <n>},
    "leverageRatio": 1.0
  },
  "seed": <int>,                  // optional, for reproducibility
  "includeAllEquityCurves": false // true returns N curves; usually not needed (percentile fan is computed regardless)
}
```

## How to run

- **Endpoint:** `POST /api/monte-carlo/simulate` on PRD port `9080` with `X-API-Key` header
- **Iterations:** 10,000 is the default sweet spot. Range allowed: 100 – 100,000. Below 1,000 percentile estimates are noisy; above 10,000 wall time grows without much precision gain.
- **Wall time:** 10–60 seconds for 10k iterations on a typical backtest. Grows linearly with iterations × trade count.
- **Run sequentially**, not concurrently — same engine constraint as `/backtest`.
- **Save raw response to `/tmp/mc-<technique>.json`** — analyst agent reads from disk.

Capture `executionTimeMs` from the response if reporting cost.

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

## Agent delegation

After both API calls return, spawn `monte-carlo-analyst` with the paths to both saved JSONs. The agent:

- Combines shuffling + bootstrap into one report
- Computes drawdown-threshold probabilities (`P(max DD > X%)`) for typical thresholds (20 / 25 / 30 / 35%) — this is currently analyst-side work, see [Known limitations](#known-limitations)
- Locates the original backtest's metrics in the resampled distribution (which percentile?)
- Flags lucky / unlucky path
- Compares actual DD to MC shuffled distribution — actual >> p95 indicates structural correlation (market-wide shocks hit multiple positions simultaneously)
- Produces verdict + position-sizing recommendation

The skill itself does API orchestration + raw report assembly. Statistical interpretation is the agent's job.

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

## Critical warnings

- **Don't run shuffling without `positionSizing` on large trade sets.** Without sizing, shuffled compounded returns over 9,000+ trades become numerically meaningless (single trades can compound to absurd values). Either include `positionSizing` or restrict to bootstrap.
- **Don't conflate bootstrap and shuffling.** Bootstrap validates that the edge is statistically significant given the trade sample; it does NOT validate that the edge will persist forward. That's `/walk-forward`'s job.
- **`P(max DD > pain threshold)` is the single most important number** for sizing decisions. If the user has a personal pain threshold (e.g. -25%), the analyst should compute and headline this probability — not bury it in the percentile table.
