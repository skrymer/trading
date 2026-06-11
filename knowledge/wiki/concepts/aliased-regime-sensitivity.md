---
type: concept
title: Aliased Regime Sensitivity (ARS)
summary: Non-monotone pass/fail across a parameter neighborhood plus per-window edge sign-flips = the parameter dimension is the wrong abstraction; reject, don't tune.
status: stable
tags: [failure-mode]
sources: ["feedback_aliased_regime_sensitivity"]
related: ["[[parameter-robustness-g13]]", "[[participate-and-lose]]", "[[lottery-vs-signature]]", "[[thrust-degenerates-to-level]]", "[[beta-delivery]]", "[[pead]]", "[[quality-profitability-tilt]]", "[[2026-06-09-pead-earnings-gap-screen-reject]]", "[[2026-06-09-pead-market-neutral-residual-screen-reject]]", "[[2026-06-09-pead-eps-gated-residual-screen-reject]]", "[[2026-06-11-quality-tilt-condition-screen-killtest]]"]
updated: 2026-06-11
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

## Instance — PEAD price-gap proxy (2026-06-09), the non-monotone "island"

`gapAtr` sweep at `/condition-screen`, 20d post-entry lift: **0.5 → −0.18%, 1.0 → +0.11%, 1.5 → −0.20%.**
Only the centre cell is positive; **both neighbours are negative** — the Idunn island pattern, here on a
*continuous* threshold tunable. Demanding a *stronger* surprise (1.5 ATR) turns lift negative, the opposite
of surprise-magnitude monotonicity the underreaction mechanism requires.

⚠ **Caveat — formal 3-clause test inconclusive-by-construction:** detection clause 4 (stable trade counts)
is **not** satisfiable here — firing is not held within ±15% across the cells (0.5 = +68%, 1.5 = −36% rel),
because a gap-size threshold *is* a support cliff (like a level-degenerating thrust, [[thrust-degenerates-to-level]]).
So this is recorded as the **non-monotone-island tell + a cross-sectional regime-tertile sign-flip** (down
positive / up negative at every horizon — see [[beta-delivery]]), not a formal stable-firing ARS pass. The
structural verdict is identical to a clean ARS: the gap-size dimension carries no robust drift edge —
redesign the surprise proxy, don't tune the threshold. See [[2026-06-09-pead-earnings-gap-screen-reject]].

The same non-monotone-island + regime-tertile-sign-flip recurred on **both residual successors**, on the
`theta` (residual-threshold) tunable: the [[2026-06-09-pead-market-neutral-residual-screen-reject|market-neutral
residual]] (20d θ-lift all-negative/near-zero, flat tertile negative) and the
[[2026-06-09-pead-eps-gated-residual-screen-reject|EPS-sign-gated residual]] (20d θ-lift {−0.056%, −0.0069%,
−0.070%}, all three cells negative; tertiles down +0.99% / flat **−0.31%** / up −0.51%). Both record the
same way as the raw-gap proxy — non-monotone-island tell + cross-sectional regime sign-flip, **not** a
formal stable-firing ARS pass (firing not held within ±15% across cells — `relativeStep` 0.67; and the 20d
θ-swing ≪ 2× centre SE, so there is no *swing* to be fragile about because there is no edge). Three proxies,
same structural verdict: the surprise dimension carries no robust drift edge on this premise (see [[pead]] —
surprise-proxy axis exhausted).

## Anti-ARS reference case — quality-tilt minPercentile sweep (2026-06-11)

The contrast that calibrates the tell. `minPercentile` sweep {72, 80, 88} on the
[[quality-profitability-tilt]] gate at `/condition-screen`, 20d post-entry lift: **72 → +0.20%, 80 → +0.27%,
88 → +0.36%** — strictly positive and **monotone-increasing** in the threshold at every horizon. No
sign-flip (signature clause 2 fails), pass/fail monotone (clause 3 fails), `swing/|center|` 0.60–0.76 (< 1.0,
not even the lesser monotone-steep tell). Firing moves ±39%/−41% rel — a percentile threshold *is* a support
cliff, so the stable-firing clause is unsatisfiable by construction, exactly as for the PEAD gap-size island
above. **But the structural verdict is the opposite:** more-selective → more-lift is the right-signed
dose-response a real selection variable shows. Recorded as **clean monotone, NOT fragility** — the reference
case proving the firing-cliff caveat doesn't render every quantile-gate sweep unreadable: the *shape*
(monotone-positive vs non-monotone-island) is what separates a real knob from an aliased one. See
[[2026-06-11-quality-tilt-condition-screen-killtest]].

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

[[parameter-robustness-g13]] · [[participate-and-lose]] (Idunn was doubly condemned) · [[lottery-vs-signature]] · [[thrust-degenerates-to-level]] · [[beta-delivery]] · [[pead]]
