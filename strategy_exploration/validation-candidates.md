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

## Strategic framing — regime-conditional portfolio (quant 2026-05-28)

After VZ3's PASS A+B / FAIL C result, the quant called the direction:

> Uber-strategy search has hit diminishing returns. The set of "single rule with `E[edge | any regime] > 0`" is approximately empty for any premise rich enough to have edge in one regime. Going higher than VCP requires **orthogonality (regime-specialists), not depth (more conditions on a single premise)**.

**Current state vs portfolio transition threshold**: **1/4 criteria met** (VCP only). Threshold requires ≥3 components passing relaxed regime-conditional bar + pairwise in-regime correlation ≤ 0.3 + combined regime coverage ≥ 70% + classifier passes v4. **Stay in component-search mode.**

**Search direction — fill regime gaps**:
| Gap | Status | Notes |
|---|---|---|
| Broad-participation trending | **Filled** (VCP) | Stop firing candidates here; would be duplication, not diversification |
| Narrow-leadership / Mag-7 trending | **Empty (highest-value gap)** | Where VZ3 died. Hardest to fill — historically thin edges here |
| Range-bound / low-breadth chop | **Empty** | Where VCP underperforms but doesn't bleed |
| Crisis / high-volatility | **Empty** | Defensive component with mediocre edge but anti-correlation value |

**Component bar (relaxed, not v4)**:
- Pass target regime block all 10 gates (cleanly, not borderline)
- Flat-or-better outside target regime (DD ≤ 1.5× in-regime DD)
- G6 crash survival NON-NEGOTIABLE for every component (classifier confidence is lowest in crises)
- Edge-stability within target regime (no sign-flips across sub-periods)
- Design-isolation (no feature-set reuse across components)

Under this bar, **VZ3 may actually pass as a broad-participation-trending component** despite v4 REJECTION (Block A+B cleared cleanly; Block C is "out of regime"). Not actionable today though — broad-participation trending is already covered by VCP.

See [`project_regime_conditional_portfolio_framework.md`](../../.claude/projects/-home-skrymer-Development-git-trading/memory/project_regime_conditional_portfolio_framework.md) for the full framework.

---

## Firewall outcome (2026-05-28) — both REJECTED under single-strategy v4

- **VZ3-s3 baseline (1.25%/2.0nAtr)**: PASS Block A, PASS Block B-corrected, **FAIL Block C**. Per-trade edge inverted +0.48% → −0.11% in 2024. Mean-reversion-on-pullback breaks in narrow-leadership trending tape. Quant verdict (post-firewall): not iterable on this axis — adding a regime filter would be IS-fitting to the single Block C OOS window.
- **VZ3-s3 baseline 25y refined-firewall smoke (2026-05-28)**: ~~initially read as TRADABLE under refined framework~~. **INVALIDATED 2026-05-29** — verdict was carried by an off-by-one bug in the inline higher-low script (`ref[ref.size - 10]` against an inclusive-range `quotesInRange` result, reading 9 trading days back instead of 10). The corrected lookback (Idunn) fails Block B. Treat VZ3-s3 TRADABLE as a bug-induced artifact; do NOT ship the buggy lookback=9 even though it passed. See `feedback_parameter_fragility_must_be_verified.md`.
- **MR3-s1 baseline (1.25%/2.0nAtr)**: **FAIL Block A** with multi-dimensional drift (G3 worst-window DD 20.47% > 20%, G4 8/11 windows positive < 75%, G5 CoV 1.77 > 1.5 — three tight failures, NEAR_MISS bucket capped at 2). Quant levers (ATR-percentile floor + min pullback depth + held≥5 exit) noted but not yet built.

## Mean-reversion-on-pullback — DEPRECATED for current macro regime (2026-05-29)

Per quant 11th consultation: **doubly-condemned and abandoned for current macro regime** (post-2020 narrow-leadership equity tape).

**Two-strike Bayesian update**:
- **Strike 1 — Regime-weak in narrow-leadership tape**: VZ3-s3 Block C edge inverted +0.48% → −0.11% in 2024; MR3 failed Block A on multi-dimensional drift; DV1 similar. Flow rotates away from laggards before mean-reversion fires; leaders' pullbacks are too shallow to touch the entry EMA.
- **Strike 2 — Parameter-brittle at condition-design level**: Idunn brittleness sweep (lookback ∈ {8,9,10,11}) showed Aliased Regime Sensitivity (ARS) — non-monotone pass/fail across the parameter neighborhood, per-window edges flipping sign at regime gates under 1-day shifts. The "today's low vs N-bars-ago low" sub-condition is structurally an anti-pattern for noisy regime-variant data.

