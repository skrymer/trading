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
| **B — COVID** | 2014-01-01 → 2021-06-30 (7.5y) | 2020 COVID OOS+ | Continue to C |
| **C — Firewall** | 2021-01-01 → 2025-12-31 (5y) | 2022 inflation bear OOS+ | TRADABLE |

Block B ends 2021-06-30 (not 2020-12-31) so the W4 OOS window covers 2020 — without the extension G6 is structurally unreachable. 6-month overlap with Block C is contained in C's IS warm-up (C's first OOS under 36/12/12 cadence is 2024), preserving firewall integrity. Quant-verified boundaries — do not adjust.

**G6a/G6b half-year split deferred.** The quant's preferred design splits Block B's G6 into G6a (2020-H1 crash survival, edge ≥ −0.5%) and G6b (2020-H2 recovery, edge > 0) so a strategy that bled in March but got rescued by the rally can't sneak through. Implementing requires per-trade entry-date data in the walk-forward response, which the engine doesn't currently expose — tracked in [issue #51](https://github.com/skrymer/trading/issues/51). Until that lands, Block B uses single G6 ("2020 OOS edge > 0") evaluated on the W4 window aggregate.

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
| **TRADABLE** | Pass A+B+C, G11 ok. Eligible for live deployment after position-sizing finalisation + Monte Carlo (`/monte-carlo`). **See "Script-condition promotion" below — TRADABLE is conditional if the candidate uses inline scripts.** |
| **PROVISIONAL** | Pass A+B+C but G11 fail (edge degraded > 50% A→C). Paper-trade only; do not commit capital. |
| **INCONCLUSIVE_G11** | Pass A+B+C but G11 couldn't be evaluated (missing data or Block A edge/CAGR non-positive). **NOT a TRADABLE verdict** — investigate the data anomaly before treating as ready. |
| **NEAR_MISS** | Failed ≤ 2 gates AND every failure within tight margin AND no G6 failure. **NOT tradable** — "one design iteration away", not "almost tradable". Surfaces a `remediation_hint` indicating which axis to vary. Re-design and re-enter via `/strategy-screen`. |
| **REJECTED** | Any other failure: catastrophic miss, G6 failure, OR 3+ tight failures (multi-dimensional drift). Re-design and re-enter via `/strategy-screen`. |

### NEAR_MISS tight-margin bands (quant-verified 2026-05-28)

| Gate type | Tight if failure within |
|---|---|
| Percentage gates (G1, G2, G3, G4 pct, G4a, G4b) | 5% relative to threshold |
| Ratio gates (G5, G9) | 20% relative |
| Count gates (G4 by-count, G8) | 1 unit |
| Trade count (G12) | 20% relative |
| **G6 regime mandate** | **No near-miss — must be strictly positive** |
| G7 chop (OR-rule) | No near-miss possible — either at least one positive or none |

**Multi-gate cap**: NEAR_MISS requires ≤ 2 tight-margin failures total across all blocks. 3+ tight failures = REJECTED with "multi-dimensional drift" note (the pattern says systematic slightly-off-mandate, not iterational).

### `remediation_hint` values

The summary surfaces one of these tokens based on which gates failed:

| Hint | Triggered by |
|---|---|
| `regime_survival_redesign` | Any G6 failure (fundamental — strategy can't survive that regime) |
| `tune_position_sizing` | G1 alone, or with non-DD gates (sizer too conservative for the regime) |
| `add_regime_filter` | ≥2 of {G3, G4, G5} (the consistency cluster — usually fixed by suppressing entries in the bad regime) |
| `tighten_exit_or_reduce_positions` | G2/G3 only (DD-only failures with edge intact) |
| `expand_universe_or_loosen_entry` | G8/G12 (trade count too low) |
| `review_failed_gates` | Anything else / catch-all |

Hint is INFORMATIONAL — the firewall does not pre-approve any specific remediation. The operator picks the track.

### Script-condition promotion (gate on TRADABLE → live)

A TRADABLE verdict on a candidate that uses inline `{"type": "script"}` conditions in its `entryStrategy` or `exitStrategy` is **TRADABLE-PENDING-PROMOTION**, not final. Treat as paper-only until:

1. Each inline script is promoted to a real, named, version-controlled condition class via `/create-condition` (passes the skill's lookahead-safety audits and is unit-tested).
2. The candidate's request JSON is rewritten to use the promoted `type` name (e.g. `"type": "sectorBreadthDivergence"` instead of `"type": "script"`).
3. The candidate **re-enters the firewall from Block A** with the promoted-condition request. Pre-promotion and post-promotion runs are not interchangeable — the firewall validates the exact config that will ship.

Why: inline scripts are text in a request JSON; they bypass the `/create-condition` lookahead audits (PR #34 future-OB bug class), aren't unit-tested, aren't discoverable via `/api/backtest/conditions`, and silently drift between sessions. A TRADABLE verdict on a script-based config is a result about the *current text*, not the *deployed code*.

The skill's summary report flags script-condition usage when emitting TRADABLE so this isn't missed.

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
