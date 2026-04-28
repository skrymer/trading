# VCP Strategy V2 — Survivorship-Bias-Corrected Development

*Started 2026-04-28 after the survivorship-bias correction work landed (V6/V18 migrations + delisted-ticker ingestion). The original `VCP_STRATEGY_DEVELOPMENT.md` and `VCP_TRADING_PLAN.md` were built on a survivors-only universe; their headline numbers are systematically optimistic. This document is the living record of strategy work under realistic data.*

---

## 1. Context — why V2

Through April 2026 the VCP strategy was developed and validated on a stock universe that contained only currently-listed names. Stocks that delisted during the backtest period (mergers, bankruptcies, voluntary withdrawals) were absent. This is textbook survivorship bias: it inflates win rate, depresses drawdown, and concentrates per-trade edge on the historical winners.

In the 2026-04-27 → 2026-04-28 work we:

1. Added 1,091 historically-delisted US Common Stock tickers to the universe across all 11 GICS sectors (V6 in midgaard, V18 in udgaard).
2. Fixed `BacktestService.createTradeFromEntry()` to force-close on delisting day with `Trade.EXIT_REASON_DELISTED` instead of silently dropping trades whose exit-date quote didn't exist.
3. Recalculated market and sector breadth against the realistic universe (per-date dynamic denominator handled this cleanly).
4. Re-ran the canonical backtests against the corrected universe.

