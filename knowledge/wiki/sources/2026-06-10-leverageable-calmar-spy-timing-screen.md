---
type: source
title: Leverageable-Calmar reframe + SPY trend-timing screen
summary: SPY trend-timing REJECTED at screen (Calmar ceiling 0.341 ≪ 1.5; base CAGR un-leverageable). Verified SPY 25y buy-and-hold Calmar = 0.141. Quant ADOPT-NARROW on leverageable-Calmar.
status: active
tags: [run, screen, leverage, calmar, market-timing, rejected, methodology]
sources: ["knowledge/wiki/entities/spy-trend-timing.md", "udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/service/PositionSizingService.kt"]
related: ["[[spy-trend-timing]]", "[[beta-delivery]]", "[[regime-conditional-portfolio]]", "[[participate-and-lose]]", "[[purpose]]", "[[gjallarhorn]]"]
updated: 2026-06-10
---

# Leverageable-Calmar reframe + SPY trend-timing screen (2026-06-10)

Arc of three linked steps: a strategic reframe (should the hunt target *leverageable Calmar* instead of
raw 25% CAGR?), the verified beta baseline it hinges on (SPY's own Calmar), and the first candidate it
spun out (SPY trend-timing) — screened and rejected. Outcomes first.

## Outcome 1 — SPY trend-timing REJECTED at screen

5 variants, unlevered, SPY-only (ETF), 2000–2025, 100%-equity, net 10bps, idle-cash credited:

| Variant | trades | CAGR | maxDD | Calmar | vs SPY |
|---|---|---|---|---|---|
| EMA200-in / EMA50-out | 185 | 3.72% | 24.8% | 0.150 | 1.1× |
| EMA200-in / EMA20-out | 310 | 2.10% | 23.8% | 0.089 | 0.6× |
| EMA200-in / ATR-trail | 86 | 3.72% | 33.5% | 0.111 | 0.8× |
| **EMA200-gate / 20-50 crossover** | **37** | **5.82%** | **17.1%** | **0.341** | **2.4×** |
| EMA200-in / 10d-confirmed EMA50 | 46 | 6.22% | 28.9% | 0.215 | 1.5× |
| *SPY buy-and-hold* | — | 7.85% | 55.2% | *0.142* | — |

**Two walls, both confirmed:** (1) best Calmar **0.341 ≪ 1.5** (the absolute G15 bar) — the
single-instrument-timer ceiling; (2) best base CAGR **5.82% is too low to lever** — at 2× the cost stack
(~7.5%) exceeds the leverage gain, netting ~4% (net-negative). Fast EMA20/50 exits whipsaw to Calmar ≤ SPY;
the slow crossover is the best and still fails. The exact textbook 200-MA symmetric isn't buildable (no
EMA200 exit condition) but lands in the same 0.3–0.5 zone and the base-CAGR wall kills it regardless.
See [[spy-trend-timing]].

**But the structural escape is real:** single-instrument timing is *immune* to [[participate-and-lose]]
(no cross-sectional unit below the gate); V4's 2.4×-SPY Calmar is genuine tail-truncation alpha (beta is
pinned at 0.142, can't reach it). The escape works — there just isn't enough alpha, and what there is can't
be levered.

## Outcome 2 — SPY 25y buy-and-hold Calmar = 0.141, VERIFIED

The G16 beta baseline (ADR 0013), which the wiki never recorded on-engine. **Verified two independent
ways** — the engine's `benchmarkComparison` (RiskMetricsService) AND a from-scratch Python recompute on
the raw dividend-adjusted SPY closes — match to 4 sig figs (0.14105 vs 0.1411). CAGR 7.78% (total-return),
maxDD 55.2% (2007-10-09 → 2009-03-09 GFC). **~3× lower than the prior estimate (0.4–0.5).** Consequence:
G16 (beat-SPY) is now a *low* bar — a tradable Calmar-1.5 book is ~10.6× SPY, so **G15-absolute is the sole
binding wall**, and a Calmar > ~1.0 on a SPY-instrument *is* tail-truncation alpha by construction (beta is
pinned at 0.141 and leverage only degrades it).

## Outcome 3 — the leverageable-Calmar reframe: quant verdict ADOPT-NARROW

The question: because **Calmar is ~leverage-invariant**, should the hunt target high unlevered Calmar
(leverageable to 25%) instead of raw 25% CAGR? Quant verdict **ADOPT-NARROW**:

- **Calmar is NOT leverage-invariant after costs — it degrades ~0.5 at 2×.** The engine models leverage
  **cost-free** (`LeverageCap` = notional cap; `PositionSizingService.kt:154-155` clamps the margin borrow
  to zero — confirmed) — so any `leverageRatio:2` backtest prints the *gross* Calmar. The honest map at L≤2×:
  `CAGR_L ≈ L·CAGR − (L−1)·4.5% − ½(L²−L)σ² − 2%`; `maxDD_L ≈ L·maxDD`; so `Calmar_L < Calmar_unlev` always.
  The reframe's "Calmar 1.6 → tradable" is false (lands ~1.1). **Leverageable sweet-spot: unlevered Calmar
  ≥ 2.0, DD ≤ 8.3%, CAGR ≥ 16.75%** (σ-dependent, per-candidate).
- **The space is disproportionately beta.** A smooth de-risked long sits near the index; a *pure* beta book
  is Calmar-invariant at SPY's 0.141 (leverage re-imports the beta one level up). It revives **nothing
  buried** — all 5 dead classes died upstream of the return floor ([[2026-06-06-gate-basis-and-cagr-floor-feasibility]]).
- **Gate:** keep G1 (25% raw) primary; add a thin **secondary** path G1-LEV (unlevered Calmar ≥ 2.0 + DD ≤
  8.3% + CAGR ≥ 16.75% + honest-cost levered recheck), G16/G9 unchanged. Two prerequisites before any
  levered claim: build an **honest-cost levered recompute** in `RiskMetricsService` (the cost-free gap), and
  the SPY-Calmar measurement (done — outcome 2).

## What it taught

- **The leverage misconception is fully resolved:** long-only is direction, not a leverage ban; 25% is a
  long-run average; but leverage **cannot rescue a low-Calmar edge** (invariant-then-degrading) — proven on
  R1 (Calmar 0.32) and now SPY-timing (0.34). The binding wall is always the *unlevered* risk-adjusted
  quality.
- **A market-timing premise needs BOTH a high base CAGR (so ≤2× is net-positive) AND Calmar ~1.5** — SPY
  trend-timing delivers neither. The convex/asymmetric timer ([[gjallarhorn]]) has the high-Calmar half but
  is cadence-blocked. ^[inferred — the "needs both" framing is the synthesis across the SPY-timing screen +
  the prior leverage cost-stack; each half is quant-stated, the conjunction is mine]

## Pages updated

New: [[spy-trend-timing]] entity. Updated: [[beta-delivery]] (the verified 0.141 G16 baseline),
[[purpose]] (open-question + leverageable-Calmar finding), index, log.
