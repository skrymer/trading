# Condition Screen — Reference

Background, statistics reference, ARS-detection patterns, the script-sweep convention, and abandon criteria. The quant signed off on the statistics design (2026-05-29, 11th consultation); the flag thresholds below are **defaults, explicitly uncalibrated** until a corpus of known-distinct / known-duplicate conditions exists.

## Why the screen exists

`pullback2of3` exhibited **Aliased Regime Sensitivity (ARS)** — firing rate stable across `lookbackDays ∈ {8,9,10,11}` but mean forward-return lift sign-flipping non-monotonically across those neighbours — and it was only caught *after* a full firewall run. The screen surfaces that fingerprint at design time. Per quant: *"This is exactly the loop you didn't have when designing pullback2of3."*

## The statistics (raw; the backend emits no flags)

### 1. Forward-return distribution + lift (the headline)

Per horizon N ∈ {5,10,20}, **entry-anchored**: `close[fill+N] / close[fill] − 1`, where `fill = signal bar + entryDelayDays`. Measuring from the signal bar instead would capture the signal→fill gap the strategy never earns — look-ahead that flatters momentum/breakout conditions.

- **`condition`** and **`universe`** each report `mean`, `median`, `std`, `skew`, `hitRate`, `clusteredMean`, `clusteredStdError`, `signalCount`, `dateCount`, `droppedCount`.
- **`meanLift` = condition.clusteredMean − universe.clusteredMean**; **`hitRateLift`** likewise. **Lift is the alpha signal.** Absolute forward return is uninformative for a high-firing condition because it converges to the universe base rate.
- **`signalToFillGap`** is reported separately. A condition whose edge lives entirely in the gap is untradeable by construction.

**Date-clustering is mandatory.** Same-day signals across symbols are ~1 observation, not N. `clusteredMean` averages per-date means first, then across dates; `clusteredStdError` is the sample std of per-date means / √(dateCount); **`dateCount` is the honest effective sample size.** Read `signalCount` vs `dateCount` — a large ratio means the edge is concentrated in few days.

### 2. Firing rate per year

`firing.byYear[]` = `signals / eligibleBars` per calendar year. Catches regime-conditional firing collapses (50/yr 2017–2019 → 5/yr 2020–2021).

**Selectivity flags (analyst-applied):** overall firing rate **≥ 33% → amber** ("absolute stats ≈ universe, read lift only"); **≥ 60% → red** ("universe filter, not a signal"). Never auto-reject — a low-selectivity condition can still be one valid clause in an AND-stack.

### 3. Parameter sensitivity sweep (ARS detection)

`parameterSweep[]` — one entry per swept tunable, each with `cells[]` at the centre value and its neighbours. Each cell carries `firingRate`, `liftByHorizon`, `clusteredStdErrorByHorizon`, `relativeStep`.

- **Registered conditions:** every numeric tunable is auto-swept (one-at-a-time). Discrete params (an `options` set) step to **adjacent allowed values**; continuous params sweep **±10%**. Capped at 8 tunables (overflow noted).
- **Script conditions:** declare `scriptSweeps` (below) — the backend cannot see constants inside Kotlin source.

**ARS auto-flag (analyst):** for a tunable, at a horizon, flag ARS when **all three** hold:
1. `meanLift` **sign-flips** across an adjacent cell pair, **and**
2. the swing `|lift(P+1) − lift(P−1)|` **> 2 × the centre cell's `clusteredStdError`**, **and**
3. firing rate stays within **±15% relative** across the three cells (stable support).

Down-weight the flag when `relativeStep > 0.5` (a coarse discrete grid — a large jump producing a sign-flip is weak evidence). Economic magnitude of lift stays your judgment — the 2×-SE test self-calibrates to the condition's own noise.

Secondary (non-ARS) tells worth printing: **monotone-but-steep** (`|lift(P+1)−lift(P−1)| / |lift(P)| > 1.0`), **support cliff** (firing rate moves > ±25% relative for one step).

