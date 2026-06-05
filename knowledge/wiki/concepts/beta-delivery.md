---
type: concept
title: Beta-Delivery
summary: A long book whose risk-adjusted return is just the index's — profitable in absolute terms but no better than holding SPY; the failure mode G16 catches.
status: seed
tags: [failure-mode, methodology]
sources: ["docs/adr/0013-spy-buy-and-hold-is-a-binding-calmar-only-firewall-baseline.md", "strategy_exploration/FUNNEL_DEEPRESEARCH_FINDINGS.md"]
related: ["[[component-firewall]]", "[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[the-funnel]]"]
updated: 2026-06-05
---

# Beta-Delivery

> **status: seed** — the failure-mode anatomy and detector are settled, but no candidate has been
> rejected by [[component-firewall]]'s G16 gate yet. The *Instances* section is intentionally empty;
> the first real rejection promotes this to `active` with its numbers (via `/wiki-ingest`).

## Definition

A candidate **delivers beta** when its return is just *market exposure in disguise* — it makes money
in absolute terms, but no more than you'd have made (risk-adjusted) by **just holding SPY** over the
same days. The "alpha" is the index's drift, not the strategy's selection or timing. For a long-only
book this is the default null hypothesis, not an exotic failure: any always-long-ish premise inherits
SPY's bull-market return, so an attractive-looking CAGR proves nothing until it is measured *against*
the passive alternative.

## How to detect

The firewall's **G16 SPY buy-and-hold Calmar baseline** is the detector (see [[component-firewall]]):
strategy stitched-OOS **Calmar ≥ SPY's Calmar** over the *identical* OOS-stitched support, binding on
Block A + Block B + the 25y aggregate. A FAIL = the candidate's risk-adjusted return did not beat
holding the index → beta-delivery → REJECTED.

Why **Calmar**, and why **relative** to SPY:

- **Calmar, not Sharpe** — a part-in-cash long-only timer is structurally penalised on Sharpe in a
  low-vol bull block (its cash days drag the per-day mean while always-invested SPY sits ~1+). Calmar
  is neutral to sitting in cash and rewards crash-avoidance, so it isolates real selection/timing skill
  rather than time-in-market.
- **Relative, not absolute** — the absolute Calmar floor (G15) asks "is this tradable *quality*?"; G16
  asks "did it beat *the alternative you already have for free*?". A candidate can clear the absolute
  floor and still fail G16 — high absolute Calmar that is nonetheless ≤ SPY's over the same window is
  still just beta.^[inferred]

Adjacent, cheaper tells before the firewall: a permissive-entry + ranker-selects candidate that loses to
a byte-identical **random-ranker baseline** on blended CAGR *and* per-trade edge is delivering
entry-universe beta (the random-ranker-baseline rule); and a high *correlation*/`beta` to SPY in the
single-backtest `BenchmarkComparison` is a yellow flag that the eventual G16 read may confirm.

## Why it kills

A long-only book that can't beat the index, risk-adjusted, has **no reason to exist** — it adds
operational complexity, execution cost, and single-strategy risk to reproduce something an index fund
delivers passively at lower cost. Worse, beta dressed as alpha is *fragile in exactly the regime you
deploy it for*: when the market that supplied the beta turns (narrow-leadership tape, a drawdown the
timer doesn't dodge), the "edge" evaporates because there was no selection skill underneath it. This is
the structural cousin of [[participate-and-lose]] and the through-line of
[[long-premise-in-narrow-leadership]] — both are about long exposure that looked like skill until the
supporting regime withdrew.

## Why a sizer can't fix it

G16 is a statement about the *shape* of the return stream (Calmar = CAGR / |maxDD|) relative to SPY's
shape over the same days. A position sizer or exit tweak rescales the curve but cannot manufacture
SPY-relative risk-adjusted advantage that the *selection/timing* did not produce. A G16 FAIL therefore
demands a structurally different entry premise, never a sizer/exit iteration (the standard firewall
remediation discipline — see [[the-funnel]]).^[inferred]

## Instances

_None yet._ G16 was implemented in #102 (ADR 0013); no candidate has been rejected by it at the time of
writing (2026-06-05). When one is, record it here with: block(s) failed, `strategyCalmar` vs
`benchmarkCalmar`, whether it nonetheless cleared the absolute floors (G1/G2/G9/G15), and the regime in
which its beta was sourced.

## Related

[[component-firewall]] · [[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[the-funnel]]
