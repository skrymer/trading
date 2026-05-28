# VZ3 Strategy Development

_Status: **REJECTED** at Block C (2026-05-28). PASS Block A, PASS Block B-corrected, FAIL Block C — per-trade edge inverted +0.48% → −0.11% in 2024. Mean-reversion-on-pullback breaks in narrow-leadership trending tape. **Not iterable** — adding a regime filter would be IS-fitting to the single Block C OOS window. Search exhausted in this direction._

## Hypothesis

Long entries on pullbacks to the 20-day EMA in a confirmed uptrend. The thesis: when price retraces to the EMA20 in an established trend with rising EMA and higher-low structure, the pullback is likely to fail and the trend resumes. Trend-following with mean-reversion-style entry timing.

## Current configuration (frozen 2026-05-28)

### Entry strategy

3-of-3 conditions must pass:

1. **`marketUptrend`** — predefined market-regime gate (breadth-EMA based)
2. **Pullback-to-EMA20 with structure** (inline `script`): at least 2 of the following 3 sub-conditions hold —
   - `|close − closeEMA20| ≤ 1.5 × ATR` (price within 1.5 ATR of the 20-day EMA = "in the zone")
   - `low > low[-10]` (higher low vs 10 days ago = no breakdown)
   - `closeEMA20 > closeEMA20[-1]` (EMA20 rising = local trend intact)

### Exit strategy

Any of:
- **+10% gain reached** (inline `script`)
- **`close < closeEMA50`** (50-day EMA break, inline `script`)
- **Stop-loss**: 2.5 × ATR from entry (`stopLoss` condition)

### Ranker

`DistanceFrom10Ema` — ranks candidates by proximity to the 10-day EMA. Lower distance = higher rank. Seed-tie-break behavior observed across s1/s2/s3.

### Position sizing (baseline)

```
AtrRisk(riskPercentage=1.25%, nAtr=2.0)
maxPositions=15, leverageRatio=1.0, entryDelayDays=1
startingCapital=10000
```

### Source request

`/tmp/screen-req-VZ3-s3.json` — random seed 3 (best of 3-seed screen).

## /strategy-screen results (2005-2015, 7 OOS windows)

3-seed mean: Edge 0.62% / Sharpe 2.45 / **CAGR 33.43%** / MaxDD 12.45% / Calmar 2.78. Best seed: s3 — edge 0.70% / Sharpe 2.52 / **CAGR 36.97%** / MaxDD 11.92% / Calmar 3.10. All 5 screen gates passed.

## Firewall results (2026-05-28)

### Block A (2000-2014, 11 OOS windows) — **PASS**

| Gate | Value | Threshold | Status |
|---|---|---|---|
| G1 CAGR | 36.02% | ≥ 30% | PASS |
| G2 agg DD | 11.92% | ≤ 25% | PASS |
| G3 worst-window DD | 11.92% | ≤ 20% | PASS |
| G4 positive pct | 10/11 = 90.9% | ≥ 75% | PASS |
| G5 CoV edge | 0.71 | ≤ 1.5 | PASS |
| G6 2008 GFC | edge = 0.394 | > 0 | PASS |
| G7 chop | 2004 = 0.472 | ≥ 1 of {2004, 2011, 2015-H1} | PASS |
| G8 min trades | 174 | ≥ 30 per window | PASS |
| G9 Sharpe/Calmar | 2.54 / 3.02 | ≥ 0.8 / ≥ 0.5 | PASS |
| G12 block trades | 2761 | ≥ 100 | PASS |

Clean Block A pass. All gates with comfortable margins.

### Block B (2014-2020, 3 OOS windows under OBSOLETE endDate=2020-12-31) — **FAIL**

