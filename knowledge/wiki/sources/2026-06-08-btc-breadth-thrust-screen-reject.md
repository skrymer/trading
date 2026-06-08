---
type: source
title: 2026-06-08 — BTC breadth-thrust gate /condition-screen REJECT
summary: BTC+Tyr's fresh component (the breadth-thrust GATE) failed solo — regime sign-flip all 3 horizons, no 10/20d edge, thrust degenerates to a level gate, 2009-14 one-tape lift. Kills the candidate.
status: stable
tags: [source, condition-screen, breadth, reject]
sources: ["strategy_exploration/dossier/condition-breadththrust.jsonl"]
related: ["[[btc-tyr]]", "[[lottery-vs-signature]]", "[[thinning-not-selecting]]", "[[thrust-degenerates-to-level]]", "[[gjallarhorn]]", "[[aliased-regime-sensitivity]]"]
updated: 2026-06-08
---

# 2026-06-08 — BTC breadth-thrust gate `/condition-screen` REJECT

> Diagnostic, not predictive. A design-time condition kill — **not** a firewall death (no `config_hash`
> burned, the G13 cross-candidate brake is **not** engaged).

## What was screened

The genuinely-fresh component of [[btc-tyr]] in **isolation**: an inline-script **breadth-thrust gate**
(dip-then-surge) — fires when today's market `breadthPercent ≥ {{high}}` AND breadth dipped `≤ {{low}}`
within the prior `{{window}}` trading days. The continuation sibling of [[gjallarhorn]]'s breadth-washout
bottom-timer. Screened *alone* (no order-block trigger, no ADX — those are downstream), because the entity
established the whole bet rests on this gate; the order-block trigger is a deprecated-breakout-family
cousin already weak in isolation ([[order-block-breakout-condition]]).

- **Sweep:** `window` 10±2 {8,10,12}, `low` 30±5 {25,30,35}, `high` 55±5 {50,55,60}; horizons 5/10/20d.
- **Universe:** 300-sym sanity main subset (a market-day gate is high-firing → full-universe OOM risk;
  the subset is faithful for the distinct-firing-*date* count). Window 2000-01-01 → 2021-01-01.
- **Request:** `knowledge/wiki/entities/btc-tyr.breadth-thrust-screen.request.json`.
- **Authored via** `/create-condition` with the efficient O(1) `getMarketBreadth` point-lookup pattern
  (not the codebase's standard full `filterKeys+toSortedMap` scan), strictly anti-lookahead.

## Step-0 cadence — PASS (the one thing that held)

199 distinct firing dates / 21yr (~9.5/yr), present in 15/21 years — **not** a
[[crisis-timer-cadence-ceiling]]. Zero-years are mechanically sensible: 2000–02 (sustained bear, no ≥55
surge), 2006/2017 (calm melt-up, no ≤30 dip), 2018 (recovery straddled into 2019). But firing is
**62% concentrated in 2009–2014** (2009 alone = 13.1% firing) — a tell that feeds the lottery read.

## The three binding failures (any one sufficient)

1. **SPY-regime SIGN-FLIP at all 3 horizons** (the decisive check; the same failure that killed solo-Tyr,
   here worse + incoherent): 5d down **−1.62%** (n=590) / flat +0.10% / up +0.29%; 10d flat **−0.11%**;
   20d up **−0.23%**. A continuation signal must hold sign when it fires; this is regime-determined noise.
   The gate fires **6.98% in up-tape vs 0.17% in down-tape** → it measures *"market already recovered,"*
   not the transition *into* recovery. A regime gate can't rescue a regime-sign-flipped premise.
2. **No detectable forward-return edge.** 5d meanLift +0.249% (t≈1.23, sub-threshold); hit-rate lift goes
   **negative** at 10d (−1.47pp) and 20d (−0.86pp); the horizon shape **decays** — exactly wrong for a
   deploy-and-ride continuation thesis. Fill gap (+0.080%, posRate 0.49) eats ~32% of the lone 5d positive.
3. **The "thrust" is a LEVEL gate, not a transition detector** ([[thrust-degenerates-to-level]]). All lift
   lives in the loosest near-level cell `high=50` (5d +0.38 / 10d +0.41 / 20d +0.44, the grid's largest)
   and collapses to ~0/negative at `high=60`. Firing is **un-holdable** across the grid (per-step support
   moves +46/+47/−52% rel), so the ARS firing-stability precondition is unsatisfiable *by construction* —
   the level-gate degeneration is the real structural tell. Aggregate (already weak) lift is a **2009–2014
   one-tape artifact** ([[lottery-vs-signature]]).

## Disposition

**REJECT (design-time kill) → KILLS BTC+Tyr.** The fresh component fails in isolation; nothing remains to
wire. Do **not** iterate by tuning `window`/`low`/`high` (no robust corner) or by bolting a SPY-regime gate
on top (= IS-fitting the very sign-flip that condemns it). Any breadth-event successor needs a
**structurally different transition predicate** (a true multi-day thrust/slope measure that is
regime-sign-consistent), screened from scratch *and* screened-together-with-[[gjallarhorn]] to avoid
double-counting the same breadth read. The premise *class* is re-scopable; this specific gate is rejected.

## Related

[[btc-tyr]] · [[gjallarhorn]] · [[lottery-vs-signature]] · [[thinning-not-selecting]] ·
[[thrust-degenerates-to-level]] · [[aliased-regime-sensitivity]] · [[crisis-timer-cadence-ceiling]]
