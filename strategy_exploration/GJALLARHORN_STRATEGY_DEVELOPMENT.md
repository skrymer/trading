# Gjallarhorn — Breadth-Thrust Exhaustion-Reversal strategy development

_Created: 2026-06-04 · Status: **SPEC — quant-signed; awaiting operator go on build+screen**._
_Next candidate of the single-strategy search (`STRATEGY_LEDGER.md` §C.2). Spec routed to and signed by `quant-analyst`._

## Premise

**Breadth-thrust EXHAUSTION-REVERSAL** — a crisis-bottom long re-entry timer. Deploy long *after a
market-breadth washout reverses* (the violent post-capitulation snapback). Index-level breadth-state
mean-reversion *at the bottom* — it fires when there is NO uptrend yet (no uptrend required). Anchor:
Zweig Breadth Thrust + the capitulation-reversal literature.

**Strategic role:** fills the **crisis/transition regime gap** every other long family in this project
stands aside in or dies in (breakout, mean-reversion-on-pullback, RS-momentum rotation are all
deprecated and all go to cash in crisis). Gjallarhorn is a *bottom-timer*, not a narrow-leadership
participant. Passed the **data-span check** (runs on market breadth, trustworthy 2000-2025).

**Distinctness vs the four deprecated classes (§B):** not return-CHANGE (momentum/breakout/MR) — it is
an index-level breadth-*state* reversal event; not a stock-picker (Random ranker — see §3); fires
*pre-uptrend* so it carries no trend/breakout-follow-through death surface.

## The pivotal engine finding (drives the whole design)

The two natural premise conditions **cannot co-fire on the same bar by construction:**
- `marketBreadthNearDonchianLow` fires when breadth is **pinned at the Donchian floor** (below its EMA10).
- `marketBreadthRecovering` fires when breadth has **already recrossed above its EMA10** — by which point
  it has risen materially off the floor and is no longer in the bottom 10% of the channel.

`NearDonchianLow AND Recovering` on one bar ≈ the empty set. Loosening the percentile to 0.30-0.40 does
**not** fix this — it dilutes "washout extreme" into "mildly low + crossed EMA," which is premise drift,
not a fix. The current condition set is memoryless (except `marketBreadthIncreasing`, which encodes
monotone-rising, not "was-low-recently"). **→ a NEW first-class memory condition is required.**

## The new artifact to build (TDD, first-class — `/create-condition`)

**Name (mechanic-describing, strategy-neutral, no "Gjallarhorn"):** `marketBreadthWashoutWithin`

| Aspect | Spec |
|---|---|
| Type id | `marketBreadthWashoutWithin` |
| Mechanic | True on `quote.date` iff, on **any** breadth reading in the trailing window `[date-lookbackDays, date]`, breadth was near its Donchian low: `breadthPercent <= lowerBand + (upper-lower)*percentile`. A bounded *memory* primitive — "breadth was at a washout extreme within the last K bars." |
| Param `percentile` | **0.10** — reuse the exact extremeness bar of `marketBreadthNearDonchianLow` so the two stay calibrated. |
| Param `lookbackDays` | **15** trading-day readings (≈3 weeks). Long enough to bridge floor→EMA10-recross (5-12 sessions in 2009/2020), short enough not to call a 6-week-old washout "recent." |
| Data source | `context.marketBreadthMap` — iterate readings `!isAfter(date)`, take trailing window by reading-count (same weekend/holiday-robust pattern as `MarketBreadthIncreasingCondition`). |
| Category | `Market` |

Build = window-walk cloned from `MarketBreadthIncreasingCondition` + the threshold test from
`MarketBreadthNearDonchianLowCondition`. Both exist → low build/test risk (unit-test against synthetic
breadth series). **This is the only new code.** All exits below are already first-class.

## ⚠ Screen run #1 (2026-06-04) — DEAD INSTANTIATION, premise survives → rebuilt

The first screen (relative-Donchian washout stack) **crashed (in-JVM heap OOM) in window-1 OOS** and the
partial run was decisive: window-1 IS produced **544 trades / 29,268 missed** ⇒ ~1,000+ liquid names
eligible per market-wide fire day ⇒ **~7-10 entry events/year**, ~20-40× the intended crisis cadence.

