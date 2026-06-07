---
type: concept
title: The Component Firewall
summary: The binding 3-block + 25y validation (Block C informational), its anti-data-mining interlocks (G10/G11/G14 binding; G13 advisory), the absolute gates, and the SPY-baseline gate (G16).
status: stable
tags: [methodology]
sources: [".claude/skills/validate-candidate/SKILL.md", "docs/adr/0013-spy-buy-and-hold-is-a-binding-calmar-only-firewall-baseline.md", "docs/adr/0015-absolute-calmar-floor-and-g9-sharpe-recalibration.md"]
related: ["[[the-funnel]]", "[[participate-and-lose]]", "[[parameter-robustness-g13]]", "[[beta-delivery]]", "[[regime-conditional-portfolio]]"]
updated: 2026-06-06
---

# The Component Firewall

The binding validation layer of [[the-funnel]] — `/validate-candidate`. A candidate that clears it is
*tradable*; one that fails is *rejected* (and its config hash goes dead, ADR 0008). The exact frozen
gate table lives in the `/validate-candidate` skill + its `REFERENCE.md` (don't restate it here — it
drifts). This page is the *shape and intent*. The regime-conditional *component*-firewall variant (C-gates)
is shelved with its abandoned program — see [[regime-conditional-portfolio]].

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
- **G13 parameter robustness** *(currently **advisory** — runs & reports, does NOT bind the verdict; see
  [[parameter-robustness-g13]])* — a TRADABLE verdict should survive ±1 step on every discrete tunable and
  ±10% on every continuous one. Picking the value that passes *after* seeing the OOS result is data-snooping.
  Binds only after its known-passer/known-failer calibration sweeps land.
- **G14 implementation invariance** — a promoted condition must reproduce the inline-script trade list
  by `(entry_date, symbol)` over 25y, or the inline verdict is void.

## Absolute gates (post ADR 0014/0015 recalibration)

The firewall moved from *relative* baselines (beat-VCP) to **absolute** floors: CAGR ≥ 25% (G1),
maxDD ≤ 25% (G2), Sharpe ≥ 0.5 (G9), absolute Calmar ≥ 1.5 (G15). *(The G15/G9/G1 recalibration + DSR
flag are ADRs 0014-0016. The G1/G9/G15 gate recalibration landed in the `validate-candidate` scripts
in #106 on 2026-06-06 — G9 is now Sharpe-only (the old Calmar ≥ 0.5 conjunct re-homed as the higher
G15 floor); the DSR multiple-testing flag in #105.)*

**Gate basis re-confirmed against the cost + idle-cash engine (quant, 2026-06-06).** After #101
(per-trade cost, net 10 bps default) and #103 (idle-cash crediting, default ON) landed, the gates were
re-reviewed and **all held — run the firewall on that realistic default basis, no recalibration**
([[2026-06-06-gate-basis-and-cagr-floor-feasibility]]). Idle-cash is Sharpe-neutral by construction
(zero effect on G9) and only eases Calmar (G15/G16) in the correct direction; 10 bps cost is a
sub-half-point CAGR / sub-0.05 Sharpe drag inside every margin; the two roughly cancel for a part-in-cash
candidate. Pinning to the old cost-free/0%-cash basis would re-introduce the biases those changes fixed.

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

Every gated metric is downstream of the sizer — the same edge can flip a gate under a different risk-% or
leverage cap, so the [[position-sizing-and-risk]] choices are part of the frozen config G13 perturbs.

## Related

[[the-funnel]] · [[participate-and-lose]] · [[lottery-vs-signature]] · [[parameter-robustness-g13]] · [[beta-delivery]] · [[position-sizing-and-risk]]
