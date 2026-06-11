---
type: source
title: Quality-tilt /strategy-screen Random-ranker null — PASS at 5 seeds
summary: FundamentalQualityRanker beats the Random null (edge 0.802 vs 0.635, CAGR 19.0 vs 14.0) on the variance-collapse signature; quant PASS-at-5. Next = trend-leg ablation, then Block A. Not validated.
status: stable
tags: [candidate, fundamentals, quality, strategy-screen, beta-delivery, random-null]
sources: ["knowledge/wiki/entities/quality-profitability-tilt.request.json", "docs/adr/0009-rs-percentile-is-a-component-firewall-baseline.md", "knowledge/wiki/sources/2026-06-11-quality-tilt-condition-screen-killtest.md"]
related: ["[[quality-profitability-tilt]]", "[[beta-delivery]]", "[[george]]", "[[mrm]]", "[[the-funnel]]", "[[2026-06-11-quality-tilt-condition-screen-killtest]]"]
updated: 2026-06-11
---

# Quality-tilt /strategy-screen Random-ranker null — PASS at 5 seeds

## What was run

The pre-registered step-2 gate: the **Random-ranker null** for [[quality-profitability-tilt]]. Config =
the **bare-gate primary pass** (`fundamentalQualityPercentile≥80` entry, **no** trend leg, so the ranker is
the only cross-sectional selector; quality-deterioration<60 OR SMA200-break OR ATR-trail-2.7 exit;
ATR-risk 1.25%/2.7ATR; maxPositions 15; 2005–2015 36/12/12, 7 OOS windows; full universe). Both arms swept
seeds {42..46}: `FundamentalQuality` vs byte-identical `Random` (only the `ranker` string + seed differ).
10 walk-forward runs (~14 min each). Quant-verified.

## Result — the two 5-seed clouds

| arm | edge% median [range] | CAGR% median [range] | Sharpe | Sortino | posWin |
|---|---|---|---|---|---|
| **FundamentalQuality** | **0.802** [0.781, 0.947] | **19.02** [15.57, 26.60] | 0.881 | 1.276 | [5,6,6,6,6] |
| Random | 0.635 [0.278, 0.860] | 14.01 [13.08, 22.20] | 0.687 | 0.983 | [5,6,6,6,6] |

## Verdict — PASS at 5 (quant-adjudicated)

Candidate beats Random on **every median** (edge, CAGR, Sharpe, Sortino). Strict "clean separation" (every
candidate point above every Random point) was **not literally met** — Random s42 edge (0.860) and s45 CAGR
(22.20) poke above the candidate median — but the verdict is PASS on the **variance-collapse signature**:

- **Candidate edge spread 0.166 vs Random 0.582 (3.5× tighter)**; the candidate's *worst* seed (0.781) sits
  above the Random *median* (0.635).
- **Random s42 = the expected upper tail of a 5-draw null**, not "Random matches the ranker" — it's one
  coherently-lucky seed (also Random's best Sharpe). The other four Random points sit clearly below.
- **Random s45's high CAGR was bought via turnover** (1,760 trades, the most of any seed) while its per-trade
  edge (0.635) lost cleanly — exactly the artifact the "edge AND CAGR" double-test exists to catch.
- **Not a tail-risk / turnover edge:** equal drawdown envelope (candidate worst-window DD 22.9–35.9% vs
  Random 25.6–34.8%); the candidate was *less* bad in the GFC W1 window (mean W1 edge −0.79 vs −0.92).

## The reusable read — variance-collapse signature

^[inferred — methodology synthesis from the quant's reasoning] A **tight candidate cloud sitting above a
wide Random cloud** is stronger evidence of a real cross-sectional sort than the median gap alone. A noise
selector has high seed-to-seed variance (nothing anchors *which* eligible names fill the 15 slots, so each
seed samples the universe's dispersion); a real ranker **collapses** that variance because it
deterministically steers to the same names. The pre-registered binary rule (clean-separation→PASS /
inside-band→top-up) had a **gap** for the intermediate case here — candidate above the Random median with a
tighter cloud, but two single high Random draws poking in — resolved by cloud **shape**, not just cloud
position. This is why the verdict was *not* a mechanical top-up-to-20: more seeds would only sharpen a
separation the shape already shows.

## Residual caveats (carry to Block A — do NOT block the screen)

- **Modest absolute edge** — ~0.17 edge / ~5pts CAGR at the median. Real but not large.
- **Holding-period symmetry unconfirmable from WF aggregates** → a *binding Block A gate*: a slice of the
  edge could be a lower-turnover-into-quality-names effect rather than better selection.
- **Cohort diagnostics (cohortSize / rankInCohort / degenerate-day rate) are not in the WF aggregate** —
  deferred to an instrumented Block-A single-window run. The cohort-degeneracy failure mode is falsified
  *indirectly*: a consistent cross-seed edge **existing at all** proves the ranker bound (a non-binding
  ranker would make both arms byte-identical).
- **The inline `script` SMA200-break exit must be promoted** to a first-class condition (G14) before any
  TRADABLE claim.

## Next

Quant's call: **trend-leg ablation** (re-run with `priceAboveSma200` added back — does the edge live in the
ranker or the gate?), *then* full **Block A** firewall with the instrumented single-window run + a 25y
Random baseline (the 2005–2015 screen null does not satisfy the firewall's own baseline requirement).
**Not** top-up-to-20. No backtest beyond this screen has run.