**Quant diagnosis (signed):** the breadth Donchian channel is only **20 days** (`MarketBreadthService.kt:32`),
so `marketBreadthNearDonchianLow(0.10)` is a *20-day local-minimum detector*, not a crisis floor — it is
**scale-free, and crisis is a scale phenomenon.** Paired with `marketBreadthRecovering` (EMA10-recross,
fires at every dip-bounce) the stack reads "20-day local low + just ticked up" = **routine dip-recovery**.
Wrong dimension, not a tuning issue. The OOM is downstream (the engine records an `EntryDecisionContext`
per missed candidate; ~1,000/fire-day × windows → heap blowup). **The premise (crisis-bottom timing)
survives; the operationalization was wrong → rebuilt below.** (The original relative `marketBreadthWashoutWithin`
condition is kept as a reusable *dip-recovery* primitive — strategy-neutral, not used by Gjallarhorn.)

## Candidate spec (assembled strategy "Gjallarhorn") — REBUILT 2026-06-04

### NEW condition #2 (built first-class, TDD): `marketBreadthAbsoluteWashoutWithin`
Clone of `marketBreadthWashoutWithin` with the washout test changed to an **absolute** breadth-bull-% floor
(`breadthPercent <= threshold`) instead of the range-relative Donchian-low. An absolute floor only fires in
genuine market-wide capitulations (the teens: 2008/2011/2015/2020). Params `threshold=15.0`, `lookbackDays=30`.
7 unit tests. **Thresholds are the quant's DRAFT — Step 0 fire-date confirmation precedes any durable persistence.**

### Entry stack (AND — all on the fill-trigger bar)

| # | Condition | Params | Rationale |
|---|---|---|---|
| 1 | `marketBreadthAbsoluteWashoutWithin` (NEW) | `threshold=15.0`, `lookbackDays=30` | "Breadth hit an absolute crisis floor (≤15%) within the last 30 days." True capitulation memory. |
| 2 | `marketBreadthIncreasing` | `days=3` | Turn confirmation — breadth rising 3 consecutive readings. A *much* stiffer "it's turning" test than EMA10-recross (which fired on every one-day bounce → the run-#1 over-firing). |
| 3 | `minimumPrice` | `5.0` | Tradability hygiene. |
| 4 | `averageDollarVolumeAbove` | `thresholdUsd=5_000_000`, `lookbackDays=20` | Tradability hygiene — liquid names only; keeps the test breadth-timing, not a microcap-bounce artifact. |

**Dropped from run #1:** the relative `marketBreadthWashoutWithin` and `marketBreadthRecovering` (the
over-firing pair). **Still NOT included:** `spyTrendUp`/`marketUptrend`/etc. — Gjallarhorn fires *before* an
uptrend exists; any trend/level gate nulls the premise. Conditions 1+2 are pure market-timing (identical for
every symbol on a day); 3+4 are *liquidity-only* (not quality/trend) to keep the read clean.

### Ranker / what we buy — broad liquid basket + `Random` ranker

| Choice | Value |
|---|---|
| Ranker | `Random`, `randomSeed = 42` |
| What we buy | every symbol passing the liquidity filters on the fire day, Random breaking the `maxPositions` cap |

The hypothesized alpha is **the timing of market re-entry**, not stock selection. A selection ranker
(`TrailingReturn` / `nearness52WeekHigh` / `SectorStrength`) would confound breadth-timing with
stock-picking (and would itself have to beat a Random baseline anyway — the George lesson). Random *as the
ranker* makes the basket an unbiased equal-probability sample of "what's liquid at the bottom," so the only
thing under test is entry timing. Fixed seed → reproducible + byte-comparable to the null (§null-baseline).

### Exit stack (OR — first to fire wins; all first-class, screen needs no inline script)

| # | Exit | Params | Tunable? | Rationale |
|---|---|---|---|---|
| 1 | `percentGain` | `targetPct=15.0` | **Tunable — flag for G13** | Take the snapback. Crisis bounces deliver fast, large gains; harvest rather than round-trip. 15% ≈ first leg of a capitulation rally. |
| 2 | `marketBreadthDeteriorating` | — | No (no params) | Regime re-rolls over (EMA5<EMA10<EMA20) → bottom failed / second leg down. The **false-bottom whipsaw** circuit-breaker (the 2008 "5 failed bounces" defense). |
| 3 | `trailingStopLoss` | `atrMultiplier=3.0` | **Tunable — flag for G13** | Per-name protective trail (drops X ATR below highest since entry). Wide (3×ATR) — crisis vol is enormous; don't get shaken out of the snapback. |

