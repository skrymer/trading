---
type: source
title: Minervini Sector dimension re-rated live under #167
summary: First worked example of the live Sector applicability rating (ADR 0025/#167): minervini sector = NEUTRAL, 0 favourable — trim/concentration/sign guards hold every cell off a label. Decision unchanged.
status: stable
tags: [assessment, sector, applicability-rating, minervini, methodology]
sources: ["strategy_exploration/assessments/minervini-vcp-breakout/ledger.jsonl", "docs/adr/0025-strategy-assessment-emits-applicability-ratings-and-the-decision-names-its-dimension.md", ".claude/agents/assessment-analyst.md"]
related: ["[[minervini-vcp-breakout]]", "[[strategy-assessment]]", "[[beta-delivery]]", "[[aliased-regime-sensitivity]]"]
updated: 2026-06-14
---

# Minervini Sector re-rate — the live Sector rating's first worked example (#167)

**What ran.** Issue #167 (PR #171, merged 2026-06-14) added the three per-sector statistics the
[[strategy-assessment]] Sector applicability rating needs — `edgeStandardError` (entry-month-clustered
CR0 SE), `trimmedEdge` (tail-trimmed mean), `maxSingleTradeProfitShare` (concentration share). With the
engine deployed to PRD (udgaard 1.0.94), the [[minervini-vcp-breakout]] Sector dimension — which had
shipped `unrateable-pending-instrumentation` in the 2026-06-13 assessment — was **re-rated live** on the
**same continuous run** as the original battery (backtestId `239232fb`, 1043 trades, `endDate 2025-12-31`,
edge 3.65/trade, CAGR 14.0%). The `assessment-analyst` applied the now-live ADR 0025 rule; only the Sector
dimension changed (Broad/Regime unchanged — same trade population).

**The live rule (ADR 0025).** A cell is **rateable** only if `N ≥ 30` AND `edgeStandardError > 0` AND
`maxSingleTradeProfitShare ≤ 0.40` AND `sign(edge) == sign(trimmedEdge)`; then (in order) `adverse` iff
`trimmedEdge < −2.5·SE`, `favourable` iff `trimmedEdge > +2.5·SE` (**k = 2.5** for the 11-cell family),
else `neutral`. The directional test runs on the **trimmed** edge, never the raw mean.

## Headline numbers (per cell, k = 2.5)

| Sector | N | edge (raw) | trimEdge | SE | 2.5·SE | maxShare | Rating (decider) |
|---|---|---|---|---|---|---|---|
| XLF | 171 | 3.75 | 1.77 | 1.27 | 3.18 | 0.05 | **neutral** (\|trim\| ≤ 2.5·SE) |
| XLK | 139 | 3.12 | **−1.50** | 2.57 | — | 0.13 | **unrateable** (sign-flip) |
| XLV | 127 | 4.52 | 2.76 | 1.79 | 4.47 | 0.07 | **neutral** |
| XLI | 127 | 2.39 | **−1.34** | 2.28 | — | 0.21 | **unrateable** (sign-flip) |
| XLY | 125 | 2.81 | **−1.64** | 2.43 | — | 0.12 | **unrateable** (sign-flip) |
| XLB | 82 | 5.84 | 2.50 | 2.61 | 6.53 | 0.12 | **neutral** |
| XLC | 70 | 1.25 | **−0.54** | 1.86 | — | 0.16 | **unrateable** (sign-flip) |
| XLE | 70 | 1.18 | **−1.22** | 2.03 | — | 0.18 | **unrateable** (sign-flip) |
| XLP | 52 | 7.94 | 0.47 | 7.29 | — | **0.53** | **unrateable** (concentration >0.40) |
| XLRE | 48 | 3.61 | 1.63 | 2.12 | 5.30 | 0.19 | **neutral** |
| XLU | 32 | 8.51 | 5.03 | 5.34 | 13.35 | 0.31 | **neutral** |

**Net Sector → `neutral` (hypothesis-thin): 5 neutral · 6 unrateable · 0 favourable · 0 adverse.**

## What it taught

- **The guards do what prose could only assert.** The 2026-06-13 report flagged in words that minervini's
  two highest *raw* sector edges sat in the thinnest cells (XLU 8.51/N=32, XLP 7.94/N=52) — the
  tail-carried-mean pattern. The instrumentation now resolves them numerically: **XLP** raw 7.94 →
  `trimmedEdge` **0.47** and trips the concentration guard (one trade = **53%** of cell P&L) → `unrateable`;
  **XLU** survives the guards but its trim (5.03) sits far inside `2.5·SE` (13.35) on N=32 → `neutral`.
  Neither thin "winner" carries a label.
- **The robust-sign guard is the dominant disqualifier here.** Five mid-cyclical cells (XLK, XLI, XLY,
  XLC, XLE) carry a *positive raw mean* entirely from a handful of multi-baggers that goes **negative once
  trimmed** → `sign(edge) ≠ sign(trimmedEdge)` → `unrateable`. ^[inferred — that the dimension-wide
  tail-dependence is the headline read is synthesis; the per-cell sign flips are measured]
- **k = 2.5 holds the well-populated cells to neutral too.** XLF (N=171) and XLV (N=127) are positive and
  large, but their trimmed edges (1.77, 2.76) don't clear `2.5·SE` (3.18, 4.47) → `neutral`. The
  family-wise bar refuses a favourable label until the robust edge separates from clustered noise.
- **Sector is more fragile than regime** (thin N + tail/bad-print contamination) — the three guards are
  load-bearing; default `unrateable`. This is the same audit lens that exposed the dev-data VPI/LAF
  bad-print incident, now formalized into the rating bar.

## Decision

**Unchanged: `shelve(dimension=broad)`.** No sector cell is `favourable`, so no fresh confirm-path opens
and nothing redirects the operator's terminal act. A favourable sector *would* have pointed at a fresh,
distinct, sector-scoped candidate + within-sector conditional null through the firewall — **never** a
prune of this config to its winning sectors (the rescue-forbidden / [[aliased-regime-sensitivity]] trap).
A new RATINGS ledger event was appended (sector now `neutral`); Broad/Regime carried forward verbatim.

## Pages updated

[[minervini-vcp-breakout]] (Sector dimension re-rated), [[strategy-assessment]] (Sector views now live,
#167 worked example), `index.md`, `log.md`.
