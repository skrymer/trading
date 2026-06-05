---
type: entity
title: Gjallarhorn
status: active
tags: [candidate, timing, overlay]
sources: ["strategy_exploration/GJALLARHORN_STRATEGY_DEVELOPMENT.md", "strategy_exploration/dossier/", "project_engine_flat_condition_stack_no_overlay"]
related: ["[[lottery-vs-signature]]", "[[crisis-timer-cadence-ceiling]]", "[[participate-and-lose]]"]
updated: 2026-06-05
---

# Gjallarhorn

A **breadth-thrust exhaustion-reversal** crisis-bottom long re-entry timer. Deploy long *after a
market-breadth washout reverses* (the violent post-capitulation snapback). Fires when there is **no
uptrend yet** (no uptrend required) — index-level breadth-*state* mean-reversion *at the bottom*. Anchor:
Zweig Breadth Thrust + the capitulation-reversal literature.

## Strategic role

Fills the **crisis/transition regime gap** every other long family in this project stands aside in or
dies in — breakout, mean-reversion-on-pullback, and RS-momentum rotation are all deprecated and all go to
cash in crisis (see [[purpose]]). Gjallarhorn is a *bottom-timer*, not a narrow-leadership participant, so
it carries no [[participate-and-lose]] death surface. Passed the data-span check (runs on market breadth,
trustworthy 2000-2025).

## Verdict so far

| Test | Result |
|---|---|
| Data-span check | ✅ PASS (market breadth, 2000-2025) |
| Timing-alpha NULL ([[lottery-vs-signature]]) | ✅ **PASS +22σ** — per-trade edge +2.19% vs null mean −0.17% (all 20 seeds negative), CAGR +2.39% > null median −0.78% |
| Standalone firewall | ❌ **funnel-disqualified** ([[crisis-timer-cadence-ceiling]]) |

**The NULL is the headline result.** The conditional within-regime null (20 seeds, entries from the same
`breadthPercent ≤ 25` population at matched rate, all else byte-identical) confirmed **timing alpha, not
crisis beta**: random same-regime dip-buying *loses* per trade (catching falling knives), while
Gjallarhorn's sustained-washout-then-recovery timing makes +2.19%. The unmatched bare-mask (buy *every*
breadth ≤ 25 day) was only +0.11%/trade — corroborating that the *specific* timing carries the edge.

## Why it's funnel-disqualified standalone

Standalone CAGR is ~2.39% because it's **cash most of the calendar** (in-market only at bottoms) — exactly
the profile of an **overlay component**, not a 25%-CAGR standalone. And ~17 crisis bottoms in 26 years
(~0.65/yr) means it cannot populate per-window OOS folds — the [[crisis-timer-cadence-ceiling]] by
construction. It can only be validated as one leg of a **higher-cadence composite** that trades every
window (needs nested condition groups, #93 — now resolved).

## The engine finding that drove the design

The two natural premise conditions **cannot co-fire on one bar**: `marketBreadthNearDonchianLow` fires
when breadth is pinned at the floor; `marketBreadthRecovering` fires only *after* breadth recrosses its
EMA10 (by which point it's off the floor). So a **new first-class *memory* condition** was required —
"breadth was at a washout extreme *within* the last K bars."

## Reusable findings (durable)

- **`breadthPercent` is a short-horizon oscillator, not a crisis floor** — mean 42.5, median 43.6, max
  88.3 (never ~100); breadth ≤ 15% is ≈ the **7th percentile**, a routine pullback level touched every
  year. A single-touch "washed out ≤ 15% within N days" gate fires constantly (1100 trades). The breadth
  Donchian channel is only **20 days** — "near the Donchian low" is a routine 20-day local minimum.
  **Implication for any future breadth condition: isolate real crises with a *sustained-run duration*
  requirement, not a single low touch.**
- The `marketBreadth*` condition family is **non-terminating under the `/condition-screen` auto-sweep**
  even on the reduced universe — surface firing rate via a single backtest instead.

## Status / forward

Shelved as a **research-confirmed overlay component** awaiting a host: the breakout+Gjallarhorn composite
A/B (now unblocked by #93), and ultimately a separately-validated regime-transition layer (#83). The 25%
CAGR floor is the composite's real test, not Gjallarhorn's standalone number.

## Related

[[lottery-vs-signature]] · [[crisis-timer-cadence-ceiling]] · [[participate-and-lose]] · [[2026-06-04-gjallarhorn-null]]
