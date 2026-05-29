---
name: validate-candidate
description: Run a /strategy-screen survivor through the refined firewall — Block A v4 (binding) + Block B v4 (binding, COVID-inclusive) + 25-year aggregate v4 (binding) + Block C as informational sanity check. Use when promoting a candidate from /strategy-screen to deeper validation, when applying final go/no-go to a candidate strategy, or when verifying a strategy is tradable before live deployment.
argument-hint: "[candidate-name] [request-template-path]"
---

# Candidate Validation — Refined Firewall (3 Binding Layers + Informational Block C)

Promotes a `/strategy-screen` survivor through **three binding validation layers** plus an informational Block C sanity check, applies the v4 gates per layer, and emits a final verdict: **TRADABLE / PROVISIONAL / REJECTED**.

This skill is strategy-neutral. Substitute the user's actual candidate label / request template in every example.

**Why the refinement (quant-verified 2026-05-28).** The original 3-block firewall over-rejected on Block C because Block C's 5-year range under 36/12/12 cadence fits only 1 OOS window — a single-window edge sign is a Type I error generator. The refined framework keeps Block A and Block B as binding (properly powered, regime-separated for edge-decay detection) and adds 25-year aggregate v4 as a new binding statistical-power layer. Block C demotes to informational only — it still surfaces 2024-style regime risk as a yellow flag, but a single −0.11% window can no longer reject a strategy that cleared the binding layers.

## Pipeline

| Layer | Range | Windows | Binding? | Mandate regime | If pass |
|---|---|---|---|---|---|
| **Block A** | 2000-01-01 → 2014-01-01 (14y) | ~10 OOS | **Binding** | 2008 GFC OOS+ | Continue to B |
| **Block B (COVID)** | 2014-01-01 → 2021-06-30 (7.5y) | ~6 OOS | **Binding** | 2020 COVID OOS+ | Continue to 25y + Block C |
| **25-year aggregate** | 2000-01-01 → 2025-12-31 (26y) | ~22 OOS | **Binding** | Aggregate v4 (statistical-power layer) | Continue to Block C |
| **Block C (informational)** | 2021-01-01 → 2025-12-31 (5y) | 1 OOS | Informational only | 2022 inflation bear (yellow flag if negative) | Verdict computed from binding layers; Block C surfaces risk |

**TRADABLE iff:** Block A v4 PASS AND Block B v4 PASS AND 25-year aggregate v4 PASS AND Block C non-catastrophic (no `|edge| > 0.5%`, no DD > 20%).

A candidate that clears the binding layers but fails Block C's non-catastrophic check is **PROVISIONAL** (paper-trade only, do not commit capital until 2024-style regime is more cleanly survived). A candidate that fails any binding layer is **REJECTED**.

Block B ends 2021-06-30 (not 2020-12-31) so the W4 OOS window covers 2020 — without the extension G6 is structurally unreachable. 6-month overlap with Block C is contained in C's IS warm-up (C's first OOS under 36/12/12 cadence is 2024), preserving firewall integrity. Quant-verified boundaries — do not adjust.

