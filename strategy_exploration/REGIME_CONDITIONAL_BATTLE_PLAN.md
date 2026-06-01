# Regime-Conditional Battle Plan

_Created: 2026-06-01 · Status: APPROVED · **Active focus: Phase 1 (broad-rally timer). Phase 2 deferred — not designed yet.**_

A regime-conditional **risk-on/risk-off** system. The book is in exactly one regime
state at a time; we trade the bull regimes aggressively and sit in cash through chop
and crisis. This document is the strategic plan only — it fires no backtest. Each
component still runs the standard leaf-skill funnel (`/condition-screen` →
`/strategy-screen` / curve-track → `/validate-candidate` → `/monte-carlo`) with all
existing approval gates intact.

## Origin / framing

The exercise began as "is `market breadth >50% & rising` + `sector breadth >50% &
rising` a good *base* for a bullish stock-selection strategy?" That premise was
**abandoned**: a base that only fires when the whole market is rising is dominated by
SPY beta, and any measured "edge" risks being index correlation in disguise. The
useful survivor of that idea is breadth as a **regime detector** (an allocation
signal), not as a stock-selection entry filter.

This plan sits under the standing mandate: build a portfolio of regime-**specialist**
components, not one uber-strategy. Broad-rally is the regime where the natural
"specialist" is simply **leveraged beta** — so the deliverable there is a *timer*, not
a clever stock strategy.

## The four regimes

| Regime | Definition (signal family) | Stance | Component |
|---|---|---|---|
| **Broad rally** | market breadth >50% & rising, sector breadth confirming | **TRADE 3×** | Signal on SPY, **trade real SPXL/UPRO bars**; asymmetric fast-exit |
| **Narrow-leadership** | index up but breadth weak/flat (few mega-caps carry it) | **TRADE selective** | Leaders momentum / relative-strength selection; **no breadth-confirm gate** |
| **Chop** | range-bound, no breadth trend, whipsaw | **AVOID** | Cash |
| **Crisis / bear** | breadth collapsing, SPY < trend, beta negative | **AVOID** | Cash |

Two TRADE components, two cash states. Architecture: each component **self-gates via
its own entry conditions** (the "broad-rally condition stack" *is* the regime gate) —
no engine changes. A unified portfolio classifier / state-machine (mutual exclusion,
transition hysteresis, cash routing) is a **later overlay (Phase 3)**, added only once
both components prove out.

## Signal vs instrument: cap-weight vs equal-weight

A cap-weighting distinction runs through the whole plan; getting the roles straight
matters more than picking one index.

- **Detector (broad-rally on/off) = equal-weight, always.** Market breadth = *% of all
  STOCK-type symbols in uptrend* — one stock, one vote. That is the correct,
  cap-weight-immune tool for measuring *participation*. **Do not** confirm broad
  participation with a SPY-price gate (e.g. "SPY > EMA50"): SPY is cap-weighted, so a
  handful of mega-caps can hold it above its EMA while breadth rots underneath
  (2H-2021, much of 2023–24). If you want a price-based cross-check, use **RSP**
  (equal-weight S&P, history to 2003) — RSP rolling over means the *average* stock is
  rolling over. Carry RSP-vs-SPY divergence as an informational cross-check, not a gate.

- **Instrument = cap-weight, unavoidably.** There is no liquid 3× equal-weight ETF;
  SPXL/UPRO are 3× cap-weight SPY, TQQQ is 3× (even more concentrated) Nasdaq-100.
  Default to **UPRO/SPXL**; treat TQQQ as out-of-scope unless an NDX-specific breadth
  series is built. So the system **detects on equal-weight breadth but rides a
  cap-weight 3× instrument.** That mismatch is intentional: when SPY/SPXL keep climbing
  on mega-caps while breadth narrows, the breadth detector exits SPXL *at the
  participation peak* — which is exactly the hand-off to the narrow-leadership regime,
  not a bug.

- **Cap-weighting is *a* narrow-leadership vehicle, but probably not the best one.**
  SPY's cap-weighting is a passive momentum tilt — winners auto-gain weight — so in a
  narrow melt-up **SPY already holds the leaders, rebalanced for free.** That makes a
  3× SPXL-in-narrow play tempting *and* dangerous: the same auto-concentration loads you
  into the names with the most downside, and narrow tops break violently and correlated
  (one mega-cap miss unwinds the cohort) — brutal at 3×. The preferred Phase 2 direction
  is therefore a **stocks-based cross-sectional selector** that owns the *specific*
  leaders (relative-strength ranked, potentially beyond the S&P, less mega-cap-tech
  concentrated than SPXL). That is the only narrow play that genuinely *diversifies* the
  broad-rally timer rather than being more of the same cap-weight beta.

- **Collapse risk — breadth must change the *treatment*, not just the direction.** If
  *both* TRADE regimes simply go long SPXL, their union is "SPY in an uptrend" and the
  breadth signal stops affecting the trade decision — you've quietly built a single
  3×-SPY trend-timer with breadth as decoration. Breadth only earns its keep if narrow
  trades a **genuinely different instrument** (the stocks-based selector — preferred), or
  at minimum gets **different exposure** (a de-risked SPXL overlay — fallback only if the
  stocks selector doesn't pan out). The Phase 1 decomposition test (below) tells us
  whether the narrow regime is even a profitable place to be long, which informs that
  fallback.

## Component 1 — Broad-rally leveraged timer (PHASE 1)

The cheap, fast falsification: single instrument, clearest benchmark. Build and prove
this **first**; everything else is gated on it surviving.

**Mechanism (engine-native, no multiplier fiction).** The engine's
`StockPair(tradingStock, strategyStock)` split (`useUnderlyingAssets` +
`customUnderlyingMap`) generates signals on **SPY** but takes entry/exit prices and
P&L from **real SPXL bars** — actual volatility decay, financing drag, and gap
behavior are in the data. Leverage is therefore **never** a scalar multiplied onto a
1× curve.

**Signal design — asymmetric (slow in, fast out).** Breadth is coincident-to-lagging;
under 3× a late exit is the catastrophe case (a breadth-collapse exit firing on a
daily close after a -4% SPY day is a -12% SPXL day, with Monday gap risk on top).
- *Entry (competing candidates, decide empirically on SPXL MAR):*
  `MarketBreadthAbove(50) + MarketBreadthIncreasing(3)` (slow-confirmed) **vs**
  `MarketBreadthRecovering` (leadier early up-thrust, more whipsaw).
- *Exit (fast):* breadth-EMA cross-down / `BreadthEma10Above50` failing — exit faster
  than entry confirms.

**Validation track — CURVE-LEVEL, not the cross-sectional firewall.** A single-instrument
timer makes ~60–150 round-trips over 25y — too few per window for the firewall's
per-window CoV / edge-sign gates (forcing it there reproduces the "lottery-edge" trap
as a *test artifact*). The unit of evidence is the **equity curve**, judged on path
statistics, plus a **manual ±1 / ±10% G13 sweep** for ARS (the `/condition-screen`
auto-sweep perf-cliffs on `MarketBreadthIncreasing`, so the robustness check is manual).

**Go / no-go (split gate):**
- **1× SPY signal** (job = risk-adjusted timing; validated full 25y across Blocks A/B/C):
  - MAR (CAGR/MaxDD) ≥ **1.5×** buy-and-hold SPY MAR, each block
  - Max drawdown ≤ **60%** of B&H MaxDD
  - Sortino ≥ B&H Sortino
  - Time-in-market < **70%** (must actually be out during bad tape, not closet-long)
  - Edge present in **all three blocks** (block-level sign-stable; edge concentrated in
    1–2 blocks ⇒ lottery / regime-detector ⇒ reject)
- **3× SPXL execution** (the tradable artifact; ~2010→ data only — Block B partial incl
  COVID, Block C full, **no Block A**):
  - CAGR ≥ **30%** (standing floor — now legitimately applicable)
  - **MAR-invariance:** SPXL MAR ≥ SPY MAR. If MAR *collapses* under leverage, the
    vol-decay/gap penalty exceeds the edge → keep the timer **unleveraged**, don't force 3×.
  - Survives the **COVID path** in Monte-Carlo (median path no >50% peak-to-trough)

**Monte-Carlo:** block bootstrap to preserve gap-clustering / vol-decay autocorrelation
— **not** IID trade resampling (which destroys the path dependence that is the point).

**Breadth broad-vs-narrow decomposition (free Phase 1 byproduct, informs Phase 2).**
On the *same* SPY/SPXL equity curve, tag each in-market sub-period as breadth-broad vs
breadth-narrow and attribute MAR to each. This costs nothing extra and answers whether
the narrow regime is even a profitable place to be long 3×:
- Narrow sub-periods contribute **positive** MAR → the de-risked SPXL overlay is a viable
  Phase 2 *fallback* if the stocks-based selector underdelivers.
- Narrow sub-periods **bleed** (the prior, given the cliff) → empirically justifies
  **cash-in-narrow** as the fallback and raises the bar the stocks-based selector must
  clear to be worth building.
Either way the result is informational, not a Phase 1 gate — it pre-positions the Phase 2
decision instead of committing to the hard cross-sectional build on a hunch.

## Component 2 — Narrow-leadership leaders selector (PHASE 2)

Gated on Phase 1 surviving. **Not designed yet** — deliberately deferred. Recorded here
only so Phase 1 doesn't foreclose it.

**Preferred direction: a stocks-based cross-sectional selector that owns the *specific*
leaders, not cap-weight SPXL.** The thesis is that hand-picking the leading names
(relative-strength ranked, potentially beyond the S&P) can beat riding SPXL through
narrow leadership — less mega-cap-tech concentration, genuine diversification from the
broad-rally timer, and an exit you control per-name rather than at the index's mercy.
This is the **harder** path and carries premise-class risk: "mean-reversion-on-pullback
dies in narrow-leadership tape" (catalogued). A momentum / relative-strength
**trend-continuation** premise is a *different* class and not auto-disqualified — but the
design must obey:
- **Do NOT require sector-breadth confirmation.** Narrow-leadership is precisely the
  regime where breadth is structurally absent; a breadth-confirm gate would veto the
  very leaders you want to buy.
- Cross-sectional → **standard 3-block firewall** + a **cost/slippage haircut**
  sensitivity (frequent rebalancing makes perfect-fill optimistic; the timer barely
  cares, the selector does).
- Standard v4 gates + 30% CAGR floor.

**Fallbacks (only if the stocks-based selector underdelivers), chosen by the Phase 1
decomposition:**
- *de-risked SPXL overlay* — if narrow sub-periods showed positive MAR: trade the index
  but at reduced exposure (1× SPY / half-size / faster exit), not full 3×.
- *cash-in-narrow* — if narrow sub-periods bled: sit it out, accept forgoing narrow
  melt-ups.

## Component 3 — Unified regime classifier overlay (PHASE 3)

Only after both components prove out. Requirements to flag now:
- Regimes defined by **strictly causal** breadth reads (all conditions above are causal).
- Regimes **mutually exclusive and exhaustive** per bar, or capital is double-counted /
  cash periods become ambiguous. The dangerous boundary is **"index up but breadth
  weak"** (narrow-leadership) vs **broad rally** — "index up" needs a concrete causal
  definition (e.g. SPY > which EMA?) that does not overlap broad rally.
- Transition hysteresis to stop the classifier flip-flopping at boundaries.

## Carry-forward risks / flags

1. **Survivorship bias in the breadth series itself.** Market breadth = % of *today's
   surviving* STOCK-type symbols in uptrend → historical breadth carries a survivorship
   tilt that worsens further back. **Mitigation (operator decision 2026-06-01): trust
   floor at 2000-01-01 — pre-2000 breadth is discarded** (thinnest universe / worst
   tilt; pre-2000 also showed degenerate startup values). A residual upward tilt may
   remain *within* 2000-2014, but crisis years validate qualitatively (2008 avg 26.7,
   2022 avg 31.9), so treat it as a modest caveat on Block A *levels*, not a blocker.
2. **COVID is the only leveraged-crisis sample (n=1).** The "3× survives crises" claim
   rests on a single regime instance — treat as n=1 and Monte-Carlo the path around it.
3. **`MarketBreadthIncreasing` is ARS-fragile by construction** (strict consecutive
   monotonicity; one flat day resets the streak) and **non-terminating under the
   `/condition-screen` ARS auto-sweep**. Robustness check is a manual ±1 grid.
4. **No genuinely *leading* broad-rally signal exists** in the breadth primitives —
   breadth is coincident on an already-aggregated trend. Design for
   "fast-confirmed, faster-exit," not "predictive."

## Phase 1 plumbing & data — VERIFIED 2026-06-01

**StockPair signal/trade split works as designed (no engine changes needed).**
`AssetMapper` maps `SPXL→SPY` / `UPRO→SPY`; `getStrategySymbol` resolves the strategy
symbol (custom map overrides `AssetMapper`); `loadBatchWithUnderlying` **auto-loads the
underlying**, so `stockSymbols:["SPXL"]` + `useUnderlyingAssets:true` (default) loads SPY
for signals and trades real SPXL bars. P&L/fills come from `tradingStock`; conditions
evaluate on `strategyStock`. The MAR-invariance test is two POSTs with an identical
signal: Run A `stockSymbols:["SPY"]`, Run B `stockSymbols:["SPXL"]`.

**Data depth (PRD, queried 2026-06-01):**
- SPY: 1995→2026 (7900 bars) · SPXL: 2008-11-05→2026 (4413) · UPRO: 2009-06-25→2026 (4254).
- Market breadth: real & history-consistent from 1996 (2008 avg 26.7 / 2022 avg 31.9 =
  bear; 2024 avg 43.3 = narrow tape; broad-bull years in the 50s).
- **Trust floor = 2000-01-01 (operator decision): do NOT use breadth before 2000.**
  Coincides with Block A start, so the firewall window is unaffected. Use **SPXL** as the
  primary 3× instrument (longer history than UPRO; both cover COVID).
- **Consequence:** the 1× SPY signal validates across the full firewall (2000→), but the
  3× SPXL execution / MAR-invariance comparison can only start **2008-11** — a Block
  B-partial + Block C inference, with COVID as the key leveraged-crisis stress. Accepted.

## Build order (strictly sequential)

1. **Phase 1** — broad-rally timer: define entry/exit stacks → SPY 25y curve-track →
   SPXL execution run (MAR-invariance) → manual G13 → Monte-Carlo. Kill-or-continue.
2. **Phase 2** — narrow-leadership selector (separate cross-sectional firewall track),
   only if Phase 1 survives.
3. **Phase 3** — unified classifier overlay.

## Key engine references

- `backtesting/model/StockPair.kt` — signal-stock / trading-stock split (signal on SPY,
  execute on real SPXL bars; obsoletes the "leverage multiplier" framing).
- `backtesting/service/BacktestService.kt` — `tradingStock` real bars drive P&L; exits
  fill at signal-bar `closePrice` (same-bar close fill → gap risk hits at the close-fill).
- `backtesting/strategy/condition/entry/MarketBreadthIncreasingCondition.kt` — strict
  consecutive monotonicity (ARS-fragile entry timing).
- `backtesting/strategy/condition/entry/MarketBreadthRecoveringCondition.kt` — leadier
  early-thrust entry alternative.
