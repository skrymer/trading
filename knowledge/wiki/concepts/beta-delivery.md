---
type: concept
title: Beta-Delivery
summary: A long book whose risk-adjusted return is just the index's — profitable in absolute terms but no better than holding SPY; the failure mode G16 catches.
status: seed
tags: [failure-mode, methodology]
sources: ["docs/adr/0013-spy-buy-and-hold-is-a-binding-calmar-only-firewall-baseline.md", "strategy_exploration/FUNNEL_DEEPRESEARCH_FINDINGS.md", "strategy_exploration/GEORGE_STRATEGY_DEVELOPMENT.md"]
related: ["[[component-firewall]]", "[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[the-funnel]]", "[[george]]", "[[mrm]]", "[[2026-06-08-random-baseline-reproducibility-fix]]", "[[2026-06-08-mrm-screen-reject]]"]
updated: 2026-06-08
---

# Beta-Delivery

> **status: seed** — the failure-mode anatomy and both detectors are settled. The firewall's **G16**
> gate has not rejected a candidate yet; the **screen-stage random-ranker tell** has — [[george]]
> (2026-06-04). So the *Instances* section holds George (caught early, before the firewall), and is
> still awaiting its first G16-firewall rejection. The first G16 FAIL promotes this to `active`.

## Definition

A candidate **delivers beta** when its return is just *market exposure in disguise* — it makes money
in absolute terms, but no more than you'd have made (risk-adjusted) by **just holding SPY** over the
same days. The "alpha" is the index's drift, not the strategy's selection or timing. For a long-only
book this is the default null hypothesis, not an exotic failure: any always-long-ish premise inherits
SPY's bull-market return, so an attractive-looking CAGR proves nothing until it is measured *against*
the passive alternative.

## How to detect

The firewall's **G16 SPY buy-and-hold Calmar baseline** is the detector (see [[component-firewall]]):
strategy stitched-OOS **Calmar ≥ SPY's Calmar** over the *identical* OOS-stitched support, binding on
Block A + Block B + the 25y aggregate. A FAIL = the candidate's risk-adjusted return did not beat
holding the index → beta-delivery → REJECTED.

Why **Calmar**, and why **relative** to SPY:

- **Calmar, not Sharpe** — a part-in-cash long-only timer is structurally penalised on Sharpe in a
  low-vol bull block (its cash days drag the per-day mean while always-invested SPY sits ~1+). Calmar
  is neutral to sitting in cash and rewards crash-avoidance, so it isolates real selection/timing skill
  rather than time-in-market.
- **Relative, not absolute** — the absolute Calmar floor (G15) asks "is this tradable *quality*?"; G16
  asks "did it beat *the alternative you already have for free*?". A candidate can clear the absolute
  floor and still fail G16 — high absolute Calmar that is nonetheless ≤ SPY's over the same window is
  still just beta.^[inferred]

Adjacent, cheaper tells before the firewall: a permissive-entry + ranker-selects candidate that loses to
a byte-identical **random-ranker baseline** on blended CAGR *and* per-trade edge is delivering
entry-universe beta (the random-ranker-baseline rule); and a high *correlation*/`beta` to SPY in the
single-backtest `BenchmarkComparison` is a yellow flag that the eventual G16 read may confirm.

> **Reproducibility caveat (2026-06-08).** The random-ranker baseline must be *reproducibly seeded* to
> mean anything. Until #130 the engine's `RandomRanker` scored via an **unseeded** RNG (the `randomSeed`
> fed only the 1e-10 tie-break jitter), so every "lost to Random" read — including [[george]]'s — was
> not actually reproducible. Fixed in #130: `RandomRanker(seed)` now scores deterministically per
> `(seed, symbol, date)`, enabling the per-window p95 a multi-seed null needs. See
> [[2026-06-08-random-baseline-reproducibility-fix]]. **George's reclassification was re-run (#135) on a
> seeded 17-draw distribution and HELD — affirmatively re-confirmed:** George's CAGR fell below the entire
> Random cloud and it won 0/7 windows' tails. The seeded null *strengthened* the original single-point
> read rather than overturning it ([[2026-06-08-george-random-revalidation-prereg]]).

