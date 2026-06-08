---
type: source
title: MRM /strategy-screen Stage-0 reject — single-factor residual momentum is beta-delivery (2026-06-08)
summary: MRM lost Stage-0 triage to a seeded Random baseline on edge AND CAGR (2.2-2.6x) — anti-selective beta-delivery. Stage 1 not run; single-factor recipe crossed off, class stays untested (#137).
status: stable
tags: [strategy-screen, g-random, beta-delivery, idiosyncratic-momentum, reject]
sources: ["https://github.com/skrymer/trading/issues/130", "https://github.com/skrymer/trading/issues/137"]
related: ["[[mrm]]", "[[beta-delivery]]", "[[george]]", "[[2026-06-08-random-baseline-reproducibility-fix]]", "[[long-premise-in-narrow-leadership]]", "[[the-funnel]]"]
updated: 2026-06-08
---

# MRM /strategy-screen Stage-0 reject (2026-06-08)

First funnel run of the **factor-neutral idiosyncratic-RS** premise (#130): a single-factor
SPY-beta-stripped residual-momentum ranker ([[mrm]]) vs the mandatory, now-reproducibly-seeded Random
baseline ([[2026-06-08-random-baseline-reproducibility-fix]]). Run on PRD (udgaard 1.0.88), 2005-2015,
36/12/12, 7 OOS windows; byte-identical entry/exit/sizer/N/seed, only `ranker` swapped.

## Verdict — REJECT at Stage 0 (G-RANDOM FAIL on both legs)

| Metric | MRM | Random (seed 42) |
|---|---|---|
| Per-trade edge | +2.80% | **+6.21%** |
| Blended OOS CAGR | +8.95% | **+23.05%** |
| Aggregate Sharpe | 0.50 | **0.97** |
| Positive windows | 6/7 | 6/7 |
| Head-to-head per-window | — | **Random 5/7** |
| 2008 GFC window edge | −12.93% | −2.50% |

The G-RANDOM rule requires beating Random on blended CAGR **AND** per-trade edge; MRM loses both by
>2× and is beaten head-to-head in 5/7 windows. Adjudicated by the strategy-screen-analyst against the
raw result files. **[[beta-delivery]]**, and a *stronger* signature than [[george]]: George *matched*
Random on edge (no-information ranker), MRM *loses* on edge — an **anti-selective** tilt.

## Stage 1 not run — deliberately

The issue's discriminator (per-trade edge > per-window Random **p95**, persisting in narrow-leadership
2021-2023/2024) lives in a separate 2003-2025 / 31-run walk-forward. A candidate that loses to a *single*
Random draw by 2× in the broad 2005-2015 window has no path to clearing a Random p95 *threshold* later —
running 31 walk-forwards to confirm a foregone negative is the waste the screen exists to prevent.

## What the 23% Random CAGR is (and isn't) ^[inferred]

Diagnosed from the result's `spyBaselineComparison`, after retracting an initial wrong survivorship guess:
- SPY buy-and-hold over the same 2008-2015 support: **9.86% CAGR, 51% DD**. Random book: 23% CAGR, 35.6% DD.
- The gap is **structural long-beta** of the long-only engine: it cash-dodges much of 2008 (−8% vs SPY
  −38%, via partial investment + 5×ATR stops + 91-day holds), then its equal-weight (small-cap-tilted)
  selection rides the 2009 +58% / 2010 +53% rebound.
- **Not survivorship** (the universe carries 1500+ delisted names across years/sectors — corrected by the
  operator after a bad 7-famous-ticker probe) and **not alpha**. Both arms share the scaffold + universe,
  so the relative verdict is unaffected.

## What this concludes — and does NOT

- **Concludes:** the *single-factor* (SPY-beta-only) residual-momentum recipe is dead — anti-selective
  beta-delivery. Crossed off; no sizer/exit/param iteration ([[beta-delivery]] "why a sizer can't fix it").
- **Does NOT conclude:** the factor-neutral idiosyncratic-RS **class** is earned-dead. Stripping only market
  beta is the weakest neutralization — the residual still carries sector/size/value momentum (the part that
  dies in narrow leadership). The class stays **untested** ([[purpose]] #4). Next honest test = a
  **multi-factor-neutral** residual (strip sector/size/value), screened the same way — **#137**.

## Related

[[mrm]] · [[beta-delivery]] · [[george]] · [[2026-06-08-random-baseline-reproducibility-fix]] · [[long-premise-in-narrow-leadership]] · [[the-funnel]]
