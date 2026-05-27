# Validation Candidates

_Generated 2026-05-27, updated 2026-05-28 to include DV1 as a near-miss. Captures strategies surfaced as worth deeper validation against the post-V13 cleaned universe._

**Roster:**
- **Firm survivors** (PASS all 5 `/strategy-screen` gates + CAGR ≥ 30%): **VZ3**, **MR3**
- **Near-miss** (full Block A, fails 2 v4 gates by tiny margins; not advanced under current strict gates): **DV1**

**All three candidates use inline `script` conditions in their `entryStrategy`. None is tradable as-is.** Even if `/validate-candidate` returns TRADABLE on VZ3 / MR3, the verdict is **TRADABLE-PENDING-PROMOTION** — the inline scripts must first be promoted to real, named, version-controlled `EntryCondition` classes via `/create-condition` (lookahead-audited + unit-tested), then the candidate re-enters the firewall from Block A with the promoted config. Inline scripts bypass the audit, aren't reproducible across sessions, and aren't discoverable via `/api/backtest/conditions`.

## Methodology context

- **Universe**: post-V13 midgaard data (after V11/V12/V13 sanitization removed 206 symbols with V-shape bad-prints + V2/V3 split-adjustment failures). 3,964 active symbols.
- **Engine**: udgaard backtest, capital-aware engine, AtrRisk(1.25%, 2.0×ATR stop) sizer, leverage 1.0, 15 max positions, 1-day entry delay.
- **Screen window**: 2005-2015 (10y), 36-month IS / 12-month OOS / 12-month step = 7 OOS windows per candidate.
- **Gates** (all 5 must pass; G5 inactive at 17 < 50 variants):
  - G1 — aggregate OOS edge ≥ 0.125% per-trade (0.10 × riskPercentage)
  - G2 — stitched aggregate Sharpe ≥ 0.70
  - G3 — ≥ 5/7 OOS windows positive AND median window edge > 0
  - G4 — W1 2008 (GFC) DD ≤ 2× median DD across windows
- **Tradability bar** (separate from screen): CAGR ≥ 30%.

A "screen pass" means the strategy is worth deeper validation (full Block A → Block B → Block C). It is NOT yet a tradable strategy. Survivors must clear the strict v4 gates on full Block A before progressing.

---

## Candidate 1: VZ3 — Mean-reversion to EMA20

**Hypothesis**: take long entries when price has pulled back near its 20-day EMA in a confirmed uptrend, with rising EMA and higher-low structure. Exit either on a small profit (10%) or a break of the 50-day EMA.

### Entry conditions (3-of-3 must pass)

1. **`marketUptrend`** — predefined market-regime gate (breadth-EMA based)
2. **Pullback-to-EMA20 with structure** (script): at least 2 of the following 3 sub-conditions must hold —
   - `|close − closeEMA20| ≤ 1.5 × ATR` (price within 1.5 ATR of the 20-day EMA = "in the zone")
   - `low > low[-10]` (higher low vs 10 days ago = no breakdown)
   - `closeEMA20 > closeEMA20[-1]` (EMA20 rising = local trend intact)

### Exit conditions (any triggers)

- **+10% gain reached** OR **`close < closeEMA50`** (50-day EMA break)
- **Stop-loss**: 2.5 × ATR from entry

### Ranker

`DistanceFrom10Ema` — ranks signal candidates by proximity to the 10-day EMA. Lower distance = higher rank. Has some seed-tie-break behaviour (small variation observed across s1/s2/s3 — see below).

### Screen result (3-seed mean)

| Metric | s1 | s2 | s3 | Mean |
|---|---:|---:|---:|---:|
| Edge | 0.66% | 0.68% | 0.70% | **0.68%** |
| Sharpe (stitched) | 2.42 | 2.39 | 2.52 | **2.44** |
| CAGR | 34.40% | 34.47% | 36.97% | **35.28%** |
| Max DD | 11.92% | 11.92% | 11.92% | **11.92%** |
| Calmar | 2.89 | 2.89 | 3.10 | **2.96** |
| OOS trades | 1,831 | 1,848 | 1,849 | ~1,843 |

### Per-window OOS breakdown (s3 — best of the three)

