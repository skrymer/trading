---
type: source
title: 16-candidate /strategy-screen sweep + regime-gate null
summary: The 2026-05-27 16-candidate /strategy-screen sweep (2005-2015) — VZ3/MR3 the firm survivors; G4 GFC-stress the dominant failure; prepending marketUptrend did NOT rescue the G4 failures.
status: stable
tags: [screen, failure-mode, methodology]
sources: ["strategy_exploration/dossier/"]
related: ["[[vz3]]", "[[mr3]]", "[[the-funnel]]", "[[participate-and-lose]]"]
updated: 2026-06-07
---

# 16-candidate /strategy-screen sweep (2026-05-27)

A full `/strategy-screen` triage pass — 16 candidates, 2005-2015, post-V13 universe, 7 OOS windows, risk
1.25%, against the 5 relaxed screen gates. The sweep that produced the mean-reversion survivors and
established **G4 (GFC stress) as the dominant screen failure mode**.

## Survivors (PASS 5/5) and the tradability caveat

- **VZ3-s1/s2/s3** — edge 0.657/0.684/0.698%, Sharpe 2.42/2.39/2.52, CAGR 34.4/34.5/36.97%, DD 11.92%.
- **MR3-s1/s2/s3** (seed-invariant) — edge 0.284%, Sharpe 2.29, CAGR 36.77%, DD 17.7%, 2,899 trades.
- **BR1-s1** — PASS but **CAGR only 9.44%** (DD 12.1%, 622 trades) — below the tradability floor; a "passed
  the screen, not worth a firewall run" case.

The two firm survivors (VZ3 best Calmar, MR3 best CAGR) both went on to die in the firewall — see
[[2026-05-28-mean-reversion-firewall-runs]].

## The durable finding — a coarse regime gate does NOT rescue a GFC-stress failure

The dominant failure was **G4 (GFC stress)**: BR1-s2/s3, BR3-s1/2/3, MO3-s1/s3 all failed it. A
**regime-gated re-fire** prepending `marketUptrend` to the three flagged candidates was run to test the
obvious fix — and **all three still failed G4**:

- BR1-s2-regime FAIL G4 (14.61%), BR1-s3-regime FAIL G4 (12.13%), MO3-s3-regime FAIL G4 (19.17%, DD 24.5%).

**Lesson:** bolting a coarse `marketUptrend` gate onto a candidate that bleeds in GFC stress does not fix
it — the gate is too blunt to separate the participating-loss windows from the survivable ones. This is an
early, concrete instance of the [[thinning-not-selecting]] / [[participate-and-lose]] pattern: a coarse
regime selector reduces trade count without removing the loss surface.

## Pages this updated

[[vz3]] · [[mr3]] · [[participate-and-lose]] · [[thinning-not-selecting]]
