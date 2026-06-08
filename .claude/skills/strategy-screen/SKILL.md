---
name: strategy-screen
description: Run a fast 10-year (2005-2015) walk-forward screening pass with relaxed gates to filter candidate sweeps before the full 3-block firewall validation. Use when sweeping multiple strategy variants and the full firewall is too slow for first-pass triage. Survivors progress to /validate-candidate, whose Block A is the full-period binding layer.
argument-hint: "[strategy-name] [variant-label]"
---

# Strategy Screen

Answers one question: **"Is this candidate worth a full Block A run, or can I drop it now?"**

Filters obvious losers fast (~3-5 min per candidate vs ~10+ min on full Block A) so a 10-30 variant sweep takes hours instead of a day. Survivors progress to `/validate-candidate`, whose Block A (2000-2014) is the full-period binding layer — there is no separate `/walk-forward` Block A / B / C step.

This skill is strategy-neutral. Substitute the user's actual entry / exit / sizing in every example.

## Screening config (fixed)

The screening window and cadence are deliberately fixed. Don't tune them — that defeats the purpose.

| Setting | Value | Why |
|---|---|---|
| Date range | **2005-01-01 to 2015-01-01** (10y) | Quant-validated. One major stress (GFC 2008-09), two regimes (pre-GFC trend + QE grind). Furthest from Block C (2021-2025) so no leakage by proximity. |
| IS / OOS / step | **36 / 12 / 12 months** | Yields 7 OOS windows — minimum defensible for cross-window stability. |

## Quick start

```bash
.claude/scripts/udgaard-post.sh /api/backtest/walk-forward '{
  "assetTypes": ["STOCK"],
  "useUnderlyingAssets": false,
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"},
  "startDate": "2005-01-01",
  "endDate":   "2015-01-01",
  "inSampleMonths": 36,
  "outOfSampleMonths": 12,
  "stepMonths": 12,
  "maxPositions": <N>,
  "entryDelayDays": 1,
  "ranker": "<ranker>",
  "positionSizing": {
    "startingCapital": <dollars>,
    "sizer": <SIZER>,
    "leverageRatio": 1.0
  },
  "riskFreeRatePct": 0.0
}' /tmp/screen-<variant-label>.json
```

Sizer / ranker options are the same as `/backtest` and `/walk-forward` — see `/backtest` SCENARIOS.md §2 and `/walk-forward` SCENARIOS.md §4.

## Relaxed screening gates

Pass **all five** to qualify as a survivor. Reject any candidate that fails any gate. **No softening for borderline cases** — that defeats the screen.

| Gate | Threshold | Field |
|---|---|---|
| **G1 — Pooled OOS edge** | `aggregateOosEdge` ≥ **`0.10 × riskPercentage`** (e.g. **≥ 0.125%** at 1.25% risk; **≥ 0.20%** at 2.0% risk) | `aggregateOosEdge` |
| **G2 — Sharpe** | stitched Sharpe ≥ **0.7** | `aggregateOosRiskMetrics.sharpe` |
| **G3 — Positive windows + median** | ≥ **5 of 7** windows with `outOfSampleEdge > 0` **AND** median per-window edge > 0 (prevents 5 marginal positives + 2 deep negatives from passing) | `windows[].outOfSampleEdge` |
| **G4 — GFC stress** | W1 (OOS Jan 2008 – Jan 2009) `outOfSampleMaxDrawdownPct` ≤ **2× median** across 7 windows | `windows[0].outOfSampleMaxDrawdownPct` |
| **G5 — Multi-comparison hygiene** | If **> 50 variants** screened across the sweep: raise G1 to `0.12 × riskPercentage` (e.g. 0.15% at 1.25% risk) **AND** require **6 of 7** in G3 instead of 5 | sweep-wide |

**G1 scaling**: the per-trade edge floor scales with the configured risk-per-trade. Picking gates in absolute % silently looses if you bump risk from 1.25% → 2.0%. Always express G1 relative to `riskPercentage`.

## How to run

- **Always show the POST first and wait for explicit user approval before firing.** Same rule as `/walk-forward`. A 3-5 min screen is cheap individually but a 20-variant sweep is still hours.
- **Endpoint:** `POST /api/backtest/walk-forward` on PRD port `9080` with `X-API-Key` (via `udgaard-post.sh`).
- **One run at a time** — engine OOMs concurrent backtests.
- **Save raw response to `/tmp/screen-<variant-label>.json`** — analyst agent reads from disk.
- **Wall time:** ~3-5 min per candidate (10y span × 7 windows × sized backtest).
- **Track variants-screened across the sweep** — G5 fires at 50.