| Gate | Value | Threshold | Status |
|---|---|---|---|
| **G1 CAGR** | **28.39%** | **≥ 30%** | **FAIL** |
| G2 agg DD | 4.74% | ≤ 25% | PASS |
| G3 worst-window DD | 4.74% | ≤ 20% | PASS |
| G4a no-blowup (N<4) | worst 8.84% | ≥ −5% | PASS |
| **G4b block CAGR** | **28.39%** | **≥ 30%** | **FAIL** |
| G5 CoV edge | 0.70 | ≤ 1.5 | PASS |
| **G6 2020 COVID** | **None** | **> 0** | **FAIL** (structural — no 2020 OOS window) |
| G7 chop | 2018-Q4 = 0.345 | ≥ 1 of {2015-H2, 2018-Q4} | PASS |
| G8 min trades | 192 | ≥ 30 per window | PASS |
| G9 Sharpe/Calmar | 2.92 / 5.99 | ≥ 0.8 / ≥ 0.5 | PASS |
| G12 block trades | 615 | ≥ 100 | PASS |

**Two failures, distinct causes:**

1. **G1/G4b CAGR shortfall** — **the failure we're addressing**: 28.39% vs 30% threshold = 1.61pp short. The sizer sweep targets this gate specifically; lifting risk-% redeploys the unused DD headroom into CAGR.
2. **G6 2020 COVID = None** (structural artifact, not a strategy property): Block B endDate was 2020-12-31 at runtime. Under 36/12/12 cadence, the last OOS window terminates 2020-01-01 — meaning **no OOS window covers 2020 at all**. Same design bug we then fixed in the skill (endDate extended to 2021-06-30). The 2020 COVID test never actually ran for VZ3-s3 under this artifact, so the G6 verdict is uninformative. **Action item**: re-fire VZ3-s3 baseline (1.25%/2.0nAtr) Block B on the corrected endDate so the sweep winner has a clean apples-to-apples baseline to compare against.

### Per-window Block B detail

| Window | OOS range | CAGR | Edge | Trades | DD |
|---|---|---:|---:|---:|---:|
| W1 | 2017-2018 | 46.01% | +1.04% | 192 | 3.29% |
| W2 | 2018-2019 | 32.71% | +0.34% | 221 | 4.74% |
| W3 | 2019-2020 | 8.84% | +0.34% | 202 | 3.63% |

W3 (the 2019 trend-extension year) is the drag — only 8.84% CAGR. Profit-target capped many extended winners. Quant flagged this as the structural cause of the CAGR shortfall.

### Block B-corrected (2014-2021H1, 4 OOS windows) — **PASS** (added 2026-05-28)

After fixing the Block B endDate to 2021-06-30 (so W4 OOS covers 2020), VZ3-s3 baseline (1.25%/2.0nAtr) actually **passes Block B cleanly — all 10 gates**:

| Gate | Value | Threshold | Status |
|---|---|---|---|
| G1 CAGR | 36.33% | ≥ 30% | PASS |
| G2 agg DD | 8.61% | ≤ 25% | PASS |
| G3 worst-window DD | n/a | ≤ 20% | PASS |
| G4a no-blowup (N<4) | n/a | ≥ −5% | PASS |
| G4b block CAGR | 36.33% | ≥ 30% | PASS |
| G5 CoV edge | n/a | ≤ 1.5 | PASS |
| G6 2020 COVID | edge = +0.309% | > 0 | **PASS** |
| G7 chop (2018-Q4) | +0.215 | ≥ 1 of {2015-H2, 2018-Q4} | PASS |
| G8 min trades | n/a | ≥ 30 per window | PASS |
| G9 Sharpe/Calmar | 2.32 / 4.22 | ≥ 0.8 / ≥ 0.5 | PASS |
| G12 block trades | n/a | ≥ 100 | PASS |

The original "FAIL on G1" was entirely a structural artifact of the obsolete endDate — the 2020 OOS window (the V-recovery year) is what lifts block-aggregate CAGR from 28.39% to 36.33%.

### Block C (2021-2025, 1 OOS window = 2024 only) — **FAIL**

| Gate | Value | Threshold | Status |
|---|---|---|---|
| **G4b block CAGR** | **4.26%** | ≥ 30% | **FAIL** (real) |
| **G9 Sharpe/Calmar** | **0.62 / 0.46** | ≥ 0.8 / ≥ 0.5 | **FAIL** (real) |
| G5 CoV edge | null (1 window) | ≤ 1.5 | structural N/A |
| G6 2022 inflation bear | None (2022 in IS warmup) | > 0 | structural N/A |
| G2 DD agg | 9.29% | ≤ 25% | PASS |
| G3 worst-window DD | 9.29% | ≤ 20% | PASS |
| G4a no-blowup | worst window 4.26% | ≥ −5% | PASS |
| G8 min trades | 245 | ≥ 30 | PASS |
| G12 block trades | 245 | ≥ 100 | PASS |

