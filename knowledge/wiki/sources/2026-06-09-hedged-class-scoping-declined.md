---
type: source
title: Structurally-hedged 6th premise class — scoped then DECLINED on CAGR appetite (2026-06-09)
summary: Quant scoped a beta-hedged 6th class (earnings-surprise dispersion spread, backtestable as a synthetic NAV, zero engine change); operator DECLINED — 25% CAGR firm, no market-neutral sleeve.
status: stable
tags: [premise-class, market-neutral, scoping, quant-consult, appetite]
sources: ["knowledge/wiki/sources/2026-06-09-pead-premise-class-adjudication.md", "strategy_exploration/dossier/"]
related: ["[[pead]]", "[[beta-delivery]]", "[[purpose]]", "[[2026-06-09-pead-premise-class-adjudication]]", "[[2026-06-09-ovtlyr-plans-assessment]]"]
updated: 2026-06-09
---

# Structurally-hedged 6th premise class — scoped, then DECLINED

The [[2026-06-09-pead-premise-class-adjudication|PEAD adjudication]] steered the 6th premise class toward
a **structurally beta-hedged** premise (net the SPY-direction component out *in the P&L*, not at entry),
since the durable lesson of five dead families is that beta enters through the holding-window return. The
quant scoped it; the operator **declined it on appetite grounds.** Recorded here so the scoping IP (esp.
the synthetic-spread engine insight) survives and the decline is not re-litigated.

## The quant's scoping (the part worth keeping)

Three candidates, ranked by minimum engine spend:

1. **Cross-sectional earnings-surprise dispersion spread** (top pick) — long top-decile / short
   bottom-decile by EPS surprise, traded as **one synthetic dollar-neutral NAV**. Key engine insight: a
   precomputed spread NAV is itself a price series, so ingesting it as a "symbol" lets the **current
   long-only engine** backtest "go long the spread" with **zero engine change** —
   `profit = spreadClose_exit − spreadClose_entry` *is* the dollar-neutral P&L, and `/condition-screen`'s
   close-to-close forward return *is* the hedged return (read **inverted**: a neutral spread should be
   **regime-invariant** across SPY tertiles; a monotone tilt = disguised long/short). Resurrects PEAD's
   untouched diffusion mechanism in the form that nets the holding-window beta out. Data already GREEN
   (reuses PEAD's earnings/`surprise`/OHLCV). Null = a **random-decile-spread** baseline, not SPY
   buy-and-hold; G16 inappropriate for the class.
2. **Event-conditioned single-name vs sector-ETF pair spread** — redeems the #143 sector idea by trading
   the sector hedge over the whole holding window (low-medium spend; needs sector-ETF OHLCV).
3. **Cointegration stat-arb** — rejected: needs a true short-side engine (signed `Trade.direction` +
   sign-aware P&L + borrow cost) *and* a pair-selection multiple-testing surface the funnel would shred.

Engine fact (confirmed): the engine is **long-only, no signed position concept** (`Trade.kt` has no
`direction`/`side`; `profit`/`profitPercentage` long-convention). The synthetic-spread shortcut sidesteps
this for candidates 1-2; true short-side support is the big deferred spend, justified only *after* a
synthetic spread shows class-level alpha.

## Why it was declined — the CAGR-floor appetite call

A structurally market-neutral book is high-Sharpe / **low-CAGR** / ~zero-SPY-correlation by construction
and **fails the 25% CAGR floor (G1) unlevered.** The operator was offered the fork — (i) accept a
market-neutral tradability bar (Sharpe/Calmar-primary, CAGR-secondary), (ii) leverage to the floor
(likely infeasible within `LeverageCap`), or (iii) drop the class — and chose **"25% CAGR or nothing —
drop the class."** The 25% floor is a *return-rate* requirement net exposure must meet, **not** a
risk-adjusted-quality bar a hedged book could satisfy differently. (Operator appetite, not a domain call
— see memory *feedback-min-cagr-tradable*.)

## Disposition

The structurally-hedged class is **off the table** under current appetite. The 6th premise class must be
**net-long / directional and capable of ~25% CAGR**; do not re-propose market-neutral / dollar-neutral
spread sleeves however high their Sharpe. The binding wall ([[purpose]] — *is every long premise
structurally regime-beta?*) must be answered from the long side or via a regime-transition layer (#83),
not by hedging the exposure away. (The subsequent [[2026-06-09-ovtlyr-plans-assessment|OVTLYR plans]]
were evaluated as a possible directional 6th class and also did not supply one.)

## Pages updated

`index.md`, `wiki/log.md`, memory *feedback-min-cagr-tradable* (the appetite ruling).
</content>
