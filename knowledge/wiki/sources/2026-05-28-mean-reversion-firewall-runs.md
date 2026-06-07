---
type: source
title: Mean-reversion family firewall + screen runs (VZ3 / MR3 / DV1 / Idunn)
summary: The 2026-05-27/28 firewall + screen runs that deprecated the long-pullback mean-reversion class — VZ3 A+B pass / C fail, MR3 + DV1 Block-A rejects, Idunn ARS.
status: stable
tags: [candidate, failure-mode, firewall, mean-reversion]
sources: ["strategy_exploration/dossier/", "feedback_mean_reversion_pullback_known_weakness", "feedback_aliased_regime_sensitivity"]
related: ["[[vz3]]", "[[mr3]]", "[[dv1]]", "[[idunn]]", "[[participate-and-lose]]", "[[aliased-regime-sensitivity]]"]
updated: 2026-06-07
---

# Mean-reversion family firewall + screen runs (2026-05-27/28)

The run cluster that took the **long-pullback / dip-toward-EMA mean-reversion** premise class from
"three screen survivors" to **deprecated**. Each candidate's full post-mortem lives on its entity page;
this is the dated run record + cross-candidate verdict. The canonical numbers are reproduced on the
entity pages — see [[vz3]], [[mr3]], [[dv1]], [[idunn]].

## What was run

| Run | Candidate | Window | Verdict | First gate failed |
|---|---|---|---|---|
| `/strategy-screen` sweep | VZ3-s1/2/3, MR3-s1/2/3 | 2005-2015, 7 OOS windows | **PASS** (all 6) | — (survivors) |
| Firewall (canonical) | VZ3-s3 | A 2000-14 · B 2014-21.5 · C 2021-25 | **REJECTED** | Block C G4b CAGR 4.26% |
| Firewall | MR3-s1 | Block A only | **REJECTED** | G3 worst-window DD 20.47% |
| Firewall | DV1 (near-miss config) | Block A only | **REJECTED** | G1 CAGR 29.86% (by 0.14pp) + G6 2008 |
| Firewall + ARS sweep | Idunn (corrected VZ3 lookback) | A + B | **REJECTED** | Block B G1+G5+G7 |

## Headline numbers (the durable ones)

- **VZ3-s3** — Block A PASS (CAGR 36.02%, DD 11.92%, Sharpe 2.54, Calmar 3.02), Block B PASS (CAGR 36.33%,
  2020-COVID OOS edge **+0.31%**), Block C FAIL (CAGR 4.26%, Sharpe 0.62, Calmar 0.46). The per-trade edge
  **sign-flips +0.62% (A) → +0.48% (B) → −0.11% (C)** — the death signature (canonical VZ3-s3 firewall
  run, 2026-05-28T12:28).
- **MR3-s1** — Block A FAIL on **three simultaneous tight failures** (G3 DD 20.47%, G4 8/11=72.7%, G5 CoV
  1.77) = multi-dimensional drift, structural not iterational. CAGR 43.83%, 4,830 trades.
- **DV1** — near-miss: CAGR 29.86%, DD 14.41%, Sharpe 2.14, Calmar 2.07, 1,739 trades, 2008 edge −0.48%;
  fails G1 by 0.14pp and G6 (2008). See the source-reconciliation note below.
- **Idunn** — the corrected-lookback VZ3 variant; Block A PASS (CAGR 41.44%, edge +0.86%) but Block B
  REJECTED (G1+G5+G7; CAGR 29.36%, edge +0.12%). The {lookback 8,9,10,11} matrix is non-monotone —
  the canonical [[aliased-regime-sensitivity]] instance.

## What it taught

- **The class is deprecated**, not just these configs: long-pullback mean-reversion *participates and
  loses* in narrow-leadership tape ([[participate-and-lose]]). VZ3 is the cleanest proof — it survives two
  blocks then the edge inverts in 2021-25's narrow tape. Adding a regime filter to rescue it is IS-fitting
  to the single Block C OOS window (memory: `feedback_mean_reversion_pullback_known_weakness`).
- **The sizer-sweep edge-inversion lesson** (from VZ3-s3-B): lifting risk-% to ~1.75% raised Block A CAGR
  to 46.64% but **inverted** the 2020-COVID OOS edge (+0.31% → −0.05%) on near-identical trade counts
  (299 vs 297) — capital/leverage constraints select a different trade set on the crash leg, so a single
  scalar metric is not trustworthy across sizers.
- **Idunn → G13 / ARS**: a 1-day lookback shift flips the verdict and per-window signs — the parameter
  dimension is structurally inappropriate, not merely brittle. Birth of [[aliased-regime-sensitivity]].

## Source reconciliation (DV1) ^[ambiguous]

A standalone DV1 Block A run (2026-05-28T07:06) records a **different** result — CAGR **23.31%**,
DD 24.35%, 2008 edge **−1.35%**, 1,607 trades, first failure G1 — and a `minimumPrice ≥ 5` re-fire failed
G3 at CAGR 17.27% (see [[2026-05-27-v4-block-a-sweep]]). The **near-miss numbers (29.86% / −0.48% / 1,739
trades)** are treated as authoritative ([[dv1]]); the lower-CAGR runs are earlier/variant fires under a
different universe or price filter.

## Pages this updated

[[vz3]] · [[mr3]] · [[dv1]] · [[idunn]] · [[participate-and-lose]] · [[aliased-regime-sensitivity]]