**Per-trade edge inverted sign:** Block A +0.620% → Block B-corrected +0.478% → **Block C −0.106%**. This is the diagnostic finding — sample noise would compress toward zero, not invert. Per quant: "~2.5-3σ shift on 245 trades, combined with 3.7× Sharpe collapse (2.32 → 0.62), joint probability of noise is low."

**Mechanism (quant 2026-05-28):** narrow-leadership / Mag-7-concentrated tape in 2024 breaks pullback-to-EMA20. Flow rotates away from laggards before mean-reversion fires; leaders' pullbacks are too shallow to touch EMA20. Strategy mechanic and observed failure align — diagnostic, not noise.

## Verdict

**REJECTED** — passes Block A + Block B (corrected), fails Block C on real gates (G4b + G9) with edge sign-flip.

### Why we're not iterating

Per quant 2026-05-28:

1. **Three independent real gate failures with a clean mechanism.** G4b, G9, and edge sign-flip aren't borderline — qualitatively different from Block A+B. Trust the firewall.
2. **Adding a regime filter is IS-fitting** to the single Block C OOS window. "Block C failed, let me add a breadth-divergence gate and re-test on Block C" is single-block tuning — same variance-mining defect as the rejected sizer sweep variants.
3. **v2/v3 goal-search outcomes already exhausted the VCP-adjacent pullback-in-uptrend space.** Bolting filters on an inline-script PENDING-PROMOTION candidate digs the hole deeper.

**Filed**: mean-reversion-on-pullback strategies have a known weakness in low-breadth trending markets. They won't clear v4 without a **structurally different entry premise** — not a filter added after the fact.

## Sizer sweep findings (kept for record, not actionable for VZ3)

The original sweep was motivated by what we then thought was a Block B G1 shortfall. Once the Block B boundary was fixed, baseline was already passing Block B at 1.25% — the sweep no longer had a rescue motivation, and the sweep winner (B at 1.75%) failed the corrected Block B G6.

| Variant | Sizer | Block A CAGR | Block A DD | Block B G6 (per-trade edge) | Status |
|---|---|---:|---:|---:|---|
| Baseline | 1.25% / 2.0 nAtr | 36.02% | 11.92% | **+0.309%** ✓ | Used in final firewall |
| A | 1.50% / 2.0 nAtr | 40.25% | 10.32% | not tested | n/a |
| B (sweep winner) | 1.75% / 2.0 nAtr | 46.64% | 10.89% | **−0.046%** ✗ | REJECTED on Block B G6 |
| C | 2.00% / 2.0 nAtr | 46.44% | 11.11% | not tested | curve broke at 2.00% |
| D | 1.50% / 1.75 nAtr | 42.48% | 12.43% | not tested | Pareto-dominated |

Standalone finding: **per-trade edge inverts at higher risk-% in crash regimes** even when headline CAGR rises. At fixed entry/exit logic, R-multiples should be sizer-invariant — a sign-flip on near-identical trade counts (299 vs 297) means the portfolio is selecting a different trade set under capital/leverage constraints during the crash leg. Same defect G6 catches in the COVID window, would catch in any crash regime. Useful general lesson; doesn't change VZ3's verdict.

## Improvement recommendations (historical record; superseded by Block C rejection)

These were the quant's pre-Block-C recommendations. Block C's failure made them moot. **Kept for record only — not actionable for VZ3.**

The original framing: gates pass cleanly on Block A, CAGR is the constrained dimension, DD headroom is unused — so sweep the sizer. The sweep produced clean Pareto data on Block A but didn't address the 2024 narrow-leadership regime that ultimately killed the candidate. The 2019-drag-fix idea (trailing EMA20 / chandelier 3×ATR replacing the +10% target) was also pre-Block-C and is similarly moot.

