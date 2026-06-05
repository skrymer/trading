---
type: concept
title: Thinning, Not Selecting
status: stable
tags: [failure-mode, methodology]
sources: ["project_minervini_vcp_breakout_rejected", "feedback_ablation_metric_confound_capital_aware", "feedback_random_ranker_baseline_mandatory", "strategy_exploration/TRACK2_BREADTH_GATE_PLAN.md"]
related: ["[[participate-and-lose]]", "[[lottery-vs-signature]]"]
updated: 2026-06-05
---

# Thinning, Not Selecting

When a filter added to rescue a strategy **removes trades uniformly without improving the ones it
keeps** — it *thins* the population instead of *selecting* the good names out of it. The tell: good
windows flip negative on luck while bad windows deepen.

## Definition

A *selector* changes the **composition** of trades (keeps winners, drops losers → edge rises). A
*thinner* changes only the **count** (drops winners and losers at the same rate → edge unchanged,
variance up). Adding a thinner to a [[participate-and-lose]] strategy makes it strictly worse: fewer
trades on the same dead edge.

## How to detect

- A rescue filter **halves trade counts uniformly in every window** rather than concentrating cuts in
  the bad windows.
- **Good windows flip positive → negative** (2006/2018/2019) while **bad windows deepen** (2011
  −4.6 → −14.3, 2016 −1.8 → −7.4). Deepening a *bad* window is the instant-reject signature — a real
  selector can only *improve* a window or leave it.
- Block B's "0 negative windows" proof is destroyed (in-mkt CAGR 20.8 → 12.1).

## Why it kills — the durable law

> **A scalar market-regime gate (one number/day, same for all names) cannot solve a cross-sectional
> per-name selection problem.** It has zero cross-sectional resolution, so on a breadth-low day it
> suppresses the genuine leader and the junk breakout *identically*. Thinning a window amplifies noise
> without changing edge.

The Minervini breakout proved this across **three** selector flavors — no gate (Track-1), scalar market
gate (`breadthEma10Above50`, Track-2), and even a **per-name** gate (`sectorBreadthGreaterThanMarket`,
Track-2b) — all thinned-and-deepened the same way. Conclusion: the failure is the **entry premise**, not
the selector. Do **not** open a 4th selector flavor — that's searching the design space against one OOS
realization ([[aliased-regime-sensitivity]] in design clothing).

## The ablation-metric corollary (why you can't shortcut this)

You cannot read a condition's contribution from a **capital-aware single backtest**:
- **Blended CAGR is ~monotone in trade count** — with fixed risk-per-trade + maxPositions cap on a
  tail-carried edge, removing almost any filter *raises* blended CAGR (more lottery tickets) even as
  Profit Factor craters. CAGR rewards filter-*removal*, which is not "better."
- **PF is tail-dominated and cross-population**; **monster-retention is sizer/concurrency-contaminated**.
- Read the **coherent joint pattern qualitatively** (PF + monster-retention + trade-count moving
  together with an a-priori mechanism). For a clean contribution metric use a **sizer-free
  `/condition-screen` + multiple realizations**. Carry a condition forward only on a *large + monotone
  + mechanism* effect, re-tested as a fresh screen.

## The ranker corollary — beat Random or it's beta

For a **permissive-entry + ranker-selects** candidate, the entire claimed alpha lives in the ranker, so
it must beat a **byte-identical Random-ranker baseline** on blended CAGR AND per-trade edge AND
positive-window count — *not* win-rate/WFE (a payoff-shape artifact). A permissive entry is a long-biased
basket whose ~1% per-trade beta Random harvests for free. George (52-week-high anchoring ranker) lost to
Random — its concentration was a *liability* (−14.3% GFC window vs Random's −2.1%).

## Related

[[participate-and-lose]] · [[lottery-vs-signature]] · [[component-firewall]]
