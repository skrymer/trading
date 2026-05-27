---
name: validate-candidate
description: Run a /strategy-screen survivor through the 3-block firewall (Block A 2000-2014, Block B 2014-2020 incl COVID, Block C 2021-2025) with strict v4 gates + design-isolation + cross-block edge-decay check. Use when promoting a candidate from /strategy-screen to deeper validation, when applying final go/no-go to a candidate strategy, or when verifying a strategy is tradable before live deployment.
argument-hint: "[candidate-name] [request-template-path]"
---

# Candidate Validation — 3-Block Firewall

Promotes a `/strategy-screen` survivor through three increasingly OOS blocks, applies the v4 gates per block, and emits a final verdict: **TRADABLE / PROVISIONAL / REJECTED**.

This skill is strategy-neutral. Substitute the user's actual candidate label / request template in every example.

## Pipeline

| Block | Range | Mandate regime | If pass |
|---|---|---|---|
| **A — Initial** | 2000-01-01 → 2014-01-01 (14y) | 2008 GFC OOS+ | Continue to B |
| **B — COVID** | 2014-01-01 → 2020-12-31 (7y) | 2020 COVID OOS+ | Continue to C |
| **C — Firewall** | 2021-01-01 → 2025-12-31 (5y) | 2022 inflation bear OOS+ | TRADABLE |

No date gaps. Quant-validated boundaries — do not adjust.

A candidate must clear all 3 blocks. **A failed block is final. Modifying the config and re-running is data-mining, not validation.** The legitimate remediation path is to go back to `/strategy-screen` and re-survey the variant before re-entering the firewall.

## v4 gates (applied at each block; some block-specific)

| Gate | Threshold |
|---|---|
| G1 — CAGR | ≥ max(10%, SPY+2%) AND **≥ 30%** (tradability floor) |
| G2 — Aggregate max DD | ≤ 25% |
| G3 — Worst-window DD | ≤ 20% |
| G4 — Positive windows | ≥ 75% (N ≥ 4); on short blocks use G4a + G4b below |
| G4a (N < 4) | No single OOS window worse than −5% CAGR |
| G4b (N < 4) | Block-aggregate CAGR ≥ G1 threshold |
| G5 — CoV of per-window edges | ≤ 1.5 |
| G6 — Regime mandate | Block A: 2008 OOS+. Block B: 2020 OOS+. Block C: 2022 OOS+. |
| G7 — Chop regime | Block A: ≥1 of {2004, 2011, 2015-H1} positive. Block B: ≥1 of {2015-H2, 2018-Q4} positive. Block C: skipped. |
| G8 — Min trades per window | ≥ 30 |
| G9 — Sharpe + Calmar | Sharpe ≥ 0.8 AND Calmar ≥ 0.5 |
| G10 — Design isolation (Block C only) | Skill emits the candidate config + freeze date. User must confirm no config change since Block B passed. |
| G11 — Cross-block edge decay (final) | edge_C ≥ 0.5 × edge_A AND CAGR_C ≥ 0.5 × CAGR_A. Failure downgrades verdict to PROVISIONAL. |
| G12 — Block-aggregate trade count | ≥ 100 trades per block |

## Quick start

```bash
.claude/skills/validate-candidate/scripts/run-pipeline.sh <candidate-name> <request-template>
```

The template is the `/tmp/v3-req-<candidate>.json` produced earlier in the screen, OR any request file with the candidate's full `entryStrategy` / `exitStrategy` / `positionSizing` / `ranker`. The pipeline overrides only `startDate` and `endDate` per block.

Outputs:
- `/tmp/validate-<candidate>-block{A,B,C}.json` — raw walk-forward results
- `/tmp/validate-<candidate>-eval-block{A,B,C}.json` — per-block gate report
- `strategy_exploration/validate-<candidate>.md` — final summary with verdict

## How to run

- **Always show the POST sequence + plan first and wait for explicit user approval before firing.** Three blocks ≈ 30-60 min total. A failed config burns the slot.
- **One candidate at a time** — engine OOMs on concurrent backtests.
- **Pipeline stops at the first failing block.** No point continuing if A fails.
- **For Block C: G10 requires explicit user confirmation.** Skill pauses, prints the frozen config, waits for user to type "confirmed" before firing.

## Verdict structure

| Outcome | Meaning |
|---|---|
| **TRADABLE** | Pass A+B+C, G11 ok. Eligible for live deployment after position-sizing finalisation + Monte Carlo (`/monte-carlo`). |
| **PROVISIONAL** | Pass A+B+C but G11 fail (edge degraded > 50% A→C). Paper-trade only; do not commit capital. |
| **INCONCLUSIVE_G11** | Pass A+B+C but G11 couldn't be evaluated (missing data or Block A edge/CAGR non-positive). **NOT a TRADABLE verdict** — investigate the data anomaly before treating as ready. |
| **REJECTED** | Failed any block. Candidate config burned. Re-design and re-enter via `/strategy-screen`. |

## Agent delegation

After the pipeline completes (pass or fail), spawn `firewall-analyst` with paths to all completed block JSONs + the summary JSON. The agent:
- Confirms the deterministic verdict against the per-block gates
- Trajectory analysis A→B→C across Sharpe / CAGR / DD / edge / trade count (distinguishes alpha decay from liquidity decay)
- Block-specific regime decomposition (outlier-driven blocks, narrow regime wins)
- "Passed but barely" gate margin scan (within 5% of threshold AND/OR within 1 IQR of the PASS cohort)
- Verdict-specific next-step recommendation

The skill does orchestration + per-block gate evaluation (deterministic); the agent does cross-block interpretation (pattern recognition past the verdict).

## Critical warnings

- **A REJECTED candidate is not "almost there".** Don't modify and re-run with the same firewall — that's data-mining. Go back to `/strategy-screen`.
- **No gate loosening to advance.** The firewall only works if it's strict.
- **The Block C result is the only one that matters for tradability.** A + B set up the assumption that Block C is true OOS. Pass without re-running C if any earlier block re-runs.
- **PROVISIONAL is not "almost tradable" — it means decay was observed.** Treat as a separate diagnostic problem before committing capital.

See [REFERENCE.md](REFERENCE.md) for gate rationale, block-boundary justification, and the cross-block decay math.
