---
type: source
title: V4 Block-A 16-candidate sweep + minimumPrice bad-print finding
summary: The 2026-05-27 v4-gate 16-candidate Block-A sweep — all failed; MR3's edge traced to a single 2007 +72% outlier, and a minimumPrice≥5 re-fire collapsed CAGRs, confirming bad-print inflation.
status: stable
tags: [firewall, failure-mode, data-integrity]
sources: ["strategy_exploration/dossier/"]
related: ["[[mr3]]", "[[dv1]]", "[[2026-05-28-mean-reversion-firewall-runs]]", "[[component-firewall]]"]
updated: 2026-06-07
---

# V4 Block-A 16-candidate sweep (2026-05-27)

An earlier v4-gate **strategy-selection sweep** — 16 candidates, 12-window walk-forward 2000-2015
(IS36/OOS12/step12), atrRisk(1.25%, 2.0), leverage 1.0, run against PRD. It uses a **different gate set +
window** (12-window 2000-2015) than the later 11-window Block-A firewall, so the *first-failing gate
differs per candidate* — this file is the authoritative record of the **variant** runs, not the canonical
firewall verdicts (those are [[2026-05-28-mean-reversion-firewall-runs]]).

## What was run / verdicts (all FAIL)

| Candidate | First gate | CAGR | maxDD | Sharpe | Calmar | trades |
|---|---|---|---|---|---|---|
| VCP-corrected (reference) | G2 DD | 40.62% | 39.16% | 0.54 | 1.04 | 713 |
| MR3-s1/2/3 | G5 CoV (~3.10) | 40.59% | 19.65% | 2.46 | 2.07 | 5,328 |
| VZ3-s1/2/3 | G5 CoV | 129-222% | 9-11% | — | 11.95-24.6 | ~3,180 |
| DV1 | G5 CoV | 34.86% | 12.6% | 2.36 | 2.77 | 1,960 |
| BR2 | G2 DD 25.37% | — | 25.37% | — | — | — |

## The durable finding — bad-print inflation, fixed by minimumPrice ≥ 5

MR3's screen edge was driven by a **single W5-2007 +72.24% edge window** — the signature of a
penny-stock / stale-print artifact, not a repeatable edge. A **`minimumPrice ≥ 5` re-fire** collapsed the
CAGRs and confirmed it:

- **MR3-s1-minprice**: CAGR 40.59% → **20.56%**, now fails G2 (DD 32.88%), 4,180 trades (down from 5,328).
- **DV1-minprice**: fails G3, CAGR **17.27%** (DD 23.06%), 1,304 trades.

**Lesson:** an unfiltered universe lets sub-$5 bad-prints inflate per-window edge; a `minimumPrice` floor
is a required hygiene filter before trusting a high-CoV outlier window. This is the same data-integrity
class Midgaard's bad-print validators catch upstream (`BadPrintIntegrityValidator` V1-V3), surfacing here
as a per-window edge spike when it slips through.

## Pages this updated

[[mr3]] · [[dv1]] · [[component-firewall]]
