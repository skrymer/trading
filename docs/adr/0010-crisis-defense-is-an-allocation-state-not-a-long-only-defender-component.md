---
status: accepted
---

# Crisis defense is an allocation state (cash, via the regime read-out), not a long-only "defender" component

The regime-conditional portfolio framework originally assumed its second component would be a **crisis/defensive specialist** — a "defender" that is *active and profitable* in 2008/2020 to balance the risk-on participants and unlock the deferred portfolio gates (`C-CASHOVERLAP`, Portfolio-blend G6, both of which name "survive 2008 + 2020"). We decided that **no such component exists in this engine**: defense is an **allocation state** — sitting in cash, routed by the Track #2 regime read-out — not a strategy with its own alpha. Consequently "≥2 components" is reframed as a **coverage** requirement (two long specialists harvesting *different non-crisis up-tapes*), not an attack/defend pairing, and `C-CASHOVERLAP` is re-scoped to exempt crisis cash.

See `CONTEXT.md` *Crisis defense* / *Coverage* / *Regime specialist* for the terms, and `strategy_exploration/REGIME_CONDITIONAL_BATTLE_PLAN.md` for the trading plan this governs. This ADR records *why* there is no defender so a future session does not re-propose one.

## Why the engine forbids a positive-edge defender

The backtest engine is **strictly long-only**: no short, no direction/side, no negative quantity, and P&L is hardcoded `exitPrice − entryPrice` (`backtesting/service/BacktestService.kt:244`). "Make money when the market falls" therefore has exactly three expressions, and all three are disqualified:

- **Long inverse leveraged ETFs** (SQQQ, SPXU, SDOW, TZA — present under `AssetType.LEVERAGED_ETF`). This is the *same thin ETF universe family already REJECTED twice* (the leveraged-long-ETF attempts, premise capped — a ranker bake-off had Random tie the smart rankers), and inverse ETFs add **daily-rebalance decay drag** on top — strictly worse than the long-3× attempts that already failed.
- **Long bonds / gold safe-havens** (`BOND_ETF`, `COMMODITY_ETF`). Decay-free and genuinely different, but **TLT/GLD/SHY do not exist before ~2002–2004**, so the 2000–2002 dot-com crisis window has *no tradable instrument*, and the thin instrument set re-triggers the capped-palette problem.
- **Pure cash stand-aside.** No edge — and it is exactly the "always in cash trivially passes every gate" case the `C-PARTICIPATE` gate was invented to *reject*.

There is no long instrument that both rises in a crash *and* survives the firewall's own data/universe constraints. Defense is thus an allocation decision, not an edge.

## Why this satisfies the portfolio gates anyway

**Portfolio-blend G6 (book survives 2008 + 2020) is a *survival* gate — it rewards *not losing*, never *winning*.** A book whose every long component correctly goes to cash in crisis has ~zero crisis drawdown and survives maximally well. No bear component is needed. The single contingency: the read-out's crisis classifier must actually *fire the cash state* in 2008/2020 rather than leave a specialist deployed-and-bleeding — `spyTrendUp` was too coarse for exactly this, so the sharpness of the crisis label is Track #2's burden, not a defender's.

## Why "≥2 components" is coverage, not defense

The reason to build a second component survives the long-only constraint, but its *rationale changes*: it is **diversification across non-crisis risk-on sub-regimes**, so the book is rarely all-cash when opportunity exists. All expressible components are long; they share crisis cash by construction. They earn separate keep only by being active in *different* up-tapes (the shelved Minervini breakout in broad-rally thrust; a low-vol/quality grind specialist in the low-dispersion grind). A second component active only when the first is active collapses into its beta and earns nothing.

## The `C-CASHOVERLAP` re-scope (a gate-bug fix)

As originally written — "stand-aside windows must not all coincide with a partner component's cash windows" — `C-CASHOVERLAP` is a **bug** in a long-only engine: every valid long component *correctly* shares crisis cash, so the gate false-rejects every valid book and directly contradicts G6 (which *credits* that same shared cash). Re-scoped: **crisis/bear windows are exempt** (mandatory shared survival, credited by G6); the gate measures coincidence of stand-aside windows **in non-crisis windows only** — a frozen ceiling on the fraction of non-crisis OOS days where *all* components sit in cash. This makes it a genuine *coverage* gate again, distinct from G6's *survival* role.

## Consequences

- **There is no "defender" / crisis / inverse-ETF / bond-rotation component on the roadmap.** A future session that proposes one must first overturn the long-only engine constraint (a real engine change to support shorts), not merely pick a different premise.
- **The framework may honestly reduce to one component + a cash read-out.** If the low-dispersion-grind specialist (Track #1) fails to validate, the only validated component is the shelved breakout, and `C-CASHOVERLAP` / Portfolio-blend G6 stay permanently deferred. This is an accepted possible outcome. **— This is exactly what happened: on 2026-06-03 the grind specialist was judged non-viable (its native tape has no dispersion to harvest) and the regime-conditional PORTFOLIO program was abandoned — it reduced to one timed strategy + cash at ~4–6% blended CAGR, below any defensible target. This ADR's engine truth (long-only ⇒ defense-is-cash) survives the abandonment; the portfolio thesis built on it does not. See `REGIME_CONDITIONAL_BATTLE_PLAN.md` post-mortem.**
- **The Component Firewall's `C-PARTICIPATE` and §5 window classifier are re-derived per archetype**, never inherited from the breakout — a grind specialist participates in different windows than a thrust specialist.
- **Track #2 (the regime read-out) is load-bearing, not a convenience.** Crisis survival, breakout deployment, and the grind/thrust hand-off all depend on a market-defined, pre-registered, OOS-validated regime classifier (issue #83).
