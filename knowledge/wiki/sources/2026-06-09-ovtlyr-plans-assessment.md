---
type: source
title: OVTLYR Plan M & Plan ETF — backtestability assessment + quant verdict (2026-06-09)
summary: OVTLYR Plan M/ETF — ~5y signal span (all in Block C) ⇒ un-validatable; quant TEST_NOT_WORTHWHILE / LIFT_IDEAS_ONLY. No novel 6th class; F&G-extreme entry collapses into Gjallarhorn.
status: stable
tags: [vendor-strategy, data-span, beta-delivery, condition-screen, methodology]
sources: ["strategy_exploration/dossier/", "knowledge/wiki/sources/2026-06-09-pead-premise-class-adjudication.md"]
related: ["[[crisis-timer-cadence-ceiling]]", "[[gjallarhorn]]", "[[participate-and-lose]]", "[[beta-delivery]]", "[[aliased-regime-sensitivity]]", "[[purpose]]", "[[the-funnel]]"]
updated: 2026-06-09
---

# OVTLYR Plan M & Plan ETF — backtestability assessment

The operator asked whether two of OVTLYR's published trading plans (a behavioral-analytics vendor)
could be *correctly* backtested in our engine, given their data can be ingested like the OVTLYR
buy/sell signals we already hold. Verdict after a data check + quant consult: **do not build a backtest
(TEST_NOT_WORTHWHILE); lift three reconstructable mechanisms instead (LIFT_IDEAS_ONLY).** No
structurally-novel premise class on offer.

## The plans (what we'd be testing)

- **Plan M** — a Market/Sector/Stock **40/30/30 bullish-confluence long swing**. Market (40%): SPY
  10/20/50 uptrend + SPY OVTLYR buy signal + SPY F&G heatmap <70 & rising + full-stocks breadth
  bullish-cross-10-EMA. Sector (30%): sector heatmap <70 & rising + sector relative-greed >1 + sector
  breadth bullish-cross + OVTLYR-channel breadth-state (M1S1/M2); healthcare excluded. Stock (30%):
  stock 10/20/50 uptrend + stock buy signal + stock heatmap rising + >2% from an order block + price
  >10-EMA + >$10. Exits (any): OVTLYR sell signal / 10-20 bearish cross / hit a 120-day+ order block /
  earnings / gap-and-crap (≥5% gap) / 2-ATR stop / 3-ATR intraday. Entry valid up to **5 days after** the
  signal. **Live execution = 80-delta CALL OPTIONS** with ATR-rolling.
- **Plan ETF** — market-regime-bullish but no stock setup → **buy SHARES of a 3× index ETF** (TQQQ on
  QQQ, SPXL on SPY) in the "value zone" (20-EMA → +2 ATR); exit on the index's sell signal / bearish
  cross / 30-day+ order-block hit / close >3 ATR above 20-EMA.
- **Their published Plan M claim:** 7,020 trades, 56.94% WR, avg trade +2.25%, MC PF ~1.85, avg DD −24%.
  Their headline "Overall Outcome 15,827%" is just **Σ(per-trade returns)** (2.2546% × 7020) — *not* an
  account return (assumes all signals taken, unlimited capital, no concurrency cap). The per-trade table
  is the only verifiable core.

## The hard data fact (verified, not assumed)

Our ingested OVTLYR buy/sell signals span **2020-01-03 → 2026-05-20 — ~5.4 years, nothing before 2020**
(211k rows / 2,458 symbols; SPY/QQQ/TQQQ/SPXL signals same span). The F&G heatmaps, OVTLYR channels,
relative-greed, and bull-list breadth are **not ingested at all** and would inherit the same ~2020
ceiling (OVTLYR is a ~2020-inception product). The firewall needs **2000+** with an in-sample design
block (A 2000-2014), a binding OOS block (B 2014-2021), and a sanity block (C 2021-2025). **OVTLYR's
entire history sits inside Block C** → no in-sample to design on, no out-of-sample to validate on. Any
backtest is in-sample replication by construction. Same span-disqualifier as the earlier ovtlyr/Forseti
thread (memory: *check a signal's data span spans the firewall window*; sibling of
[[crisis-timer-cadence-ceiling]]).

## Q1 — does a backtest warrant the effort? **TEST_NOT_WORTHWHILE**

An in-sample replication could only check whether their per-trade stats reproduce on 2020-2025 — which
**changes no funnel decision** and is **blind to the two gates that actually kill candidates**:
- **No regime-survival read** — Block C is a single regime sample; [[participate-and-lose]] /
  [[aliased-regime-sensitivity]] are *defined* by cross-block sign-flips invisible in one block.
- **No beta-delivery read** — a 57% WR / PF 1.85 over the bull-heavy 2020-2025 tape is *exactly what
  [[beta-delivery]] looks like*; without a multi-block G16 SPY-Calmar baseline you cannot separate alpha
  from SPY drift.
The most informative thing a build would surface (the 15,827% = Σ-per-trade artifact) was already free.
Build cost (ingest 4-5 proprietary series + author the 40/30/30 stack) buys nothing that moves a gate.

## Q2 — what's transferable? **LIFT_IDEAS_ONLY**

**Proprietary-bound, dead-on-arrival** (only exist 2020+): the OVTLYR buy/sell signals, the F&G heatmap
*as their series*, the channel breadth-states, sector relative-greed.

**Reconstructable from our 2000+ data, firewall-validatable:**
1. **Gap-and-crap exit** (≥5% gap up → close below the gap-day low within 5d) — pure OHLCV, lowest-risk
   lift, adopt as an exit refinement for any long candidate.
2. **Earnings-proximity entry filter** (≥4 days to earnings) — clean historical filter.
3. **A reconstructed fear&greed oscillator** (breadth+momentum+vol+52wk-high, à la CNN's index) entered
   "<70 & rising" — worth *one cheap* `/condition-screen`, **but pre-flagged likely-dead**: "buy as fear
   recedes" *is* oversold-bounce mean-reversion = our buried dead-family #1; expect the PEAD
   flat-tertile-negative beta-delivery signature. First funnel step if run: `/condition-screen` with the
   SPY-regime-tertile decomposition, **kill on sight if the flat tertile is negative**.

## The sharp finding

Made honest (breadth-state-conditioned, measured against a within-regime null), OVTLYR's
behavioral-extreme entry **collapses into the [[gjallarhorn]] washout-timer we already hold** —
inheriting its [[crisis-timer-cadence-ceiling|cadence]] problem, not a fresh premise. OVTLYR provides
**no structurally-novel 6th class**; it is a well-packaged net-long, breadth-gated, behavioral-extreme
instance of families already buried. The unexplored corner remains structurally beta-hedged
([[2026-06-09-pead-premise-class-adjudication]]) — which the operator declined on the 25%-CAGR appetite.
The funnel stays empty; the directional 6th premise OVTLYR was hoped to seed is not there.

## Disposition

Parked (operator decision 2026-06-09). Not backtested. The three lifts are noted for a future candidate;
the F&G-oscillator screen is an optional cheap diagnostic the operator may run later to *document* the
F&G-extreme class as tested rather than assumed-dead. The 80-delta-call options + ATR-rolling execution
layer is untestable in our long-only daily engine and orthogonal to the alpha question — ignored for
research.

## Pages updated

`index.md`, `wiki/log.md`, [[purpose]] (OVTLYR assessed → no 6th class; funnel still empty).
</content>