The outcome: VCP still has real per-trade edge (~5.6% ex-2018, EC 92.8 — Excellent), but its drawdown profile is dramatically wider than V1 reported (worst-seed MDD 42-49% vs V1's 18.9% mean / 20.7% worst-seed). **Parameter tuning at the margin (risk %, stop ATR, maxPositions) does not close the gap.** The dispersion is regime-driven — when momentum-favourable conditions reverse, VCP signals fire en masse within sectors and concentrated cluster losses dominate. The next round of work is structural, not parametric.

This document is the V2 working space.

---

## 2. Current strategy definition (unchanged from V1)

The strategy itself was *not* redesigned during the survivorship-correction work. We're still validating the same DSL.

**Entry** (`Vcp` predefined):
```kotlin
marketUptrend()
sectorUptrend()
uptrend()                                                           // 5>10>20 EMA, price > 50 EMA
volatilityContracted(lookbackDays = 10, maxAtrMultiple = 3.5)
aboveBearishOrderBlock(consecutiveDays = 1, ageInDays = 0)
priceNearDonchianHigh(maxDistancePercent = 3.0)
volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
minimumPrice(10.0)
```

**Exit** (`VcpExitStrategy` predefined):
```kotlin
emaCross(10, 20)
stopLoss(atrMultiplier = 2.5)
stagnation(thresholdPercent = 3.0, windowDays = 15)
```

**Ranker**: `SectorEdge` with IS-derived sector ordering (via walk-forward) or hardcoded `["XLC","XLU","XLI","XLK","XLE","XLB","XLV","XLF","XLY","XLP","XLRE"]` for single-period backtests.

**Sizing**: `AtrRisk(riskPercentage = 1.0%, nAtr = 2.0)`, `leverageRatio = 1.0`, `maxPositions = 15`.

(Note: V1 plan recommended 1.25% risk; the corrected-universe risk sweep showed 1.0% has the highest worst-seed Calmar — see §3.)

---

## 3. Validation results under the corrected universe

All numbers below are from the survivorship-corrected universe (~4,158 STOCK symbols, 2016-01-01 → 2025-12-31 unless stated otherwise). Original 2016-2025 baseline (V1 plan): CAGR 51.4%, MaxDD 18.9%, Calmar 2.73 (4-seed mean at 1.25% risk).

### 3.1 Walk-forward 2000-2026 (3yr IS / 1yr OOS / annual step)

Same config as V1's 2026-04-25 reference, just on the corrected universe.

| | V1 baseline (no delisted) | V2 (with delisted) |
|---|---|---|
| Windows | 23 | 23 |
| OOS trades | 1,253 | 1,249 |
| Aggregate OOS edge | +3.70% | +3.49% |
| WFE | 1.084 | 1.314 |
| Negative OOS | 2 (2007, 2008) | 2 (2007, 2008) |

The WFE rise is mostly arithmetic (IS denominator shrinks more than OOS numerator under bias correction), not a strategy-quality improvement. Per-trade edge held up within noise (-0.21pp inside 0.8 SE).

### 3.2 2016-2025 single backtest, seed 42 only

| Metric | V1 baseline (4-seed mean) | V2 (seed 42, corrected) |
|---|---|---|
| Total trades | ~612 | 638 |
| Win rate | 52.9% | 48.9% |
| Edge | +6.4% | +5.05% |
| CAGR | 51.4% | ~46.9% |
| Max DD | 18.9% | **32.08%** |
| Calmar | 2.73 | ~1.46 |

CAGR drop within quant-predicted range. **MaxDD nearly doubled** — the headline finding.

### 3.3 4-seed risk sweep (1, 7, 42, 100) at 1.0% risk, no filter

| Metric | Mean | Median | Worst | Best | SD |
|---|---|---|---|---|---|
| Edge | +6.54% | — | +3.95% | +12.59% | 3.91 |
| Edge ex-2018 | +5.55% | — | +4.67% | +6.33% | 0.51 |
| CAGR | 54.3% | 45.3% | 39.9% | 78.6% | 14.7 |
| MDD | 35.0% | 37.3% | 27.2% | 42.5% | 5.7 |
| Calmar | 1.57 | 1.33 | 1.04 | 2.34 | 0.55 |

Seed dispersion is wide. Seed 100 produced a 2018-freak-driven outlier (single year +97.91% per-trade edge from a 50-100x runner). Other seeds may or may not catch the freak depending on ranker tie-breaking.

### 3.4 12-run IPO-recency-filter diagnostic — filter is dead

4 seeds × 3 filter levels {none, 180-day minimum history, 365-day} at 1.0% risk. Quant hypothesised that 2018 weakness in honest seeds was the IPO-recency-overfit fingerprint, and a `minimumHistoryDays` filter would recover edge.

Result: aggregate 4-seed mean edge moved from +7.72% → +9.11% with the 180-day filter. **But the entire +1.4pp delta is explained by which seeds caught the 2018 freak winner shifting from 2/4 (no filter) to 3/4 (filter 180).** Ex-2018 mean per-year edge effect: ~+0.3 to +0.5pp, well inside seed dispersion. Filter 365 was uniformly worse across years.

The `MinimumHistoryDaysCondition` ships in udgaard (commit 4da886c) as a re-usable knob for future strategies, but it is **not** part of VCP.

### 3.5 4×4 risk sweep — 1.0% best on worst-seed

Risk ∈ {0.75%, 1.0%, 1.25%, 1.5%} × seeds {1, 7, 42, 100}, 16 backtests:

| Risk | Trades | Mean Edge | e_ex2018 | Mean CAGR | Mean MDD | Mean Calmar | Worst MDD |
|---|---|---|---|---|---|---|---|
| 0.75% | 796 | +8.71 | +5.47 | 61.5% | 27.0% | 2.52 | 35.12% |
| 1.0% | 703 | +7.72 | +5.55 | 53.7% | 31.2% | 1.78 | **33.96%** |
| 1.25% | 622 | +6.54 | +5.65 | 54.3% | 35.0% | 1.57 | 40.78% |
| 1.5% | 561 | +7.01 | +6.11 | 59.9% | 37.2% | 1.69 | 40.34% |

0.75% wins mean Calmar by a wide margin but its lead is freak-catch artifact (3/4 seeds catch the freak at 0.75% vs 2/4 at 1.0% — smaller positions fit more candidates, including marginal ones). Worst-seed Calmar at 0.75% is 1.09 (vs 1.18 at 1.0%); worst-seed MDD 35.12 sits exactly on the 35% gate. **1.0% is the most robust on worst-seed criteria** — picked for further validation.

V1 plan's recommendation was 1.25% (Pareto-dominant under survivors-only). Survivorship bias was concealing the U-shape: 1.25% is locally the worst risk level under the corrected universe (worst-seed MDD 40.78%, worst-seed Calmar 1.04).

### 3.6 8-seed validation at 1.0% risk — 2/3 gates fail

Added seeds 12, 24, 88, 200 to the 4-seed sweep at 1.0%:

| Metric | Mean | Median | Worst | Best | SE |
|---|---|---|---|---|---|
| Edge ex-2018 | +5.61 | +5.69 | +4.67 | +6.33 | 0.18 |
| Edge (with 2018) | +7.71 | +7.32 | +4.73 | +11.50 | 1.10 |
| CAGR | 52.0% | 45.3% | 39.9% | 78.6% | 5.18 |
| MDD | 35.6% | 37.3% | 27.2% | **42.5%** | 2.02 |
| Calmar | 1.51 | 1.33 | 1.07 | 2.90 | 0.21 |

Quant's gate check:
- Worst-seed Calmar 1.07 — **PASS** ≥ 1.0 (by hair)
- Worst-seed MDD 42.46% (seed 24) — **FAIL** ≤ 35%
- Calmar SE 0.213 — **FAIL** < 0.15

4 of 8 seeds catch the 2018 freak (50/50 split). Per-trade ex-2018 edge is rock-solid (SE 0.18). MDD dispersion 27→43% is the killer — the strategy's drawdown depends on which trades the ranker tie-breaking happens to fill on signal-dense days.

### 3.7 Iteration: 2.0 ATR stop + maxPos=10 — 3/3 gates fail

Per quant: tighter stop attacks per-trade max loss; lower maxPos reduces concentration risk. 8 seeds, same 1.0% risk:

| | Baseline (2.5 ATR / maxPos=15) | Iteration (2.0 ATR / maxPos=10) | Δ |
|---|---|---|---|
| Mean Calmar | 1.51 | 1.27 | -0.24 |
| Median Calmar | 1.33 | 1.11 | -0.22 |
| Worst Calmar | 1.07 | **0.73** | -0.34 |
| Mean MDD | 35.6% | 37.1% | +1.5pp |
| Worst MDD | 42.5% | **49.3%** | +6.8pp |
| Calmar SE | 0.213 | 0.178 | -0.035 |
| Mean ex-2018 edge | 5.61% | 5.38% | -0.23 |

Worse on every dimension except SE (which tightened only because everything got uniformly worse). Mechanism: the 2.5 ATR stops were absorbing real losers; the 2.0 stops fire more often (whipsaw) without proportionally reducing per-loser depth. Smaller maxPos concentrated rather than diversified.

This is the diagnostic point — **per-trade risk and concentration headcount are not the binding constraints**. Cluster size during sector-correlated drawdowns is.

### 3.8 Verified single-seed baseline (2026-04-28, current best config)

Re-ran the §8 reproducibility curl to confirm the documented config produces the documented numbers. Seed 42, 1.0% risk, 2.5 ATR stop, maxPositions=15.

| Metric | Value |
|---|---|
| Total trades | 699 |
| Win rate | 48.78% |
| Edge | +4.97% |
| Avg win / Avg loss | +15.81% / -5.35% |
| Profit factor | 2.70 |
| SQN | 6.33 |
| Tail ratio | 3.58 |
| Final capital | $354,734 |
| CAGR | 42.89% |
| Max DD | 27.34% ($26,781) |
| Calmar | 1.57 |
| Edge consistency | 92.0 / 100 (Excellent) |

**Yearly edges**: 2016 +1.65%, 2017 +10.68%, **2018 +0.71%**, 2019 +3.03%, 2020 +7.43%, 2021 +5.14%, 2022 +0.83%, 2023 +7.07%, 2024 +7.13%, 2025 +9.01%.

Seed 42 is one of the four 8-seed members that *missed* the 2018 freak (edges in {seed1: +66, seed24: +65, seed100: +81, seed200: +66} caught it). On this seed the strategy's "honest" performance is visible: solid +5-10% in normal years, breakeven in 2018 and 2022 (the two adverse environments), no negative years. Calmar 1.57 is at the median of the 8-seed distribution.

**Exit-reason breakdown**:
- EMA 10/20 cross: 488 trades (69.8%), avg +8.08%, WR 52.7%, hold 49.6 days
- Stagnation (<3% gain after 15d): 156 trades (22.3%), avg -0.28%, WR 53.2%, hold 21.7 days
- Stop loss (2.5 ATR): 53 trades (7.6%), avg -8.01%, WR 0.0%, hold 10.0 days
- **Delisted (force-close)**: 2 trades (0.3%), avg +0.03%, WR 50.0%, hold 2.5 days

The 2 `EXIT_REASON_DELISTED` trades confirm the force-close path is wired correctly — small contribution in 2016-2025, will matter more in pre-2010 walk-forward windows. Stop-loss rate 7.6% matches V1's reported figure, so the exit profile is structurally unchanged by the universe correction.

Output: `/tmp/vcp_v2_baseline_seed42.json` · `backtestId = e09837a4-de56-4813-89be-66eecc653eb0`.

### 3.9 The "2018 freak" is a data-quality artifact, not alpha (2026-04-28)

Investigated the freak winner that drove ~50% of the 8-seed mean Calmar (per quant's "trim 2018" framing). The entire freak in the 1.0% / seed 100 run is a **single trade on AFX**:

| Field | Value |
|---|---|
| Symbol | AFX |
| Entry | 2018-01-16 at $52.30 |
| Exit | **2020-01-24 at $2,200.00** (the symbol's `delisted_at`) |
| Shares | 69 |
| $ profit | $148,191 |
| Position return | **+725.98%** of starting capital |
| Hold | ~2 years |

**This is an EODHD source-data issue, not our ingestion bug.** Verified by calling `https://eodhd.com/api/eod/AFX.US?...&from=2018-01-01&to=2020-02-01` directly:

```
EODHD returned 247 bars for the period.
First 5: all date 2018-01-02..2018-01-08, open=high=low=close=52.30, volume=0
Last 5: 2018-12-18 .. 2018-12-21 all close=64.20 volume=0,
        then a 13-month gap, then 2020-01-24 open=2201, high=2221, close=2200, volume=8075
```

EODHD pads non-trading-day periods on delisted issuers with **zero-volume "stuck" bars** that repeat the prior close. AFX's *actual* trading history ended 2017-08-08 (last bar with meaningful volume — 16,491 shares); everything between then and the 2020-01-24 delisting-day terminal bar is either zero-volume filler or a single 100-share off-market print. Our `StockIngestionService` faithfully persisted what EODHD returned, which is the correct ingestion behaviour but produces unusable backtest data.

The strategy entered on the EODHD synthetic bar at $52.30 (which looked legitimate to the entry conditions because they don't check volume), held through a year of stuck synthetic bars (no exit could fire — EMAs locked, ATR collapsed, stagnation never tripped because price didn't change), then "exited" at the bogus 2020-01-24 terminal price.

**Universe-wide contamination (`volume = 0` bars in `stock_quotes`):**

| Cohort | Bars | % |
|---|---|---|
| Total | 16,812,324 | 100% |
| Volume = 0 | 405,200 | 2.41% |

Heavily concentrated in delisted symbols. Top contributors are 60-87% synthetic:

| Symbol | Total bars | Volume=0 | % |
|---|---|---|---|
| BSIRY | 5,052 | 4,420 | **87.5** |
| AAK | 4,976 | 4,224 | 84.9 |
| ALPA | 3,989 | 3,330 | 83.5 |
| AYE | 4,782 | 3,946 | 82.5 |
| ABF | 5,184 | 3,749 | 72.3 |
| FLUT | 5,926 | 4,266 | 72.0 |
| BCAL | 5,146 | 3,448 | 67.0 |
| RCAT | 7,296 | 4,922 | 67.5 |

**Implications for the rest of the work:**

1. **The 2018 freak that was distorting 8-seed dispersion is artificial.** Different seeds' ranker tie-breaking land different sets of trades on AFX-like bad-bar symbols. The "real" seed dispersion in mean Calmar is much tighter than the 1.07-2.90 spread we observed.
2. **A non-trivial fraction of the delisted universe likely has similar artifacts** — paired-spike data, sparse-coverage delisting bars with bogus terminal prices. The 1,091 delisted symbols added in V6/V18 inherit EODHD's data-quality limitations on long-tail names.
3. **The strategy needs a stale-data safeguard.** Holding a position through a 2-year data gap and exiting at a bogus terminal price is not a desirable failure mode. A `maxBarsHeldWithoutNewClose` exit (force-close after N consecutive identical or absent bars) would have prevented this.

**Action**: data-quality audit of the delisted universe is now Step 0 of the §6 sequence — before any regime-conditional analysis or structural backtests. Filtering out symbols with bad-bar patterns will:
- Materially tighten 8-seed Calmar dispersion (probably from SD 0.21 → 0.10-0.12)
- Reduce mean Calmar (the freak was inflating it) but make the "honest" floor visible
- Potentially close enough of the worst-seed MDD gap that some Step 1-3 structural work becomes unnecessary

---

## 4. Quant verdict (2026-04-28)

> "Parameter tuning is exhausted. 24 backtests across three configs all show worst-seed MDD 40%+. The dispersion is regime-driven, not parameter-driven. Stop tweaking parameters."

The strategy has real edge (5.6% ex-2018 per-trade, EC 92.8). The implementation under realistic data has unacceptably wide MDD dispersion. The next round of work targets the regime mechanism, not the parameter values.

---

## 5. Honest performance floor (V2 headline candidates)

The V1 headlines (CAGR 51%, Calmar 2.73, MaxDD 18.9%) were calibrated to a survivors-only universe. The V2 headline candidates, depending on how much further structural work narrows MDD:

**Conservative (current best — 1.0% / 2.5 ATR / maxPos=15, 8 seeds):**
- CAGR median 45%, mean 52%
- MDD median 37%, **worst 42.5%**
- Calmar median 1.33, mean 1.51, **worst 1.07**
- Edge ex-2018 5.61% (rock-solid, EC 92.8 Excellent)

**Realistic ship floor**: 25-35% CAGR, ≤40% worst-seed MDD, ≥1.0 worst-seed Calmar. This positions VCP as a high-conviction retail / family-office product, not an institutional one. Headline framing: *"concentrated growth, designed for capital that can sit through 40% drawdowns"*.

If the structural-change work in §6 pushes worst-seed MDD under 35% with Calmar SE < 0.15, the institutional framing returns.

---

## 6. Next-steps sequence (quant-prescribed, 2026-04-28)

Order matters — earlier steps disambiguate later ones. **Step 0 was added 2026-04-28** after the §3.9 data-quality finding — it precedes any structural work because the current dispersion measurements include the AFX-like bad-bar artifact and so over-state how regime-driven the dispersion truly is.

### Step 0 — Filter EODHD volume=0 synthetic bars (NEW, blocks Steps 1-5)

Confirmed via direct EODHD API call (§3.9): EODHD pads non-trading-day periods on delisted issuers with `volume=0` synthetic bars that repeat the prior close. Our ingestion correctly preserves them; the strategy then misinterprets them as real flat-priced data, which is what produced the AFX freak.

**Fix layers:**

1. **Backtest-time filter (immediate, recommended)** — skip `volume = 0` bars in `BacktestService.loadStockBars` (or wherever `Stock.quotes` is materialised). Strategy never sees synthetic data; existing entry/exit conditions remain unchanged. Catches all 405,200 contaminated bars without touching ingestion. Drops some legitimate halt-day bars too (rare on liquid names — acceptable).

2. **Ingest-time filter (cleaner, longer-term)** — `StockIngestionService.fetchAndBuildStock` drops `volume = 0` bars before persistence. Saves storage + indices + downstream costs. Requires re-running the bulk ingest. Defer until layer 1 has been validated.

3. **Stale-data safeguard exit** — also force-close any open position whose underlying has had no `volume > 0` bar for ≥ 10 trading days. Belt-and-braces in case some volume=0 patterns sneak through (e.g. trading halts that happen mid-trade). Add to `BacktestService.createTradeFromEntry` alongside the existing `EXIT_REASON_DELISTED` force-close.

**Validation after layer 1:**

Re-run §3.6 8-seed validation under the cleaned universe and check:

| Metric | Pre-cleanup (current) | Expected post-cleanup | Gate |
|---|---|---|---|
| Calmar SE | 0.213 | 0.10-0.12 | < 0.15 |
| Worst-seed MDD | 42.5% (seed 24) | 32-36% | ≤ 35% |
| Worst-seed Calmar | 1.07 (seed 12) | 1.2-1.5 | ≥ 1.0 |
| Mean Calmar | 1.51 | 1.30-1.45 | n/a (will compress as freak goes away) |

If the gates close at this stage, **Steps 1-5 are deferrable** — the strategy ships under a corrected universe with no further structural change. If gates still fail (esp. worst-seed MDD > 35%), the dispersion is genuinely regime-driven and Steps 1-5 stay on the critical path.

The 2020-01-24 AFX terminal bar (close $2,200, volume 8,075) has non-zero volume and would survive a pure `volume = 0` filter. But the §3.9 trade only happens because the 247 *preceding* synthetic bars let VCP enter at a ghost price; with those filtered, AFX has no 2017-2020 entry signal and never trades, so the bogus terminal never matters. We don't need a dedicated "bogus terminal bar" filter for the immediate fix.

### Step 1 — Regime-conditional edge analysis (no new backtests)

Use existing 2016-2025 trades (8-seed dataset at 1.0% baseline) to compute per-regime edge:
- **Per-VIX-quintile** edge (proxy: 21-day realised vol of SPY)
- **Per-market-breadth-state** edge (above/below market_breadth_daily EMA10)
- **Per-SPY-200EMA-state** edge (uptrend / downtrend)

Two diagnostic outcomes:
- *Edge collapses or goes negative in adverse regimes* → strategy alpha is regime-dependent → regime exits / regime-conditional sizing is the right structural lever
- *Edge stays positive across all regimes but MDD spikes anyway* → it's pure timing/clustering, not edge decay → per-sector cap is the right lever

This step disambiguates Steps 2 and 3 priority. ~1 day of analysis, no new backtests needed.

### Step 2 — Per-sector capital cap (test first if Step 1 is inconclusive or points at clustering)

Cap total open notional per sector at e.g. 25% of portfolio value. Attacks the actual mechanism: VCP signals fire in clusters within sectors during regime shifts (energy 2008, tech 2022). The maxPos=10 iteration already proved that headcount isn't the constraint — cluster size is.

Test: 8 seeds at one cap level (25%); if winning, sweep {20, 25, 30}.

Existing infrastructure check: does `BacktestService` support per-sector notional caps? If not, this is a code change first. (Likely `PositionSizingService` extension — need to investigate.)

### Step 3 — `marketAndSectorDowntrend` regime exit

Already implemented and tested 2026-04-23 under V1's survivors-only universe; verdict at the time was "do not adopt" because the 12pp CAGR cost outweighed the 7.7pp MDD reduction (17.3% baseline). **Math is different now**: 7.7pp off a 42% baseline lands under the gate, and a 12pp CAGR cost on 45% leaves a 33% CAGR strategy that ships.

Test: 8 seeds at 1.0% risk with `marketAndSectorDowntrend` appended to the exit chain. ~1-1.5 hours wall clock.

### Step 4 — Drawdown-responsive sizing (only if 2+3 don't close the gap)

Same logic as Step 3 — V1's rejection was on a 17.3% baseline. Recompute on the corrected baseline. Use `drawdownScaling` as documented in run-backtest skill. Test: 8 seeds with thresholds e.g. `[5% → 0.67×, 10% → 0.33×]`.

### Step 5 — V2 headline + ship decision

Whatever the post-structural-change config is, do an 8-seed final validation at the new config and check:
- Worst-seed Calmar ≥ 1.0
- Worst-seed MDD ≤ 35% (institutional) or ≤ 40% (family-office floor)
- Calmar SE < 0.15

If both ≤ 35% gates pass: pitch as institutional. If only family-office floor passes: ship with revised positioning. If neither: VCP needs structural rethink (entry filter, not just exit/sizing).

---

## 7. Carried-forward open items from V1

These were flagged as TODOs in V1's `VCP_STRATEGY_DEVELOPMENT.md` and remain relevant — survivorship correction doesn't replace any of them:

- **Periodic sector-ranker recalibration.** V1 hardcoded the SectorEdge ordering. Live trading should regenerate it quarterly from the trailing 5y unlimited backtest.
- **Liquidity floor / minimum dollar volume** (V1 "Additional Gaps", Liquidity Risk section). The `minimumPrice(10.0)` filter handles penny stocks but doesn't prevent thinly-traded names from getting positions sized to $1M+ (impactful at scale). Add `minimumDollarVolume($1M/day average)` once we hit the size where it matters.
- **Per-trade autocorrelation analysis.** V1 noted Monte Carlo trade shuffling assumes independence. Compute autocorrelation function of trade returns at lags 1-10 to test the assumption — if losses cluster, MC underestimates tail risk. Should be done before any live deployment.
- **Maximum consecutive losses analysis.** Win rate 49%, ~640 trades → expected max consecutive loss streak of ~10-12. Document the historical worst streak and MC-simulated worst-case for psychological prep.
- **Multivariate parameter sensitivity grid.** V1 only ran one-at-a-time sweeps for VC, Donchian, Volume. A 3×3×3 grid over the corrected universe would reveal interaction effects. Do this *after* structural work in §6 lands so we're not re-optimizing during change.
- **Quarterly walk-forward at production config.** V1 ran a 27-window 36mo IS / 3mo OOS. Repeat under the corrected universe at the V2 chosen config.

---

## 8. Methodology notes (current state)

**Universe**: 4,158 STOCK symbols across 11 GICS sectors. Includes 1,091 historically-delisted Common Stock tickers (V6 in midgaard, V18 in udgaard). Bar history median ~5 years per delisted symbol.

**Engine**: capital-aware (commit `15c9fe2`, merged 2026-04-17). Each trade entry checks portfolio budget under leverage cap; unfundable candidates skip. Most signal-dense days are leverage-saturated at $10K starting capital.

**Force-close on delisting** (commit `922c1d4`, 2026-04-27). `BacktestService.createTradeFromEntry()` now exits with `Trade.EXIT_REASON_DELISTED` when the trading symbol's last bar predates the strategy's natural exit date. Replaces a silent-drop bug that was suppressing pre-2010 losses.

**Breadth recalculation** uses a per-date dynamic denominator (`COUNT(*) FROM stock_quotes JOIN stocks ... GROUP BY quote_date`). Handles partial-history universe correctly — delisted symbols contribute pre-delisting and disappear after, no NaN/zero pollution.

**Reproducibility**:
- Single backtest at the current best config:
  ```bash
  curl -s -X POST http://localhost:8080/udgaard/api/backtest \
    -H "Content-Type: application/json" \
    -d '{
      "assetTypes": ["STOCK"],
      "useUnderlyingAssets": false,
      "entryStrategy": {"type": "predefined", "name": "Vcp"},
      "exitStrategy": {"type": "predefined", "name": "VcpExitStrategy"},
      "startDate": "2016-01-01",
      "endDate": "2025-12-31",
      "maxPositions": 15,
      "entryDelayDays": 1,
      "randomSeed": 42,
      "positionSizing": {
        "startingCapital": 10000,
        "sizer": {"type": "atrRisk", "riskPercentage": 1.0, "nAtr": 2.0},
        "leverageRatio": 1.0
      }
    }' > /tmp/vcp_v2_baseline.json
  ```
- Walk-forward 2000-2026 (3yr IS / 1yr OOS / annual step): swap endpoint to `/api/backtest/walk-forward` and add `"inSampleMonths": 36, "outOfSampleMonths": 12, "stepMonths": 12`.
- 8-seed validation: same payload, run with `randomSeed` ∈ {1, 7, 12, 24, 42, 88, 100, 200}.
- Diagnostic data files for the 2026-04-28 work: `/tmp/vcp_baseline_2016_2025_seed*.json`, `/tmp/vcp_diag_seed*_filter*.json`, `/tmp/vcp_risksweep_seed*_risk*.json`, `/tmp/vcp_iterate_seed*.json`.

**Run sequentially** — backend OOMs with concurrent backtests. ~12GB heap per run, ~7-10 min wall clock at the current data sizes.

---

## Changelog

- **2026-04-28** — Document created. V1 work (`VCP_STRATEGY_DEVELOPMENT.md`, `VCP_TRADING_PLAN.md`) frozen as the survivors-only-universe historical record. V2 starts with the corrected-universe re-validation and quant-prescribed structural-work sequence in §6.