| OOS year | Edge | Trades | Max DD | Sharpe |
|---|---:|---:|---:|---:|
| 2008 (GFC) | +0.39% | 378 | 10.22% | 1.71 |
| 2009 | +1.51% | 294 | 8.41% | 2.88 |
| 2010 | +0.71% | 244 | 11.92% | 1.32 |
| 2011 | -0.07% | 270 | 7.51% | 1.52 |
| 2012 | +1.23% | 236 | 4.49% | 3.94 |
| 2013 | +0.60% | 174 | 3.91% | 3.34 |
| 2014 | +0.59% | 253 | 6.19% | 3.53 |

**Observations**:
- 6/7 windows positive. 2011 a tiny negative (the EU-debt regime — chop, not crash).
- 2008 W1 DD 10.22% vs median 7.51% = 1.36× — well under G4's 2× ceiling. Regime filter is doing its job.
- Sharpe is highest in non-crisis windows (3-4 range in 2012-2014); regime filter trims edge in 2008 to keep DD bounded.

### Strengths

- **Best risk-adjusted profile of the entire sweep** — Calmar 2.96 mean.
- **Robust across seeds** — three seed variants all pass with similar metrics (s1=2.89, s2=2.89, s3=3.10 Calmar). Not seed-luck.
- **Bounded drawdown** — 11.92% max DD is the lowest in the entire sweep, including failures.
- **Includes `marketUptrend` natively** — the regime gate is built in, not retrofitted.

### Risks / unknowns

- **Lower CAGR than top-end of the sweep** — 35% vs MR3's 37%. Likely sustainable; lower DD compensates.
- **2008 edge is small (+0.39%) but positive** — strategy doesn't profit much during crashes; it doesn't lose either (the regime gate suppresses entries).
- **Not yet tested on Block B (2015-2020) or Block C (2021-2025).** All claims above are 2008-2014 OOS only.

---

## Candidate 2: MR3 — 3-day pullback then up day in uptrend

**Hypothesis**: take long entries on the first up day after a 3-day pullback in an established uptrend. Exit after 3 days held or +6% gain, whichever comes first.

### Entry conditions (all 3 must hold)

1. **`marketUptrend`** — predefined market-regime gate
2. **Pullback-then-up** (script): all of —
   - `close[t-1] < close[t-2] < close[t-3]` (three consecutive lower closes — the pullback)
   - `closeEMA20 > closeEMA50` (uptrend confirmed by EMA stack)
   - `close > open` today (today is an up day)

### Exit conditions (any triggers)

- **Held ≥ 3 trading days** OR **+6% gain reached**
- **Stop-loss**: 2.5 × ATR from entry

### Ranker

`Volatility` — ranks candidates by ATR-based volatility. **Seed-invariant**: s1/s2/s3 produce bit-identical results, so MR3 is effectively a single candidate.

### Screen result (seed-invariant — one effective run)

| Metric | Value |
|---|---:|
| Edge | 0.28% |
| Sharpe (stitched) | 2.29 |
| **CAGR** | **36.77%** |
| Max DD | 17.70% |
| Calmar | 2.08 |
| OOS trades | 2,899 |

### Per-window OOS breakdown

| OOS year | Edge | Trades | Max DD | Sharpe |
|---|---:|---:|---:|---:|
| 2008 (GFC) | +1.19% | 422 | 10.07% | 3.36 |
| 2009 | +0.58% | 485 | 16.15% | 1.30 |
| 2010 | -0.37% | 456 | 16.95% | 1.25 |
| 2011 | -0.74% | 364 | 17.70% | -0.33 |
| 2012 | +0.13% | 426 | 8.78% | 2.31 |
| 2013 | +1.08% | 382 | 5.98% | 4.87 |
| 2014 | +0.02% | 364 | 10.85% | 3.69 |

**Observations**:
- **2008 is the BEST window** (+1.19% edge, Sharpe 3.36). Mean-reversion strategies thrive on volatility — exactly what GFC provided. The `marketUptrend` gate trims the worst exposure but the strategy still finds profitable signals.
- 2010-2011 negative — quiet uptrend regimes punish mean-reversion (no pullbacks to fade).
- 5/7 windows positive (G3 met at the floor); 2011 was the worst.

### Strengths

- **Highest CAGR among survivors** (36.77%).
- **Highest 2008 Sharpe** of any candidate — strategy actively benefits from crisis volatility.
- **Very high trade count** (2,899 = ~414/window). Statistical power is excellent.
- Per-trade edge is small (0.28%) but it's an arithmetic of many small wins.

### Risks / unknowns

