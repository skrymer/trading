---
type: source
title: Quality-tilt trend-leg ablation — DROP the trend leg (tail-harmful), ranker edge confirmed
summary: priceAboveSma200 doubles drawdown + quadruples the 2008 loss (adverse selection); the ranker's +0.16 edge over Random is invariant — edge is the ranker not the gate. Drop the leg; bare gate → Block A.
status: stable
tags: [candidate, fundamentals, quality, strategy-screen, ablation, thinning-not-selecting]
sources: ["knowledge/wiki/entities/quality-profitability-tilt.with-trend.request.json", "knowledge/wiki/entities/quality-profitability-tilt.request.json", "knowledge/wiki/sources/2026-06-11-quality-tilt-random-null-screen.md"]
related: ["[[quality-profitability-tilt]]", "[[thinning-not-selecting]]", "[[beta-delivery]]", "[[2026-06-11-quality-tilt-random-null-screen]]", "[[the-funnel]]"]
updated: 2026-06-11
---

# Quality-tilt trend-leg ablation — DROP the trend leg

## What was run

Re-ran the Random-ranker null **with `priceAboveSma200` added back** to the entry (vs the bare-gate primary
pass), to localise whether the candidate's edge is the quality **ranker** or the trend **gate**. Same shape:
`FundamentalQuality` vs `Random`, seeds {42..46}, full universe, 2005–2015, 10 walk-forward runs. Config:
`quality-profitability-tilt.with-trend.request.json`. Quant-adjudicated.

## Result — bare gate vs with trend leg (medians)

| metric | bare gate | with trend leg |
|---|---|---|
| candidate edge | 0.802 | 0.965 ↑ |
| candidate CAGR | 19.02 | 16.16 ↓ |
| candidate trades | ~1634 | **~839 (halved)** |
| Random edge | 0.635 | 0.805 ↑ |
| Random CAGR | 14.01 | 12.02 ↓ |
| **edge GAP (cand−rand)** | **+0.168** | **+0.160** |
| aggregate max-DD | ~22–41% | **~43–53% (doubled)** |
| candidate 2008 window edge | −0.79 | **−3.56** |

## Verdict — DROP the trend entry leg (quant)

**The trend leg is worse than [[thinning-not-selecting]] — it is adverse selection in the tail.** Beyond
halving trades and lowering CAGR in both arms, `priceAboveSma200` **doubles aggregate max-drawdown and
quadruples the 2008 worst-window loss** (candidate 2008 edge −0.79 → −3.56, DD 28% → 39%). It is a
trend-following re-entry rule that **whipsaws into a sustained bear** — re-admitting names on each failed
rally cross above the 200-day, which then roll over into the 2.7-ATR stop. It adversely selects in the exact
regime a trend gate is sold to handle. There is no metric on which it helps. **Carry the bare-gate config to
Block A** (which keeps the SMA200 *exit* + ATR trail — trend risk-control on the exit side is retained; only
the *entry* gate is dropped).

## What it confirmed — the ranker edge is real and entry-construction-independent

The candidate-vs-Random **edge gap is invariant**: +0.168 (bare) ≈ +0.160 (trend). Measured against a
byte-identical Random baseline across two independently-constructed entry universes (1640-trade and
840-trade), the ~0.16/trade sort edge survives both. **The edge is the RANKER, not the gate** — the gate's
own contribution is *negative*. This gap-invariance is now the load-bearing evidence for the candidate, and
it re-grounds the [[2026-06-11-quality-tilt-random-null-screen|bare-gate Random-null PASS]] on firmer footing.

## Methodology correction — the variance-collapse signature is corroborating, not load-bearing

The bare-gate PASS leaned on a variance-collapse read (candidate seed-cloud tight 0.166 vs Random wide
0.582). The ablation **inverted** it: with the trend leg the candidate is WIDE (0.985, s46 outlier 1.559) and
Random is TIGHT (0.294). Seed-spread scales ~1/√trades, and gate-induced universe homogeneity compresses the
Random cloud — so the variance *ordering* is **not** a sample-size-invariant fingerprint of real-sort-vs-noise.
**General rule (quant):** a Random-null verdict rests on the **edge gap and CAGR gap** (direction + magnitude
vs the null cloud); the variance-collapse ordering is a **secondary corroborant**, valid only when trade
counts are matched between arms and read at a **stated N**. ^[inferred — this sharpens the existing
"random-ranker baseline is mandatory" rule by naming which statistic carries the verdict.] The bare-gate PASS
stands because it does not rest on the variance ordering.

## Next

Proceed to the **Block A firewall** with the bare-gate config (`quality-profitability-tilt.request.json`),
carrying the Random null. Watch-items: the 2008 + 2000–02 bear windows (the quality gate + deterioration
exit are the intended bear defense, not an entry trend gate); quality-data span back to 2000 already
verified (kill-test coverage gate). The inline SMA200-break *exit* script still needs G14 promotion before a
TRADABLE claim.
