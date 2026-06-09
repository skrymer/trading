---
type: entity
title: MRM (MarketResidualMomentum)
summary: Single-factor SPY-residual-momentum ranker (#130). EARNED-DEAD — fixed-engine re-run confirms anti-selective: below the entire 10-seed Random cloud on edge AND CAGR (K=0/10).
status: stable
tags: [candidate, ranker, deprecated, beta-delivery, idiosyncratic-momentum, rs-momentum]
sources: ["https://github.com/skrymer/trading/issues/130", "https://github.com/skrymer/trading/issues/137", "knowledge/wiki/sources/2026-06-08-mrm-screen-reject.md", "knowledge/wiki/sources/2026-06-09-trailing-ranker-warmup-starvation.md", "docs/adr/0018-trailing-ranker-warmup-history-is-loaded-but-never-traded.md"]
related: ["[[beta-delivery]]", "[[george]]", "[[the-funnel]]", "[[long-premise-in-narrow-leadership]]", "[[2026-06-08-random-baseline-reproducibility-fix]]", "[[2026-06-08-mrm-screen-reject]]", "[[2026-06-09-trailing-ranker-warmup-starvation]]"]
updated: 2026-06-09
---

# MRM (MarketResidualMomentum)

A long-only candidate built around a **single-factor market-residual momentum ranker**: regress each
stock's daily returns on SPY over a 504-day window (OLS), accumulate the standardized residual over a
recent 252-21 day sub-window, score `Σ(accumulation residuals) / stdev(estimation residuals)`. This is
**beta-stripped** relative strength — *not* raw price momentum (the `TrailingReturn` ranker), *not*
52-week-high anchoring ([[george]]). The entry is **tradability-only** (history / price / dollar-volume,
no signal view), so the ranker is the sole selection signal. Built for #130 to test whether a
**factor-neutral idiosyncratic-RS** premise survives narrow-leadership tape.

The strategy is the named artifact; the ranker stays strategy-neutral (it names the market-residual
mechanic). Honest naming: it is **market-residual**, not "idiosyncratic" — single-factor (SPY-beta-only)
is the strongest neutralization available in-engine (no Fama-French factor series).

## Status

**EARNED-DEAD (2026-06-09, re-run confirmed).** The single-factor recipe is anti-selective and genuinely
dead. The journey: the original #130 screen verdict (2026-06-08, "anti-selective beta-delivery") was
**voided** when the [[2026-06-09-trailing-ranker-warmup-starvation]] finding showed the walk-forward
starved this 504-day ranker of its lookback in every OOS window (no warmup buffer → unscoreable → "rank
top-N" collapsed to tie-break RNG → the screen compared two random draws, ADR 0018). The engine was fixed
(warmup-buffered loading) and the screen **re-run on the fixed engine** vs a seeded 10-draw Random
baseline (1,500-sym universe): **MRM edge 0.588 / CAGR 5.52% sits BELOW the entire Random cloud on both
legs (K=0/10)** — anti-selective, confirmed. So the void verdict's *conclusion* was right; #130 just
measured it by accident. The fix made the death **defensible**, not different. See
[[2026-06-09-rs-momentum-class-earned-dead]]. The class-level death is sealed by the multi-factor sibling
[[multifactor-residual-momentum]] (#137), which is *even more* anti-selective.

> Resolved contradiction: the void (RNG-vs-RNG) and the original beta-delivery read are reconciled — the
> fixed-engine re-run confirms anti-selection on a trustworthy measurement. History retained below; the
> artifact itself is an instructive failure mode (a plausible verdict produced by a starved ranker).

## Funnel history

| Stage | State |
|---|---|
| Build — ranker + 13 tests, TDD (#130, merged #136) | ✅ first-class ranker |
| Prereq — fix unseeded Random baseline (#130) | ✅ [[2026-06-08-random-baseline-reproducibility-fix]] |
| `/strategy-screen` 2005–2015 (Stage 0 triage) | ❌ **G-RANDOM FAIL** — lost to seeded Random on edge AND CAGR |
| Stage 1 (2003–2025 discriminator, 31 runs) | ⛔ **not run** — foregone negative (can't beat one Random draw, let alone the p95) |

## Verdicts — MRM vs the seeded Random baseline

Screen 2005–2015, 7 OOS windows, byte-identical entry / exit / sizer / `maxPositions` / seed — only
`ranker` swapped (MarketResidualMomentum vs the now-reproducibly-seeded Random):

| Metric | MRM | Random (seed 42) |
|---|---|---|
| Per-trade edge | +2.80% | **+6.21%** (Random 2.2×) |
| Blended OOS CAGR | +8.95% | **+23.05%** (Random 2.6×) |
| Aggregate Sharpe | 0.50 | **0.97** |
| Positive windows | 6/7 | 6/7 (tie) |
| Per-window head-to-head | — | **Random wins 5/7** |
| 2008 GFC window edge | **−12.93%** | −2.50% |
| OOS trades | 499 | 527 |

## Why it died

The residual-momentum tilt is **anti-selective**: a Random ordering of the same liquid basket harvests
*more* per-trade edge (+6.21% vs +2.80%) for free, so MRM's selection actively subtracts from
entry-universe beta. This is **[[beta-delivery]]**, and a *stronger* signature than [[george]] (which only
*matched* Random on edge — a no-information ranker; MRM *loses* on edge). It also reproduces George's GFC
liability — the tilt concentrates into the names that crater hardest in 2008.

**The 23% Random CAGR is not alpha or survivorship** — it is structural long-beta of the long-only engine:
the book cash-dodges much of 2008 (−8% vs SPY −38%, via partial investment + 5×ATR stops + 91-day holds),
then its equal-weight (small-cap-tilted) selection rides the 2009 (+58%) / 2010 (+53%) rebound; SPY
buy-and-hold was 9.86% over the same support. Both arms share that scaffold and the same delisting-inclusive
universe, so the **relative** verdict (MRM loses to Random) is clean.

## Failure modes hit

- **[[beta-delivery]]** — second screen-stage instance, anti-selective variant. First candidate
  adjudicated against the reproducibly-seeded baseline.

## Reusable findings (durable)

- **Single-factor neutralization is too coarse to call "idiosyncratic."** Stripping only SPY beta leaves
  sector/size/value momentum in the residual — the factor-momentum component theory predicts dies in
  narrow leadership. A REJECT here crosses off the *recipe*, not the *class*; the class verdict stays
  untested (#137 = multi-factor-neutral, the real next test).
- **No sizer/exit/parameter tweak rescues a beta-delivery reject** ([[beta-delivery]] "why a sizer can't
  fix it") — a structurally different entry/selection premise is required.

## Related

[[beta-delivery]] · [[george]] · [[the-funnel]] · [[long-premise-in-narrow-leadership]] · [[2026-06-08-mrm-screen-reject]] · [[purpose]]
