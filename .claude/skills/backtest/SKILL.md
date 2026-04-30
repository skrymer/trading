---
name: backtest
description: Run a single backtest against the Udgaard API, save raw results, and delegate post-hoc analysis (Sharpe / Sortino / drawdown duration / SPY correlation) to the post-backtest-analyst sub-agent. Use when the user asks to backtest a strategy, measure edge, or check whether a strategy is fit to trade.
argument-hint: "[strategy-name] [scenario]"
---

# Backtest a Trading Strategy

Answers one question: **"Would I have stuck with this strategy through its worst stretch?"**

Returns trade-by-trade performance with a verdict on profit, edge stability, drawdown depth + duration, and regime sensitivity. For walk-forward validation use `/walk-forward`. For path/edge confidence use `/monte-carlo`.

This skill is strategy-neutral. Substitute the user's actual strategy/exit/sizer/ranker in every example below.

## Discovery

Before running, list what's available unless the user specifies exact names:

```bash
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/strategies   # entry + exit names
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/conditions   # custom DSL conditions + parameters
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/rankers      # ranker names for position-limited runs
```

## Scenarios

Pick the scenario that matches the user's ask, then assemble the request from that section. Replace `<entry>` / `<exit>` / sizer params / ranker / dates with what the user provides.

### 1. Quick edge check (unlimited)

Statistical edge measurement — every signal taken, no position cap, no sizing. Use to sanity-check a freshly registered strategy or run an ablation.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/backtest \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "<entry>"},
    "exitStrategy":  {"type": "predefined", "name": "<exit>"},
    "startDate": "<YYYY-MM-DD>",
    "endDate":   "<YYYY-MM-DD>"
  }' > /tmp/backtest-unlimited.json
```

### 2. Position-sized realism (live-trade decision)

The real go/no-go run. Required before recommending live trading. See [§3 Ranking](#3-ranking) for picking which signals to take when more fire than `maxPositions` allows.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/backtest \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "<entry>"},
    "exitStrategy":  {"type": "predefined", "name": "<exit>"},
    "startDate": "<YYYY-MM-DD>",
    "endDate":   "<YYYY-MM-DD>",
    "maxPositions": <N>,
    "entryDelayDays": 1,
    "ranker": "<ranker>",
    "rankerConfig": <RANKER_CONFIG_OR_OMIT>,
    "positionSizing": {
      "startingCapital": <dollars>,
      "sizer": <SIZER>,
      "leverageRatio": 1.0
    }
  }' > /tmp/backtest-sized.json
```

`<SIZER>` is one of:

| Sizer | Shape | Use when |
|-------|-------|----------|
| `{"type": "atrRisk", "riskPercentage": <r>, "nAtr": <n>}` | Equal $ risk per trade scaled by ATR | Strategy where stop distance varies per name |
| `{"type": "percentEquity", "percent": <p>}` | Equal-notional, ignores ATR | Strategy with similar volatility across names |
| `{"type": "kelly", "winRate": <W>, "winLossRatio": <R>, "fractionMultiplier": 0.25}` | Kelly criterion (quarter-Kelly recommended) | High-confidence W/R estimates, uncorrelated trades |
| `{"type": "volTarget", "targetVolPct": <v>, "kAtr": <k>}` | Equal vol contribution | Multi-asset where vol parity matters |

Always include `leverageRatio: 1.0` for stocks unless the user explicitly enables leverage. Without it, ATR-based sizers can produce extreme leverage on low-ATR names. Optional `drawdownScaling` overlay reduces risk in deeper drawdowns — see the project README or ask the user if relevant.

### 3. Ranking

Ranking only matters when `maxPositions` is set (scenario 2). On any bar where more entry signals fire than open slots, the ranker decides which ones get taken. In an unlimited backtest every signal is taken, so the ranker is unused.

