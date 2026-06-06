---
type: entity
title: VZ3-s3
summary: Mean-reversion-on-pullback (to EMA20) in uptrend. Passed Block A + Block B cleanly, REJECTED at Block C — per-trade edge sign-flipped +0.48% → −0.11% in 2024 narrow-leadership tape.
status: stable
tags: [candidate, mean-reversion, rejected, deprecated-class]
sources: ["strategy_exploration/VZ3_STRATEGY_DEVELOPMENT.md", "strategy_exploration/validate-VZ3-s3-final.md", "strategy_exploration/validation-candidates.md"]
related: ["[[idunn]]", "[[mr3]]", "[[dv1]]", "[[participate-and-lose]]", "[[long-premise-in-narrow-leadership]]", "[[component-firewall]]", "[[parameter-robustness-g13]]", "[[the-funnel]]"]
updated: 2026-06-06
---

# VZ3-s3

A **long mean-reversion-on-pullback** candidate. Premise: in a confirmed uptrend, a pullback to the
20-day EMA fails and the trend resumes. Entry was a 3-of-3 stack — a market-regime gate, plus a
pullback-to-EMA20-with-structure check (price within 1.5×ATR of the EMA20, higher low vs ~10 days ago,
EMA20 rising), with a +10% / EMA50-break / 2.5×ATR-stop exit and a distance-from-10-EMA ranker. A
representative of the now-deprecated long-pullback mean-reversion class (see
[[long-premise-in-narrow-leadership]]).

## Status

**REJECTED** at Block C (2026-05-28). Not iterable on this search axis — adding a regime filter would be
IS-fitting to the single Block C OOS window. Entry/exit were inline `script` conditions (PENDING-PROMOTION
when alive); that promotion is what spawned [[idunn]].

## Funnel history

- **/strategy-screen** (2005-2015, 7 OOS windows): all 5 gates passed. Best seed s3 — edge 0.70%,
  Sharpe 2.52, CAGR 36.97%, MaxDD 11.92%, Calmar 3.10. Promoted to the firewall.
- **/validate-candidate** (3-block [[component-firewall]]): PASS Block A, PASS Block B-corrected,
  **FAIL Block C**.

## Verdicts

| Block | Range | Verdict | CAGR | DD | Sharpe | Calmar | Per-trade edge |
|---|---|---|---:|---:|---:|---:|---:|
| A | 2000-2014 | **PASS** 10/10 | 36.02% | 11.92% | 2.54 | 3.02 | +0.62% |
| B (corrected) | 2014-2021H1 incl COVID | **PASS** 10/10 | 36.33% | 8.61% | 2.32 | 4.22 | +0.48% |
| C | 2021-2025 (2024 only) | **FAIL** (G4b, G9) | 4.26% | 9.29% | 0.62 | 0.46 | **−0.11%** |

Block C first failure was `G4b_block_cagr` (4.26% ≪ 30%); `G9` also failed (Sharpe 0.62 < 0.8, Calmar
0.46 < 0.5).

> Note: an initial Block B run FAILED on G1 (28.39% CAGR) under an **obsolete endDate=2020-12-31** — a
> structural artifact where no OOS window covered 2020 at all. Extending endDate to 2021-06-30 (so the
> V-recovery year enters an OOS window) lifted block CAGR to 36.33% and Block B passed cleanly. The G1
> "failure" was the endDate bug, not the strategy.

## Why it died

The headline diagnostic is the **per-trade edge sign-flip**: +0.62% (A) → +0.48% (B) → **−0.11% (C)**.
Sample noise compresses an edge toward zero; it does not invert. Per quant this is a ~2.5-3σ shift on 245
trades combined with a 3.7× Sharpe collapse (2.32 → 0.62) — joint probability of noise is low.

**Mechanism:** the 2024 narrow-leadership / Mag-7-concentrated tape breaks pullback-to-EMA20. Flow rotates
away from laggards *before* mean-reversion fires, and the actual leaders' pullbacks are too shallow to
touch the EMA20. The strategy mechanic and the observed failure align — diagnostic, not noise. This is the
[[participate-and-lose]] surface of the long-pullback class playing out in low-breadth trending tape (see
[[long-premise-in-narrow-leadership]]).

**Crash survival is fine** — it does *not* bleed in crises (2008 OOS edge +0.39%, 2020 OOS edge +0.31%).
The death is regime-specific to narrow leadership, not a tail failure.

## Failure modes hit

- **[[participate-and-lose]]** in narrow-leadership tape — the class-level death surface.
- A sizer sweep surfaced a related, general lesson: **per-trade edge inverts at higher risk-% in crash
  regimes even when headline CAGR rises**. The sweep winner (1.75% risk) lifted Block A CAGR to 46.64% but
  inverted the 2020 COVID OOS edge (+0.31% → −0.05%) on near-identical trade counts (299 vs 297) — under
  capital/leverage constraints the portfolio selects a *different* trade set on the crash leg. G6's
  per-trade-edge formulation catches this where headline CAGR would mislead. ^[inferred] This generalizes
  to any position-sized firewall validation, not just VZ3.

## Reusable findings (durable)

- **Mean-reversion-on-pullback has a structural weakness in low-breadth trending markets** — it won't
  clear the firewall without a *structurally different entry premise*, not a filter bolted on after seeing
  a window fail. Filed to memory (`feedback_mean_reversion_pullback_known_weakness`).
- **The firewall correctly catches a regime-conditional death that screen + Block A + Block B all miss** —
  VZ3 looked clean for 21 of 25 years and died only in the most recent regime. The cross-block edge-decay
  signal (sign-flip across blocks) is the diagnostic, not any single block's headline.
- **Block C cadence math**: under 36/12/12, only 1 OOS window fits Block C (2024). G5 (CoV) and G6 (2022
  inflation bear) are structurally untestable there and correctly N/A — don't redesign Block C to rescue a
  candidate.
- VZ3, [[mr3]], [[dv1]], and [[idunn]] are all mean-reversion-in-uptrend variants, each REJECTED for a
  *different* reason — together they exhaust this premise class on the current universe. ^[inferred]

## Related

[[idunn]] · [[mr3]] · [[dv1]] · [[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[component-firewall]] · [[parameter-robustness-g13]] · [[the-funnel]]
