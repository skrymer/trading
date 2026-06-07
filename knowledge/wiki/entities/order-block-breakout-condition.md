---
type: entity
title: Order-Block Breakout Entry Condition
summary: An order-block breakout entry condition. 50-sym sanity screen showed +0.211% 5d lift; the representative 300-sym screen showed NO lift — universe-size optimism that didn't replicate.
status: stable
tags: [condition, design-time, rejected]
sources: ["strategy_exploration/dossier/condition-orderblockbreakout.jsonl"]
related: ["[[the-funnel]]", "[[btc-tyr]]", "[[minervini-vcp-breakout]]", "[[lottery-vs-signature]]", "[[beta-delivery]]"]
updated: 2026-06-06
---

# Order-Block Breakout Entry Condition

An **order-block breakout entry condition** — `orderBlockBreakout(2, 5, 0)`: price breaking above a
recently-mitigated bearish order block and holding. Referred to by its type, not a god-name — per the
project convention, only assembled strategies are named; the bare alpha engine stays a condition. It is
the **event-trigger** form of the order-block alpha (vs the `aboveBearishOrderBlock` *state* filter),
isolated from the [[btc-tyr]] candidate (then Tyr's solo screen) to test whether the order-block leg
carries standalone forward-return lift decoupled from the breadth-recovery gate that starved Tyr's
sample to ~17 dates/yr.

## Status

❌ **REJECTED as a standalone alpha engine at `/condition-screen`** (design-time, 2026-05-31). **Not a
[[component-firewall]] death** — no config_hash burned, no brake; Tyr's order-block leg is exhausted as a
standalone premise. No strategy was assembled, so none was named.

## Funnel history — the two screens

1. **50-symbol orderBlock-subset sanity sweep** — the **only non-rejected condition of the session**.
   5d lift **+0.211% (1.84× SE)**, front-loaded (decays to ~0 at 10d, NEGATIVE −0.416% at 20d),
   positive hit-rate lift all horizons, and regime-consistent (5d same-sign positive across all 3 SPY
   tertiles — the cleanest of the four screens). Flagged FOR-FULL-RESCREEN; reduced-universe mode
   forbids promotion.
2. **300-symbol main subset** (survivorship-honest, sector-proportional) — **REJECT**. **NO detectable
   lift at any horizon**: 5d −0.036% (−0.71 SE), 10d −0.114% (−1.53 SE), 20d −0.066% (−0.56 SE) — all
   `|lift| < SE`, leaning negative. Firing 5.93%, ~63032 sig / ~5130 dates, ~12/day, not clustered ⇒
   broad, robust flatness. This screen **supersedes** the 50-sym positive.

## Why the 50-sym positive didn't replicate

The 50-symbol universe was **stratified on the order-block mechanism the condition trades**, so its
positive 5d edge was a stratified-sample + anti-conservative-SE artifact — the 1.84× SE was an *upper
bound*, not a real signal. On the representative universe, hit-rate lift stays mildly positive (+0.9 /
+0.7 / +1.0 pp) while meanLift is negative — an **adverse-skew non-edge** (strips right-tail winners,
keeps the left tail), unusable even as a filter. Jaccard vs `aboveBearishOrderBlock` is **0.074** — the
condition is structurally *distinct* from the VCP order-block state filter ([[minervini-vcp-breakout]]),
but distinct ≠ additive given no standalone edge.

## What it teaches

The headline lesson: **a `/condition-screen` on a small sanity universe can show false-positive lift —
the representative ~300-symbol universe is the faithful screen.** A subset selected on the very mechanism
under test is anti-conservative; treat any reduced-universe positive as `FLAG-FOR-FULL-RESCREEN`, never
as promotable. ^[inferred] And, as with [[baldr]] / [[fenrir]], a **design-time rejection burns no
config_hash** — it is a cheap early kill, distinct from a firewall death. The condition's front-loaded
profile (positive at 5d, negative by 20d) also implies any strategy wrapping it would need a *short-hold*
exit, not a VCP-style multi-week hold.

## Related

[[the-funnel]] · [[btc-tyr]] · [[minervini-vcp-breakout]] · [[baldr]] · [[fenrir]] · [[lottery-vs-signature]] · [[beta-delivery]]