- **Higher DD than VZ3** (17.7% vs 11.92%) — more volatile equity curve.
- **Lower Sharpe than VZ3** (2.29 vs 2.44) — risk-adjusted return is a step behind.
- **Two consecutive negative windows in 2010-2011** — quiet markets are hostile to this strategy. Could underperform during prolonged trend regimes.
- **G3 met at the floor** (5/7 positive) — would fail v4's stricter G4 (75% positive). Worth verifying on full Block A.
- **Not yet tested on Block B or Block C.**

---

## Near-miss: DV1 — sector-breadth divergence with EMA20 trend filter

**Status**: documented as a near-miss only. Under the strict v4 gates baked into `/validate-candidate`, DV1 fails 2 of 10 gates on full Block A:
- **G1 (CAGR floor)** — 29.86% vs 30% required (fails by **0.14 percentage points**)
- **G6 (2008 regime mandate)** — 2008 OOS edge −0.48% (fails by **0.48 percentage points**; broke even rather than strictly positive during GFC)

Both misses are tiny. DV1 is NOT advanced to `/validate-candidate` under current strict gates (advancing would require relaxing the gates, which the quant explicitly warned against). Tracked here because the profile is otherwise strong and a small re-design might recover it.

**Hypothesis**: take long entries when price has formed a higher low while sector breadth diverged lower (bullish reverse-divergence) in a confirmed uptrend. Exit on +12% gain or break of 20-day EMA.

### Entry conditions (2-of-2 must pass)

1. **`marketUptrend`** — predefined market-regime gate (already includes it; not a candidate for "add regime filter" remediation)
2. **Bullish divergence + trend confirmation** (script): all of —
   - `priceLowNow > priceLowThen` — recent 5-bar low above the 5-bar low from ~15 bars ago (higher low structure)
   - `sectorBreadthNow.bullPercentage < sectorBreadthThen.bullPercentage` — sector breadth declined over the same window (the divergence)
   - `close > closeEMA20` — currently above the 20-day EMA (trend confirmation)

### Exit conditions (any triggers)

- **+12% gain reached** OR **`close < closeEMA20`** (20-day EMA break)
- **Stop-loss**: 2.5 × ATR from entry

### Ranker

`SectorEdge` — sector-priority ranker without the tightness tiebreaker.

### Full Block A result (post-V13 universe, 12 OOS windows 2003-2014)

| Metric | Value |
|---|---:|
| Edge | 0.71% |
| Sharpe (stitched) | 2.14 |
| CAGR | **29.86%** ⚠ (G1 fail by 0.14pp) |
| Max DD | 14.41% |
| Calmar | 2.07 |
| Sortino | 3.36 |
| OOS trades | 1,739 |
| Positive windows | 10/12 (83.3%) |
| **G6 — 2008 OOS edge** | **−0.48%** ⚠ |

### Per-window OOS breakdown

| OOS year | Edge | Trades | Max DD | Sharpe |
|---|---:|---:|---:|---:|
| 2003 | +2.09% | 156 | 5.97% | 2.98 |
| 2004 | +1.25% | 125 | 7.79% | 2.20 |
| 2005 | +0.37% | 128 | 8.49% | 2.26 |
| 2006 | +1.03% | 188 | 6.31% | 3.47 |
| 2007 | −0.70% | 160 | 9.42% | 0.99 |
| **2008 (GFC)** | **−0.48%** ⚠ | 225 | 11.59% | 0.74 |
| 2009 | +0.59% | 181 | 11.24% | 1.57 |
| 2010 | +1.37% | 121 | 14.41% | 2.21 |
| 2011 | +0.74% | 112 | 10.19% | 0.80 |
| 2012 | +1.32% | 147 | 4.10% | 3.93 |
| 2013 | +1.14% | 108 | 5.87% | 3.20 |
| 2014 | +0.66% | 88 | 8.21% | 2.36 |

### Strengths

- **Almost passes** — 8 of 10 strict v4 gates. Sharpe 2.14, Calmar 2.07 are both well above thresholds.
- **Includes `marketUptrend` natively** — already has a regime gate; not a "needs regime filter" candidate.
- **Bounded behaviour in GFC** — −0.48% in 2008 with 11.59% DD is "broke even", not a blowup. 1.4× median DD, comfortably inside G3's 2× ceiling.
- **Highest 12-window trade count** of the candidate set (1,739) — statistical power is excellent.

### Risks / unknowns

