---
type: source
title: Trailing-ranker warmup starvation voids the #130 MRM screen (2026-06-09)
summary: Walk-forward loaded each window with no lookback buffer, so 504-day residual rankers were unscoreable for every OOS entry — selection degraded to tie-break RNG. Voids #130; fixed by ADR 0018.
status: stable
tags: [engine-defect, methodology, ranker, beta-delivery, verdict-void, warmup]
sources: ["docs/adr/0018-trailing-ranker-warmup-history-is-loaded-but-never-traded.md", "https://github.com/skrymer/trading/issues/130", "https://github.com/skrymer/trading/issues/137"]
related: ["[[mrm]]", "[[the-funnel]]", "[[2026-06-08-mrm-screen-reject]]", "[[purpose]]"]
updated: 2026-06-09
---

# Trailing-ranker warmup starvation (2026-06-09)

Found while wiring the multi-factor residual-momentum ranker (#137) into `/strategy-screen`. Quant-adjudicated.

## The defect

The walk-forward engine loaded each window's quotes with a hard filter `QUOTE_DATE >= windowStart`
(`StockJooqRepository.findBySymbols`), **no warmup buffer**, and loaded each IS/OOS window independently.
Rankers that compute trailing returns **in-engine** — `MarketResidualMomentum` (504d),
`MultiFactorResidualMomentum` (504d), `TrailingReturn` (252d) — need their lookback of prior bars to
score; without it they return the `UNSCOREABLE` sentinel.

Every OOS window in the funnel is **12 months** (~250 trading days) — shorter than a 504-day lookback —
so a residual ranker was **unscoreable for every OOS entry**. When all candidates score the sentinel,
capital-aware "rank top-N" selection collapses to the **tie-break jitter RNG**
(`BacktestService.tieBreakRandom`), a stream independent of the `RandomRanker`'s per-`(symbol,date)` hash
RNG ([[two random seed paths]] ^[inferred]).

## Why it voids #130

The [[mrm]] screen swapped only `ranker` (MarketResidualMomentum vs seeded Random). Under the defect,
MRM's OOS "selection" was random-via-jitter-RNG and the baseline was random-via-score-RNG — **two
different random draws over the same basket.** That alone explains MRM's 499 OOS trades vs Random's 527
and the edge gap, with **no anti-selection required.** The "anti-selective beta-delivery" verdict is
therefore unsafe and **withdrawn pending a re-run**. (The 36-month IS legs were only partly starved, so
IS carried some real signal — but the screen verdict is OOS-aggregated, and OOS was fully starved.)

## Blast radius (verified)

- **Only in-engine trailing rankers affected.** Precomputed-indicator rankers/conditions (ATR, EMA,
  Donchian, 52-week high, breadth) read ingestion-time columns and are not starved.
- **Sizers clean:** `AtrRiskSizer` / `VolatilityTargetSizer` consume the scalar precomputed `ctx.atr`,
  no in-engine trailing vol — so no sized equity curve shifts; **no passing candidate needs re-validation.**
- **Conditions:** several do small trailing math (≤ ~50d lookback ≪ 250d OOS), fail-closed gracefully —
  no recorded verdict invalidated; the warmup fix improves their first-bars behavior as a bonus.
- **Only recorded verdict at risk: #130 MRM.** [[george]] used a precomputed-column ranker (safe).

## The fix — ADR 0018

`StockRanker.warmupTradingDays()` declares each ranker's lookback; the engine loads stock/SPY/sector
series from `after − ceil(warmupTradingDays × 365/252 × 1.10)` calendar days. Warmup bars feed
`StockRanker.score` only; trades/equity/curve/OOS-buckets stay gated to `[after, before]`. Pre-`startDate`
ranking data is forward-clean (precedes every entry it informs). Tripwire logs if a trailing ranker still
leaves >5% of in-window entries unscoreable (under-sized buffer).

## What it taught / pages updated

- A plausible-looking screen verdict can be a pure measurement artifact when a ranker is silently
  unscoreable — the trade-count delta vs Random is *equally* the signature of two RNG streams.
- Updated [[mrm]] (status → disputed, verdict void), [[purpose]] (#4 RS-momentum re-opened), filed ADR 0018.
- **Re-run plan:** fix engine → re-run #130 (artifact check + validates the fix) with the mandatory
  seeded-Random baseline → then screen #137. No re-validation of passing candidates.
