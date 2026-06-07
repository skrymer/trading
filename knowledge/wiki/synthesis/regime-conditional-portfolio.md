---
type: synthesis
title: The regime-conditional portfolio program (ABANDONED)
summary: A long-only regime-conditional portfolio (specialist per up-tape, else cash) — abandoned 2026-06-03: long-only ⇒ no defender + no viable 2nd long component, collapsing to one strategy + cash.
status: superseded
tags: [program, abandoned, portfolio, methodology]
sources: ["docs/adr/0010-crisis-defense-is-an-allocation-state-not-a-long-only-defender-component.md", "project_regime_conditional_portfolio_framework"]
related: ["[[minervini-vcp-breakout]]", "[[gjallarhorn]]", "[[thinning-not-selecting]]", "[[participate-and-lose]]", "[[beta-delivery]]", "[[purpose]]"]
updated: 2026-06-07
---

# The regime-conditional portfolio program (ABANDONED 2026-06-03)

> ⛔ **Program abandoned 2026-06-03** (operator decision after a full design grilling + quant
> program-level review). **Do not resume it; do not re-propose a regime specialist.** The engine truth it
> rests on (long-only ⇒ defense-is-cash) survives in **ADR 0010**; the *portfolio thesis built on it* does
> not. Captured here so a future session does not re-derive it from scratch. Memory:
> `project_regime_conditional_portfolio_framework`.

## The thesis (what it was)

A regime-conditional **risk-on system** for a strictly **long-only** engine: the book is in exactly one
regime state at a time; deploy a long specialist in the up-tape where it has a real edge, and **sit in
cash** through every other tape. Built on the standing mandate "a portfolio of regime-*specialist*
components, not one uber-strategy." It fired no backtest — it was a strategic plan.

## Why it was abandoned (the post-mortem)

1. **Long-only engine ⇒ no defender component** (ADR 0010). P&L is hardcoded `exit − entry`; "make money
   when the market falls" has only three expressions and all are disqualified (inverse 3× ETFs = capped
   thin family + decay drag; bonds/gold = no instrument pre-2002; pure cash = no edge). Defense is an
   **allocation state**, not a strategy with alpha. So "≥2 components" became a **coverage** requirement,
   not attack/defend.
2. **No viable 2nd long component exists.** Narrow-leadership long = the breakout's **twin** (dies the same
   [[participate-and-lose]] way). The low-dispersion **grind** specialist is non-viable: that tape *by
   definition has no cross-sectional dispersion to harvest*, and the concentration ceiling that decorrelates
   it from the breakout is the same ceiling that caps its CAGR. Every premise that clears a CAGR bar in calm
   tape loads into the crowded leaders = the banned twin. Boxed by construction.
3. **So the "portfolio" reduces to ONE timed strategy (the shelved breakout) + cash** — a market-timing
   overlay on a single mediocre strategy, not a portfolio. The multi-component Sharpe-via-orthogonality
   thesis is gone.
4. **The arithmetic kills the 25% target** (quant 2026-06-03, estimates; `f` not measured): breakout
   cross-block in-market CAGR ≈ **12%** (blocks 9.6 / 20.8 / 9.2 — not the cherry-tested Block-B 20.8%),
   active fraction **f ≈ 0.32** → **blended CAGR ≈ 4-6%** (incl. ~3% cash yield on the idle ~68%). 25%
   blended needs ~120% in-market = the leverage the engine forbids. **Max defensible ≈ 5-7%.**
5. **The only honest pitch was MAR, not CAGR** (~half SPY return at lower drawdown): MAR ≈ 0.25 vs SPY
   ≈ 0.15 = only ~1.5-2×, *entirely contingent* on an unbuilt read-out beating `spyTrendUp` (the exact
   thing that failed before). Not worth solo-operator complexity unless DD reduction is dramatic (MAR ≥ ~3×
   SPY).
6. **Breakout-extension-hold rejected** — raises active fraction but holds names into the breakout's
   documented give-back tape (2015 −14.7%, 2021 −10.3%, 2023 −19.4%); net neutral-to-negative.

