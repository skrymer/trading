---
type: concept
title: Strategy assessment — the non-adjudicating report funnel
summary: The /assess-strategy funnel (ADR 0022) — pre-flight, full battery, no verdict, human decides; full C-span disclosure with eyeballed-C annotation; what the analyst may and may not read into it.
status: stable
tags: [methodology, assessment, funnel, reporting]
sources: ["docs/adr/0022-strategy-assessment-is-a-separate-non-adjudicating-funnel.md", "knowledge/wiki/sources/2026-06-12-strategy-assessment-design-and-regime-readout-prereg.md", "CONTEXT.md", ".claude/skills/assess-strategy/SKILL.md"]
related: ["[[regime-read-out]]", "[[component-firewall]]", "[[the-funnel]]", "[[aliased-regime-sensitivity]]", "[[beta-delivery]]", "[[2026-06-14-regime-classification-v3-research]]", "[[2026-06-14-minervini-sector-rerate]]"]
updated: 2026-06-14
---

# Strategy assessment — the non-adjudicating report funnel (ADR 0022)

The complement to [[component-firewall]]: where the validation funnel short-circuits at the first
failing binding layer and adjudicates, the assessment funnel (`/assess-strategy`) runs **everything**,
reports **everything**, and adjudicates **nothing** — the operator ingests the report and decides.
Term definitions live in CONTEXT.md (*Strategy assessment* section); the decision record is ADR 0022.
This page holds what the future assessment-analyst needs that neither states.

## What it is for

