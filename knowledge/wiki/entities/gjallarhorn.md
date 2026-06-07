---
type: entity
title: Gjallarhorn
summary: Breadth-washout crisis-bottom timer; timing alpha CONFIRMED (NULL +22σ) but composite A/B is NO-GO — SHELVED as a certified-but-homeless matched pair with the breakout. No iteration.
status: stable
tags: [candidate, timing, overlay, shelved]
sources: ["strategy_exploration/dossier/", "project_engine_flat_condition_stack_no_overlay", "project_regime_conditional_portfolio_framework"]
related: ["[[lottery-vs-signature]]", "[[crisis-timer-cadence-ceiling]]", "[[participate-and-lose]]", "[[regime-conditional-portfolio]]", "[[2026-06-04-gjallarhorn-null]]"]
updated: 2026-06-07
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
| Composite A/B (30% overlay on the shelved breakout) | ❌ **NO-GO** (2026-06-04) — full-period MAR +5% / DD +0% vs breakout+T-bills; benefit crisis-confined (Block B +31% MAR, Block C +46% / −7% DD); all 3 pre-registered NO-GO triggers fired |

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

## The composite A/B that shelved it (2026-06-04)

The NULL confirmed timing alpha, so the next step was the **breakout + Gjallarhorn composite** — validated
as an **offline two-curve blend** (the quant ruled an in-engine #93 composite would decapitate the
breakout's right-tail runner and run Gjallarhorn in its forbidden AtrRisk sizer). Standalone A/B inputs
(300-sym, 2000-2026):

| Leg | CAGR | maxDD | MAR (Calmar) | Sharpe |
|---|---|---|---|---|
| Breakout (Minervini EX-ATR20×SSM) | 5.32% | 22.1% | 0.241 | 0.66 |
| Gjallarhorn (sustained washout) | 2.39% | 20.9% | 0.114 | **1.04** |

Blended (70/30, swept 25/75·30/70·35/65), the composite returned **NO-GO**: full-period MAR +5% / DD +0%
vs breakout+T-bills, benefit confined to crisis windows (Block B +31% MAR, Block C +46% / −7% DD); all
three pre-registered NO-GO triggers fired. **Structural, not fixable:** a mostly-cash crisis leg (~10%
active) as a *fixed* sleeve is ~30% cash drag (can't move the book), and its crisis-timed drawdowns overlap
the breakout's (long-only ⇒ everything correlates in crises, [[regime-conditional-portfolio]] / ADR 0010)
→ no DD offset. The only construction that captures its value is **dynamic regime-switching** = the
abandoned regime-portfolio program. **No iteration** (tuning sleeves/exits = IS-fitting).

## Reusable findings (durable)

- **Crisis-episode run-length ground truth** (the calibration any future breadth-crisis condition needs) —
  `breadthPercent ≤ threshold` for ≥ K consecutive days:
  - **≤ 15% for ≥ 10 consecutive days → 17 episodes / 26y:** 2001-09, 2002-07, 2004-05, 2008 (Jun/Sep/Nov),
    2009-02, 2011 (Jun/Aug/Sep), 2014-09, 2015-08, 2016-01, 2018 (Oct/Dec), 2020-02 (31d), 2022-09.
  - **≤ 10% for ≥ 10 consecutive days → 8 episodes** (major crises only): 2002-07, 2008 (×2), 2009-02,
    2011-08, 2016-01, 2018-12, 2020-02 (28d).
  - The built condition `MarketBreadthSustainedWashoutWithinCondition(threshold=15, consecutiveDays=10)`
    (**consecutive, NOT N-of-M** — N-of-M readmits the every-year touches) hit the cadence target
    ~0.5-0.65/yr, near-zero in calm years.
- **`breadthPercent` is a short-horizon oscillator, not a crisis floor** — mean 42.5, median 43.6, max
  88.3 (never ~100); breadth ≤ 15% is ≈ the **7th percentile**, a routine pullback level touched every
  year. A single-touch "washed out ≤ 15% within N days" gate fires constantly (1100 trades). The breadth
  Donchian channel is only **20 days** — "near the Donchian low" is a routine 20-day local minimum.
  **Implication for any future breadth condition: isolate real crises with a *sustained-run duration*
  requirement, not a single low touch.**
- The `marketBreadth*` condition family is **non-terminating under the `/condition-screen` auto-sweep**
  even on the reduced universe — surface firing rate via a single backtest instead.

## Status / forward

**SHELVED — terminal (2026-06-04).** Timing alpha is real and the composite A/B was run and returned
NO-GO; Gjallarhorn is a **certified-but-homeless matched pair with the shelved [[minervini-vcp-breakout]]**
— both validated, neither standalone-tradable, awaiting a portfolio framework the long-only engine cannot
currently express (the [[regime-conditional-portfolio]] program that would have hosted them is itself
abandoned). **No iteration.** Durable assets kept: the three washout conditions, the conditional
within-regime NULL methodology, and the breadth-oscillator distribution + 17-event crisis map above.
Next premise class: [[btc-tyr]].

## Related

[[lottery-vs-signature]] · [[crisis-timer-cadence-ceiling]] · [[participate-and-lose]] · [[2026-06-04-gjallarhorn-null]]
