---
name: walk-forward
description: Run walk-forward validation against the Udgaard API and delegate WFE interpretation + per-window stability analysis to the walk-forward-analyst sub-agent. Use to test whether a strategy's edge persists out-of-sample before live trading.
argument-hint: "[strategy-name] [cadence]"
---

# Walk-Forward Validation

Answers one question: **"Does this degrade smoothly out-of-sample, or is it regime-dependent?"**

Validates that strategy edge persists on unseen data and isn't curve-fit. Returns Walk-Forward Efficiency (WFE = aggregate OOS edge / aggregate IS edge) + per-window IS/OOS breakdown. For initial edge measurement use `/backtest`. For path/edge confidence use `/monte-carlo`.

This skill is strategy-neutral. Substitute the user's actual strategy/exit/dates in every example.

## Discovery

Same endpoints as `/backtest` — list available strategies / conditions / rankers before running:

```bash
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/strategies
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/conditions
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/rankers
```

## Scenarios

### Choosing a scenario

| Question being asked | Use |
|----------------------|-----|
| Do my entry/exit conditions still produce per-trade edge OOS? | §1 / §2 (unsized — every signal taken, max statistical power) |
| Does my full system (entry + exit + ranker + sizer) survive OOS? | §3 (position-sized) |
| Strategy uses an IS-tunable ranker (e.g. SectorEdge sector-priority)? | §3 — the ranker is the most overfittable part and must be stress-tested OOS |
| Reproducibility of a non-deterministic ranker? | §5 (multi-seed) on top of §1 or §3 |

Unsized walk-forward is faster and tighter on edge CIs. Sized is what you want before live trading. They answer different questions — pick deliberately.

### Window sizing constraint

Default `inSampleYears: 5, outOfSampleYears: 1, stepYears: 1` requires at least **6 years** of data (`endDate − startDate ≥ inSamplePeriod + outOfSamplePeriod`) to fit even one window. On shorter spans, the API returns 0 windows and an empty result.

For shorter periods, override with months:

```jsonc
{"inSampleMonths": 24, "outOfSampleMonths": 12, "stepMonths": 12}   // fits a 3-year span (1 window) up to 5-year (2 windows)
{"inSampleMonths": 18, "outOfSampleMonths": 6,  "stepMonths": 6}    // tighter — fits a 2-year span
```

Rule of thumb: pick `inSamplePeriod` ≥ 12 months (need enough trades to estimate IS edge), `outOfSamplePeriod` long enough to expect ≥ 30 OOS trades on the chosen universe. Step ≤ OOS period for non-overlapping OOS coverage; step < OOS period gives more windows but reuses OOS data across windows.

### 1. Default cadence (5y IS / 1y OOS / 1y step)

The standard validation cadence. With 10+ years of data this gives ~5+ rolling windows — enough to spot trends in OOS degradation.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/backtest/walk-forward \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "<entry>"},
    "exitStrategy":  {"type": "predefined", "name": "<exit>"},
    "startDate": "<YYYY-MM-DD>",
    "endDate":   "<YYYY-MM-DD>",
    "inSampleYears": 5,
    "outOfSampleYears": 1,
    "stepYears": 1
  }' > /tmp/walk-forward.json
```

### 2. Custom cadence (months)

For shorter histories, faster-moving regimes, or finer-grained windows. Months override years if both are set.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/backtest/walk-forward \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "entryStrategy": {"type": "predefined", "name": "<entry>"},
    "exitStrategy":  {"type": "predefined", "name": "<exit>"},
    "startDate": "<YYYY-MM-DD>",
    "endDate":   "<YYYY-MM-DD>",
    "inSampleMonths": <N>,
    "outOfSampleMonths": <N>,
    "stepMonths": <N>
  }' > /tmp/walk-forward.json
```

Tighter step (e.g. 3 months) gives more windows but more overlapping IS data — windows are no longer fully independent.

### 3. Position-sized walk-forward

