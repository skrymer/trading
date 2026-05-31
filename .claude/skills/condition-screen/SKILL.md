---
name: condition-screen
description: Fast diagnostic pre-screen of a single entry condition (or AND/OR stack, incl. inline script) before wiring it into a strategy — forward-return lift, firing-rate-per-year, parameter-sensitivity (ARS) sweep, SPY-regime lift, and overlap with known-good conditions. Use at condition DESIGN time, before /strategy-screen. Produces no verdict; rejects bad conditions cheaply.
argument-hint: "[condition-label]"
---

# Condition Screen

> **This is diagnostic, not predictive. A condition that passes /condition-screen is NOT validated. A condition that fails /condition-screen is rejected without further work.**

Lead every report with that sentence. The screen catches structurally-unsound conditions — Aliased Regime Sensitivity, firing collapses, noisy clones of existing conditions — at design time, as a pure read in seconds-to-minutes, before any backtest engine-time is spent. It emits a raw statistics table and **no pass/fail verdict**. Verdicts come from `/strategy-screen` and `/validate-candidate`.

This skill is strategy-neutral. Substitute the user's actual condition in every example.

## Where it sits in the workflow

```
condition design → /condition-screen →
  alarming (ARS / firing collapse / high overlap) → redesign or abandon
  clean                                            → wire into a strategy → /strategy-screen → /validate-candidate
```

It does **not** replace validation. A clean screen means "worth wiring up", never "tradable".

## Quick start

```bash
.claude/scripts/udgaard-post.sh /api/conditions/screen '{
  "conditions": [{"type": "<conditionType>", "parameters": {<params>}}],
  "operator": "AND"
}' /tmp/condition-screen-<label>.json
```

Then hand the saved JSON to the analyst:

> Spawn `condition-screen-analyst` with `/tmp/condition-screen-<label>.json`.

The request fields (all optional except `conditions`):

| Field | Default | Notes |
|---|---|---|
| `conditions` | — | entry condition stack; inline `script` is first-class |
| `operator` | `AND` | `AND` / `OR` across the stack |
| `symbols` | all `STOCK` | explicit universe override. Full set is the default and correct for a single-condition screen. For a **whole-library sanity sweep**, use the frozen reduced universe instead — see REFERENCE "Library sanity-sweep mode" |
| `assetTypes` | `["STOCK"]` | universe when `symbols` omitted |
| `startDate` | `2000-01-01` | freely movable earlier |
| `endDate` | `2021-01-01` | **hard-capped at 2021-01-01** — see leakage rule |
| `entryDelayDays` | `1` | fill bar = signal bar + this; forward returns anchor here |
| `horizons` | `[5,10,20]` | forward-return horizons N (trading days) |
| `scriptSweeps` | `[]` | sweep a `{{param}}` tunable inside a script — see REFERENCE |
| `referenceConditions` | `[]` | known-good stacks for Jaccard overlap; empty → N/A |

## The leakage rule (non-negotiable)

`endDate` is hard-capped at **2021-01-01** (Block C's start). The endpoint returns **400** for any later date. Block C is the firewall's only true out-of-sample window; eyeballing a condition's Block-C behaviour at design time and then refining leaks it irrecoverably. Do not try to work around the cap. Move `startDate` earlier for more history instead. Rationale: ADR 0007.

## How to run

- **Always show the POST first and wait for explicit user approval before firing** — same rule as every other API skill.
- **Endpoint:** `POST /api/conditions/screen` on PRD port `9080` with `X-API-Key` (via `udgaard-post.sh`).
- **Pure read** — no backtest engine, no position sizing, no exit. Safe to run repeatedly.
- **Save the response to `/tmp/condition-screen-<label>.json`** — the analyst reads from disk.

## Reading the output

The headline is **lift**, not absolute forward return. See [REFERENCE.md](REFERENCE.md) for the full stat reference, the ARS-detection patterns, the `{{param}}` script-sweep convention, and when to abandon a condition. Delegate interpretation to `condition-screen-analyst` — it applies the (uncalibrated) flag thresholds the backend deliberately leaves out.

## Critical warnings

- **Screening is a filter, not a verdict.** A clean screen means "keep going"; it never authorises live trading or skips the firewall.
- **High-firing conditions (≥33%) have meaningless absolute stats** — read lift only. ≥60% is effectively a universe filter.
- **A script with an internal tunable is not screened for fragility unless you declare a `scriptSweeps` entry for it.** The backend cannot see constants baked into Kotlin source. See REFERENCE.
