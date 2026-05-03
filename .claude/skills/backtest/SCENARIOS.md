# Backtest Scenarios

Request templates for each scenario index entry in [SKILL.md](SKILL.md). Replace `<entry>` / `<exit>` / sizer params / ranker / dates with what the user provides.

All scenarios POST through `.claude/scripts/udgaard-post.sh` (auth + error handling). See [SKILL.md](SKILL.md) for the approval-gate rules, the one-at-a-time constraint, and the `/tmp/...` save convention.

## 1. Quick edge check (unlimited)

Statistical edge measurement — every signal taken, no position cap, no sizing. Use to sanity-check a freshly registered strategy or run an ablation.

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
  "assetTypes": ["STOCK"],
  "useUnderlyingAssets": false,
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"},
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>"
}' /tmp/backtest-unlimited.json
```

## 2. Position-sized realism (live-trade decision)

The real go/no-go run. Required before recommending live trading. See [§3 Ranking](#3-ranking) for picking which signals to take when more fire than `maxPositions` allows.

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
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
}' /tmp/backtest-sized.json
```

`<SIZER>` is one of:

| Sizer | Shape | Use when |
|-------|-------|----------|
| `{"type": "atrRisk", "riskPercentage": <r>, "nAtr": <n>}` | Equal $ risk per trade scaled by ATR | Strategy where stop distance varies per name |
| `{"type": "percentEquity", "percent": <p>}` | Equal-notional, ignores ATR | Strategy with similar volatility across names |
| `{"type": "kelly", "winRate": <W>, "winLossRatio": <R>, "fractionMultiplier": 0.25}` | Kelly criterion (quarter-Kelly recommended) | High-confidence W/R estimates, uncorrelated trades |
| `{"type": "volTarget", "targetVolPct": <v>, "kAtr": <k>}` | Equal vol contribution | Multi-asset where vol parity matters |

Always include `leverageRatio: 1.0` for stocks unless the user explicitly enables leverage. Without it, ATR-based sizers can produce extreme leverage on low-ATR names. Optional `drawdownScaling` overlay reduces risk in deeper drawdowns — see the project README or ask the user if relevant.

## 3. Ranking

Ranking only matters when `maxPositions` is set (scenario 2). On any bar where more entry signals fire than open slots, the ranker decides which ones get taken. In an unlimited backtest every signal is taken, so the ranker is unused.

> **Discover available rankers first** — `GET /api/backtest/rankers` (see [SKILL.md → Discovery](SKILL.md#discovery)) returns the live list of `RankerMetadata` (type, displayName, description, parameters, category, usesRandomTieBreaks). Don't hard-code ranker names; the list can change.

**Defaulting:** if `ranker` is omitted, the strategy's preferred ranker is used (each `EntryStrategy` can declare one). Specify `ranker` only when overriding that default.

**Request shape — two separate top-level fields, NOT a nested object:**

```jsonc
{
  "ranker": "<ranker-type>",          // top-level STRING — the .type field from /api/backtest/rankers
  "rankerConfig": {                    // top-level OBJECT — omit entirely when the ranker has no parameters
    "sectorRanking": ["XLK", "XLF", "..."]
  }
}
```

> ⚠ **Common pitfall:** `ranker` is a string, not an object. `"ranker": {"type": "SectorEdge", "sectorRanking": [...]}` will fail with `Cannot deserialize value of type java.lang.String from Object value`. The `/api/backtest/rankers` response is *metadata* (showing each ranker's `type` + `parameters`); the request format splits those across two fields.

**Examples by category:**

```jsonc
// Score-Based ranker with no parameters — omit rankerConfig entirely
{ "ranker": "Volatility" }

// Sector-Priority ranker — rankerConfig required, key matches parameters[].name
{ "ranker": "SectorEdge", "rankerConfig": { "sectorRanking": ["XLK", "XLF"] } }

// Random — pair with randomSeed for reproducibility
{ "ranker": "Random", "randomSeed": 42 }
```

The `rankerConfig` shape is determined by the ranker's `parameters` array — one config key per `parameters[].name`. Family is given by the `category` field:

| `category` | Meaning |
|---|---|
| `Sector-Priority` | `parameters` includes `sectorRanking` (a `stringList` — supply a non-empty ordered array of sector symbols, highest priority first) |
| `Score-Based` | Ranks by an intrinsic per-stock metric. `parameters` is empty unless the ranker exposes tunables. |
| `Random` | No parameters; non-deterministic unless `randomSeed` is set on the backtest request |

Programmatic decision: if a ranker's `parameters` array is empty, omit `rankerConfig`. If a parameter has `defaultValue: null`, it is required and you must supply a value.

**Reproducibility:** any ranker with `usesRandomTieBreaks: true` needs `randomSeed` for reproducible results. Tie-break order can swing edge by 0.5%+ on tight position-limited runs — pair with [§6 Multi-seed sanity check](#6-multi-seed-sanity-check) when the ranker is non-deterministic.

## 4. Custom DSL strategy

Inline `entryStrategy`/`exitStrategy` with conditions. Conditions need parameters nested in a `parameters` object.

> **Discover available conditions first** — `GET /api/backtest/conditions` (see [SKILL.md → Discovery](SKILL.md#discovery)) returns every condition type, its parameters with types/defaults, and which side (entry/exit) it applies to. Don't guess condition names or parameter shapes.

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
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
}' /tmp/backtest-custom.json
```

Common pitfall: nesting parameters at the top level instead of under `"parameters"` silently ignores them.

## 5. Targeted symbol subset

Use `stockSymbols` for debugging, watchlist runs, or fast iteration. Pair with a tight date range for sub-30s turnaround.

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
  "stockSymbols": ["<SYM1>", "<SYM2>", "<SYM3>"],
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>",
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"}
}' /tmp/backtest-subset.json
```

## 6. Multi-seed sanity check

Run scenario 2 with 3+ different `randomSeed` values. If edge std-dev across seeds is large, the strategy depends on tie-break order — flag it.

```bash
for seed in 1 2 3; do
  .claude/scripts/udgaard-post.sh /api/backtest \
    "{... ,\"randomSeed\": $seed}" \
    /tmp/backtest-seed-$seed.json
done
```
