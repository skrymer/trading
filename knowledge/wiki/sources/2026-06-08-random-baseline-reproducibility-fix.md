---
type: source
title: Methodology fix — the G-RANDOM Random baseline was non-reproducible (2026-06-08)
summary: RandomRanker used an unseeded RNG, so the G-RANDOM baseline wasn't reproducible even with a pinned randomSeed; fixed in #130. George's lost-to-Random reclassification was re-run on the seeded baseline in #135 and HELD (deprecation affirmatively re-confirmed).
status: stable
tags: [methodology, g-random, random-baseline, bugfix]
sources: ["https://github.com/skrymer/trading/issues/130", "https://github.com/skrymer/trading/issues/135", "udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/strategy/StockRanker.kt"]
related: ["[[beta-delivery]]", "[[george]]", "[[2026-06-08-george-random-revalidation-prereg]]", "[[thinning-not-selecting]]", "[[gjallarhorn]]", "[[the-funnel]]"]
updated: 2026-06-08
---

# Methodology fix — the G-RANDOM Random baseline was non-reproducible (2026-06-08)

## What was wrong

The mandatory **G-RANDOM** gate — a permissive-entry / ranker-selects candidate must beat a
**byte-identical Random-ranker baseline** on blended CAGR *and* per-trade edge (see [[beta-delivery]],
[[thinning-not-selecting]]) — assumed a *reproducible* baseline. It wasn't. `RandomRanker.score()`
called an **unseeded** global `kotlin.random.Random`, re-rolled every run. The request's `randomSeed`
seeded only the engine's **1e-10 tie-break jitter**, which the 0–100 random score dwarfs — so the
ranker's ordering was never seed-controlled. Two "same-seed" Random runs produced different fills, and
no multi-seed Random *distribution* (the per-window p95 a fair null needs) was actually reproducible.

## The fix (#130, commit `390e6ad`)

`RandomRanker(seed)` now scores a **deterministic** function of `(seed, symbol, date)` —
order- and thread-independent, stable across runs and JVMs. `RankerFactory.create` threads the request
`randomSeed` through, both `BacktestController` call sites pass it, and `description()` records the seed
(or flags `unseeded`) so a persisted firewall report attests which null it was compared against. A null
seed preserves the legacy non-reproducible draw for ad-hoc use. The catalog's long-standing "Use with
`randomSeed` for reproducible runs" description is now actually true.

## Implications for past verdicts

- **[[george]] — load-bearing.** Its reclassification from "[[participate-and-lose]]-in-crisis, build a
  defended successor" → "capped premise, anchoring class deprecated, no successor" rested **entirely** on
  the lost-to-Random comparison (the spec pinned seed 42 — intent was reproducible, engine wasn't). That
  reclassification was **re-run with the seeded baseline** (#135 → [[2026-06-08-george-random-revalidation-prereg]])
  as a 17-draw distribution and **HELD — deprecation affirmatively re-confirmed** (George's CAGR below the
  entire Random cloud, K=0/7 windows). George's **independent `/strategy-screen` FAIL** (G2 Sharpe 0.14,
  G4 GFC drawdown 44.7%) does **not** touch the Random baseline and stands — **George remains non-tradable
  either way**.
- **Leveraged-ETF / Heimdall — not load-bearing.** The "Random tied the smart rankers → selection adds
  nothing" finding was a *generalization*; those candidates were already REJECTED on independent
  CAGR/dispersion gates. The reproducibility bug softens the generalization, not the rejections.
- **[[gjallarhorn]] — lower-stakes open question.** It PASSED via a *conditional within-regime null*
  (20 seeds, +22σ), not the standard Random ranker. **If** that null draws its random entry-days through
  the same RNG path it may share the reproducibility risk — worth a check, but a +22σ pass is far less
  fragile than George's marginal loss. ^[inferred]

## Open question (the #130 premise this un-blocked)

The fix was required to settle **#130**: does a **factor-neutral idiosyncratic-RS** premise survive
narrow-leadership tape? #130 shipped a new `MarketResidualMomentum` ranker — single-factor
SPY-beta-stripped residual momentum (NOT raw price momentum, NOT 52-week-high anchoring, i.e. NOT
[[george]]) — to be screened vs the now-reproducible Random baseline, with the discriminator being
per-window edge > Random p95 *persisting in 2021–2023 / 2024*. A positive result is clean, a null only
weakly conclusive (single-factor is the strongest neutralization available without a Fama-French factor
series). **Resolved (2026-06-08):** [[mrm]] was screened and **REJECTED** — it lost to the seeded Random
baseline on edge AND CAGR (anti-selective [[beta-delivery]]); Stage 1 not run. The single-factor recipe is
crossed off; the class stays untested (multi-factor recipe = #137). See [[2026-06-08-mrm-screen-reject]] and
[[long-premise-in-narrow-leadership]].

## Related

[[beta-delivery]] · [[george]] · [[thinning-not-selecting]] · [[gjallarhorn]] · [[the-funnel]]
