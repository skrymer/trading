---
type: source
title: 2026-06-10 — Quality/profitability tilt scoping + design lock
summary: Quant proposed a cross-sectional quality tilt as the post-empty-funnel candidate; /grill-with-docs locked the architecture (ADR 0019); a 2nd quant consult locked the signal (GP/TA gate + ranker). Build = #150.
status: stable
tags: [source, scoping, design, fundamentals, quality]
sources: ["docs/adr/0019-fundamentals-are-point-in-time-reference-data-and-quality-is-a-cross-sectional-percentile.md", "https://github.com/skrymer/trading/issues/150", "https://github.com/skrymer/trading/pull/151"]
related: ["[[quality-profitability-tilt]]", "[[beta-delivery]]", "[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[purpose]]"]
updated: 2026-06-10
---

# 2026-06-10 — Quality/profitability tilt: scoping + design lock

> Scoping + architecture/signal lock — **not** a run. No backtest fired; the candidate
> ([[quality-profitability-tilt]]) is in build, build spec = GitHub issue #150, architecture = PR #151.

## What happened

With the funnel empty (5 classes + R1 + SPY-timing + B all dead), a `quant-analyst` consult was asked for
net-long directional entry premises with a *structural* narrow-leadership-survival mechanism. The headline:
the dead axis is **price-state-conditioned long exposure** — pullback, breakout, RS-momentum, PEAD all
select on where price has *been*, which in a strong tape is market direction, so they participate-and-lose
or deliver beta. The escape is to select on a **non-price, persistent cross-sectional variable** →
**fundamental quality / profitability**, the one factor whose killing regime (narrow leadership =
concentration into high-profitability mega-caps) is a **tailwind**.

A correction during scoping (now a memory + ADR-driving fact): **data availability is NOT a scoping
constraint** — the boundary is what EODHD can source, not what is currently ingested. A single EODHD probe
verified the fundamentals are feasible: quarterly Income-Statement + Balance-Sheet with a real
**`filing_date`** (distinct from fiscal-period-end, ~5-week lag), history to 1985, all needed line items
present.

## The two locks

- **Architecture** (`/grill-with-docs` → **ADR 0019** + CONTEXT.md *Point-in-time fundamentals*): L1 raw
  fundamentals = the **earnings** precedent (Midgaard-stored, shipped to Udgaard, `filing_date`-gated); L2
  quality percentile = the **ADR 0009** cross-sectional-indicator precedent (Midgaard operator-triggered
  pass, persisted per row, fail-closed on null); Midgaard-not-Udgaard boundary (ADR 0011); gate-vs-ranker
  split; single baked gate metric; restatement residual accepted + documented.
- **Signal** (2nd quant consult): gate = **Gross Profitability** `grossProfit_TTM / totalAssets_asof`,
  top-quintile percentile; ranker = `FundamentalQualityRanker` `0.5·z(GP/TA) + 0.5·z(margin-trend YoY)`,
  intra-subset; full candidate config + a **pre-registered flat-SPY-tertile kill-test** (the
  [[beta-delivery]] tell) + the Random-ranker null. ROE rejected (negative-equity mega-caps); composite
  declined for the *baked* gate (kept in the ranker, which iterates free). No new EODHD line item needed.

## What it taught (durable)

- A concrete answer to the binding open question ([[long-premise-in-narrow-leadership]], [[purpose]]): the
  candidate escape from the price-state-conditioned dead axis is a **non-price fundamental selector**, and
  quality is the one whose mechanism *benefits* from narrow leadership rather than fighting it.^[inferred —
  quant mechanism, pending the screen]
- The **most likely death is still [[beta-delivery]]**: if top-quality names *are* the Mag-7, the gate is
  the index in disguise (flat-tertile ≤ 0). The screen is built to catch exactly that, cheaply.

## Pages touched

Created [[quality-profitability-tilt]] (entity), this source. Updated [[overview]], [[purpose]],
[[index]]. Architecture recorded in ADR 0019 + the CONTEXT.md term (PR #151), build spec in issue #150.

## Related

[[quality-profitability-tilt]] · [[beta-delivery]] · [[long-premise-in-narrow-leadership]] · [[purpose]]
