---
type: concept
title: Parameter Robustness (G13)
summary: A TRADABLE verdict must survive ±1 step on every discrete tunable and ±10% on every continuous one — else the edge is alignment-fitting, not structural.
status: stable
tags: [methodology, failure-mode]
sources: ["feedback_parameter_fragility_must_be_verified"]
related: ["[[aliased-regime-sensitivity]]", "[[component-firewall]]"]
updated: 2026-06-06
---

# Parameter Robustness (G13)

A TRADABLE verdict must survive perturbation of **every** numeric tunable, or the edge is
*alignment-fitting*, not structural — and won't survive live trading. G13 is the binding firewall gate
that enforces this; [[aliased-regime-sensitivity]] is the specific failure it catches at its worst.

## The rule

- **Discrete** tunables (lookback days, position counts, history requirements): survive **±1 step**.
- **Continuous** tunables (thresholds, multipliers, percentages): survive **±10% relative**.

Accept TRADABLE only if all neighbors PASS the binding-layer gates. Skip if the candidate has no
tunables (e.g. zero-config `marketUptrend`).

## Why — the autocorrelation argument

Adjacent bars are highly autocorrelated (1-day autocorrelation of lows ≈ 0.85-0.95). A *structural* edge
at a ~2-week horizon should be approximately **invariant** to a 1-day lookback shift. If the verdict
flips on a single bar, the edge tracks *which specific historical bars the index happens to hit*, not the
structural feature the variable name claims.

## Worked example — Idunn off-by-one (2026-05-29)

VZ3-s3 had an off-by-one in its higher-low lookback (read 9 trading days back, not 10). Under the buggy
`lookback=9` it was **TRADABLE** in the smoke test. Under the corrected `lookback=10` (Idunn) it was
**REJECTED** at Block B (G5 CoV 2.86, G7 2018-Q4 −0.45%; its CAGR 29.36% failed the 30% G1 floor in
force then, but *clears* the since-lowered 25% floor of ADR 0015 — the rejection never hinged on G1).
The 1-day shift moved Block B aggregate edge +0.48% → +0.12%, flipped the 2018-Q4 chop sign, and exploded
G5 CoV 0.70 → 2.86 — far beyond 220-trade sample noise. The example stands under the recalibrated gates:
G13 catches parameter fragility through the CoV explosion + chop sign-flip, not the CAGR floor.

## The selection-bias trap

If `lookback=N` fails and `N±1` passes, **do not promote N±1**. Picking the value that passes *after*
seeing the OOS result is data-snooping laundered as "we found the right value." A promising N±1 is a
candidate for a *fresh* `/strategy-screen` → `/validate-candidate` with a **pre-registered** parameter
justification — never a retroactive rescue of the failing config. (And changing a numeric constant
**invalidates any prior firewall verdict** — re-fire.)

## Scope — two tiers of tunable

Not every numeric constant gets the same treatment; provenance decides:

- **Tier 1 — parameters we invented** (a lookback we chose, a threshold we picked): the full binding
  ±1-step / ±10% sweep. We have no external justification for the value, so robustness *is* the whole case.
- **Tier 2 — externally-provenanced constants** (e.g. a published-spec value): a **one-time confirmatory
  check, no retune.** External provenance kills the *anti-snooping* concern (we didn't choose it to fit our
  OOS) but **not** the *robustness* concern — so a cliff at the spec value **demotes the verdict** (flag for
  manual review), it does not license a re-tune. Cheaply-sweepable Tier-2 params are still checked;
  untestable-by-construction params are **flagged, never silently exempt**.

## One-at-a-time is the weak part (joint fragility) ^[inferred]

The ±1 / ±10% sweep moves **one tunable at a time**. Robust-parameter practice searches **joint,
multi-dimensional plateaus**, because interactions between tunables create fragility that axis-by-axis
perturbation misses — a config can pass every one-at-a-time move and still be fragile to a *joint* move.
[[aliased-regime-sensitivity]] is a discovered instance of joint fragility. A coarse joint grid on the 2-3
most sensitive tunables would harden G13 (open work — see [[2026-06-05-funnel-deepresearch-findings]]).

> ⚠ Status note ^[ambiguous]: G13 is treated as a **binding** interlock in [[component-firewall]], but its
> **step-size calibration** (±1 / ±10%) and false-positive rate were flagged pending a confirming
> known-passer / known-failer sweep + quant sign-off (memory `feedback_parameter_fragility_must_be_verified`).
> Binding-in-principle, calibration-confirmation outstanding.

## Relationship to ARS

G13 is the discipline ("verify ±1 robustness before TRADABLE"). [[aliased-regime-sensitivity]] is the
worst thing G13 finds: not "only one value passes" but "pass/fail is non-monotone and per-window signs
flip" — meaning the parameter dimension itself is structurally wrong, so even the passing values are
parameter-luck.

## Related

[[aliased-regime-sensitivity]] · [[component-firewall]] · [[the-funnel]]