1. **Autopsy** — the validation funnel tells you a candidate died at Block A and nothing else; an
   assessment shows its behaviour across the whole tape (the operator's original pain).
2. **Deployment read** — for an already-validated strategy: "given the current regime, do I deploy /
   how do I size" via the regime table + current-regime line ([[regime-read-out]]).

It accepts any config — dead, settled, or fresh. It never resurrects, settles, or kills; the only road
to TRADABLE remains the firewall.

## The pre-flight (Step 0) — blocks only on impossibility

A mechanical check before the battery fires (`.claude/skills/assess-strategy/scripts/preflight.py`).
Its contract: **block only when the report would be garbage or wasted compute, shape the battery for
known config classes, advise on everything else** — never judge strategy quality (that is the
firewall's job).

- **Blockers**: unknown condition/ranker types; span-disqualified signals (a ~5y signal under a 25y
  spine is silently empty for 20 years — the recurring funnel-disqualification tell); regime-label
  gates before the [[regime-read-out]] anchor check has PASSED (the classifier must be validated
  before anything consumes it).
- **Battery shaping**: Random ranker → multi-seed sweep (one run is one draw); selecting ranker →
  the byte-identical Random-baseline arm (the [[beta-delivery]] guard); missing `randomSeed` → pin one
  (tie-break jitter makes reruns non-reproducible).
- **Advisories** (stamped into the report): inline `script` conditions (promotion + G14 reminder,
  static lookahead smells — a flag, not proof); firewall-DEAD config → autopsy framing, never refusal.
- **Cadence probe**: one small early-window + late-window backtest pair before the expensive spine —
  catches never-fires, span gaps the static check missed, and the full-universe OOM class.

## The battery, and why it is shaped this way

One expensive spine + cheap complements (~55–65 min): **25y walk-forward** (spine) + **continuous 25y
backtest** + **Monte Carlo** (interim: on the continuous run's trades, an IS-inclusive deviation from
ADR 0022's stitched-OOS prescription pending issue #161 — the report carries the stamp) +
**deflated-Sharpe flag**; **Random-ranker baseline only for
permissive-entry + ranker-selects candidates** (without it such a report can be pure entry-universe
beta — see [[beta-delivery]]).

- Block A/B/C-range slices of the spine are **proxies**, not the firewall's block verdicts: each
  firewall block is its own walk-forward with its own IS-anchoring and window phasing, so
  window-definition-dependent stats (G3/G4/G5) and per-block Calmars genuinely differ. Show real block
  JSONs beside the proxies where they exist; never re-fire blocks for a report.
- The continuous backtest is the only artifact showing the **real un-stitched drawdown path** (the WF
  stitch omits IS-window drawdowns, ADR 0005) — the honest "what would my account have done" curve.
- Monthly entry buckets (ADR 0006) are additive, so the GFC/COVID mandate slices are fully recoverable
  from the spine.

## The C-span exposure (know what was traded away)

Assessments show **full per-window 2021–25 numbers** — the operator's costed overrule of the quant's
C-coarse recommendation (SURVIVED/CATASTROPHIC/trade-count only). Floor: contamination stamps on every
C-span section + a permanent **operator-eyeballed-C** annotation on the premise family, surfaced by the
deflated-Sharpe readout. Consequence the analyst must carry: **from a family's first assessment onward,
the firewall's Block C verdict is decorative for that family.** The C-leak is about the *tape*, not the
block label — the spine's 2021–25 windows are the same leak surface.

## The three couplings (and only three)

1. Every assessment run is a **firewall trial** in the deflated-Sharpe ledger; the updated flag prints
   in every report — iterating variants through assessments is *visible*, disclosure-based discipline
   where the validation funnel uses structural refusal.
2. The **operator-eyeballed-C annotation**.
3. The **assessment decision log** (redesign / send to firewall / paper-trade / deploy-at-own-risk /
   shelve), appended to the assessment ledger.

## The regime table — honest vs theater (the analyst's line to hold)

Honest: descriptive per-regime edge ± **date-clustered SE** + N over the spine's trades, labels from
the strategy-blind [[regime-read-out]] (published/dwell-smoothed label; raw label as diagnostic
column), hard **insufficient-N floor ~30 trades** ("insufficient — do not infer"; crisis/chop will
almost always trip it, correctly). Per the ADR 0024 trust grades: GRIND/NARROW/CHOP rows render only
under the fixed reliability banner (below the axes' resolving power — treat as one
uptrend/unclassified bucket), the THRUST row carries the recovery-suppression note, and the
current-regime line reports CRISIS authoritatively while collapsing the fine-grain labels.
Legitimate use: *sizing/timing context for an already-validated strategy*. The descriptive-only ceiling
on GRIND/NARROW/CHOP is the v2 axes' resolving power; the from-scratch v3 path to making those rows
gateable (and the regime applicability ratings rateable) is [[2026-06-14-regime-classification-v3-research]] (#168).

Theater (refuse to enable): per-regime backtests or re-optimization; any per-regime number used to
**select or redesign** ("add a grind gate") — that is [[aliased-regime-sensitivity]] and the rescue
path ADR 0023 forbids; crisis/chop point estimates; "current regime → expected edge" point forecasts.
The standing warning prints under every regime table.

Sector dimension, three views answering different questions: an unconditional **per-sector
performance league table** (the candidate's own trades by sector — N, win rate, edge, maxDD — from
the continuous run's `sectorStats`, 30-trade insufficient-N floor; "which sectors does the strategy
win in"); a market-scoped **regime×sector matrix** (strategy-blind, spell-clustered SEs; "how does
each sector behave per regime, independent of the strategy"); and a per-candidate **sector×regime
drill-down** (insufficient-N per cell; expect mostly-grey tables — readable cells only for the
dominant sectors × dominant regimes; "does a sector tilt explain the regime edge"). All descriptive —
pruning to winning sectors after the fact is sector-overfitting, the same ARS trap as the regime table.

**The Sector applicability rating is live (ADR 0025, issue #167, 2026-06-14).** `sectorStats` now emits
per-cell `edgeStandardError` (entry-month-clustered CR0 SE), `trimmedEdge`, and `maxSingleTradeProfitShare`,
so the league table's cells carry ratings: **rateable** iff `N ≥ 30` AND `edgeStandardError > 0` AND
`maxSingleTradeProfitShare ≤ 0.40` AND `sign(edge) == sign(trimmedEdge)`; then `favourable`/`adverse` iff
**`trimmedEdge`** clears `±2.5·SE` (**k = 2.5** for the ~11-cell family — higher than the regime family's
k=2 because the look-elsewhere multiplicity bites across 11 simultaneous cells, flagged-not-corrected),
else `neutral`. Three rateability guards are mandatory because **sector is more fragile than regime**
(thin N + bad-print/tail contamination): a 1–2-trade tail must never read `favourable`. The
`edgeStandardError > 0` guard catches a single-entry-month cell (its clustered SE collapses to 0, making
the directional test trivially true). First worked example: [[2026-06-14-minervini-sector-rerate]] — net
`neutral`, the trim/concentration/sign guards held minervini's two thin "winner" sectors (XLU 8.51/N=32,
XLP 7.94/N=52, both tail-carried) off a label, and 5 mid-cyclical cells flipped sign once trimmed →
`unrateable`. The regime×sector and sector×regime cross views stay `unrateable` by construction (N fractures).

## Persistence

`strategy_exploration/assessments/<candidate>/` — request JSON (ADR 0017 pattern), assessment markdown,
append-only JSONL ledger (runs, C-annotation, decisions). The deflated-Sharpe trial count reads
dossiers **and** this ledger.
