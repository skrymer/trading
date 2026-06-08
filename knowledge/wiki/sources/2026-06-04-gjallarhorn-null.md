---
type: source
title: Gjallarhorn conditional within-regime NULL (2026-06-04)
summary: The 20-seed conditional within-regime NULL that confirmed Gjallarhorn's +2.19% edge is timing alpha (+22σ), not crisis beta.
status: stable
tags: [run, null-test, timing]
sources: ["strategy_exploration/dossier/"]
related: ["[[gjallarhorn]]", "[[lottery-vs-signature]]"]
updated: 2026-06-05
---

# Source summary — Gjallarhorn timing-alpha NULL (2026-06-04)

**What was run.** The conditional random-entry-timing NULL (quant-specced) for [[gjallarhorn]]: 20 seeds,
entries drawn from the same comparable-stress population (`breadthPercent ≤ 25`) at matched firing rate
(P_FIRE = 0.1486 = 81/545), all else byte-identical (Random seed 42, 5%×20 sizer, same exits, 300-sym,
2000-2026).

## Headline numbers

| Metric | Gjallarhorn | Null (20 seeds) | Verdict |
|---|---|---|---|
| Entry dates | 81 | ~76 (matched) | rate-matched ✓ |
| **Per-trade edge** (primary) | **+2.19%** | mean −0.17%, sd 0.11, max −0.005% (all 20 negative) | **+22σ, ≫ p95** ✅ |
| Blended CAGR (confirmatory) | +2.39% | median −0.78% (all 20 negative) | > null median ✅ |

## What it taught

- **Timing alpha, not crisis beta.** Random same-regime dip-buying *loses* per trade (−0.17%, catching
  falling knives); Gjallarhorn's sustained-washout-then-recovery timing makes +2.19%. This is the textbook
  signature in [[lottery-vs-signature]]. The bare-mask (buy every breadth ≤ 25 day, 7702 trades) was only
  +0.11%/trade — corroborating.
- **Standalone CAGR is low *because* it's cash most of the calendar** — the overlay-component profile, not
  a standalone strategy.
- Confirms the **conditional within-regime null** is the right gate for *timing* candidates (uniform-random
  would have proved only crisis beta).

## RNG-reproducibility (2026-06-08, #135 footnote — closed)

This null does **not** share George's #130 `RandomRanker` reproducibility bug: it is a random-entry-*timing*
null (random entry **days** from the `breadthPercent ≤ 25` population at matched rate), not a random-*ranker*
baseline. `RandomRanker` only orders simultaneous candidates — it cannot generate entries — and `randomSeed`
plumbs only to the ranker / tie-break paths, never into `script` conditions. So the #130 fix is orthogonal
to this null's code path. The null's own seeding mechanism is **unverifiable from the repo** (the run config
was in the retired, uncommitted Gjallarhorn dossier). Stakes are low: Gjallarhorn is SHELVED, and the verdict
is robust to any RNG concern — all 20 seeds negative + a cluster-free bare-mask corroboration means a
reproducibility flaw could move the exact σ but cannot flip a uniformly-negative null positive. **If
Gjallarhorn is ever un-shelved, its null must be re-run with a deliberately-seeded, repo-persisted mechanism
(ADR 0017; cf. [[2026-06-08-george-random-revalidation-prereg]]).**

## Pages updated

[[gjallarhorn]] (verdict table + NULL result), [[lottery-vs-signature]] (added the +22σ signature
example), [[crisis-timer-cadence-ceiling]] (instance link).
