---
type: source
title: 2026-06-10 — Proposal B (breadth-recovery deploy timer) funnel-disqualified
summary: Quant adjudication killed B without a run — it is the set-union of three already-dead lines (BTC breadth-thrust + SPY-trend-timing + the abandoned deployed-basket construction). The breadth-deploy class is exhausted.
status: stable
tags: [source, breadth, timing, reject, no-run]
sources: ["knowledge/wiki/sources/2026-06-08-btc-breadth-thrust-screen-reject.md", "knowledge/wiki/sources/2026-06-10-leverageable-calmar-spy-timing-screen.md", "knowledge/wiki/synthesis/regime-conditional-portfolio.md"]
related: ["[[btc-tyr]]", "[[spy-trend-timing]]", "[[gjallarhorn]]", "[[regime-conditional-portfolio]]", "[[thrust-degenerates-to-level]]", "[[crisis-timer-cadence-ceiling]]", "[[purpose]]"]
updated: 2026-06-10
---

# 2026-06-10 — Proposal B (leader-basket breadth-recovery deploy timer) funnel-disqualified

> Adjudication, **not a run**. No config_hash burned, no `/condition-screen` or backtest fired — killed by
> mapping B onto runs already on file. The "free screen" the operator queued resolved to "free *kill*."

## What B was

A long-only **deploy/cash market-timing** premise: hold a (deliberately beta) leader basket only while
deployed; deploy on a breadth-recovery transition after a sustained washout + `SPY > SMA200`; cash on
deterioration. Thesis = **left-tail truncation** lifts a beta book's Calmar above SPY's 0.141, leverageable
toward 25% CAGR. Ranked cheapest-to-disprove but doubtful on edge in its own scoping.

## Why it's disqualified — B = union of three dead lines

Built from the existing first-class conditions, B's deploy predicate decomposes to
`SustainedWashoutWithin ∧ MarketBreadthRecovering ∧ SpyPriceUptrend`:

| B's building block | Already dead as |
|---|---|
| `SustainedWashoutWithin` + `MarketBreadthRecovering` (dip-then-surge) | the **[[btc-tyr]] breadth-thrust gate** (REJECTED 2026-06-08) — a **dead-config neighbour**. The `MarketBreadthRecovering` EMA10-cross "surge" is a single-bar near-level read = the [[thrust-degenerates-to-level]] corner BTC already condemned. |
| `SpyPriceUptrend` (deploy gate) | **[[spy-trend-timing]]** (REJECTED 2026-06-10, Calmar ceiling 0.341). BTC's disposition explicitly forbids bolting a SPY gate on top (= IS-fitting the sign-flip). |
| deploy-a-beta-basket-while-timer-on | the **[[regime-conditional-portfolio]]** program (ABANDONED) / [[gjallarhorn]] composite (NO-GO) — long-only ⇒ crisis drawdowns correlate ⇒ the left-tail truncation B's thesis needs **does not materialise** in this engine. |

A `/condition-screen` would re-derive BTC's regime sign-flip; a strategy backtest would re-derive
SPY-timing's ~0.3 Calmar + the Gjallarhorn NO-GO. The operator pre-mortem ("lands Calmar ~0.3–0.5 like
SPY-timing") *is already the measured outcome* — nothing to confirm.

## The only admissible successor (and why it's not worth building now)

Per BTC's disposition, a revivable breadth-event successor needs a **structurally different,
regime-sign-consistent transition predicate** — a *signed multi-bar breadth-velocity* (rate of climb off
the washout floor), **not** the existing single-bar EMA10 cross — and must be screened from scratch AND
co-screened with [[gjallarhorn]] for overlap. But even that inherits two ceilings already on file: the
**[[crisis-timer-cadence-ceiling]]** (~0.65 deploy events/yr → un-validatable standalone) and a **~6× Calmar
gap** (single-instrument/breadth-timer ceiling 0.341 vs the leverageable unlevered Calmar ≥ 2.0). So scope
its events/yr *before* building; expect a Step-0 funnel-disqualification.

## What it taught (durable)

- **The breadth read is now triple-counted** ([[gjallarhorn]], [[btc-tyr]], B) and the **deploy-timer Calmar
  ceiling is pinned** (~0.34, single-instrument-timer class). The **breadth-deploy premise class is
  exhausted for now** — don't re-propose a breadth deploy timer; spend the next run on a structurally
  different alpha source.
- A clean instance of the dead-config-neighbour discipline working *before* a run: a candidate assembled
  from already-rejected first-class conditions is disqualified by adjudication, saving the run.^[inferred]

## Related

[[btc-tyr]] · [[spy-trend-timing]] · [[gjallarhorn]] · [[regime-conditional-portfolio]] ·
[[crisis-timer-cadence-ceiling]] · [[thrust-degenerates-to-level]] · [[purpose]]