**Time cap deliberately NOT used in the screen.** `maxHoldingDays` exists first-class (no inline script
needed), but `marketBreadthDeteriorating` already provides the regime-based "move is over" signal. Start
without the time cap to keep the screen clean; add a promoted `maxHoldingDays` only if exits 1-3 leave
trades dangling.

> _Exit-name reconciliation (engine-verified 2026-06-04): the quant spec named `percentGainExit` /
> `atrTrailingStopLoss`; the engine's first-class equivalents are `percentGain` [targetPct] and
> `trailingStopLoss` [atrMultiplier]. Mechanics identical; names corrected here._

### Sizer / portfolio knobs

| Param | Value | Rationale |
|---|---|---|
| Sizer | `PercentEquity` (equal-weight) | Few, lumpy, simultaneous entries at one bottom event — equal-weight is the honest "I have a timing signal, spread the bet." `AtrRisk` is **rejected**: ATR is at its annual max at a crisis bottom, so risk-parity would shrink every position to a sliver exactly when the signal is strongest. |
| `maxPositions` | **20** | Diversified market-proxy basket (the timing is the bet, not 3 names), still investable. With Random = a 20-name unbiased sample. |
| `entryDelayDays` | **1** | Rank on the breadth-recross signal day, fill next session. Realistic; avoids same-bar look-ahead. |
| `leverage` | **1.0** | Do not lever the riskiest (max-whipsaw) trades in the book. |
| `randomSeed` | **42** | Reproducibility + null-baseline comparability. |

## Null baseline — the timing test (the real gate)

The standard G-RANDOM *ranker* baseline does not apply (Random is already the ranker). The correct null is
a **random-entry-timing baseline** — hold everything else byte-identical, destroy only the timing:

**"Gjallarhorn-NULL":** same universe, liquidity filters, `Random` ranker seed 42, `maxPositions=20`,
`entryDelayDays=1`, exit stack, sizer, leverage, walk-forward windows. **Replace entry conditions 1+2 with
random entry days**, drawn to **match Gjallarhorn's realized firing rate per OOS window** (same number of
entry events per window; days sampled uniformly within that window). Run **20 seeds (1-20)** → a
distribution, not a point.