## Why it kills

A long-only book that can't beat the index, risk-adjusted, has **no reason to exist** — it adds
operational complexity, execution cost, and single-strategy risk to reproduce something an index fund
delivers passively at lower cost. Worse, beta dressed as alpha is *fragile in exactly the regime you
deploy it for*: when the market that supplied the beta turns (narrow-leadership tape, a drawdown the
timer doesn't dodge), the "edge" evaporates because there was no selection skill underneath it. This is
the structural cousin of [[participate-and-lose]] and the through-line of
[[long-premise-in-narrow-leadership]] — both are about long exposure that looked like skill until the
supporting regime withdrew.

## Why a sizer can't fix it

G16 is a statement about the *shape* of the return stream (Calmar = CAGR / |maxDD|) relative to SPY's
shape over the same days. A position sizer or exit tweak rescales the curve but cannot manufacture
SPY-relative risk-adjusted advantage that the *selection/timing* did not produce. A G16 FAIL therefore
demands a structurally different entry premise, never a sizer/exit iteration (the standard firewall
remediation discipline — see [[the-funnel]]).^[inferred]

## Instances

- **[[george]]** (2026-06-04) — the **screen-stage** instance, caught by the random-ranker tell, not
  G16. A 52-week-high anchoring ranker whose per-trade edge (+1.01%) was *matched* by a byte-identical
  Random-ranker baseline (+1.08%) and whose blended CAGR (+1.1%) was *beaten* by it (Random +6.6%) over
  2005–2015 — i.e. the ~1% "edge" was entry-universe beta, not ranker alpha (and the anchoring tilt was
  a worse-than-noise GFC liability, −14.3% vs Random's −2.1% in 2008). Deprecated at `/strategy-screen`
  via G-RANDOM before ever reaching the firewall, so it has **no G16 read** — it is the cheaper-tell
  confirmation that this failure mode is real, not a G16 rejection.

- **[[mrm]]** (2026-06-08) — the **second screen-stage** instance, and a *stronger* beta-delivery signature
  than George. A single-factor SPY-beta-stripped residual-momentum ranker on a neutral entry (so the ranker
  is the sole selection signal). It **lost** to a now-seeded byte-identical Random baseline on *both* legs:
  per-trade edge **+2.80% vs +6.21%**, blended CAGR **8.95% vs 23.05%** (2.2–2.6×), beaten head-to-head in
  5/7 windows, and it reproduced George's GFC liability (2008 window −12.93% vs Random −2.50%). Where George
  *matched* Random on per-trade edge (a no-information ranker), MRM *loses* on edge — an **anti-selective**
  tilt that systematically picks worse names than a random draw. First instance adjudicated against the
  reproducibly-seeded baseline (the #130 fix). Note the high absolute Random CAGR (23%) is **structural
  long-beta** of this long-only engine (cash-dodges 2008 → −8% vs SPY −38%, then equal-weight small-cap tilt
  rides the 2009 +58% / 2010 +53% rebound; SPY buy-hold was 9.86% over the same support) — *not* survivorship
  (the universe carries 1500+ delisted names) and *not* alpha; both arms share it, so the relative verdict
  holds. See [[2026-06-08-mrm-screen-reject]].

_No G16-firewall instance yet._ G16 was implemented in #102 (ADR 0013); no candidate has been rejected by
the **firewall** gate itself at the time of writing (2026-06-06). When one is, record it here with:
block(s) failed, `strategyCalmar` vs `benchmarkCalmar`, whether it nonetheless cleared the absolute floors
(G1/G2/G9/G15), and the regime in which its beta was sourced.

## Related

[[component-firewall]] · [[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[the-funnel]]