**G6a/G6b half-year split deferred.** The quant's preferred design splits Block B's G6 into G6a (2020-H1 crash survival, edge ≥ −0.5%) and G6b (2020-H2 recovery, edge > 0) so a strategy that bled in March but got rescued by the rally can't sneak through. Implementing requires per-trade entry-date data in the walk-forward response, which the engine doesn't currently expose — tracked in [issue #51](https://github.com/skrymer/trading/issues/51). Until that lands, Block B uses single G6 ("2020 OOS edge > 0") evaluated on the W4 window aggregate.

**Why each layer is necessary (quant 2026-05-28 7th consultation):**
- **3-block separation** catches *edge decay* — the single most predictive failure mode for live deployment. A strategy whose alpha is shrinking across blocks will pass a 25y aggregate (because the early blocks are still strong) but fail block-separated comparison. Multiple previously-investigated candidates showed this signature (Block-A-edge >> Block-B-edge). 25y aggregate alone would miss it.
- **25-year aggregate** fixes statistical-power weakness in the per-block gates. v4 gates calibrated on 5-7 windows (Block A) or 1 window (Block C) have low power. The same gates on 22 windows are properly powered. G5 CoV with N=22 is a real test; with N=5 it's noise.
- **Block C demoted** because 5-year-range + 36/12/12 cadence = 1 OOS window. Rejecting on a single-window edge sign generates Type I errors. Block C still reports numbers for the 2024-style narrow-leadership regime risk (informational yellow flag) but no longer binds the verdict.

A candidate must clear **all three binding layers** (Block A v4, Block B v4, 25-year aggregate v4). **A failed binding layer is final. Modifying the config and re-running is data-mining, not validation.** The legitimate remediation path is to go back to `/strategy-screen` and re-survey the variant before re-entering the firewall.

## v4 gates (applied at each layer; some block-specific)

| Gate | Threshold |
|---|---|
| G1 — CAGR | ≥ max(10%, SPY+2%) AND **≥ 30%** (tradability floor) |
| G2 — Aggregate max DD | ≤ 25% |
| G3 — Worst-window DD | ≤ 20% |
| G4 — Positive windows | ≥ 75% (N ≥ 4); on short blocks use G4a + G4b below |
| G4a (N < 4) | No single OOS window worse than −5% CAGR |
| G4b (N < 4) | Block-aggregate CAGR ≥ G1 threshold |
| G5 — CoV of per-window edges | ≤ 1.5 |
| G6 — Regime mandate | Block A: 2008 OOS+. Block B: 2020 OOS+. 25y aggregate: 2008 OOS+. Block C: 2022 OOS+ (informational only). |
| G7 — Chop regime | Block A: ≥1 of {2004, 2011, 2015-H1} positive. Block B: ≥1 of {2015-H2, 2018-Q4} positive. 25y aggregate: ≥1 of {2004, 2011, 2015-H1} positive. Block C: skipped. |
| G8 — Min trades per window | ≥ 30 |
| G9 — Sharpe + Calmar | Sharpe ≥ 0.8 AND Calmar ≥ 0.5 |
| G10 — Design isolation | Skill emits the candidate config + freeze date. User must confirm no config change before any binding layer beyond Block A. |
| G11 — Cross-block edge decay | edge_B ≥ 0.5 × edge_A AND CAGR_B ≥ 0.5 × CAGR_A (applied between binding blocks). Failure downgrades verdict to PROVISIONAL. |
| G12 — Block-aggregate trade count | ≥ 100 trades per block |

**Block C non-catastrophic check (informational, not a gate):** No `|edge| > 0.5%` AND no DD > 20%. A binding-layer-clearing candidate that breaches non-catastrophic Block C is **PROVISIONAL** (paper-trade only). A binding-layer-clearing candidate with clean Block C is **TRADABLE** (subject to script-condition promotion). Block C's other gates are reported but do not bind the verdict.

## Quick start

```bash
.claude/skills/validate-candidate/scripts/run-pipeline.sh <candidate-name> <request-template>
```

The template is the `/tmp/v3-req-<candidate>.json` produced earlier in the screen, OR any request file with the candidate's full `entryStrategy` / `exitStrategy` / `positionSizing` / `ranker`. The pipeline overrides only `startDate` and `endDate` per block.

Outputs:
- `/tmp/validate-<candidate>-block{A,B}.json` — raw walk-forward results (binding layers)
- `/tmp/validate-<candidate>-25y.json` — raw walk-forward result (binding statistical-power layer)
- `/tmp/validate-<candidate>-blockC.json` — raw walk-forward result (informational only)
- `/tmp/validate-<candidate>-eval-block{A,B}.json`, `/tmp/validate-<candidate>-eval-25y.json`, `/tmp/validate-<candidate>-eval-blockC.json` — per-layer gate reports
- `strategy_exploration/validate-<candidate>.md` — final summary with verdict

## How to run

- **Always show the POST sequence + plan first and wait for explicit user approval before firing.** Four runs ≈ 60-90 min total (Block A ~25 min + Block B ~10 min + 25y ~40 min + Block C ~7 min). A failed binding layer burns the slot.
- **One candidate at a time** — engine OOMs on concurrent backtests.
- **Pipeline stops at the first failing binding layer.** No point continuing if Block A fails.
- **G10 design-isolation:** explicit user confirmation required before firing the 25y aggregate run (the config must be unchanged from Block A + Block B). Skill pauses, prints the frozen config, waits for user to type "confirmed".

## Verdict structure

| Outcome | Meaning |
|---|---|
| **TRADABLE** | Pass Block A v4 + Block B v4 + 25y aggregate v4 (all 3 binding) AND Block C non-catastrophic (no \|edge\| > 0.5%, no DD > 20%) AND G11 ok across binding blocks. Eligible for live deployment after position-sizing finalisation + Monte Carlo (`/monte-carlo`). **See "Script-condition promotion" below — TRADABLE is conditional if the candidate uses inline scripts.** |
| **PROVISIONAL** | Pass all 3 binding layers but EITHER Block C breaches non-catastrophic check OR G11 fails (edge degraded > 50% A→B). Paper-trade only; do not commit capital until the degraded regime is more cleanly survived (typically 6 months of paper-trade-tracking-vs-25y-distribution per quant). |
| **INCONCLUSIVE_G11** | Pass all 3 binding layers but G11 couldn't be evaluated (missing data or Block A edge/CAGR non-positive). **NOT a TRADABLE verdict** — investigate the data anomaly before treating as ready. |
| **NEAR_MISS** | Failed ≤ 2 gates across binding layers AND every failure within tight margin AND no G6 failure. **NOT tradable** — "one design iteration away", not "almost tradable". Surfaces a `remediation_hint` indicating which axis to vary. Re-design and re-enter via `/strategy-screen`. |
| **REJECTED** | Any other failure: catastrophic miss in a binding layer, G6 failure, OR 3+ tight failures (multi-dimensional drift). Re-design and re-enter via `/strategy-screen`. |

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

After the pipeline completes (pass or fail), spawn `firewall-analyst` with paths to all completed block JSONs + the 25y JSON + the summary JSON. The agent:
- Confirms the deterministic verdict against the per-layer gates
- Trajectory analysis A→B across Sharpe / CAGR / DD / edge / trade count (distinguishes alpha decay from liquidity decay) — uses 25y per-window distribution as the population baseline
- Block-specific regime decomposition (outlier-driven blocks, narrow regime wins)
- Block C interpretation: is the 2024-style regime risk a yellow flag or a binding concern? Decomposes Block C edge/DD vs the 25y per-window tail to size the risk.
- "Passed but barely" gate margin scan (within 5% of threshold AND/OR within 1 IQR of the PASS cohort)
- Verdict-specific next-step recommendation (incl. paper-trade plan for PROVISIONAL)

The skill does orchestration + per-layer gate evaluation (deterministic); the agent does cross-layer interpretation (pattern recognition past the verdict).

## Critical warnings

- **A REJECTED candidate is not "almost there".** Don't modify and re-run with the same firewall — that's data-mining. Go back to `/strategy-screen`.
- **No gate loosening to advance.** The firewall only works if it's strict.
- **The binding layers (Block A + Block B + 25y aggregate) set the TRADABLE/REJECTED verdict.** Block C is informational only — it surfaces the most-recent regime as a yellow flag but a single −0.11%-edge OOS window cannot reject a candidate that cleared the binding layers (this was the 2024 VZ3-s3 lesson; single-window Block C verdicts generate Type I errors).
- **PROVISIONAL is not "almost tradable" — it means a binding-layer-clearing candidate breached Block C's non-catastrophic check or G11.** Treat as a separate diagnostic problem (typically 6-month paper-trade-vs-25y-distribution monitor before live capital).

See [REFERENCE.md](REFERENCE.md) for gate rationale, block-boundary justification, and the cross-block decay math.
