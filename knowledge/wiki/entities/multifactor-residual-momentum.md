---
type: entity
title: MultiFactor-Residual Momentum (#137)
summary: Market+sector-residual-momentum ranker (#137). EARNED-DEAD — below the entire 10-seed Random cloud on edge AND CAGR (K=0/10), worse than single-factor MRM. Closes the RS-momentum class.
status: stable
tags: [candidate, ranker, deprecated, beta-delivery, idiosyncratic-momentum, rs-momentum, class-killer]
sources: ["https://github.com/skrymer/trading/issues/137", "knowledge/wiki/entities/multifactor-residual-momentum.request.json", "knowledge/wiki/sources/2026-06-09-rs-momentum-class-earned-dead.md", "docs/adr/0018-trailing-ranker-warmup-history-is-loaded-but-never-traded.md"]
related: ["[[mrm]]", "[[beta-delivery]]", "[[george]]", "[[the-funnel]]", "[[purpose]]", "[[long-premise-in-narrow-leadership]]", "[[2026-06-09-rs-momentum-class-earned-dead]]", "[[2026-06-09-trailing-ranker-warmup-starvation]]"]
updated: 2026-06-09
---

# MultiFactor-Residual Momentum (#137)

The honest test of the **factor-neutral idiosyncratic-RS** class: a ranker that strips BOTH the market
(SPY) and the stock's **sector** (SPDR ETF) co-movement before reading residual momentum — multivariate
OLS of daily returns on `[SPY, sectorETF]` over a 504-day window, accumulate the standardized residual
over the recent 252-21 day sub-window. The multi-factor generalization of single-factor [[mrm]], built
because single-factor strips only market beta, leaving sector/size/value momentum in the residual — the
part theory blamed for dying in narrow leadership. If that theory held, stripping sector should *clean*
the signal. It did the opposite.

The strategy is the named artifact; the ranker stays strategy-neutral (it names the market+sector-residual
mechanic). Only the residual is consumed — the per-factor betas are never exposed (SPY~cap-weighted-sector
collinearity makes them unstable; a Gaussian-elimination singularity guard returns the unscoreable sentinel
on degenerate data).

## Status

**EARNED-DEAD (2026-06-09).** Screened on the warmup-fixed engine (ADR 0018) vs a seeded 10-draw Random
baseline; **below the entire Random cloud on per-trade edge AND blended CAGR (K=0/10)** — anti-selective,
*worse* than single-factor [[mrm]]. This is a **class-killer**: per the quant asymmetry below, a
market+sector FAIL earns the whole factor-neutral idiosyncratic-RS class dead.

## Funnel history

| Stage | State |
|---|---|
| Data scoping — 9 SPDR sector ETFs in DB to 1998 (full Block A span) | ✅ zero new sourcing |
| Build — ranker + 14 tests, TDD; warmup-load fix (ADR 0018) shipped alongside | ✅ first-class ranker |
| `/strategy-screen` 2005–2015, seed-42 control vs Random 1–10, 1,500-sym universe | ❌ **EARNED-DEAD — K=0/10 on edge AND CAGR** |

## Verdict — #137 vs the seeded Random baseline (1,500-sym universe)

| Metric | MultiFactor | single-factor [[mrm]] | Random (10 seeds) |
|---|---|---|---|
| Per-trade edge | **0.249** | 0.588 | min 0.80 · med 1.19 · max 1.43 |
| Blended OOS CAGR | **2.02%** | 5.52% | min 6.25 · med 9.91 · max 12.40 |
| OOS Sharpe / Calmar | 0.12 / 0.04 | 0.25 / 0.13 | ~0.5 |
| SPY-baseline | FAIL | FAIL | 4/10 PASS |
| per-window OOS edge | −1.66, −0.23, 1.84, −0.05, 3.52, 7.27, 0.24 | — | — |

## Why it died — and why it kills the class

- **Anti-selective.** Below every one of 10 random orderings on both binding legs. The residual-momentum
  ordering picks *worse* names than random — there is no cross-sectional alpha in residual relative
  strength in this universe/period.
- **Stripping more made it worse** (edge 0.59→0.25, CAGR 5.5%→2.0% vs single-factor). The
  "narrow-leadership *feeds* idiosyncratic momentum" hypothesis ([[long-premise-in-narrow-leadership]]) is
  **refuted** — more neutralization = more anti-selection.
- **The class-killer asymmetry (quant):** a market+sector FAIL is conclusive for the *class*, because the
  only remaining neutralization step (size/value via Fama-French) can *only remove* signal — it cannot
  rescue an already-anti-selective residual. So **no Fama-French sourcing is warranted.**
- **Robust to the sector look-ahead leak:** the ranker uses each stock's *current* `sectorSymbol` applied
  to past regressions, which biases toward a *weaker* apparent signal — it can erode a win, never
  manufacture this loss. The earned-dead stands.

## Reusable findings

- `MultiFactorResidualMomentumRanker` (first-class, 14 tests) + the warmup-load engine fix (ADR 0018) are
  permanent assets — the fix makes ALL in-engine trailing-history rankers validly screenable.
- The seeded-random-sample reduced universe (N=1,500, both arms identical list) is the established
  fast+valid path for ranker-selects screens (full universe ~20 min/run + memory-marginal).
