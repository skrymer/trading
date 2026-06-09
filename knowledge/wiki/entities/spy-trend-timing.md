---
type: entity
title: SPY Trend-Timing (leveraged)
summary: Time SPY in/out on a trend filter and lever it; REJECTED at screen 2026-06-10 — best Calmar 0.341 (2.4x SPY) but ~4.4x short of 1.5, and 5.82% base CAGR too low to lever (2x net-negative).
status: stable
tags: [candidate, market-timing, single-instrument, leverage, rejected]
sources: ["knowledge/wiki/sources/2026-06-10-leverageable-calmar-spy-timing-screen.md"]
related: ["[[beta-delivery]]", "[[participate-and-lose]]", "[[regime-conditional-portfolio]]", "[[gjallarhorn]]", "[[2026-06-10-leverageable-calmar-spy-timing-screen]]", "[[purpose]]"]
updated: 2026-06-10
---

# SPY Trend-Timing (leveraged)

Trade **SPY itself** (not a leveraged ETF), in/out on a trend filter — long when SPY is in an uptrend,
cash on the downtrend — and lever the result via engine `leverageRatio` (analytically, given the
cost-free-leverage gap). The operator's proposal to test the **leverageable-Calmar** reframe on a single
instrument: if a SPY timer's tail-truncation lifts Calmar above the absolute bar, lever its modest CAGR
to the 25% target.

## Status

**REJECTED at screen, 2026-06-10.** A structurally clean candidate that fails on the absolute-Calmar bar
*and* on base-CAGR-too-low-to-lever. Run + full adjudication: [[2026-06-10-leverageable-calmar-spy-timing-screen]].

## Screen (5 variants, unlevered, SPY-only, 2000–2025, 100%-equity)

| Variant | trades | CAGR | maxDD | Calmar | vs SPY |
|---|---|---|---|---|---|
| EMA200-in / EMA50-out | 185 | 3.72% | 24.8% | 0.150 | 1.1× |
| EMA200-in / EMA20-out | 310 | 2.10% | 23.8% | 0.089 | 0.6× |
| EMA200-in / ATR-trail | 86 | 3.72% | 33.5% | 0.111 | 0.8× |
| **EMA200-gate / 20-50 crossover (slow)** | **37** | **5.82%** | **17.1%** | **0.341** | **2.4×** |
| EMA200-in / 10d-confirmed EMA50 | 46 | 6.22% | 28.9% | 0.215 | 1.5× |
| *SPY buy-and-hold* | — | 7.85% | 55.2% | *0.142* | — |

Fast exits (EMA20/50) **whipsaw** (185–310 trades, CAGR crushed to 2–3.7%) → Calmar ≤ SPY: fast timing is
anti-productive. The slow crossover (37 trades) is the best and pins the ceiling.

## Why it died

1. **Absolute-Calmar wall.** Best Calmar **0.341** is ~4.4× short of G15's 1.5 (and 6× short of the
   leverageable-2.0). The single-instrument-timer ceiling is ~0.34 — far below tradable.
2. **Base-CAGR-too-low-to-lever wall (independent).** Best base CAGR **5.82%** can't be levered up: at 2×
   the cost stack (≈4.5% financing on the borrow + vol-drag + ~2% options friction ≈ 7.5%) **exceeds** the
   leverage gain, netting ~4% — *worse than unlevered*. Below ~7–8% base CAGR leverage is net-negative;
   reaching 25% would need ~5–6× (past the ≤2× appetite, amplifying the 17% DD to ~85%). See
   [[regime-conditional-portfolio]] (Calmar-leverage-invariance, the un-leverageable-Calmar wall).

## What it confirmed (durable capital — the result is valuable either way)

- **The structural escape from [[participate-and-lose]] is REAL.** Single-instrument timing has no
  cross-sectional unit below the gate for a loss to hide in — the edge and the gate live at the *same*
  market-timing level — so participate-and-lose is *structurally inexpressible*. V4's Calmar 0.341 = **2.4×
  SPY** is genuine tail-truncation alpha (beta is pinned at SPY's verified 0.142 and can't reach it), proving
  the escape works. It just isn't *enough* alpha.
- **It pins the single-instrument-timer Calmar ceiling at ~0.34** — a number the funnel lacked. Any future
  market-timing candidate inherits this as the bar a trend filter alone clears.
- **It escapes the earned-dead leveraged-ETF-timing class's data-span death** (SPY adjusted closes to 1995,
  full Block A, no ETF decay) — but inherits its regime-fragility, and adds the base-CAGR wall.
- A market-timing premise that *could* be tradable needs a **higher base CAGR** (so ≤2× leverage is
  net-positive) AND a Calmar nearer 1.5 — trend-timing SPY delivers neither. A convex/asymmetric timer
  ([[gjallarhorn]]-like) has the high-Calmar half but is cadence-blocked.

## Related

[[beta-delivery]] · [[participate-and-lose]] · [[regime-conditional-portfolio]] · [[gjallarhorn]] ·
[[2026-06-10-leverageable-calmar-spy-timing-screen]] · [[purpose]]
