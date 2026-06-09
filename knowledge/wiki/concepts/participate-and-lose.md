---
type: concept
title: Participate-and-Lose
summary: A long premise that stays fully deployed through the regime where its edge inverts — bleeding with full trade counts because the regime selector is too coarse.
status: stable
tags: [failure-mode]
sources: ["feedback_mean_reversion_pullback_known_weakness", "project_minervini_vcp_breakout_rejected", "knowledge/wiki/sources/2026-06-09-r1-leadership-gap-breakout-abandon.md"]
related: ["[[thinning-not-selecting]]", "[[aliased-regime-sensitivity]]", "[[lottery-vs-signature]]", "[[minervini-vcp-breakout]]", "[[r1-leadership-gap-breakout]]"]
updated: 2026-06-09
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
breakout.

**The market-level-gate rescue is now mechanistically dead, not merely untried.** The fix this page used
to prescribe — a *breadth-confirmed regime-transition layer as a NEW candidate* (#83) — was built,
screened, and **REJECTED** ([[r1-leadership-gap-breakout]], 2026-06-09). A market-state ON/OFF gate
**cannot** rescue this mode, because the loss lives in the **cross-section** (the individual breakout
fails at a fresh high in thin tape, even on a day a perfect classifier calls "broad") while any
market-level gate acts on the **calendar** (deploy/cash days) — one level of aggregation *above* where
the alpha decays. It can only remove days; it can't fix the entries on the days it keeps. R1's clean,
pre-registered, non-overfit gate deployed with ~zero correlation to per-year edge (`corr≈0`; ON 48–67%
of the narrow 2021/23/24 years) and left in-market Calmar at 0.32 ≈ the ungated 0.42. **The real fix is a
structurally different ENTRY premise** with genuine cross-sectional resolution in thin tape — never a
market-level regime overlay (a post-hoc patch on the rejected config is additionally IS-fitting to the
single OOS window).

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
- **[[r1-leadership-gap-breakout]]** (ABANDONED 2026-06-09): the breakout + the #83 leadership-gap regime
  gate — the *mechanistic proof* that this mode is un-gateable by a market-level layer. A clean,
  pre-registered gate deployed orthogonally to the edge (`corr(ON-fraction, annual edge)≈0`; ON 48–67% of
  2021/23/24), still bled in 2021 (−6.98%, deployed-and-bleeding), and left in-market Calmar 0.32 ≈ the
  ungated 0.42. Promotes the failure mode from *empirically observed* to *mechanistically explained*.

## The shared root cause

Both the long-pullback and breakout families fail the same way: a **too-coarse index-trend regime gate
that can't see breadth rot**. This is why "escape from mean-reversion to breakout" is *not* a clean
escape — same root cause, different entry. The earned-dead premise classes in [[purpose]] share it.^[inferred — the shared spyTrendUp root cause is quant-stated for the MR and breakout families; extending it to the other ruled-out classes is synthesis. RS-momentum-rotation is downgraded to untested (not earned-dead), so it is excluded here.]

## Related

[[thinning-not-selecting]] · [[lottery-vs-signature]] · [[aliased-regime-sensitivity]] · [[the-funnel]]