**Two independent failure modes pointing the same direction** = the alpha hypothesis is wrong for current market structure, not the encoding.

**Do not iterate.** Do not redesign the pullback-detection sub-condition. Do not add new regime filters. Move to a different premise class entirely.

**Revisit trigger**: market structure returns to broad-participation bull markets — objective measure: market breadth EMA10 > 60% sustained for 6+ months OR advance-decline line in new highs sustained. Until then, parking this premise class.

See [`feedback_mean_reversion_pullback_known_weakness.md`](../../.claude/projects/-home-skrymer-Development-git-trading/memory/feedback_mean_reversion_pullback_known_weakness.md) and [`feedback_aliased_regime_sensitivity.md`](../../.claude/projects/-home-skrymer-Development-git-trading/memory/feedback_aliased_regime_sensitivity.md).

## Idunn (promoted VZ3-s3, 2026-05-29) — REJECTED at all 4 lookback values via ARS

VZ3-s3's inline `script` conditions were promoted to first-class registered conditions (`Pullback2of3Condition` + `PercentGainExit`) and a Norse-god-named strategy (`IdunnEntryStrategy` + `IdunnExitStrategy`). During promotion, the off-by-one in the higher-low lookback was identified and corrected — the original inline script's `ref[ref.size - 10]` was effectively reading 9 trading days back; the corrected formula reads 10.

**Block A**: PASS 10/10 — CAGR 41.44%, Max DD 11.70%, Sharpe 2.70, Calmar 3.54, edge +0.86%, 2702 trades. BETTER than VZ3-s3's Block A under the buggy formula (CAGR 36.02%, Sharpe 2.54, edge +0.62%).

**Block B**: FAIL 7/10 — CAGR 29.36% (0.64pp short of 30% G1), G5 CoV 2.86 (vs 1.5 cap), G7 2018-Q4 chop edge −0.45% (vs > 0 mandate). Per-trade edge +0.12% (vs VZ3-s3 buggy +0.48%).

**The 1-day lookback shift produced an enormous swing**: 2018-Q4 chop edge flipped from +0.21% (buggy) to −0.45% (corrected) on essentially the same trade population. The strategy's edge isn't tracking the "higher low vs ~2 weeks ago" structural feature — it's tracking incidental bar alignment.

**Verdict**: REJECTED at Block B per the refined framework. Idunn is closed.

**Brittleness sweep complete (2026-05-29)**: per quant's 10th consultation, ran lookback ∈ {8, 9, 11} via the `pullback2of3` custom-strategy path. Verdict matrix:

| lookback | Block A | Block B | Block B CAGR | Block B edge |
|---|:---:|:---:|---:|---:|
| **8** | PASS 10/10 | **PASS 10/10** | 31.44% | +0.45% |
| **9** | PASS 10/10 | **FAIL G6** (2020 COVID OOS edge −0.07%) | 36.16% | +0.36% |
| **10** (nominal) | PASS 10/10 | **FAIL G1+G5+G7** (2018-Q4 chop edge −0.45%) | 29.36% | +0.12% |
| **11** | PASS 10/10 | **PASS 10/10** | 30.65% | +0.48% |

**Pattern**: Aliased Regime Sensitivity (ARS) — aggregate edges in a noise band (~3σ spread), but per-window edges flip sign at regime gates under 1-day shifts, AND pass/fail across the neighborhood is non-monotone. Quant calls this **strictly worse than simple brittleness**: there is no robust operating point because the parameter dimension is the wrong abstraction for the alpha hypothesis.

**G13 ±1 step would have correctly rejected ALL 4 nominal values** (every nominal sits one step from a failing neighbor). Empirically validates G13 as a binding gate.

**Promotion fidelity is NOT bit-equivalent.** `pullback2of3(lookbackDays=9)` diverges from the original VZ3-s3 inline-script lb=9-via-bug (which passed 10/10): the dynamic calendar buffer (`max(20, lookbackDays*2 + 10)` = 28 days at lb=9 vs hardcoded 20 in the inline script) admits ~2 more bars per stock-date, shifting the trade population enough to flip the 2020 COVID edge sign. The buffer is a hidden tunable. Issue #58 (G14 — Implementation Invariance) tracks this gap.

