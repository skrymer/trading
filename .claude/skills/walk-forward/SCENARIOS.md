# Walk-Forward Scenarios

Request templates and configuration choices that build on the Quick start in [SKILL.md](SKILL.md). Replace `<entry>` / `<exit>` / sizer params / ranker / dates with what the user provides.

All scenarios POST through `.claude/scripts/udgaard-post.sh` (auth + error handling). See [SKILL.md](SKILL.md) for the approval-gate rules, the one-at-a-time constraint, and the wall-time expectation.

## Window sizing constraint

Default `inSampleYears: 5, outOfSampleYears: 1, stepYears: 1` requires at least **6 years** of data (`endDate − startDate ≥ inSamplePeriod + outOfSamplePeriod`) to fit even one window. On shorter spans, the API returns 0 windows and an empty result.

For shorter periods, override with months:

```jsonc
{"inSampleMonths": 24, "outOfSampleMonths": 12, "stepMonths": 12}   // fits a 3-year span (1 window) up to 5-year (2 windows)
{"inSampleMonths": 18, "outOfSampleMonths": 6,  "stepMonths": 6}    // tighter — fits a 2-year span
```

Rule of thumb: pick `inSamplePeriod` ≥ 12 months (need enough trades to estimate IS edge), `outOfSamplePeriod` long enough to expect ≥ 30 OOS trades on the chosen universe. Step ≤ OOS period for non-overlapping OOS coverage; step < OOS period gives more windows but reuses OOS data across windows.

## 1. Default cadence (5y IS / 1y OOS / 1y step)

The standard validation cadence. With 10+ years of data this gives ~5+ rolling windows — enough to spot trends in OOS degradation.

```bash
.claude/scripts/udgaard-post.sh /api/backtest/walk-forward '{
  "assetTypes": ["STOCK"],
  "useUnderlyingAssets": false,
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"},
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>",
  "inSampleYears": 5,
  "outOfSampleYears": 1,
  "stepYears": 1
}' /tmp/walk-forward.json
```

## 2. Custom cadence (months)

For shorter histories, faster-moving regimes, or finer-grained windows. Months override years if both are set.

```bash
.claude/scripts/udgaard-post.sh /api/backtest/walk-forward '{
  "assetTypes": ["STOCK"],
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"},
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>",
  "inSampleMonths": <N>,
  "outOfSampleMonths": <N>,
  "stepMonths": <N>
}' /tmp/walk-forward.json
```

Tighter step (e.g. 3 months) gives more windows but more overlapping IS data — windows are no longer fully independent.

## 3. Position-sized walk-forward

Forwards `positionSizing` to each IS/OOS backtest so OOS results reflect realistic capital constraints (not just statistical edge). See [§4 Ranking](#4-ranking) for which signals get taken when more fire than `maxPositions` allows.

```bash
.claude/scripts/udgaard-post.sh /api/backtest/walk-forward '{
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
}' /tmp/walk-forward-sized.json
```

See `/backtest` SCENARIOS.md §2 for sizer options. Position-sized walk-forward takes proportionally longer than unsized.

## 4. Ranking

Ranking only matters when `maxPositions` is set (scenario 3). The `ranker` + `rankerConfig` you supply is applied **independently in each IS and OOS sub-backtest** — same ranker drives both halves of every window.

> **Discover available rankers first** — `GET /api/backtest/rankers` (see [SKILL.md → Discovery](SKILL.md#discovery)) returns the live list of `RankerMetadata` (type, displayName, description, parameters, category, usesRandomTieBreaks). Don't hard-code; the list can change.

**Defaulting:** if `ranker` is omitted, the strategy's preferred ranker is used (each `EntryStrategy` can declare one). Specify only when overriding that default.

**Specifying a ranker:**

```jsonc
{
  "ranker": "<ranker-type>",          // from /api/backtest/rankers (the .type field)
  "rankerConfig": {                    // required only if the ranker has parameters
    "sectorRanking": ["XLK", "XLF", "..."]
  }
}
```

| `category` | Meaning |
|---|---|
| `Sector-Priority` | `parameters` includes `sectorRanking` (a `stringList` — supply a non-empty ordered array of sector symbols) |
| `Score-Based` | Ranks by an intrinsic per-stock metric. `parameters` is empty unless the ranker exposes tunables. |
| `Random` | No parameters; non-deterministic unless `randomSeed` is set |

Programmatic decision: empty `parameters` array → omit `rankerConfig`. A parameter with `defaultValue: null` is required.

**Walk-forward-specific note:** the response also exposes `derivedSectorRanking` per window — that's the IS-optimal sector order for that window, **informational only**, not applied to the OOS sub-backtest. Use it as an overfitting tell (does the IS-optimal ranking churn across windows?), not as evidence the strategy uses IS sector data live.

**Reproducibility:** rankers with `usesRandomTieBreaks: true` need `randomSeed` for reproducible WFE. Pair with [§5 Multi-seed walk-forward](#5-multi-seed-walk-forward).

## 5. Multi-seed walk-forward

Run scenario 1 or 3 with 3+ different `randomSeed` values when the ranker is non-deterministic. Report std-dev of aggregate WFE across seeds — a high spread means the OOS verdict depends on tie-break order.

```bash
for seed in 1 2 3; do
  .claude/scripts/udgaard-post.sh /api/backtest/walk-forward \
    "{... ,\"randomSeed\": $seed}" \
    /tmp/walk-forward-seed-$seed.json
done
```
