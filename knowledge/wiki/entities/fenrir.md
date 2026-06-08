---
type: entity
title: Fenrir
summary: Sector Rotation Momentum candidate — binary "sector accelerating + beating breadth" gate. Rejected at /condition-screen — no detectable forward-return lift.
status: stable
tags: [candidate, rs-momentum, rejected, design-time]
sources: ["strategy_exploration/dossier/fenrir.jsonl"]
request: "fenrir.request.json"
related: ["[[thinning-not-selecting]]", "[[beta-delivery]]", "[[long-premise-in-narrow-leadership]]", "[[the-funnel]]", "[[component-firewall]]"]
updated: 2026-06-08
---

# Fenrir

**Sector Rotation Momentum** — a long candidate built on a binary sector-rotation gate: buy stocks in
sectors accelerating faster than the broad market (`sectorBreadthAccelerating(3.0)` AND
`sectorBreadthGreaterThanMarket()`) with emerging trend strength (adxRange 20–45, emaSpread). Active
relative-strength sector rotation, with no order-block alpha engine.

## Status

❌ **REJECTED at `/condition-screen`** (design-time, 2026-05-31). **Not a [[component-firewall]]
death** — no config_hash was burned, no cross-candidate G13 brake engaged.

## Funnel history

Drafted 2026-05-31 as a sector-rotation-momentum proposal; screened and abandoned the same day,
before reaching `/strategy-screen`.

## Why it was rejected

**No detectable edge** — `|meanLift|` is *smaller than its clustered SE at every horizon*, and the 20d
lift is **negative**:

| Horizon | \|meanLift\| vs SE | note |
|---|---|---|
| 5d  | 0.22× SE | |
| 10d | 0.40× SE | sign-flips across SPY tertiles |
| 20d | 0.83× SE | lift NEGATIVE (−0.076%) |

Firing ~25% with edge **diffuse across ~3900 dates** ⇒ genuine flatness, not lumpiness. `meanLift`
**sign-flips across SPY down→up tertiles** at 10d & 20d (down-negative / up-positive cancellation — the
narrow-leadership / mean-reversion cancellation signature). The threshold sweep (2.7 / 3.0 / 3.3) is
clean and monotone — but tuning a value that is statistically zero is no ARS, just a near-zero-lift
**universe filter, not a signal** ^[inferred].

## What it teaches / collides with

Fenrir **collides the cross-sectional RS-momentum rotation deprecated class**, the **diluted twin**:
the `SectorStrengthMomentum` ranker already operationalizes this exact mechanic, so the binary gate adds
no information beyond entry-universe beta ([[beta-delivery]]). A genuine sector-rotation pursuit would
need a structurally different premise (cross-sectional relative-momentum *rank*, or sector
leadership-*transition* anchoring) as a fresh candidate — not a Fenrir threshold tweak. See
[[thinning-not-selecting]] and [[long-premise-in-narrow-leadership]].

## Reproducing

The exact `/condition-screen` request that defines this candidate is persisted beside this entity at
**`fenrir.request.json`** (ADR 0017). Fenrir died at *design-time* `/condition-screen`, so its identity
is a **conditions-screen** request (`POST /api/conditions/screen`), not a walk-forward backtest — the
differentiator `sectorBreadthAccelerating(3.0) AND sectorBreadthGreaterThanMarket()` on the default full
`STOCK` universe over the standard screen window (2000-01-01 → 2021-01-01 leakage cap, `entryDelayDays` 1,
horizons 5/10/20):

```bash
API_KEY=… .claude/scripts/udgaard-post.sh /api/conditions/screen \
  @knowledge/wiki/entities/fenrir.request.json /tmp/condition-screen-fenrir.json
```

A clean screen is not a pass and a screened-out condition is rejected — re-running this only re-confirms
the no-detectable-lift read. The result is a *diagnostic* (lift / firing-rate / regime sign-flip), not a
verdict; pre-screening verdicts come from `/strategy-screen` and `/validate-candidate`.

## Related

[[thinning-not-selecting]] · [[beta-delivery]] · [[long-premise-in-narrow-leadership]] · [[the-funnel]] · [[component-firewall]]
