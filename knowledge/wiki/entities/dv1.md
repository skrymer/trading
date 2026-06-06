---
type: entity
title: DV1
summary: Bullish-divergence + EMA20 long candidate. REJECTED — near-miss Block A 2026-05-28: G1 CAGR 29.86% (0.14pp under the then-30% floor) + G6 2008 edge −0.48%. A long-pullback-MR rep.
status: stable
tags: [candidate, divergence, rejected, near-miss, long-pullback-mr]
sources: ["strategy_exploration/validation-candidates.md", "strategy_exploration/v4_block_a_results.md", "strategy_exploration/validate-DV1.md"]
related: ["[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[component-firewall]]", "[[vz3]]", "[[idunn]]", "[[mr3]]"]
updated: 2026-06-06
---

# DV1

## Premise

**Bullish reverse-divergence in a confirmed uptrend.** Take a long entry when price has carved a
higher low *while sector breadth diverged lower* — the divergence — and price sits above its 20-day
EMA. Inline `script` (2-of-2 with `marketUptrend`): `priceLowNow > priceLowThen` (recent 5-bar low
above the 5-bar low ~15 bars back), `sectorBreadthNow.bullPercentage < sectorBreadthThen` (breadth
fell over the same window), `close > closeEMA20` (trend confirmation). Exit on +12% gain OR a
20-day-EMA break, with a 2.5×ATR stop. `SectorEdge` ranker (sector-priority, no tightness tiebreaker).

A rep of the deprecated **long-pullback mean-reversion** class (see
[[long-premise-in-narrow-leadership]]) — a divergence-flavoured higher-low buy — alongside [[mr3]],
[[vz3]], and [[idunn]]. It already includes `marketUptrend` natively, so it is **not** a "needs a
regime filter" candidate.

## Status

**REJECTED — near-miss at Block A, 2026-05-28.** Tracked as a *near-miss* (not advanced to
`/validate-candidate`) because both misses were tiny and the profile was otherwise strong, but the
canonical verdict is REJECTED: it cannot pass the strict v4 Block A unmodified, and advancing it would
have required relaxing gates — which the quant explicitly warned against. The entry is an inline
`script`, so it would be TRADABLE-PENDING-PROMOTION even on a pass.

## Funnel history

- Surfaced on the post-V13 cleaned universe; documented as a near-miss on **full Block A** (12 OOS
  windows 2003-2014, post-V13 universe). Not run through the 2005-2015 `/strategy-screen` as a firm
  survivor — added to the roster (2026-05-28) specifically as a near-miss against the strict v4 gates.

## Verdicts

| Stage | Range | Verdict | Failing gates | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| Strict v4 Block A (canonical) | 2003-2014 | **REJECTED (near-miss)** | G1 CAGR, G6 2008 | 29.86% | 14.41% | 2.14 | 2.07 | 1,739 |

- **G1 CAGR 29.86%** — vs the **30% floor in force at the time**; a miss by just **0.14pp**.
- **G6 2008-GFC mandate** — 2008 OOS edge **−0.48%** (broke even rather than strictly positive during
  the GFC; 11.59% DD, 1.4× median, comfortably inside G3's 2× ceiling — bounded, not a blowup).

> The CAGR floor was **30% at the time of this run (2026-05-28)** and was **lowered to 25% on
> 2026-06-05** (memory `feedback_min_cagr_tradable`). DV1's 29.86% was a near-miss under the *then*
> 30% floor; under today's 25% floor G1 would pass — but G6 (2008 −0.48%) is independent of the CAGR
> floor and remains a binding failure, so DV1 is not retroactively rescored as a pass. ^[inferred]

> ⚠ Source reconciliation (run reports disagree): the standalone `validate-DV1.md` firewall report
> (2026-05-28T07:06) shows a **different** Block A run — first failure G1 at **CAGR 23.31%**, DD
> 24.35%, 2008 edge **−1.35%**, 1,607 trades — and a `minimumPrice ≥ 5` re-fire in
> `v4_block_a_results.md` failed G3 (CAGR 17.27%). The **near-miss numbers (29.86% / −0.48% / 1,739
> trades)** in `validation-candidates.md` match the canonical ledger and are treated as authoritative
> here; the lower-CAGR runs are earlier/variant fires under different universe or price filters.
> ^[ambiguous]

## Why it died

DV1 *almost* clears — **8 of 10 strict v4 gates pass** (Sharpe 2.14, Calmar 2.07, Sortino 3.36 all
well above threshold; 10/12 windows positive). It dies on two thin but real edges:

1. **G1 CAGR 0.14pp short** of the deliberately-set 30% floor. The fix (a small sizer bump, e.g.
   1.30% vs 1.25% risk) was explicitly flagged as **not** to be done — scraping over a floor after
   seeing the OOS result is data-snooping, and it would also widen DD.
2. **G6 2008 −0.48%** — even with `marketUptrend` filtering, the bullish-divergence entry can't
   profit during the GFC; the higher-low-vs-falling-breadth signal likely fired into 2008's false
   bottoms. This is a *structural* GFC weakness, not a tuning miss.

The two misses are independent (one CAGR, one regime mandate), so DV1 needs a **full re-design, not an
iteration** — per the firewall, modify-and-re-run is data-mining. The recommended revival paths were a
stricter 2008-style regime guard re-surveyed via `/strategy-screen`, or a deliberate 4-cell sizer
sweep — neither was pursued.

## Failure modes hit

- **G6 crash-survival failure** — non-positive 2008 GFC edge; the divergence premise has no edge in
  crisis tape even behind a regime gate.
- **[[participate-and-lose]]** — class-level: a long-pullback rep that participates in regimes its
  premise can't harvest. ^[inferred]
- Class-level: a rep of [[long-premise-in-narrow-leadership]].

## Reusable findings

- **A native `marketUptrend` gate is not enough for G6.** DV1 already carried the regime gate yet
  still broke even in 2008 — so "add a regime filter" is *not* the remediation lever for a candidate
  that already has one; the GFC weakness is in the entry premise itself.
- **0.14pp under a floor is still a fail** — and the floor-scraping sizer tweak is data-snooping, not
  a fix. Near-miss ≠ relax-the-gate; pick a re-design path deliberately or drop it.
- **Bounded GFC drawdown ≠ GFC edge.** −0.48% / 11.59% DD in 2008 is "broke even", well inside G3's
  ceiling — good risk control, but G6 demands *positive* crisis edge, which DV1 lacks.
- A floor's history matters: under the **25% floor since 2026-06-05** the G1 leg would clear, but G6
  is independent and still binds — don't read the floor change as a revival. ^[inferred]

## Related

[[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[component-firewall]] · [[vz3]] · [[idunn]] · [[mr3]]
