---
type: concept
title: Thrust Degenerates to Level
summary: A transition/thrust premise whose sweep concentrates all edge in the cell nearest a plain level threshold — it's really a level gate, not a transition. Distinct from clean ARS.
status: seed
tags: [failure-mode, methodology]
sources: ["2026-06-08-btc-breadth-thrust-screen-reject"]
related: ["[[thinning-not-selecting]]", "[[aliased-regime-sensitivity]]", "[[lottery-vs-signature]]", "[[btc-tyr]]"]
updated: 2026-06-08
---

# Thrust Degenerates to Level

A premise *designed* as a **transition/thrust** detector (something *changed* — breadth surged, momentum
accelerated, a level was *crossed from below*) but which, on a parameter sweep, reveals its entire edge
sitting in the cell **nearest a plain static level threshold**. The "transition" framing is cosmetic; the
signal is really reading **level** — and a level gate only thins, never selects ([[thinning-not-selecting]]).

## Definition

A thrust gate has two bounds: a *from* (the depressed prior state — "dipped ≤ low") and a *to* (the surge —
"now ≥ high"). It degenerates to a level gate when **loosening the surge bound monotonically raises lift**
(the edge wants `high` as low as possible — i.e. it wants "breadth is currently high," not "breadth just
surged"), and the *from* bound stops mattering. At that point you have re-derived `breadthPercent ≥ X`, a
static level read, dressed as a transition.

## How to detect (at `/condition-screen`)

- **Edge concentrates toward the level cell.** Across the surge-bound sweep, lift is largest at the
  loosest `high` cell and collapses (→0/negative) as you demand a genuinely higher surge. The grid's best
  corner is the one closest to a plain threshold.
- **Firing rate is un-holdable across the grid** — every parameter step blows past the ±25% support-cliff
  (e.g. per-step firing moves of +46% / +47% / −52% relative). This is *why* a clean ARS read is
  impossible here: ARS requires firing held ~stable to isolate the parameter dimension from population
  effects. When the gate is really a level threshold, firing moves with the threshold, so the
  firing-stability precondition is **unsatisfiable by construction**.
- **The transition (`from`) bound is inert** — removing or relaxing the dip requirement barely changes the
  firing set or the lift; all the action is in the level (`to`) bound.

## Why it's distinct from clean ARS

[[aliased-regime-sensitivity]] is *non-monotone* pass/fail with sign-flips **at stable firing** — the
parameter is the wrong abstraction for a real underlying edge. Thrust-degenerates-to-level is the opposite
diagnostic: there is **no real edge** to alias; firing **cannot** be held stable; and the lift is
*monotone* toward the level cell, not oscillating. Mark such a sweep's ARS read
**inconclusive-by-construction** and report the level-gate degeneration as the actual structural tell.

## Why it kills

If the only configuration with lift is the one nearest a static level, you do not have a transition signal
— you have a level gate, which the breadth evidence shows only *thins / scales payoff magnitude* and never
*selects* ([[thinning-not-selecting]]). Do not ship the loosest cell (that's picking the value that passes
after seeing the result) and do not iterate the bounds (no robust corner exists). A genuine thrust premise
must show lift that **survives demanding a real surge** and is carried by the transition, not the level.

## Instances

- **BTC breadth-thrust gate (2026-06-08)** — [[2026-06-08-btc-breadth-thrust-screen-reject]]. All lift in
  `high=50` (the near-level cell), collapsing at `high=60`; firing un-holdable (+46/+47/−52% per-step);
  the dip bound largely inert. Combined with a SPY-regime sign-flip and a 2009–2014 one-tape concentration
  ([[lottery-vs-signature]]), it killed [[btc-tyr]] at design time. ^[inferred] — first instance; promote
  this page from `seed` to `stable` if a second transition premise degenerates the same way.

## Related

[[thinning-not-selecting]] · [[aliased-regime-sensitivity]] · [[lottery-vs-signature]] · [[btc-tyr]]