## After screening — handoff to `/validate-candidate`

**Survivors are not winners — they're candidates worth further validation.** The next step is the 3-block firewall via `/validate-candidate`, which applies the strict v4 gates + design-isolation + cross-block edge-decay check.

**The screen request file IS the validation template.** `/validate-candidate` overrides only `startDate` / `endDate` per block — every other field (entry/exit/sizer/ranker/seed) is reused as-is:

```bash
# Generic invocation:
.claude/skills/validate-candidate/scripts/run-pipeline.sh <candidate-name> /tmp/screen-req-<candidate-name>.json
```

The pipeline runs the binding layers (Block A 2000-2014 → Block B 2014-2021 incl COVID → 25-year aggregate), stops at the first failing binding layer, treats Block C (2021-2025) as an informational sanity check, and emits `knowledge/wiki/sources/<date>-validate-<candidate-name>.md` (a seed `sources/` wiki draft, distilled via `/wiki-ingest`) with the final TRADABLE / PROVISIONAL / REJECTED verdict.

**Skill output requirement** when reporting screen results: for each PASS candidate that also clears the 25% CAGR tradability bar (the operator floor — ADR 0015, lowered from 30%), surface the exact `/validate-candidate` invocation as the recommended next step. Below-25%-CAGR passes go in a separate "filtered out" list — the screen approved them mechanically but they don't meet the user's tradability floor.

## Persist the request (ADR 0017)

A candidate's identity is its exact request JSON, and `/tmp/screen-<label>.json` is ephemeral — a reboot or doc-retirement loses it (this is the #135 George loss the ADR fixes). After a screen completes **for a named candidate** — one that has (or is about to get) a `knowledge/wiki/entities/<name>.md` page — persist its fired request beside the entity:

```bash
.claude/scripts/persist-request-json.sh <name> /tmp/screen-<label>.json
```

- **Trigger: a named candidate, regardless of PASS/FAIL.** A screen-only reject still merits its config persisted — rejections get re-tested when the engine / universe / baseline changes (George and MRM are both persisted rejects). Do **not** persist anonymous exploratory variant-labels in a sweep — one canonical skeleton per *named* candidate, not one file per probe.
- **Persist the validated skeleton only** — the body you actually fired (faithful by construction), not a hand-edited reconstruction.
- After persisting, add/refresh the entity frontmatter `request: "<name>.request.json"` pointer and a **"Reproducing"** section (george.md is the template). A one-field variant — e.g. the **Random baseline** = the same file with `"ranker": "Random"` + a swept `randomSeed` — gets **no** separate file; document that edit in "Reproducing" instead.

## Agent delegation

For individual candidate analysis: spawn `walk-forward-analyst` with the path to the saved JSON (per-window stability for that candidate).

For sweep-wide analysis (after all candidates are fired): spawn `strategy-screen-analyst` with the sweep's eval JSONs + verdict table. The agent:
- Buckets candidates into TRUE SURVIVORS / PASS-but-below-tradability / FAILURES / LOST
- Classifies each failure against the failure-mode → root-cause table (G4 over-firing pattern, ranker brittleness, edge inconsistency, etc.)
- Flags seed-invariant duplicates, ranker-family clustering, contamination tells, cross-candidate trade overlap
- Recommends per-candidate next step (`/validate-candidate` invocation for survivors; specific remediation for fixable failures; reject-and-redesign for structural ones)

## Critical warnings

- **Screening is a filter, not a verdict.** A pass means "keep evaluating". Live-trading decisions require the full firewall (`/validate-candidate`: Block A + Block B + 25y aggregate binding, Block C informational), never the 10y screen alone.
- **Don't tune the screening window or gates.** They're set by the quant. If a strategy fails and you change the gates to make it pass, you've defeated the screen.
- **Don't screen on 2014-2024 or any window touching 2021-2025** — overlaps Block C and leaks validation data. Stick to 2005-2015.
- **Background context** in [REFERENCE.md](REFERENCE.md) — quant verdict, statistical power, risks to watch.
