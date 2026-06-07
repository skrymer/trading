---
type: concept
title: Parameter Robustness (G13)
summary: Every alpha-defining tunable must survive ±1 step / ±10%, else the edge is alignment-fitting. ADVISORY today — runs & reports but does NOT bind the verdict until its calibration sweeps land.
status: stable
tags: [methodology, failure-mode]
sources: ["feedback_parameter_fragility_must_be_verified"]
related: ["[[aliased-regime-sensitivity]]", "[[component-firewall]]"]
updated: 2026-06-06
---

# Parameter Robustness (G13)

A TRADABLE verdict must survive perturbation of **every** numeric tunable, or the edge is
*alignment-fitting*, not structural — and won't survive live trading. G13 is the firewall gate (currently
**advisory** — see status below) that enforces this; [[aliased-regime-sensitivity]] is the specific
failure it catches at its worst.

> **Status: ADVISORY (calibration-pending) — runs and is reported on every TRADABLE candidate but does
> NOT change the verdict** (a yellow flag, exactly like informational Block C). Authority: the
> `/validate-candidate` skill + `g13_aggregate.py` (`"binding": False`); confirmed by quant
> 2026-06-07 ([[2026-06-07-funnel-correctness-consult]]). G13 binds the verdict only after **two
> calibration sweeps** pass: (1) a **known-failer** sweep confirming the buggy centre REJECTs at ±1 with
> the failing neighbour tripping **G5 or G7** (the CoV-explosion / chop-sign-flip mechanism), and (2) a
> **known-passer** sweep confirming G13 doesn't downgrade a legitimate passer. No known-passer strategy
> exists yet (zero have cleared the firewall), so the passer sweep runs against the first strategy to clear
> it. Flipping to binding is a one-line change (`binding=True`) once both pass. **The ±1 / ±10% step sizes
> are signed off as the design to run; only the *bind* is gated on the sweeps.** Today a G13 neighbour
> failure is surfaced as a flag alongside the verdict — it does **not** flip TRADABLE → REJECTED (the
> operator may treat a wide-margin fragility flag as a discretionary stop).

## The rule

- **Discrete** tunables (lookback days, position counts, history requirements): survive **±1 step**.
- **Continuous** tunables (thresholds, multipliers, percentages): survive **±10% relative**.

The discipline: prefer TRADABLE only when all neighbors PASS the binding-layer gates. Skip if the
candidate has no tunables (e.g. zero-config `marketUptrend`). *Advisory today — a neighbour failure is a
reported flag, not an auto-REJECT (see status above).*

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

The two-tier provenance split is **design intent, not yet wired into the executable scope** ^[inferred]:
`g13_neighbors.py` classifies tunables by the discrete/continuous param-name map only, with no Tier-1/Tier-2
provenance dimension. Until it's wired, **every tunable is swept as Tier-1** (conservative over-testing) —
consistent with the gate being advisory anyway.

## One-at-a-time is the weak part (joint fragility) ^[inferred]

The ±1 / ±10% sweep moves **one tunable at a time**. Robust-parameter practice searches **joint,
multi-dimensional plateaus**, because interactions between tunables create fragility that axis-by-axis
perturbation misses — a config can pass every one-at-a-time move and still be fragile to a *joint* move.
[[aliased-regime-sensitivity]] is a discovered instance of joint fragility. A coarse joint grid on the 2-3
most sensitive tunables would harden G13 — doubly non-urgent while G13 is advisory (open work — see
[[2026-06-05-funnel-deepresearch-findings]]).

## Relationship to ARS

G13 is the discipline ("verify ±1 robustness before TRADABLE"). [[aliased-regime-sensitivity]] is the
worst thing G13 finds: not "only one value passes" but "pass/fail is non-monotone and per-window signs
flip" — meaning the parameter dimension itself is structurally wrong, so even the passing values are
parameter-luck.

## Related

[[aliased-regime-sensitivity]] · [[component-firewall]] · [[the-funnel]]