## What survives (the program is dead, the parts are not)

- The **shelved [[minervini-vcp-breakout]]** + its promoted G14-verified conditions (PR #85).
- **[[gjallarhorn]]** — the +22σ crisis-bottom timer, a certified-but-homeless matched pair with the
  breakout (its own composite-A/B NO-GO confirmed the same long-only-correlation wall).
- **ADR 0010** (long-only ⇒ defense-is-cash) — a durable engine truth.
- The **market-regime vocabulary** (THRUST / NARROW / GRIND / CHOP / CRISIS + the leadership-concentration
  gap) — kept as market-structure language in `CONTEXT.md`.
- The **regime read-out design** — the quant-signed, pre-registered 3-axis classifier spec captured
  below — **shelved**; revive only if regime-attribution is ever wanted as a research instrument (#83).

## The shelved regime read-out (3-axis classifier, quant-confirmed 2026-06-03)

A **read-out the operator consults** (not an auto-switcher), classifying on three cheap orthogonal axes,
all full-history to 2000. A single magnitude-only "dispersion" signal was **ruled out** — sign-blind,
can't separate thrust from narrow.

1. **Breadth (participation)** — `% of STOCK-type symbols in uptrend` + EMA10 + slope. Never confirm
   participation with a SPY-price gate (cap-weighted; mega-caps mask breadth rot — 2H-2021, 2023-24).
2. **Leadership-concentration gap (the thrust/narrow discriminator)** — the *signed* 20-day rolling-return
   gap `SPY(20d) − EW-universe-mean(20d)`, cross-checked vs SPY−RSP (2003+). **The sign is the tell:**
   gap < 0 (equal-weight leads) ⇒ broad **thrust**; gap > 0 (cap-weight leads) ⇒ **narrow-leadership**.
3. **Realized vol** — SPY trailing 20-day; the low band is what pins **GRIND** apart from an early-narrow
   drift.

**Hard discipline:** the classifier must be **market-defined, pre-registered, OOS-validated — never fit to
a strategy's good/bad years** (labelling 2011/21-23 "bad" *because* the breakout lost there =
IS-fitting / ARS). Only the 3-bucket collapse (PARTICIPATE-BROAD / STAND-ASIDE-NARROW / CRISIS) was ever
permitted to flip a binding firewall gate.

## The Component Firewall (the C-gate validation methodology, shelved with the program)

Validating a *regime-conditional component* needed a firewall variant distinct from the standard
[[component-firewall]] — because the standard v4 firewall **false-rejects** a mostly-in-cash component by
design (G1 blended-CAGR drag, G6/G7 regime mandates unreachable, G8 reads stand-aside as liquidity
failure). The core principle: **validate alpha in-market and discipline in-cash separately — never credit
cash, never penalize it.** Durable anti-laundering rules from the quant review, kept if the program is
ever revived:

- **C-PARTICIPATE** floor — without it "always in cash trivially passes every gate" (the case it exists to
  reject).
- **§5 window classifier must be AND, not OR** — OR lets a participating loss masquerade as cash discipline
  (the worst laundering leak).
- **C1c split** (Sharpe on blended, Calmar in-market) prevents laundering a weak edge.
- **Every ★ threshold is from an external anchor, frozen *before* runs are read** — runs check realized
  values against frozen ceilings, never choose them (generalizes G13 / ARS discipline to threshold-setting).
- **A permitted negative window must be *pre-named*** (e.g. 2011), never "the worst window whatever it turns
  out to be" — a movable exemption invites post-hoc absorption of a second bad window.
- **In-market geometric < blended CAGR is a dispersion-dominated (not alpha-dominated) tell** — see
  [[lottery-vs-signature]].

## Related

[[minervini-vcp-breakout]] · [[gjallarhorn]] · [[participate-and-lose]] · [[thinning-not-selecting]] · [[beta-delivery]] · [[purpose]]
