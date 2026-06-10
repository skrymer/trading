---
type: source
title: Quality/profitability tilt — build complete (PR #152), awaiting kill-test
summary: The #150 build landed (PR #152): fundamentals L1 + quality-percentile L2 + gate/exit/ranker + the ADR 0020 cohort-ranking hook, all gates green. Unproven — no screen has run yet.
status: active
tags: [candidate, fundamentals, quality, build, engine]
sources: ["https://github.com/skrymer/trading/pull/152", "https://github.com/skrymer/trading/issues/150", "docs/adr/0020-same-day-cohort-ranking-is-a-first-class-stockranker-capability.md", "docs/adr/0019-fundamentals-are-point-in-time-reference-data-and-quality-is-a-cross-sectional-percentile.md"]
related: ["[[quality-profitability-tilt]]", "[[2026-06-10-quality-tilt-scoping-and-design-lock]]", "[[the-funnel]]"]
updated: 2026-06-10
---

# Quality/profitability tilt — build complete (PR #152)

The design-locked [[quality-profitability-tilt]] candidate is now **built and merged-pending in PR #152**.
This is a build milestone, **not** a run — no backtest or screen has fired, so the premise is still
**unproven** and the pre-registered kill-test is unchanged.

## What was built

- **L1 — point-in-time fundamentals (Midgaard):** EODHD quarterly income-statement + balance-sheet line
  items ingested as reference data, gated on `filing_date` (never `fiscalDateEnding`), mirroring the
  earnings pipeline. New `fundamentals` table + `/api/fundamentals/{symbol}`.
- **L2 — cross-sectional quality percentile (Midgaard):** an operator-triggered SQL pass computing
  `grossProfit_TTM / totalAssets_asof` ranked midpoint per date (`N_min=100`, 2000-01-01 floor,
  definedness-exclusion), persisted per quote row — the RS-percentile pattern (ADR 0009). Recompute is
  reachable from the API **and** a new ingestion-dashboard button.
- **Signal (Udgaard):** `FundamentalQualityPercentileCondition` (gate ≥80, fail-closed), the
  `FundamentalQualityDeteriorationCondition` exit (<60 hysteresis), `PriceAboveSmaCondition` (the
  ablatable trend leg), and `FundamentalQualityRanker`. Canonical TTM accessors on `Stock` are the single
  definition the Midgaard L2 SQL mirrors, so gate and ranker can never disagree about "quality."

All gates green: 207 Midgaard + 1433 Udgaard tests (full TestContainers E2E), ktlint/detekt clean, zero
compiler warnings.

## What it taught — a reusable engine finding (ADR 0020)

Building a *blended* cross-sectional ranker surfaced that the engine had **no way to standardize a value
across the day's firing cohort**. The existing residual-momentum rankers self-normalize **within one
stock's own series** (`Σε / stdev(ε)`), not across the cohort — so there was no intra-subset normalizer to
"copy." The load-bearing reason it matters: a **single** cross-sectional z is a *rank-preserving no-op*
(mean/stdev are day-constants, so ranking by `z(GP/TA)` ≡ ranking by raw GP/TA — the rank-preserving-transform
trap); the cohort z earns its place **only** because the ranker blends two differently-scaled legs
(GP/TA level + operating-margin YoY), which must be put on a common scale or the larger-variance leg
silently dominates. The fix is **`StockRanker.rankCohort`** (ADR 0020): a same-day-cohort ranking mode whose
default delegates to per-stock `score` (every existing ranker stays byte-identical), wired only at the
single co-resident selection site in `BacktestService`. Durable because any future cross-sectional ranker
(rank-blend, winsorized z, cohort-relative anything) reuses it.^[inferred — engine capability, reusable beyond this candidate]

## Status change

`IN BUILD (pre-screen)` → **BUILT, awaiting kill-test.** Next gate unchanged: `/condition-screen` the gate
alone on the 300-sym sanity universe (after running the L2 pass + a PRD `min(N)`-coverage / bit-exactness
check), with the pre-registered [[beta-delivery]] flat-SPY-tertile ABORT rule — **KILL** if the `flat`
tertile `meanLift ≤ 0` at 20d (confirm 10d). The pre-mortem (top-quality = Mag-7 ⇒ gate reconstructs a
cap-weighted mega-cap basket ⇒ delivers beta) still stands as the most likely death.

## Pages updated

[[quality-profitability-tilt]] (status IN BUILD → BUILT, sources + ranker note).