**Sweep informed framework expansion**:
- Issue #57 (G13 — Parameter Robustness): empirically validated at ±1 step on discrete params
- Issue #58 (G14 — Implementation Invariance): separate gate for engine-side constant stability
- Issue #59 (`/condition-screen` skill): diagnostic pre-screen that would have killed `pullback2of3` at design time via ARS detection

**Idunn definitively closed.** Even the standalone-passing values (lb=8, lb=11) are not deployable — ARS means there's no robust operating point.

**Search-area finding**: VZ3 + MR3 + Idunn are all mean-reversion-in-uptrend variants. All REJECTED for different reasons (VZ3 fails late under refined framework + bug invalidation, MR3 fails Block A multi-dim drift, Idunn fails Block B parameter brittleness). Together they signal the mean-reversion-on-pullback design space is exhausted on the current cleaned universe AND that any future promotion must include a parameter-stability check before TRADABLE is accepted.

See [`VZ3_STRATEGY_DEVELOPMENT.md`](VZ3_STRATEGY_DEVELOPMENT.md) and [`MR3_STRATEGY_DEVELOPMENT.md`](MR3_STRATEGY_DEVELOPMENT.md) for per-candidate state.

### VZ3 — sizer sweep + 2019 drag fix

**Diagnosis**: VZ3's gates pass cleanly; CAGR is the constrained dimension. AtrRisk(1.25%, 2.0×ATR) leaves DD headroom unused (Block A 11.92%, Block B ~6%) — sizer is under-deployed for the regime. Lifting risk-per-trade is the lowest-risk lever.

**Sizer sweep grid** (5 variants, Block A only first; winner re-fires Block B + C):

| Variant | nAtr | Risk % | Rationale |
|---|---|---|---|
| **Current** | 2.0 | 1.25 | Baseline — already in `/tmp/validate-VZ3-s3-blockA.json` |
| **A** | 2.0 | 1.50 | +20% — likely closes CAGR gap without DD blow-up |
| **B** | 2.0 | 1.75 | **First pick** — projected Block A DD ~16.7%, Block B ~6.6%, well inside G2 cap |
| **C** | 2.0 | 2.00 | Stress test — confirms ceiling before DD breach |
| **D** | 1.75 | 1.50 | Tighter stop + more risk — alternate axis |

**2019 drag fix** (independent of sizer): replace the `+10% gain target` exit with one of:
1. **Trailing EMA20 break** (`close < closeEMA20` after entry day + N) — lets winners run during persistent trend windows
2. **Chandelier 3×ATR** trailing stop — same intent, different mechanic

Reason: the +10% target capped many 2019 trades short of the late-2019 momentum extension. Surface as a follow-up sweep after the sizer winner is locked.

### MR3 — quiet-regime filter + minimum pullback depth + held≥5 exit

**Diagnosis**: REJECTED on Block B with three correlated structural failures (not a single-axis fix like VZ3). Edge bled when entries fired in low-volatility chop where the 3-day dip wasn't deep enough to mean-revert. The mean-reversion premise needs sharper qualification, not more capital.

| Lever | Change | Why |
|---|---|---|
| **Stock-level ATR-percentile floor** | Require trailing 252-day `ATR/close` ≥ 30th percentile of stock's own history | Suppresses entries in the stock's own quiet regime — where mean-reversion edge is weakest |
| **Minimum pullback depth** | Require cumulative 3-day return ≤ −3% | Rejects shallow dips that don't qualify as a real pullback |
| **Exit** | Held ≥ 5 days + 2×ATR stop (was held ≥ 3 days + 2.5×ATR) | Gives the reversion more time to materialize; tighter stop reduces small losers |

Lower priority than VZ3 — MR3 needs script-condition promotion + Block-A re-survey via `/strategy-screen` before re-entering the firewall.

### Pair potential

If both candidates eventually clear the firewall (post-promotion + post-improvement), the equity-curve correlation is likely 0.4-0.6 (VZ3 trend-following vs. MR3 mean-reversion, inverse regime preferences). Projected pair Sharpe 2.7-3.2 with shared capital — meaningfully higher than either solo. Pair backtest is a Phase-2 follow-up after both candidates are independently TRADABLE.

---

## Next steps

**Component search — target each remaining regime gap:**

