---
type: entity
title: MR3
summary: Mean-reversion-proper long candidate (3-day pullback + up-day in an uptrend). REJECTED Block A 2026-05-28 on multi-dimensional drift — three tight failures (G3/G4/G5). A long-pullback-MR rep.
status: stable
tags: [candidate, mean-reversion, rejected, long-pullback-mr]
sources: ["strategy_exploration/MR3_STRATEGY_DEVELOPMENT.md", "strategy_exploration/validate-MR3-s1.md", "strategy_exploration/v4_block_a_results.md", "strategy_exploration/validation-candidates.md"]
related: ["[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[component-firewall]]", "[[vz3]]", "[[idunn]]", "[[dv1]]"]
updated: 2026-06-06
---

# MR3

## Premise

**Mean-reversion proper.** Take a long entry on the first up-day after a 3-day pullback in an
established uptrend: `close[-3] > close[-2] > close[-1]` (cumulative 3-day decline), `closeEMA20 >
closeEMA50` (uptrend intact), `close > open` (entry-day green candle = reversal). Gated by the
predefined `marketUptrend` market-regime condition. Exit on held ≥ 3 days OR +6% gain, with a
2.5×ATR stop. `Volatility` ranker (highest ATR/close first — more volatile stocks revert farther).

Explicitly the **inverse** of [[vz3]] by intent: VZ3 is trend-following with MR-style timing; MR3 is
MR proper, designed to win in high-volatility tape (its best window was 2008) where VZ3 is defensive.
Both are reps of the deprecated **long-pullback mean-reversion** class (see
[[long-premise-in-narrow-leadership]]), alongside [[idunn]] and [[dv1]].

## Status

**REJECTED — Block A, 2026-05-28.** Config burned for the firewall; modifying-and-re-running would be
data-mining, not validation. Never reached Block B. The recommended three-lever re-design was a
*separate* candidate (provisionally "MR4"), never built. The entry pullback and the hold-or-target
exit were both **inline `script` conditions**, so even a hypothetical firewall pass would have been
TRADABLE-PENDING-PROMOTION until promoted via `/create-condition`.

## Funnel history

- `/strategy-screen` (2005-2015, 7 OOS windows) — **passed all 5 screen gates.** Seed-invariant
  (s1/s2/s3 bit-identical, so effectively one candidate). Best seed s1: edge 0.28% / Sharpe 2.29 /
  **CAGR 36.77%** / MaxDD 17.70% / Calmar 2.08. A firm survivor (screen pass + CAGR ≥ 30%).
- `/validate-candidate` Block A (2000-2014, 11 OOS windows) — **FAIL.** Three tight gate failures →
  [[component-firewall]] REJECTED-with-drift bucket.

## Verdicts

| Stage | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| Screen (s1) | 2005-2015 | PASS (all 5) | — | 36.77% | 17.70% | 2.29 | 2.08 | 2,899 |
| Firewall Block A | 2000-2014 | **REJECTED** | G3 worst-window DD | 43.83% | 20.47% | 2.58 | 2.14 | 4,830 |

> ⚠ Note: an earlier v4-sweep run of MR3 (`v4_block_a_results.md`, 2000-2015 12-window) failed first on
> **G5 CoV** (CAGR 40.59%, single-window dominance from a W5-2007 +72% edge outlier), and a
> `minimumPrice ≥ 5` re-fire failed G2 (CAGR 20.56%). The canonical rejection is the 2026-05-28
> 11-window Block A run below; the sweep runs are consistent in verdict (FAIL) but differ on which
> gate trips first. ^[inferred]

## Why it died

The 11-window Block A run posted **three tight failures simultaneously** — the signature of
**multi-dimensional drift** rather than a single fixable flaw:

| Gate | Value | Threshold | Tightness |
|---|---|---|---|
| G3 worst-window DD | 20.47% | ≤ 20% | 2.4% relative (in the 5% band) |
| G4 positive pct | 8/11 = 72.7% | ≥ 75% | off-by-one (needed 9 of 11) |
| G5 CoV of edge | 1.77 | ≤ 1.5 | 18% relative (in the 20% band) |

[[component-firewall]]'s NEAR_MISS bucket caps at **2** tight failures; 3+ falls into
REJECTED-with-drift. Per the quant the pattern reads as *"systematically slightly-off-mandate across
multiple dimensions"* — structural, not iterational.

Three negative-edge windows drive it: **W4 2006** (edge −0.28%, and the 20.47% DD that caps G3),
**W8 2010** (−0.37%), **W9 2011** (−0.74%, CAGR −5.52%). All three are **low-volatility / range-bound
tape** (2006 chop, 2010-2011 EU-debt-aftermath chop) — exactly the regime where MR dips aren't deep
enough to revert. The thesis works where it was designed to (2003 +116% CAGR / +1.21% edge; 2008
GFC +1.19% edge) and bleeds in quiet chop.

## Failure modes hit

- **[[participate-and-lose]]** — keeps firing in trendless low-volatility chop where the MR premise
  has no edge, instead of standing aside; the regime gate (`marketUptrend`) is too coarse to suppress
  these windows. ^[inferred]
- **Multi-dimensional drift** — three correlated tight failures (drawdown + consistency + dispersion)
  rather than one axis, the firewall's structural-rejection signature.
- Class-level: a rep of [[long-premise-in-narrow-leadership]] — the long-pullback-MR death surface.

## Reusable findings

- **MR-proper is volatility-regime-coupled.** Best window 2008 (peak volatility); worst windows are
  the quiet 2006 / 2010-2011 chops. The edge is real but **regime-conditional**, so a flat long-only
  standalone fails the consistency gates (G4/G5) by construction. ^[inferred]
- **A coarse breadth-EMA `marketUptrend` gate does not rescue MR in chop** — DD and negative-edge
  windows persisted despite it.
- **Seed-invariant under the `Volatility` ranker** — a deterministic-score ranker makes the
  multi-seed screen redundant (one effective run); don't spend three seeds on it.
- **W1 2003 outlier flagged** (116% CAGR / +1.21% edge / 5.69% DD) — the post-dot-com bottom is MR's
  ideal regime; worth confirming such windows aren't fixture-driven on a few mega-winners.

## Related

[[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[component-firewall]] · [[vz3]] · [[idunn]] · [[dv1]]
