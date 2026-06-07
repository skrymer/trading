---
type: entity
title: Idunn
summary: The promoted VZ3-s3 (pullback MR). Correcting an off-by-one lookback exposed Aliased Regime Sensitivity across lookback {8,9,10,11} — no robust operating point. REJECTED Block B.
status: stable
tags: [candidate, mean-reversion, rejected, ars, deprecated-class]
sources: ["knowledge/wiki/sources/2026-05-28-mean-reversion-firewall-runs.md", "strategy_exploration/dossier/"]
related: ["[[vz3]]", "[[mr3]]", "[[dv1]]", "[[aliased-regime-sensitivity]]", "[[parameter-robustness-g13]]", "[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[component-firewall]]", "[[2026-05-28-mean-reversion-firewall-runs]]"]
updated: 2026-06-06
---

# Idunn

The **promoted** [[vz3]] — the same long mean-reversion-on-pullback premise after its inline `script`
conditions were promoted to first-class registered conditions and a Norse-god-named strategy
(`IdunnEntryStrategy` + `IdunnExitStrategy`). During promotion the higher-low lookback was *corrected*:
the original inline script's `ref[ref.size - 10]` against an inclusive-range result read **9** trading days
back; the corrected formula reads **10**. That one-day correction is what killed it.

## Status

**REJECTED** at Block B (2026-05-29) via [[aliased-regime-sensitivity]]. Idunn is definitively closed —
even the standalone-passing lookback values are not deployable. A representative of the deprecated
long-pullback mean-reversion class (see [[long-premise-in-narrow-leadership]]).

## Funnel history

- Promoted from the [[vz3]] inline-script research candidate (entry → `Pullback2of3Condition`, exit →
  `PercentGainExit`).
- Re-entered **/validate-candidate**: PASS Block A, **FAIL Block B**.
- Ran a brittleness sweep over lookback ∈ {8, 9, 10, 11} via the `pullback2of3` custom-strategy path → the
  pass/fail matrix revealed ARS.

## Verdicts

Nominal (corrected, lookback=10):

| Block | Verdict | CAGR | DD | Sharpe | Calmar | Edge |
|---|---|---:|---:|---:|---:|---:|
| A | **PASS** 10/10 | 41.44% | 11.70% | 2.70 | 3.54 | +0.86% |
| B | **FAIL** 7/10 (G1 + G5 + G7) | 29.36% | — | — | — | +0.12% |

Block A was actually *better* than [[vz3]]'s Block A under the buggy formula (36.02% / Sharpe 2.54 /
+0.62%). Block B failed on G1 (29.36% CAGR, 0.64pp short of 30%), G5 (CoV edge 2.86 vs 1.5 cap), and G7
(2018-Q4 chop edge −0.45% vs > 0).

Brittleness sweep matrix:

| lookback | Block A | Block B | Block B CAGR | Block B edge |
|---:|:---:|:---:|---:|---:|
| **8** | PASS 10/10 | **PASS 10/10** | 31.44% | +0.45% |
| **9** | PASS 10/10 | **FAIL G6** (2020 COVID OOS edge −0.07%) | 36.16% | +0.36% |
| **10** (nominal) | PASS 10/10 | **FAIL G1+G5+G7** (2018-Q4 chop edge −0.45%) | 29.36% | +0.12% |
| **11** | PASS 10/10 | **PASS 10/10** | 30.65% | +0.48% |

## Why it died

The 1-day lookback correction produced an **enormous swing on essentially the same trade population**: the
2018-Q4 chop edge flipped from +0.21% (buggy lb=9) to −0.45% (corrected lb=10), Block B aggregate edge
collapsed +0.48% → +0.12%, and G5 CoV exploded 0.70 → 2.86. The strategy's edge isn't tracking the
"higher low vs ~2 weeks ago" structural feature — it's tracking **incidental bar alignment**.

Across the neighborhood, pass/fail is **non-monotone** ({8 PASS, 9 FAIL G6, 10 FAIL G1+G5+G7, 11 PASS}) and
per-window edges flip sign at regime gates under 1-day shifts, while aggregate edges sit in a ~3σ noise
band. That is the [[aliased-regime-sensitivity]] signature — **strictly worse than simple brittleness**:
there is no robust operating point because the discrete-lookback parameter dimension is the wrong
abstraction for the alpha hypothesis. The "today's low vs N-bars-ago low" sub-condition is structurally an
anti-pattern for noisy regime-variant data.

## Failure modes hit

- **[[aliased-regime-sensitivity]]** — the headline cause; reject the strategy entirely, don't ship the
  passing values.
- **[[parameter-robustness-g13]]** — G13's ±1-step rule would have correctly rejected *all four* nominal
  values (every nominal sits one step from a failing neighbor). Idunn is the empirical case that validated
  G13 as a binding gate.
- **Promotion-fidelity divergence (G14)** — `pullback2of3(lookbackDays=9)` is **not** bit-equivalent to
  the original VZ3-s3 inline lb=9-via-bug. The dynamic calendar buffer (`max(20, lookbackDays*2 + 10)` = 28
  days at lb=9 vs the inline script's hardcoded 20) admits ~2 more bars per stock-date, shifting the trade
  population enough to flip the 2020 COVID edge sign. The buffer was a hidden tunable. This invalidated the
  earlier [[vz3]] "TRADABLE smoke" verdict (it was carried by the off-by-one bug) and motivated G14 /
  `/verify-promotion`.

## Reusable findings (durable)

- **A condition can pass while its parameter is structurally unsound** — Idunn passed Block A at every
  lookback yet had no robust operating point. Aggregate metrics in a noise band hide ARS; you must sweep
  the neighborhood to see it. This is why `/condition-screen` runs an ARS parameter sweep at *design* time.
- **Picking the value that passes after seeing the OOS result is data-snooping** — lb=8 and lb=11 pass,
  but selecting one of them post-hoc mines variance. ^[inferred]
- **Off-by-one bugs can manufacture a passing verdict** — the buggy lb=9 inline VZ3-s3 read as TRADABLE;
  the corrected lb=10 fails Block B. Always verify the corrected, promoted form.
- Together with [[vz3]] (regime sign-flip), [[mr3]] (Block A multi-dim drift), and [[dv1]], Idunn closes
  the mean-reversion-in-uptrend design space on the current universe — and mandates a parameter-stability
  check before any future TRADABLE is accepted. ^[inferred]

## Related

[[vz3]] · [[mr3]] · [[dv1]] · [[aliased-regime-sensitivity]] · [[parameter-robustness-g13]] · [[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[component-firewall]]