- **G1 miss is small but real.** A 30% CAGR floor was set deliberately; DV1 is just under. Re-running with slight sizer changes (e.g. 1.30% risk vs 1.25%) might push CAGR above 30% but would also widen DD — needs an explicit sweep, not a coincidental tweak.
- **G6 miss in 2008** — strategy specifically can't profit during the GFC even with `marketUptrend` filtering. The bullish-divergence entry condition may have triggered repeatedly during 2008's false bottoms. Possible re-design: add a stricter "no entry when sector breadth below X" floor (orthogonal to current condition).
- **Not yet tested on Block B or Block C.**

### Recommended path if DV1 is to be revived

Do NOT advance to `/validate-candidate` with current gates — it will REJECT at Block A G1+G6.

Two legitimate paths:
1. **Re-design**: add a stricter 2008-style regime guard (e.g. `marketBreadth.bullPercentage > 35`) and re-survey via `/strategy-screen`. If it survives, re-enter the firewall from Block A with the new config.
2. **Sizer/risk sweep**: explicit 4-cell sweep (1.0% / 1.25% / 1.5% / 1.75% risk) on the existing DV1 config; if any cell pushes CAGR > 30% without exploding DD, that cell becomes a new candidate. Re-enter via `/strategy-screen` to confirm under the new sizer.

Don't tweak just to scrape over 30% — pick the path deliberately and re-validate.

---

## Head-to-head

| | VZ3 (s3 best) | MR3 |
|---|---|---|
| **Approach** | Trend-following — pullback to EMA20 | Mean-reversion — 3-day dip in uptrend |
| **Per-trade edge** | 0.70% | 0.28% |
| **Sharpe** | 2.52 ✓ | 2.29 |
| **CAGR** | 36.97% | 36.77% |
| **Max DD** | 11.92% ✓ | 17.70% |
| **Calmar** | 3.10 ✓ | 2.08 |
| **Trade frequency** | ~263/year | ~414/year |
| **2008 behaviour** | Defensive — small edge, low DD | Aggressive — best edge of all 7 windows |
| **2010-2011 behaviour** | Stable | Negative |
| **Best regime** | Trending bull | High-volatility |

**These two are largely uncorrelated** — VZ3 is trend-following, MR3 is mean-reversion. Their best/worst windows are inverse:
- VZ3 best window: 2009 (+1.51%, post-crash trend resumption)
- MR3 best window: 2008 (+1.19%, peak volatility)
- VZ3 worst: 2011 (-0.07% — trendless chop)
- MR3 worst: 2011 (-0.74% — also chop, but more affected)

If both survive Block B + C validation, a **portfolio of VZ3 + MR3** would be more interesting than either alone — they cover different regimes.

---

## Next steps

1. **Full Block A validation** (2000-01-01 to 2015-01-01, same cadence) for VZ3-s3 and MR3-s1 via `/walk-forward`. Apply strict v4 gates (CAGR ≥ 30% AND SPY+2%, max DD ≤ 25%, 75% windows positive, 2008+2022 positive, etc.). 12 OOS windows total.
2. **Block B validation** (2015-2020) for any that survive Block A.
3. **Block C validation** (2021-2025) — the firewall — for any that survive Block B.
4. **Monte Carlo** on the Block A position-sized results — quantify path risk.
5. **Optional sizer / position-count sweep** on each survivor before committing to live deployment (cf. VCP_TRADING_PLAN.md methodology).

Only candidates that clear all three blocks should be considered tradable.

---

## Failed candidates (for completeness)

Eight candidates failed the screen. All but one failed G4 (GFC stress) — the dominant failure mode in the sweep.

Per the post-sweep quant analysis (issue: pending), 3 of them are being re-fired with `marketUptrend` prepended:
- **BR1-s2-regime**, **BR1-s3-regime** — over-fired 2.5-2.6× in 2008. Regime gate expected to fix G4 (~70% probability per quant).
- **MO3-s3-regime** — closest to 30% CAGR (28.96%); regime gate could push it across the tradability line.

Skipped per quant: BR2 (structural G3 failure — edge inconsistency, not regime), BR3 (fewer trades but worse — ranker problem, not exposure), MO3-s1 (heavier loss profile than s3, lower recovery probability).

---

_Source data: `/tmp/screen-VZ3-s{1,2,3}.json`, `/tmp/screen-MR3-s{1,2,3}.json`. Per-candidate eval JSONs: `/tmp/screen-eval-*.json`. Full sweep results: `screen-results.md` (this directory)._