1. **Mjolnir entry + VCP exit (MJV)** _(screening 2026-05-28)_ — `MjolnirEntryStrategy` + `VcpExitStrategy` at VZ3 baseline sizer (1.25%/2.0nAtr), `DistanceFrom10Ema` ranker, seed 1, 2005-2015. Different premise (trend/momentum). Likely targets broad-participation trending — caveat: if it ends up in the same regime as VCP, it's duplication. Will assess regime fit from screen window-level edges.
2. **Narrow-leadership candidate (HIGHEST-VALUE GAP, undefined)** — needs a structurally different entry premise that profits in Mag-7-concentrated tape. Brainstorm directions: leadership-concentration scanner (top-N return contribution to SPY), relative-strength rank vs sector, breakout-of-narrow-range when leadership widens. Quant: "hardest to fill, highest value." Skip until VCP+Mjolnir+VCP-exit picture is clear.
3. **Chop / range-bound candidate (undefined)** — counter-trend or mean-reversion under explicit chop regime (`!marketUptrend AND !marketDowntrend` or similar). MR3 with filter refinements (ATR-percentile floor + min-pullback-depth + held≥5 exit) is a candidate IF the levers actually move the failure-mode dial. Re-survey first before firewall.
4. **Crisis / defensive candidate (undefined)** — anti-correlation play. Brainstorm: VIX-based gate + index-short or sector-rotation-to-defensive (utilities, staples). Lowest priority (smallest edge expected) but highest portfolio-level value.

**Process gates** (apply when any candidate clears the relaxed regime-conditional bar):
- **Script-condition promotion** — inline scripts must be promoted via `/create-condition` before TRADABLE→live; firewall validates the exact config that will ship.
- **Block C cadence fix (separate ADR)** — current 36/12/12 on 5y range fits only 1 OOS window. REFERENCE.md claims 2. Either widen Block C or accept 1-window limitation explicitly. Per quant: do as a future-candidate ADR, not a rescue maneuver for any single candidate.

**Do NOT do (variance-mining traps)**:
- Iterate VZ3 with regime filters bolted on — it's IS-fitting to the single Block C OOS window
- Sweep more VZ3 sizer variants — quant verdict was clean
- Fire candidates targeting broad-participation trending — VCP covers that regime; duplication, not diversification
- Tune portfolio weights to smooth equity curves — explicit anti-pattern per the framework

**When to revisit framework**: if after ~6 more candidate runs targeted at the regime gaps we have <2 additional survivors, regime-component-portfolio frame isn't producing components fast enough. Stay with VCP solo and revisit in 6 months with different premises.

---

## Failed candidates (for completeness)

### MJV-s1 — Mjolnir entry + VCP exit (2026-05-28)
- Per-trade edge +2.50% (high) but **bimodally distributed** — 5/7 windows < +1.5%, 2/7 windows > +8% (2009 +49.67% / 2013 +50.84% CAGR carrying five negative-CAGR years).
- Aggregate CAGR 7.43% (geometric stitch of the lumpy sequence), Sharpe 0.51 (fails G2), Calmar 0.28, max DD 26.70%.
- Quant verdict: **regime-conditional momentum entry without explicit regime gating = lottery**. Edge is regime-beta on momentum factor, not alpha. Iteration via faster exit or sizer sweep explicitly banned (faster exit destroys the W2/W6 runners; sizer sweep is variance-mining trap → ruined account on a 5/7-negative-years engine).
- Framework-fit: **doesn't fill any unfilled regime gap** — duplicates VCP's broad-trending footprint with worse path quality. REJECTED no iteration.
- See [`feedback_lottery_screen_diagnostic.md`](../../.claude/projects/-home-skrymer-Development-git-trading/memory/feedback_lottery_screen_diagnostic.md) for the reusable screen-stage rejection rule this case produced.

### Original /strategy-screen sweep (2026-05-27)

Eight candidates failed the screen. All but one failed G4 (GFC stress) — the dominant failure mode in the sweep.

Per the post-sweep quant analysis (issue: pending), 3 of them are being re-fired with `marketUptrend` prepended:
- **BR1-s2-regime**, **BR1-s3-regime** — over-fired 2.5-2.6× in 2008. Regime gate expected to fix G4 (~70% probability per quant).
- **MO3-s3-regime** — closest to 30% CAGR (28.96%); regime gate could push it across the tradability line.

Skipped per quant: BR2 (structural G3 failure — edge inconsistency, not regime), BR3 (fewer trades but worse — ranker problem, not exposure), MO3-s1 (heavier loss profile than s3, lower recovery probability).

---

_Source data: `/tmp/screen-VZ3-s{1,2,3}.json`, `/tmp/screen-MR3-s{1,2,3}.json`. Per-candidate eval JSONs: `/tmp/screen-eval-*.json`. Full sweep results: `screen-results.md` (this directory)._
