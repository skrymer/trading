---
type: concept
title: The Component Firewall
summary: The binding 3-block + 25y validation (Block C informational), its anti-data-mining interlocks (G10/G11/G13/G14), the absolute gates, and the SPY-baseline gate (G16).
status: stable
tags: [methodology]
sources: ["strategy_exploration/COMPONENT_FIREWALL_PLAN.md", "strategy_exploration/BACKTESTING_FUNNEL.md"]
related: ["[[the-funnel]]", "[[participate-and-lose]]", "[[parameter-robustness-g13]]", "[[beta-delivery]]"]
updated: 2026-06-05
---

# The Component Firewall

The binding validation layer of [[the-funnel]] — `/validate-candidate`. A candidate that clears it is
*tradable*; one that fails is *rejected* (and its config hash goes dead, ADR 0008). The exact frozen
gate table lives in the `/validate-candidate` skill + `COMPONENT_FIREWALL_PLAN.md §4b` (don't restate
it here — it drifts). This page is the *shape and intent*.

## The four layers

| Layer | Window | Binding? |
|---|---|---|
| Block A | 2000-2014 | **binding** |
| Block B | 2014-2021-H1 (COVID-inclusive) | **binding** |
| 25-year aggregate | pooled-and-restitched OOS | **binding** |
| Block C | 2021-2025 | **informational** (yellow flag, never a binding fail) |

Block C is informational because it's the only true holdout — making it binding invites snooping it.
The 25-year aggregate must be **genuinely restitched** (one maxDD over the full concatenated support),
never derived from sub-block Calmars — the worst drawdown can straddle the A/B seam (ADR 0013).

## The anti-data-mining interlocks

- **G10 design isolation** — the `config hash` is frozen across blocks; no per-block tuning.
- **G11 cross-block edge decay** — `edge_B ≥ 0.5 · edge_A`; the edge must persist, not collapse.
- **G13 parameter robustness** — a TRADABLE verdict must survive ±1 step on every discrete tunable and
  ±10% on every continuous one. See [[parameter-robustness-g13]]. Picking the value that passes *after*
  seeing the OOS result is data-snooping.
- **G14 implementation invariance** — a promoted condition must reproduce the inline-script trade list
  by `(entry_date, symbol)` over 25y, or the inline verdict is void.

## Absolute gates (post ADR 0014/0015 recalibration)

The firewall moved from *relative* baselines (beat-VCP) to **absolute** floors: CAGR ≥ 25% (G1),
maxDD ≤ 25% (G2), Sharpe ≥ 0.5 (G9), absolute Calmar ≥ 1.5 (G15). *(The G15/G9/G1 recalibration + DSR
flag are ADRs 0014-0016; tracked in #103/#105/#106 — decisions recorded, code pending as of 2026-06-05.)*

## G16 — the SPY buy-and-hold baseline (the one surviving *relative* gate)

The move to absolute floors kept exactly one relative gate, because it asks what no absolute floor can:
did the candidate beat **just holding SPY** on Calmar? A long-only book that can't is delivering index
beta, not alpha ([[beta-delivery]]). **Binding** on Block A + Block B + the 25y aggregate; informational
on Block C — and complementary to the absolute Calmar floor (G15): a candidate can clear one and fail
the other.

- **Calmar-only.** Sharpe is reported but never gated — a part-in-cash long-only timer is structurally
  penalised on Sharpe in a low-vol bull block (cash days drag its per-day mean while always-invested SPY
  sits ~1+), whereas Calmar is neutral to sitting in cash.
- **Stitched-OOS SPY, not full-span.** The SPY leg is stitched through the *identical* per-window OOS path
  as the strategy curve (same trading-day support); benchmarking against full-span SPY would manufacture
  fake defensive alpha from crashes that merely fall in IS gaps.
- **Engine-computed.** `WalkForwardService` emits the verdict on the WF result; the skill only reads it
  (no Calmar comparison in skill code).
- **INCONCLUSIVE** (no bind, never auto-fail) below 60 OOS days or **strategy** stitched maxDD < 3% — a
  tiny denominator manufactures an explosive Calmar that would falsely "beat" SPY (quant-adjudicated floor,
  top of the ADR's ~2–3% band; guards the strategy leg only). An INCONCLUSIVE 25y aggregate is a loud flag.

Implemented in #102 (ADR 0013). No candidate has been rejected by G16 yet — the [[beta-delivery]] failure
anatomy is a stub until a real instance exists.

## What the firewall is good at catching

- **Dispersion-dominated / lottery premises** — in-market geometric CAGR + per-window edge-sign
  stability catch them even when blended CAGR flatters ([[lottery-vs-signature]], [[thinning-not-selecting]]).
- **Participate-and-lose** — per-window negative-participating-window counts on 25y ([[participate-and-lose]]).
- **Regime-fragile parameters** — G13 + the ARS sweep ([[aliased-regime-sensitivity]]).
- **Beta-delivery** — a long book whose risk-adjusted return is just the index's, caught by G16's SPY
  Calmar baseline ([[beta-delivery]]).

## Related

[[the-funnel]] · [[participate-and-lose]] · [[lottery-vs-signature]] · [[parameter-robustness-g13]] · [[beta-delivery]]
