---
type: concept
title: Crisis-Timer Cadence Ceiling
summary: A premise firing <~1 event/yr can't populate per-window OOS folds — funnel-disqualified standalone before Step 0; the data-span rule is its sibling.
status: stable
tags: [failure-mode, methodology]
sources: ["feedback_crisis_timer_cadence_ceiling", "feedback_signal_must_span_firewall_window"]
related: ["[[lottery-vs-signature]]", "[[gjallarhorn]]", "[[the-funnel]]"]
updated: 2026-06-05
---

# Crisis-Timer Cadence Ceiling

A premise that fires on **fewer than ~1 trade-event per year** is **structurally un-validatable** as a
standalone strategy under the per-window walk-forward firewall — flag it **funnel-disqualified before
Step 0**, not after burning build/screen effort.

## Definition

This is a **structural** disqualification (like the data-span rule below), not a performance rejection —
the candidate never reaches a backtest verdict. The binding constraint is the historical **supply** of
events, which no parameter touches.

## Why — the supply trap

A crisis-bottom timer produces ~17 episodes in 26 years (~0.65/yr). The 3-block firewall then has Block C
n ≈ 2 (one event sets the sign; G5/G6/G7 uncomputable), and most walk-forward OOS windows contain **zero
trades** → WFE / per-window stability uncomputable. It **is** the lottery pattern
([[lottery-vs-signature]]) *by construction*.

No gate-shaping fixes it: every gate is a **selectivity** operator (only removes firings), but the
shortage is of *firings*. Trapped — **loosen** the trigger → it's no longer a crisis timer; **tighten** →
un-validatable.

## How to apply

- When scoping a candidate, **estimate trade-events/year first**. If < ~1/yr, do not run it standalone.
- The only path is embedding it as one leg of a **higher-cadence composite** that trades every window —
  validate the *composite* as a unit, not the rare leg in isolation. This needs the engine to express
  multi-premise strategies (nested condition groups, #93 — now resolved).
- Even as a composite leg, weigh it against the 25% CAGR floor — a standalone crisis timer is cash most
  of the calendar.

## The sibling structural disqualifier — data span

Before scoping *any* candidate, check its core signal/instrument **spans the firewall window**: data to
**screen** on 2005-2015 AND **validate** on Block A (2000-14) + Block B (2014-21). A recent-only (~5y)
signal sits **inside Block C** (the only true holdout) → can't screen (no pre-2015 data) and can't
validate without leaking. Recurs constantly: leveraged ETFs (post-2009), RSP equal-weight (2003),
bonds/gold (pre-2002 gap), the Ovtlyr signal (~5y → Forseti, disqualified 2026-06-04), listing-date
primitive (Vidar). **Query `min(date)`/`max(date)` first.**

## Instance

[[gjallarhorn]] — passed its timing-alpha NULL at +22σ ([[lottery-vs-signature]]) but is exactly this:
~17 crisis bottoms in 26y → un-validatable standalone, pivoted to an overlay component awaiting a host.

## Related

[[lottery-vs-signature]] · [[gjallarhorn]] · [[the-funnel]]
