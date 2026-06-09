---
type: entity
title: Quality / profitability tilt
summary: Live candidate (in build, #150) — a cross-sectional gross-profitability gate + ranker; the first long premise whose killing regime (narrow leadership) is a TAILWIND, not a wall. Unproven; kill-test pending.
status: active
tags: [candidate, fundamentals, quality, live]
sources: ["docs/adr/0019-fundamentals-are-point-in-time-reference-data-and-quality-is-a-cross-sectional-percentile.md", "https://github.com/skrymer/trading/issues/150", "knowledge/wiki/sources/2026-06-10-quality-tilt-scoping-and-design-lock.md"]
related: ["[[beta-delivery]]", "[[participate-and-lose]]", "[[long-premise-in-narrow-leadership]]", "[[the-funnel]]", "[[pead]]", "[[2026-06-10-quality-tilt-scoping-and-design-lock]]", "[[purpose]]"]
updated: 2026-06-10
---

# Quality / profitability tilt

The first **live directional candidate** after the funnel emptied — a cross-sectional long premise that
selects on a **non-price, persistent** variable (fundamental gross profitability), screened/built but
**not yet run**. Strategy-neutral name (only the assembled strategy earns a Norse name later).

## Premise

Long the highest-quality names in the universe, where quality = **Gross Profitability** (Novy-Marx,
`grossProfit_TTM / totalAssets_asof`). The selection variable is fundamental, slow-moving, and
*orthogonal to recent price path* — which is the whole point: every earned-dead long class
([[participate-and-lose]] / [[beta-delivery]]) conditioned on **price state**, which in a strong tape is
just market direction. A fundamental quality rank is not a price transform, so it cannot inherit SPY
through the entry the way [[george]] / [[mrm]] / [[pead]] did.

## Why it might survive narrow leadership (the load-bearing claim)

**Narrow-leadership tape is a *tailwind*, not a wall** — the first premise for which this is true. Narrow
leadership *is* capital concentrating into a few high-profitability, cash-generative mega-caps; that is
exactly the cross-section a quality tilt harvests. The regime that made every breakout / pullback /
RS-momentum premise *participate-and-lose* is the regime in which a quality keep-set converges onto the
names actually receiving the flow.^[inferred — quant mechanism, not yet measured] Quality is also
*persistent* (the documented source of the premium), so the holding-window risk is "the market falls"
(beta, handled by cash) not "my signal evaporates" (alpha decay).

## Locked design (ADR 0019 + quant consult)

- **Gate (L2):** a market-wide, survivorship-free **quality percentile** of GP/TA (TTM gross profit /
  point-in-time total assets), persisted per quote row in Midgaard — mirrors the RS-percentile pattern
  ([[component-firewall]] sibling, ADR 0009). Eligibility = top-quintile (`qualityPercentile ≥ 80`),
  fail-closed on null. Single baked metric (iterate = re-run the pass).
- **Ranker:** `FundamentalQualityRanker` = `0.5·z(GP/TA level) + 0.5·z(margin-trend YoY)`, intra-subset
  (Udgaard, from raw `Stock.fundamentals`) — the gate-vs-ranker split of ADR 0009.
- **Point-in-time:** every fundamentals read gates on EODHD `filing_date ≤ tradingDate` (never
  `fiscalDateEnding`) — the earnings `reported_date` guard; restatement residual accepted + documented
  (CONTEXT.md *Point-in-time fundamentals*).
- **Assembly:** gate + ablatable `price > SMA200` trend leg, `FundamentalQualityRanker`, maxPositions 15,
  entryDelayDays 1, ATR sizer (unlevered first pass), OR-exits (quality < 60 hysteresis / SMA200 break /
  ATR trail). Build spec: GitHub issue #150; architecture: PR #151.

## Status

**IN BUILD (pre-screen) — unproven.** Architecture locked (`/grill-with-docs` → ADR 0019), signal locked
(quant consult), EODHD feasibility verified (filing_date present, history to 1985). No backtest has run.

## Pending kill-test (pre-registered)

1. `/condition-screen` the gate alone on the 300-sym sanity universe → the **beta-delivery flat-SPY-tertile
   rule** ([[beta-delivery]]): the **flat** tertile `meanLift` must stay solidly positive at 10d+20d.
   Positive-only-in-down-tape / flat ≤ 0 = beta-delivery via selection = **KILL** (the [[pead]] death). Plus
   ARS sweep {75,80,85} and Jaccard vs the RS70 gate (must not be RS in disguise).
2. If it proceeds → the **Random-ranker null** at `/strategy-screen`: `FundamentalQualityRanker` must beat
   a byte-identical Random ranker from the same eligibility universe on per-trade edge **and** blended CAGR
   **and** positive-window count (the [[george]] / [[mrm]] discipline).

## Most likely death (pre-mortem)

The top-quality names **are** the Mag-7 ⇒ the gate reconstructs a cap-weighted mega-cap basket ⇒ it
*delivers beta* (flat tertile ≤ 0), the same population collision that makes "quality wins in narrow
leadership" indistinguishable from "quality = the index" here. Secondary: coverage — too few smaller-caps
carry statements back to 2000 to clear `N_min=100` on early dates (fail-closed). **Abort rule: kill at the
screen, do not patch with a regime gate** ([[participate-and-lose]]'s lesson).

## Failure modes to watch

[[beta-delivery]] (primary) · [[aliased-regime-sensitivity]] (the ARS sweep) · [[thinning-not-selecting]]
(if the trend leg only thins) · [[crisis-timer-cadence-ceiling]] (N/A — high cadence by construction).

## Related

[[2026-06-10-quality-tilt-scoping-and-design-lock]] · [[beta-delivery]] · [[participate-and-lose]] ·
[[long-premise-in-narrow-leadership]] · [[pead]] · [[purpose]]