Forwards `positionSizing` to each IS/OOS backtest so OOS results reflect realistic capital constraints (not just statistical edge). See [§4 Ranking](#4-ranking) for which signals get taken when more fire than `maxPositions` allows.

```bash
curl -s -X POST http://localhost:9080/udgaard/api/backtest/walk-forward \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "entryStrategy": {"type": "predefined", "name": "<entry>"},
    "exitStrategy":  {"type": "predefined", "name": "<exit>"},
    "startDate": "<YYYY-MM-DD>",
    "endDate":   "<YYYY-MM-DD>",
    "inSampleYears": 5,
    "outOfSampleYears": 1,
    "stepYears": 1,
    "maxPositions": <N>,
    "entryDelayDays": 1,
    "ranker": "<ranker>",
    "rankerConfig": <RANKER_CONFIG_OR_OMIT>,
    "positionSizing": {
      "startingCapital": <dollars>,
      "sizer": <SIZER>,
      "leverageRatio": 1.0
    }
  }' > /tmp/walk-forward-sized.json
```

See `/backtest` §2 for sizer options. Position-sized walk-forward takes proportionally longer than unsized.

### 4. Ranking

Ranking only matters when `maxPositions` is set (scenario 3). The `ranker` + `rankerConfig` you supply is applied **independently in each IS and OOS sub-backtest** — same ranker drives both halves of every window.

> **Discover available rankers first** — `GET /api/backtest/rankers` (see [Discovery](#discovery)) returns ranker names. Don't hard-code; the list can change.

**Defaulting:** if `ranker` is omitted, the strategy's preferred ranker is used (each `EntryStrategy` can declare one). Specify only when overriding that default.

**Specifying a ranker:**

```jsonc
{
  "ranker": "<ranker-name>",          // from /api/backtest/rankers
  "rankerConfig": {                    // optional, ranker-specific
    "sectorRanking": ["XLK", "XLF", "..."]
  }
}
```

| Ranker family | Config requirement |
|---------------|---------------------|
| Sector-priority (ranks by user-supplied sector order) | Requires `rankerConfig.sectorRanking` — array of sector symbols in priority order |
| Score-based (volatility, momentum, EMA-distance, etc.) | No config — ranks by an intrinsic per-stock metric |
| Random | No config — non-deterministic unless `randomSeed` is also set |

The discovery endpoint returns names only — ask the user when an unfamiliar ranker appears. Tracked in [Known limitations](#known-limitations) for backend follow-up.

**Walk-forward-specific note:** the response also exposes `derivedSectorRanking` per window — that's the IS-optimal sector order for that window, **informational only**, not applied to the OOS sub-backtest. Use it as an overfitting tell (does the IS-optimal ranking churn across windows?), not as evidence the strategy uses IS sector data live.

**Reproducibility:** non-deterministic rankers (`Random`, score-based with ties) need `randomSeed` for reproducible WFE. Pair with [§5 Multi-seed walk-forward](#5-multi-seed-walk-forward).

### 5. Multi-seed walk-forward

Run scenario 1 or 3 with 3+ different `randomSeed` values when the ranker is non-deterministic. Report std-dev of aggregate WFE across seeds — a high spread means the OOS verdict depends on tie-break order.

```bash
for seed in 1 2 3; do
  curl -s -X POST http://localhost:9080/udgaard/api/backtest/walk-forward \
    -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
    -d "{... ,\"randomSeed\": $seed}" > /tmp/walk-forward-seed-$seed.json
done
```

## How to run

- **Endpoint:** `POST /api/backtest/walk-forward` on PRD port `9080` with `X-API-Key` header
- **One run at a time** — same OOM concern as `/backtest`
- **Save raw response to `/tmp/walk-forward[-suffix].json`** — analyst agent reads from disk
- **Wall time:** roughly `(windows × IS-backtest time) + (windows × OOS-backtest time)`. A 10y/5y-IS/1y-OOS run is ~10 backtests. Plan for 30+ minutes on broad universes.

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

## Agent delegation

After the API call returns, spawn `walk-forward-analyst` with the path to the saved JSON. The agent:

- Computes per-window WFE (= OOS edge / IS edge)
- Reports OOS-positive window count, std-dev of OOS edge across windows
- Highlights worst and best windows + which regime they fell in (best-effort)
- Flags overfitting (OOS << IS, or aggregate WFE < 0.30)
- Comments on `derivedSectorRanking` stability — does the IS-optimal sector order shift across windows?
- Produces verdict + next-step recommendation

The skill itself does API orchestration + per-window table assembly. Statistical interpretation is the agent's job.

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
- **`GET /api/backtest/rankers` returns names only — no parameter metadata.** Same gap as `/backtest`: until rankers gain `RankerMetadata` (mirroring `ConditionMetadata`), unfamiliar rankers' `rankerConfig` shape isn't programmatically discoverable.
- **Small window counts.** With 10y of data and default 5y IS / 1y OOS / 1y step, you get ~5–6 windows. Aggregate metrics are trade-weighted, so windows with more trades dominate. Per-window WFEs can be wildly dispersed (0 to 1.5+) on small samples.
- **Walk-forward tests parameter durability, not optimization procedure.** Running walk-forward with fixed parameters validates that those parameters survive OOS. It does NOT validate that re-optimizing on each IS window would survive — that requires a different harness.
- Same daily-bar / no-slippage / survivorship-bias caveats as `/backtest` apply.

## Critical warnings

- **Walk-forward is the antidote to multiple-testing.** If the user has tried > 5 parameter variations on `/backtest`, walk-forward is mandatory before any live-trade decision.
- **Don't tune on OOS.** If walk-forward shows weakness and the user changes parameters, the new run is no longer truly out-of-sample for the same data — it's been peeked at. Acknowledge this in the report.
- **WFE alone is thin.** Always cross-reference with OOS-positive window count and OOS edge std-dev. Aggregate WFE 0.6 from "every window 0.6" vs "half 1.2, half 0.0" are completely different decisions.