## Structural findings worth keeping

### Block C cadence math
Block C is 2021-01-01 → 2025-12-31. Under 36/12/12 cadence, only 1 OOS window fits (2024-01 → 2025-01). The skill REFERENCE.md claims 2; that's an inaccuracy worth fixing in a separate ADR/skill change. G5 (CoV) and G6 (2022) are structurally untestable in this configuration — both became N/A and are correctly ignored. **Per quant: don't redesign Block C as a rescue for VZ3; if changed, do it as a future-candidate ADR.**

### G6 catches sizer-induced edge inversion (general lesson)
The sweep winner B (1.75%) had headline-CAGR uplift on Block A but per-trade edge **inverted sign** in the COVID OOS window vs the 1.25% baseline (+0.309% → −0.046%) on near-identical trade counts. Quant explanation: at fixed entry/exit logic, R-multiples should be sizer-invariant — a sign-flip means the portfolio is selecting a different trade set under capital/leverage constraints on the crash leg. G6's per-trade-edge formulation is the right invariant; headline CAGR alone would have misled. Applies to any future position-sized firewall validation, not just VZ3.

### Inline-script PENDING-PROMOTION (moot now)
The strategy's entry pullback structure and exit (+10% / EMA50-break) are inline `{"type": "script"}` conditions. Would have required `/create-condition` promotion before TRADABLE could become final. Doesn't matter now (REJECTED).

## Regime-conditional re-classification (quant 2026-05-28)

Under the **regime-conditional portfolio framework** (filed in memory; project doc), VZ3's verdict reads differently than under single-strategy v4:

| Bar | Result |
|---|---|
| Single-strategy v4 | **REJECTED** (Block C edge sign-flip) |
| Regime-conditional component | **Likely passes** — Block A+B cleared cleanly in target regime (broad-participation trending); Block C is "out of regime" (narrow-leadership), not in-regime failure |

VZ3's target regime is **broad-participation trending bull** (where breadth is high and pullbacks mean-revert across the population). Verified target-regime evidence:
- Block A (2000-2014): +0.62% per-trade edge, 36% CAGR, 10/11 windows positive
- Block B-corrected (2014-2021H1, incl COVID): +0.48% edge, 36% CAGR, 4/4 windows positive
- Block C (2024 only, narrow-leadership): −0.11% edge, 4.26% CAGR — out of target regime

**Crash survival** (G6 non-negotiable for any component): VZ3 PASSES — 2008 OOS edge +0.39%, 2020 OOS edge +0.31%. Doesn't bleed in crashes.

**Not actionable today**: broad-participation trending is already covered by VCP. Adding VZ3 as a component there would be **duplication, not diversification** — explicitly flagged as a search anti-pattern in the framework. The search direction is currently regimes VCP doesn't cover: narrow-leadership, chop, crisis.

If VCP ever fails or is replaced, or if VZ3's premise can be shown to materially diversify VCP's broad-participation trades (correlation analysis on overlapping target-regime windows), VZ3 could re-enter the conversation as a portfolio component without needing to clear the original v4 gates.

## Roster status

- **Verdict**: REJECTED. Not iterable on this search axis.
- **Filed lesson**: mean-reversion-on-pullback strategies have a known weakness in low-breadth trending markets and won't clear v4 without a **structurally different entry premise** (not a filter bolted on).
- **Sister candidate**: MR3 is also REJECTED on Block A (multi-dimensional drift). Different failure mode (3 tight failures in Block A vs edge-inversion in Block C), but both signal that the mean-reversion-in-uptrend search area is mostly exhausted on the current universe.
- **Source data**: `/tmp/screen-req-VZ3-s3.json`, `/tmp/validate-VZ3-s3-blockA.json`, `/tmp/validate-VZ3-s3-blockB.json` (obsolete endDate), `/tmp/validate-VZ3-s3-blockB-corrected.json` (corrected endDate, PASS), `/tmp/validate-VZ3-s3-blockC.json`, `/tmp/validate-VZ3-s3-final-summary.json`, `strategy_exploration/validate-VZ3-s3-final.md`
