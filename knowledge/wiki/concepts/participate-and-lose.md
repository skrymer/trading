---
type: concept
title: Participate-and-Lose
summary: A long premise that stays fully deployed through the regime where its edge inverts — bleeding with full trade counts because the regime selector is too coarse.
status: stable
tags: [failure-mode]
sources: ["feedback_mean_reversion_pullback_known_weakness", "project_minervini_vcp_breakout_rejected"]
related: ["[[thinning-not-selecting]]", "[[aliased-regime-sensitivity]]", "[[lottery-vs-signature]]", "[[minervini-vcp-breakout]]"]
updated: 2026-06-05
---

# Participate-and-Lose

A long premise that **stays fully deployed through the regimes where its edge inverts**, bleeding with
full trade counts instead of standing aside. The killer is not that the edge is absent everywhere — it's
that the *regime selector is too coarse to see the bad tape*, so the strategy participates and loses.

## Definition

The entry premise has genuine alpha in its **native regime** (e.g. broad-participation uptrend) but
*negative* edge in an adjacent regime (narrow-leadership / Mag-7-concentrated tape, post-2020). The
regime gate it ships with — typically a **coarse index-trend signal like `spyTrendUp`** — only stands
the book aside in outright crisis (2008), so through narrow-breadth-but-index-up chop the strategy
trades at full size and bleeds.

## How to detect

- On the 25-year firewall, **negative *participating* windows** (windows the strategy traded in and
  lost), not empty windows: e.g. 8 of 21 negative on 25y, with **full 30-47 trade counts** in the bad
  windows (2015-16, 2021-23).
- **Clustered, consistent losses** in narrow-breadth windows (2023 −19.4%, 2015 −14.7%, 2021 −10.3%
  CAGR) — *not* the lumpy one-window concentration of [[lottery-vs-signature]], *not* the parameter
  sign-flips of [[aliased-regime-sensitivity]].
- The 10-year screen (2005-2015) **passes it** by straddling the broad 2009-13 recovery and never
  seeing a sustained narrow-leadership cluster — so this failure mode often only surfaces at the full
  firewall, which is why it earns a concept page.

## Why it kills

The edge is **regime-conditional and the selector can't resolve the regime**. You cannot tune the entry
out of it (the entry premise is *real* in its native tape) and you cannot tune the selector out of it
either — see [[thinning-not-selecting]] for why no market-breadth or per-name selector rescued the
breakout. The fix is a **breadth-confirmed regime-transition layer introduced as a NEW candidate
re-screened from Stage 1** (#83), never a post-hoc patch on the rejected config (that's IS-fitting to
the single OOS window).

## Instances

- **Long-pullback mean-reversion** ([[gjallarhorn]]'s opposite): VZ3-s3 passed Block A (+0.62% edge) and
  Block B (+0.48%) but **edge inverted to −0.11% in Block C** (2024), Sharpe 2.32 → 0.62. Flow rotates
  away from laggards before mean-reversion fires; leaders' pullbacks stay too shallow to touch the entry
  EMA. **Crucially, VZ3's participate-and-lose evidence is N=1** — a *single* Block-C OOS window (2024,
  under 36/12/12 cadence) — so unlike the breakout it is NOT regime-gateable: one unfavourable window
  can't distinguish a regime-conditional edge from one-off degradation, and a gate that dodges 2024 is
  IS-fitting to that window. The [[aliased-regime-sensitivity]] condemnation belongs specifically to
  [[idunn]] (the promoted VZ3 — the parameter sweep was run there): its "edge" is an off-by-one artifact
  with **no robust edge for a gate to preserve**. So the two mean-reversion deaths are *not* gateable the
  way the breakout is: VZ3 = N=1 evidence, Idunn = ARS (separator is the parameter, not a market regime).
- **Minervini VCP breakout** (REJECTED 2026-06-03): the breakout cousin. `spyTrendUp` too coarse;
  in-market geometric CAGR 9.6-20.8% ≪ 25% floor; 8/21 negative participating windows on 25y. Block B
  *proves the premise is real* in its native regime (0 neg windows, 20.8% in-mkt CAGR, real 2020 +56.5%
  recovery) — the problem is exclusively the selector.

## The shared root cause

Both the long-pullback and breakout families fail the same way: a **too-coarse index-trend regime gate
that can't see breadth rot**. This is why "escape from mean-reversion to breakout" is *not* a clean
escape — same root cause, different entry. The earned-dead premise classes in [[purpose]] share it.^[inferred — the shared spyTrendUp root cause is quant-stated for the MR and breakout families; extending it to the other ruled-out classes is synthesis. RS-momentum-rotation is downgraded to untested (not earned-dead), so it is excluded here.]

## Related

[[thinning-not-selecting]] · [[lottery-vs-signature]] · [[aliased-regime-sensitivity]] · [[the-funnel]]
