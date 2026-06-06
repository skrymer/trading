---
type: entity
title: Baldr
summary: Value Zone Accumulation candidate — buy-the-dip near EMA20 in washed-out breadth. Rejected at /condition-screen, design-time — the differentiator is ANTI-PREDICTIVE.
status: stable
tags: [candidate, mean-reversion, rejected, design-time]
sources: ["strategy_exploration/dossier/baldr.jsonl", "strategy_exploration/STRATEGY_LEDGER.md"]
related: ["[[vz3]]", "[[mr3]]", "[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[the-funnel]]", "[[component-firewall]]"]
updated: 2026-06-06
---

# Baldr

**Value Zone Accumulation** — a long candidate built on the premise "buy the dip near EMA20 when
market breadth is washed out": 3-day higher-close accumulation *inside the value/discount zone*
(`consecutiveHigherHighsInValueZone(3, 2.0, 20)`) gated on `marketBreadthNearDonchianLow(0.15)`.
Accumulate in the discount zone — a mean-reversion-to-trend pullback buy, the timing *opposite* of a
breakout. Flagged a **known-weakness premise class** (dip-toward-EMA in uptrend) at draft.

## Status

❌ **REJECTED at `/condition-screen`** (design-time, 2026-05-31). **Not a [[component-firewall]]
death** — no config_hash was burned, no cross-candidate G13 brake engaged. A cheap early kill.

## Funnel history

Drafted 2026-05-31 from `ALTERNATIVE_STRATEGY_PROPOSALS.md`; screened the same day and abandoned. It
never reached `/strategy-screen` — the differentiator failed its design-time pre-screen outright.

## Why it was rejected

The differentiator is **anti-predictive** — `meanLift` is **negative at every horizon**, with the
signal large relative to its date-clustered SE:

| Horizon | meanLift | clustered SE |
|---|---|---|
| 5d  | −0.327% | 2.76× |
| 10d | −0.539% | 3.61× |
| 20d | −0.492% | 2.65× |

Hit-rate lift is negative too, and at ~42 sig/day across 1347 dates the anti-edge is **structural, not
lumpy** (fill gap negligible — the negative number is real). Every sweep cell
(consecutiveDays / emaPeriod / atrMultiplier / percentile) is **uniformly negative** — no passing cell
to promote, no sign-flip to alias on; more accumulation days makes the anti-edge *stronger*
(2d −0.324% → 4d −0.689% @ 10d). And it **fires most in down-tape** (0.80%) — exactly where it loses
most — with a SPY-regime sign-flip down→up at every horizon. Redesigning by dropping the SPY-down
tertile would be IS-fitting the gate to this screen's regime split ^[inferred] (the precise anti-pattern
for this class), so no rescue path exists; value-zone accumulation would need a structurally different
trigger (e.g. breadth *turning up*) as a fresh candidate.

## What it teaches / collides with

Baldr **collides STRATEGY_LEDGER §B-1** — the long-pullback / dip-buy premise, the same narrow-leadership
death documented for [[vz3]] and [[mr3]]. The screen confirmed that documented dip-toward-EMA failure
class directly. See [[long-premise-in-narrow-leadership]] and [[participate-and-lose]].

## Related

[[vz3]] · [[mr3]] · [[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[the-funnel]] · [[component-firewall]]