**Metric to beat:** **blended (geometric-compounded) OOS CAGR** AND **per-trade edge** — Gjallarhorn must
beat the **90th percentile** of the 20-seed null on **both**. (Per-trade edge guards against "more trades =
more lottery tickets"; blended CAGR is the true compounded outcome.) Beating the *median* is coin-flip
noise for a few-trade timing strategy. **Secondary reference (not a gate):** buy-and-hold SPY MAR and
always-invested-equal-weight-basket MAR.

## PASS / KILL on the first `/strategy-screen` (2005-2015, 7 OOS windows)

### PASS (all required)
1. **Timing-null beat:** blended OOS CAGR AND per-trade edge both > 90th-pct of the 20-seed Gjallarhorn-NULL.
2. **Lottery-window count: ≥ 3 of 7** OOS windows with **positive participating-window CAGR**. < 3 = KILL as
   lottery (single-regime detector) regardless of how good the blended number looks — the geometric compound
   of {one huge window, six flat/empty} is the true number and it is not a strategy.
3. **No single-window dominance:** the single best OOS window contributes **< 60%** of total compounded OOS
   return.
4. **Whipsaw survival:** in windows with a known false-bottom sequence, per-window strategy max-DD **< 1.5×
   the basket's** in that window (proves `marketBreadthDeteriorating` is cutting the false bounces).

### KILL signatures (any one)
- **Lottery:** < 3 positive participating windows, OR ≥5 participating windows negative, OR single window ≥60% of compounded return.
- **Timing carries no info:** fails the 90th-pct null beat on blended CAGR.
- **False-bottom bleed:** strategy DD ≈ basket DD in whipsaw windows.
- **ARS** — sweep these neighbours one axis at a time; KILL if pass/fail is non-monotone with per-window edge sign-flips at stable trade counts:
  - `marketBreadthWashoutWithin.lookbackDays`: **{10, 15, 20}**
  - `marketBreadthWashoutWithin.percentile`: **{0.05, 0.10, 0.15}**
  - `percentGain.targetPct`: **{10, 15, 20}**
  - `trailingStopLoss.atrMultiplier`: **{2.5, 3.0, 3.5}**
  - `maxPositions`: **{15, 20, 25}**

### 30% CAGR floor — flag, not auto-kill at screen
A standalone blended CAGR will likely be well under 30% because it is cash most of the calendar (in-market
only at bottoms). Credit the idle cash leg ~3% annualized when estimating blended CAGR. **Honest framing:
Gjallarhorn is a crisis-bottom *component*, not a standalone 30%-CAGR strategy — and the regime-conditional
*portfolio* program that would host it is abandoned (long-only ⇒ defense=cash). Even a clean screen pass may
have no current portfolio home.** ← operator decision flagged below.

## Honest risk assessment (quant)

**Prior: ~20% it survives to a PROVISIONAL screen pass, ~5% it reaches TRADABLE.** **Most likely death:
the lottery-window gate** — the 2005-2015 screen contains essentially one unambiguous crisis bottom
(2009-Q1) plus softer dip-recoveries (2010 flash-crash, 2011 EU-crisis); the whole compounded OOS result
will likely be carried by the single 2009 window — the textbook regime-detector signature §2/§3 catch.
The premise is real, but the sample of crisis bottoms in any 10-year window is too small for walk-forward to
distinguish alpha from one lucky regime call — the inherent, possibly unfixable tension of a crisis-bottom
timer. **Second death:** the timing-null beat — a *random* entry during 2009 also made a fortune (everything
went up off that bottom), so the breadth-recross timing may add little over random entry at the same rate.

## Status / funnel

| Stage | State |
|---|---|
| Data-span check | ✅ PASS (market breadth, 2000-2025) |
| Spec | ✅ quant-signed (2026-06-04) |
| Operator go on build+screen | ✅ go (2026-06-04 — accepted as research/component result; may sit shelved until a host exists) |
| Build `marketBreadthWashoutWithin` (TDD, first-class) | ✅ done (8 unit tests, `@Component`-registered) |
| PRD deploy (new condition) | ✅ udgaard 1.0.83 (condition live, verified via discovery endpoint) |
| `/condition-screen` firing-rate check | ⏭️ SKIPPED — quant fallback: the `marketBreadth*` family is non-terminating under the condition-screen auto-sweep even on the reduced universe (REFERENCE.md §perf-cliff), no API flag to disable it for a registered condition. Firing rate surfaces in the screen instead. |
| `/strategy-screen` run #1 (relative-Donchian stack) | ❌ DEAD INSTANTIATION — OOM + ~7-10/yr over-firing (wrong primitive, see above). Premise survives. |
| Rebuild: `marketBreadthAbsoluteWashoutWithin` (TDD, first-class) | ✅ done (7 unit tests) |
| **Step 0 — firing-rate/fire-date confirmation** (corrected stack, 300-sym, 2005-2015 single backtest) | ⬜ NEXT — needs PRD redeploy + approval |
| `/strategy-screen` run #2 (300-sym, corrected stack) | ⬜ only if Step 0 lands on real crises ≤2/yr |
| Gjallarhorn-NULL (20-seed random-entry-timing, matched firing) | ⬜ |
| `/validate-candidate` → `/monte-carlo` → 30% floor | ⬜ (quant prior ~10-12%: likely dies at firewall on *insufficient events*, not edge) |

## Reference
- Anchor: Zweig Breadth Thrust; capitulation-reversal literature.
- Ledger: `STRATEGY_LEDGER.md` §C.2 (Gjallarhorn row) + §C.3 (live classes — breadth-event regime-transition).
- Funnel: `BACKTESTING_FUNNEL.md` (stages, gates, failure-mode library, data-span + null-baseline discipline).
- Engine: build `MarketBreadthWashoutWithinCondition.kt` (clone `MarketBreadthIncreasingCondition` window-walk
  + `MarketBreadthNearDonchianLowCondition` threshold). Exits `percentGain` / `marketBreadthDeteriorating` /
  `trailingStopLoss` + `maxHoldingDays` all first-class. Breadth map via `BacktestContext.marketBreadthMap`.
</content>
</invoke>
