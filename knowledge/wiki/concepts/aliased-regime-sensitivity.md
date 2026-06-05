---
type: concept
title: Aliased Regime Sensitivity (ARS)
summary: Non-monotone pass/fail across a parameter neighborhood plus per-window edge sign-flips = the parameter dimension is the wrong abstraction; reject, don't tune.
status: stable
tags: [failure-mode]
sources: ["feedback_aliased_regime_sensitivity"]
related: ["[[parameter-robustness-g13]]", "[[participate-and-lose]]", "[[lottery-vs-signature]]"]
updated: 2026-06-05
---

# Aliased Regime Sensitivity (ARS)

A parameter-fragility pattern **strictly worse than simple brittleness**: the parameter dimension is the
*wrong abstraction* for the alpha hypothesis, so **no operating point is robust** — not even the ones
that pass.

## Definition

Simple brittleness (Pattern A) = only one parameter value passes; at least interpretable as "right
answer exists, narrow window." **ARS** = pass/fail across the parameter neighborhood is **non-monotone**
and per-window edges **flip sign at specific regime gates** under a single-step shift. Interpretation:
*there is no right answer; the parameter is aliasing against regime-specific microstructure cadences.*
(Discrete parameter aliasing × regime-conditional alpha — Ang & Bekaert 2002; Guidolin & Timmermann 2007.)

## Detection signature (all four must hold)

1. Aggregate per-trade edges across the neighborhood sit in a **noise band** (~3σ of sample mean).
2. Per-window edges **flip sign** (not just magnitude) on regime-gate windows.
3. Pass/fail across the neighborhood is **non-monotone**.
4. **Trade counts are stable** across the neighborhood (rules out population effects).

## Worked example — Idunn (2026-05-29)

`lookbackDays` sweep, Block B:

| Lookback | Aggregate edge | 2018-Q4 chop | 2020 COVID | Verdict |
|---|---:|---:|---:|---|
| 8 | +0.45% | +0.15% | +0.25% | **PASS 10/10** |
| 9 | +0.36% | +0.31% | **−0.07%** | FAIL G6 |
| 10 | +0.12% | **−0.45%** | +0.09% | FAIL G1+G5+G7 |
| 11 | +0.48% | +0.10% | +0.71% | **PASS 10/10** |

Both edges of the neighborhood pass cleanly; both middle values fail on *different* regime gates, with
signs flipping at 2018-Q4 and 2020 COVID. Aggregate spread 0.36pp sits within 3.6σ on a 1000-trade
sample. Classic ARS — the lookback is aliasing against regime cadences (2020 had a ~9-10 day reversal;
lb=10 catches false-rally bottoms, lb=8/11 sample off-phase).

## Why it kills

The mechanism the variable name claims (e.g. "support N bars ago") is not what the edge tracks — it
tracks *which specific historical bars the index happens to hit*. "Today's low vs N-bars-ago low" is a
**structural anti-pattern** for noisy regime-variant data: point estimates of support amplify noise.

## How to apply

- **Don't ship the passing values.** {N-1, N+1} passing standalone doesn't help — they sit one step from
  failing neighbors. *"You don't have alpha; you have parameter-luck."*
- The conclusion is **not** "tune better" or "find a wider band" — it's **redesign the condition or
  abandon the premise class.**
- Detect early: at condition-design time run P-1/P/P+1 and watch for a **forward-return sign-flip** at
  stable firing rate. This is the ARS half of [[parameter-robustness-g13]] (G13).

## Related

[[parameter-robustness-g13]] · [[participate-and-lose]] (Idunn was doubly condemned) · [[lottery-vs-signature]]