> **Discover available rankers first** — `GET /api/backtest/rankers` (see [Discovery](#discovery)) returns the live list of ranker names. Don't hard-code ranker names; the list can change.

**Defaulting:** if `ranker` is omitted, the strategy's preferred ranker is used (each `EntryStrategy` can declare one). Specify `ranker` only when overriding that default.

**Specifying a ranker:**

```jsonc
{
  "ranker": "<ranker-name>",          // from /api/backtest/rankers
  "rankerConfig": {                    // optional, ranker-specific
    "sectorRanking": ["XLK", "XLF", "..."]
  }
}
```

The exact `rankerConfig` shape depends on the ranker. Two patterns:

| Ranker family | Config requirement |
|---------------|---------------------|
| Sector-priority (ranks by user-supplied sector order) | Requires `rankerConfig.sectorRanking` — array of sector symbols (XLK, XLF, …) in priority order |
| Score-based (volatility, momentum, EMA-distance, etc.) | No config — ranks by an intrinsic per-stock metric |
| Random | No config — non-deterministic unless `randomSeed` is also set on the backtest request |

The discovery endpoint returns ranker **names only** — no parameter metadata, so the family of an unfamiliar ranker isn't programmatically discoverable. Ask the user, or treat it as score-based (no config) and check the response for missing-config errors. Tracked in [Known limitations](#known-limitations) for backend follow-up.

**Reproducibility:** any ranker that uses random tie-breaking (`Random`, or score-based with ties) needs `randomSeed` for reproducible results. Tie-break order can swing edge by 0.5%+ on tight position-limited runs — pair with [§6 Multi-seed sanity check](#6-multi-seed-sanity-check) when the ranker is non-deterministic.

### 4. Custom DSL strategy

Inline `entryStrategy`/`exitStrategy` with conditions. Conditions need parameters nested in a `parameters` object.

> **Discover available conditions first** — `GET /api/backtest/conditions` (see [Discovery](#discovery)) returns every condition type, its parameters with types/defaults, and which side (entry/exit) it applies to. Don't guess condition names or parameter shapes.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/backtest \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "entryStrategy": {
      "type": "custom",
      "conditions": [
        {"type": "<conditionA>"},
        {"type": "<conditionB>", "parameters": {"<param>": <value>}}
      ]
    },
    "exitStrategy": {"type": "predefined", "name": "<exit>"},
    "startDate": "<YYYY-MM-DD>",
    "endDate":   "<YYYY-MM-DD>"
  }' > /tmp/backtest-custom.json
```

Common pitfall: nesting parameters at the top level instead of under `"parameters"` silently ignores them.

### 5. Targeted symbol subset

Use `stockSymbols` for debugging, watchlist runs, or fast iteration. Pair with a tight date range for sub-30s turnaround.

```json
{
  "stockSymbols": ["<SYM1>", "<SYM2>", "<SYM3>"],
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>",
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"}
}
```

### 6. Multi-seed sanity check

Run scenario 2 with 3+ different `randomSeed` values. If edge std-dev across seeds is large, the strategy depends on tie-break order — flag it.

```bash
for seed in 1 2 3; do
  curl -s -X POST http://localhost:9080/udgaard/api/backtest \
    -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
    -d "{... ,\"randomSeed\": $seed}" > /tmp/backtest-seed-$seed.json
done
```

## How to run

- **Endpoint:** `POST /api/backtest` on PRD port `9080` with `X-API-Key` header
- **One backtest at a time** — backend OOMs with concurrent backtests
- **Save raw response to `/tmp/backtest-<id>.json`** — analyst agent reads from disk
- **Position-sized 10y+ runs can take 10–15 minutes** — set timeout accordingly

After the request returns, capture `backtestId` from the response (used by `/monte-carlo`).

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

## Agent delegation

After the API call returns, spawn `post-backtest-analyst` with the path to the saved JSON. The agent computes:

- Drawdown duration analysis (top 10 episodes: peak/trough/recovery dates, decline + recovery days)
- Risk-adjusted metrics: Sharpe, Sortino, CAGR, Calmar
- SPY correlation, beta, annualized alpha
- Verdict + next-step recommendation

The skill itself does API orchestration + raw report assembly. Statistical interpretation is the agent's job.

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
- **`GET /api/backtest/rankers` returns names only — no parameter metadata.** Conditions discovery returns full `ConditionMetadata` (type, displayName, parameters with types/defaults, category); rankers should follow the same shape (`RankerMetadata` with `parameters` describing `rankerConfig` requirements). Until then the skill has to ask the user about unfamiliar rankers.
- **SPY correlation** requires SPY in the symbol set or a separate fetch — the analyst handles the fetch as a workaround.
- **Daily bars only** — no intraday slippage modelling. Edge < 1.5% likely doesn't survive live costs.
- **Survivorship bias** — universe currently excludes most delisted-during-period stocks (V18 mitigates but doesn't eliminate).
- **Assumes perfect fills at close** — `entryDelayDays: 1` partially mitigates.

## Critical warnings

- **Multiple-testing**: every parameter retried inflates false-positive rate. If the user has tried > 5 variations, push for `/walk-forward` before further tuning.
- **< 30 trades**: results are noise. Refuse to draw conclusions; widen the date range or universe.
- **Don't reject on win rate alone** — see decision-framework note above.