### 4. SPY-regime breakdown

`spyRegime[]` per horizon, with `down` / `flat` / `up` buckets (empirical tertiles of SPY 20-day return over the window). Each bucket has `firingRate` and **`meanLift`**. Firing rate by regime shows *when* it fires; **lift by regime shows whether the edge flips sign with the market regime** — a disguised regime detector. Lift positive in up-tertile and negative in down-tertile (or vice-versa) is the cross-sectional ARS signature; it independently corroborates the §3 parameter-sweep finding.

### 5. Jaccard overlap

`jaccard[]` per `referenceConditions` entry: per-(symbol,date) firing-set similarity, `byYear` + `pooled`. Empty reference set → omitted, with a note (N/A — not 0.0).

**Advisory bands (analyst, uncalibrated):** max per-year Jaccard **> 0.5** → "likely substantial redundancy"; **> 0.7** → "almost certainly a near-clone, justify why it's not." These flag wasted compute (don't firewall a clone) and hidden correlation (don't stack two near-identical clauses thinking you've diversified) — not rejection. High overlap with a *good* condition can be a fine refinement.

## Tradable-universe gate (default ON)

By default the screen measures over the **tradable universe** (ADR 0026): both the condition firings and the all-bars baseline are restricted to bars that, point-in-time, clear close ≥ $5, trailing-20-bar median dollar-volume ≥ $1M, and ≥ 252 bars of history. So the lift you read is the lift over the realistically-fillable opportunity set, not the full data set — under-aged early-window bars and penny/thin names are excluded from both numerator and baseline. The history needed for the age gate is warmup-loaded before `startDate`, so a long-lived name is judged on its real history. Pass `applyLiquidityFilter: false` only to reproduce the pre-#173 full-universe screen.

## Library sanity-sweep mode (reduced universe)

**The default universe is the full `STOCK` set (~3,900 symbols), and that is correct for a normal single-condition design-time screen — it's cheap and faithful. Do not reach for the reduced universe for those.**

There is one separate, rarer use: a **whole-library sanity sweep** — screening *every* registered entry condition at once to catch any structurally-unsound one before it gets used. On the full universe this is intractable: most conditions take 1–5 min each, and a few are **non-terminating under the auto-sweep** (one order-block condition ran ~20 h at 100 % CPU and never returned). For that sweep only, use the frozen reduced universe.

**Frozen universe:** `sanity-universe/sanity-universe-v1.json` (quant-approved 2026-05-31; regenerate with `sanity-universe/build-sanity-universe.py` from the committed `symbol-stats.tsv` snapshot — same seed ⇒ identical list).
- `main` — 300 symbols: sector-proportional, 50/30/20 large/mid/small by avg dollar-volume, price-diverse, **~18 % delisted-in-window** (survivorship mitigation — the most important deviation from a naive large-cap list). Use for all non-order-block conditions.
- `orderBlock` — 50-symbol stratified subset. Use for the order-block conditions (they iterate `stock.orderBlocks` per bar and are far heavier).

Pass the symbol list via the request's `symbols` field:

```bash
SYMS=$(python3 -c "import json;print(json.dumps([r['symbol'] for r in json.load(open('.claude/skills/condition-screen/sanity-universe/sanity-universe-v1.json'))['main']]))")
.claude/scripts/udgaard-post.sh /api/conditions/screen "{\"conditions\":[{\"type\":\"<type>\",\"parameters\":{...}}],\"operator\":\"AND\",\"symbols\":$SYMS}" /tmp/condition-screen-<type>.json
```

Run conditions **sequentially** (concurrent screens OOM the backend), and put a hard client timeout on each (`curl --max-time`); a timed-out screen leaves an orphaned CPU-pegged server thread — restart udgaard to reclaim it.

**Mandatory output notice (verbatim-equivalent), lead every sanity-sweep report with it:**

> **REDUCED-UNIVERSE SANITY SWEEP — NOT A FULL-UNIVERSE RESULT.** Run on the frozen 300-symbol `condition-screen-sanity-universe-v1`, not the production universe. The reduced universe **widens the date-clustered SEs and is anti-conservative for the ARS 2×SE test** — a fragile condition is *more* likely to pass here. A "clean" result is **not validation**; it only means the condition was not rejected by a coarse net. Re-screen any survivor on the full universe (or run the full-universe firewall) before any tradability claim. Not edge estimates.

**Two binding guardrails for this mode:**
- **`dateCount` (nDates) < ~30** in a sweep cell or regime bucket (or a median of 1–2 firings/date) ⇒ **INCONCLUSIVE, not clean** — the clustered estimate is too thin to read a sign-flip from. Sparse conditions on 300 symbols hit this; say so rather than reading a flag from noise.
- **This mode may only *reject* or *flag-for-full-rescreen*** — never promote a condition. A clean sanity result must never substitute for the full-universe screen in any later go/no-go.

**Known coverage gaps (perf cliff, not condition faults):** `marketBreadthIncreasing`, `sectorBreadthIncreasing`, and `aboveBearishOrderBlock` are non-terminating under the auto-sweep even on the reduced universe — the two breadth conditions' cost does **not** scale with the equity universe (breadth is a market-wide series), so shrinking `symbols` cannot rescue them. Treat as unscreened; order-block parameter-robustness is acceptably **deferred to the firewall G13 gate** (quant-authorized). See `project_condition_screen_perf_cliff` memory.

## Statistics deliberately excluded

Win-rate-at-fixed-exit (exit-choice-sensitive), profit factor (meaningless without capital allocation), "Sharpe of forward returns" (no portfolio; fat-tailed clustered data), and **any pass/fail verdict**. Do not add them back.

## Script-sweep convention (`{{param}}`)

A tunable baked into a `script` body can't be auto-swept — the backend can't parse Kotlin to find it. Make it sweepable:

1. Replace the constant with a placeholder `{{name}}` in the script.
2. Declare a `scriptSweeps` entry `{ "name": "...", "center": <c>, "step": <s> }`.

The backend substitutes `c − s`, `c`, `c + s`, recompiles each, and emits the three ARS cells. The base run uses the centre value, so the script must compile at every swept value.

### Worked example — what would have caught `pullback2of3`

```bash
.claude/scripts/udgaard-post.sh /api/conditions/screen '{
  "conditions": [{
    "type": "script",
    "parameters": {
      "script": "(0 until {{lookbackDays}}).count { i -> quote(stock, quote.date, -i)?.let { it.closePrice < it.openPrice } == true } >= 2"
    }
  }],
  "scriptSweeps": [{ "name": "lookbackDays", "center": 10, "step": 1 }]
}' /tmp/condition-screen-pullback2of3.json
```

This sweeps `lookbackDays` = 9, 10, 11 with firing rate held roughly constant. If `meanLift` sign-flips across 9→10→11 while firing stays within ±15% — **ARS. Abandon the condition; do not ship the value that happened to pass.** (Pseudo-script; substitute the real predicate.)

## When to abandon a condition

- **ARS flagged** (§3) — the parameter dimension is structurally wrong for the alpha hypothesis. Abandon; a regime filter won't rescue it.
- **Firing collapse** — fires only in 1–2 years/regimes; the "edge" is a regime-timing artefact, not alpha.
- **High Jaccard** with an existing good condition — it's a noisy clone; don't spend a firewall run on it.
- **Lift ≈ 0 within `clusteredStdError`** at all horizons on a low-firing condition — no detectable edge.
- **Regime-sign-flip lift** (§4) — edge is regime-conditional; structurally different premise required.

Anything clean here is still only a candidate. Wire it in and run `/strategy-screen` → `/validate-candidate`.
