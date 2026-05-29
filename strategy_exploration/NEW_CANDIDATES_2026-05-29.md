# New Strategy Candidates — 2026-05-29

_Quant 12th consultation. Four candidate strategies designed in light of session findings: all premise classes tested through Idunn have been closed (mean-reversion-on-pullback deprecated; VCP invalidated; lottery + ARS patterns rejected). These are FRESH designs to start over from._

## Context — what we know and what informs these designs

**State of roster at design time** (2026-05-29 end of session):
- **0 firewall-validated strategies** in the roster
- VCP: invalidated by OB lookahead bug; even post-fix fails Block A
- VZ3-s3: TRADABLE smoke verdict invalidated by promotion-fidelity divergence
- MR3, DV1, Idunn (all pullback-style): deprecated premise class
- MJV-s1, BR1/2/3, MO1/3: REJECTED in prior sweeps (lottery / chop failures)

**Framework constraints any new candidate must satisfy**:
1. **Refined firewall** — Block A v4 (binding) + Block B v4 (binding) + 25-year aggregate v4 (binding) + Block C non-catastrophic (informational)
2. **G13 — Parameter Robustness** (sanctioned pending known-passer sweep) — must survive ±1 step on every discrete tunable AND ±10% on every continuous tunable. Designer should pre-emptively choose dimensions that are robust.
3. **G14 — Implementation Invariance** (new gate) — promoted conditions must produce bit-identical trade lists vs inline-script versions. Designer should specify using ALREADY-EXISTING first-class primitives where possible (no `/create-condition` work upfront = no G14 risk).
4. **30% CAGR floor for TRADABLE** (operator standing instruction)

**Anti-patterns to avoid (per quant 11th + 12th consultations)**:
- "Today's low/high vs N-bars-ago" point comparisons → ARS source
- Multi-period discrete-lookback ANDs → 3D ARS surface
- Mean-reversion-on-pullback variants (entire premise class) → doubly-condemned, deprecated for current macro regime
- Bolted-on regime filters after seeing a window fail → IS-fitting
- Strategies that fire only in 1-2 specific regime windows → lottery pattern

## Candidate 1: Persistent Leadership Momentum (PLM) — **TOP PRIORITY**

**Premise.** In narrow-leadership regimes, stocks already in established uptrends with broad-market participation continue to lead; the alpha is in *holding* persistence, not catching turns. Academic anchor: Jegadeesh-Titman 12-1 momentum, narrowed to a regime-conditional entry.

### Entry (ALL must hold)

```kotlin
entryStrategy {
  spyTrendUp()                       // 200-EMA-based regime gate
  marketBreadthAbove(50.0)           // breadth threshold (the only discrete-ish tunable)
  sectorUptrend()                    // sector confirms
  uptrend()                          // stock 5>10>20 EMA + price>50
  emaAlignment(10, 20)               // continuous separation
  priceAbove(50)                     // continuous, no point comparison
  noEarningsWithinDays(5)            // event hygiene
  notInOrderBlock()                  // post-OB-fix; lookahead-clean
}
```

### Exit (ANY triggers)

```kotlin
exitStrategy {
  priceBelowEma(20)                  // primary trend break
  trailingStopLoss(atrMultiplier = 3.0) // continuous, ATR-scaled
  marketBreadthDeteriorating()       // regime exit
  beforeEarnings(1)                  // event hygiene
}
```

### Sizer + Ranker
- **Sizer**: `atrRisk(riskPercentage = 1.25, nAtr = 2.0)` — sweep-validated Pareto winner; sizer is strategy-agnostic so the calibration transfers
- **Ranker**: `CompositeRanker(SectorEdge, DistanceFrom10Ema)` — picks the strongest sector among the cleanest pullback-to-mean entries within the trending universe
- **Position config**: `maxPositions=15`, `leverageRatio=1.0`, `entryDelayDays=1`, `startingCapital=10000`

### Why robust to G13 (parameter robustness)
- **Only ONE discrete threshold**: `marketBreadthAbove(50)`. ±1 step is `(45)` or `(55)` — both still capture "above-median breadth." This is a wide plateau, not a knife-edge.
- Every other entry condition is a **binary alignment check** — no continuous parameter to perturb
- `noEarningsWithinDays(5)` and `beforeEarnings(1)` are event-distance guards; ±1 day shouldn't flip a trend strategy
- Trailing stop `atrMultiplier=3.0` ±10% = 2.7-3.3. Qualitatively similar exit timing
- **No multi-period lookback ANDs. No "today vs N-bars-ago" comparisons.**

### Regime fit
- Designed for **narrow-leadership AND broad-participation trending**
- Should under-fire in chop (good: breadth filter suppresses) and crisis (good: SPY 200-EMA filter suppresses)
- Expected to fire heavily 2013-2015, 2017, 2019, 2021, 2023-2024

### Honest risk (predicted ex-ante)
1. **Whipsaw in transition periods** (early 2022, late 2018) — when breadth crosses 50 oscillating, entries and exits fire in rapid succession; transaction-cost realism may erode CAGR
2. **Late-cycle melt-up exit-too-early** — 2007/2021-style melt-ups may trigger `marketBreadthDeteriorating` exit while price still climbing

---

## Candidate 2: Breadth Thrust Continuation (BTC)

**Premise.** Sharp expansions in market breadth mark durable trend continuations rather than terminal moves. Systematically buy breadth expansions in already-trending stocks. Anchor: Zweig breadth thrust, Whaley breadth momentum literature.

### Entry (ALL must hold)

```kotlin
entryStrategy {
  marketBreadthEmaAlignment(5, 10, 20) // structural: short>medium>long on breadth itself
  sectorBreadthAccelerating(threshold = 5.0)
  spyPriceUptrend()                  // 5/10/20/50 stock-side alignment on SPY
  uptrend()                          // candidate stock
  priceNearDonchianHigh(maxDistancePercent = 3.0)
  volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
  noEarningsWithinDays(5)
}
```

### Exit (ANY triggers)

```kotlin
exitStrategy {
  priceBelowEma(20)
  trailingStopLoss(atrMultiplier = 2.5)
  marketBreadthDeteriorating()       // primary regime exit
  beforeEarnings(1)
}
```

### Sizer + Ranker
- **Sizer**: `atrRisk(1.25, 2.0)`
- **Ranker**: `SectorEdgeWithTightness` — concentrated in sectors leading the expansion AND tightest setups within them

### Why robust to G13
- `marketBreadthEmaAlignment` is a **structural** check, not a discrete-N tunable
- `sectorBreadthAccelerating` and `priceNearDonchianHigh` are **continuous proximity** measures
- `trailingStopLoss(2.5)` ±10% = 2.25-2.75 — qualitatively similar
- `noEarningsWithinDays(5)` ±1 day doesn't flip trend strategies
- **Zero discrete-lookback ANDs in entry**

### Regime fit
- Specifically for **expanding-breadth bull tapes**
- Post-2020 narrow-leadership weakness mitigated by breadth-alignment requirement (correct behavior: should NOT fire during narrow leadership)
- Expected: 2003-2007, 2009-2010, 2013, 2016-2017, 2020-Q4, 2023-2024

### Honest risk
- **Sparse firing** — if `marketBreadthEmaAlignment` too restrictive, may fail G8 (min trades) before G1/G2
- **Mitigation if screen fires <80 trades on Block A**: relax to `marketBreadthAbove(55)` and re-screen

---

## Candidate 3: Post-Earnings Drift Quality (PEDQ)

**Premise.** Post-earnings announcement drift (PEAD) is one of the most-replicated equity anomalies. Filtered to high-quality trend-aligned stocks, the drift is durable; the catalyst (earnings) is exogenous and uncorrelated with current price action. **Different data layer entirely from anything we've tested.**

### Entry (ALL must hold)

```kotlin
entryStrategy {
  daysSinceEarnings(minDays = 1, maxDays = 3) // fire within 3 days of event — wide band by design
  bullishCandle()                    // post-earnings reaction day
  volumeAboveAverage(1.2, 20)        // confirms reaction
  uptrend()                          // quality screen
  spyTrendUp()                       // bull tape only
  sectorUptrend()                    // sector participation
  notInOrderBlock()
}
```

### Exit (ANY triggers)

```kotlin
exitStrategy {
  percentGain(15.0)                  // capture the drift
  stopLoss(atrMultiplier = 7.0)      // tight ATR-stop
  priceBelowEma(20)                  // trend invalidation
  stagnation(thresholdPercent = 0.0, windowDays = 15) // time-stop; drift completes within 60d
  beforeEarnings(1)                  // ALWAYS exit before next earnings
}
```

### Sizer + Ranker
- **Sizer**: `atrRisk(1.0, 2.0)` — slightly tighter than PLM because shorter holds + earnings-cluster concentration risk
- **Ranker**: `Volatility` (ascending — prefer lower-vol post-earnings movers; reduces gap risk)

### Why robust to G13
- `daysSinceEarnings(1, 3)` is a 3-day band; ±1 step (`1,2` or `1,4`) shifts slightly but preserves "right after earnings" semantic — deliberately wide
- `percentGain(15)` ±10% = 13.5-16.5 — captures mid-drift
- `stopLoss(7)` ±10% = 6.3-7.7 — preserves "tight stop"
- `stagnation(15)` ±1 = 14 or 16 — identical on real data
- Trend filters are binary alignment

### Regime fit
- PEAD is **regime-robust** in academic literature (works in bull and chop, weaker in crisis)
- Bull filter via `spyTrendUp` keeps in friendly tape
- Should fire steadily across all blocks (earnings happen every quarter regardless of macro)

### Honest risk
1. **Earnings clustering** → concentration risk during earnings season
2. **Regime-variant drift duration** — post-2020 may show compressed drift (info diffuses faster); `stagnation(15)` may be too short for pre-2015 era and too long post-2020
3. **Midgaard earnings-data quality** has not been validated for this purpose — flag as data-quality follow-up

---

## Candidate 4: Sector-Rotation Strength (SRS)

**Premise.** In any regime, top 1-2 sectors carry most of the breadth and most of the alpha. Continuously rotating into stocks in the strongest-currently sector captures the post-2020 "concentration trade" directly. Anchor: sector momentum (Faber, Asness).

### Entry (ALL must hold)

```kotlin
entryStrategy {
  sectorUptrend()
  sectorBreadthGreaterThanMarket()   // continuous comparison, no discrete N
  sectorBreadthEmaAlignment(5, 10, 20) // structural
  uptrend()
  emaAlignment(10, 20)
  bullishCandle()
  noEarningsWithinDays(5)
  notInOrderBlock()
}
```

**Note**: deliberately NO `spyTrendUp` filter. SRS should fire during narrow-leadership periods when SPY is mixed but specific sectors are roaring.

### Exit (ANY triggers)

```kotlin
exitStrategy {
  priceBelowEma(20)
  trailingStopLoss(atrMultiplier = 2.5)
  priceBelowEma(50)                  // trend break (proxy for sector-deteriorating; weakest leg)
  beforeEarnings(1)
}
```

### Sizer + Ranker
- **Sizer**: `atrRisk(1.25, 2.0)`
- **Ranker**: `SectorStrength` — by design, this IS the strategy's edge

### Why robust to G13
- All entry conditions binary alignment or continuous comparison; **zero discrete-N tunables in entry**
- Trailing stop ±10% behaves similarly
- `SectorStrength` ranker doesn't expose a ±1-step parameter

### Regime fit
- Specifically for **narrow-leadership**
- Should fire heavily 2020-H2, 2021, 2023 (Mag-7 / energy / semis rotations)
- Coverage complement to Candidates 1 (PLM) and 3 (PEDQ)

### Honest risk
1. **Concentrated drawdowns** when leading sector reverses sharply
2. **Weakest exit set** of the four — lacks direct sector-breadth-deteriorating exit primitive; may need to promote one via `/create-condition` (G14 risk)
3. **SectorStrength may overconcentrate** in a single sector (5+ positions in same sector); leverage cap throttles operationally but per-strategy edge measurement may be noisy

---

## Priority order + decision tree

### Quant's ranked recommendation

| Priority | Candidate | Reason |
|---|---|---|
| **1** | **PLM** | Highest premise-class diversification + most robust G13 surface by construction + no promotion risk + broadest regime coverage + plausible 30% CAGR mechanism |
| **2** | **BTC** | If PLM passes screen → fire BTC as broad-participation complement |
| **3** | **PEDQ** | Different data layer worth testing; flag for earnings-data quality follow-up |
| **4** | **SRS** | Highest narrow-leadership specificity but weakest exit set; consider last |

### Decision tree post-screen

For each candidate fired through `/strategy-screen`:

- **Survives + CAGR ≥ 30%** → straight to `/validate-candidate` (4-layer refined firewall)
- **Survives + CAGR < 30%** → flag and decide whether refinement is worth the time vs. firing next candidate
- **Fails on G4 (positive windows)** → likely sample-size or trend-decay issue; diagnose which window failed
- **Fails on G5 (CoV)** → consider whether this is a noise-band-borderline pass (acceptable) or signature of ARS-like brittleness (reject and move on)
- **Fails Block A only** → diagnose which entry filter over-restricts; consider the obvious G13-style perturbation (e.g., `marketBreadthAbove(45)` instead of `(50)` for PLM)
- **Fails Block B only** → narrow-leadership regime suppression is EXPECTED for PLM/BTC; informational rather than disqualifying — proceed to next candidate as complement

## Concrete next-session start

1. **Read this file + read `validation-candidates.md`** for full context
2. **Decide which candidate to fire first** — quant's recommendation is PLM; operator may have own ranking based on intuition
3. **Build screen request JSON** matching the entry/exit composition above
4. **Show POST to operator + wait for "go"** (per memory rule on backtests requiring explicit approval)
5. **Fire `/strategy-screen`** (~7-10 min)
6. **Apply screen analysis** via the `strategy-screen-analyst` agent
7. **Decide next move** based on screen verdict per the decision tree above

## Available first-class primitives (no `/create-condition` needed)

Quick reference — all conditions/strategies used in the candidate designs above are already registered. Spot-check before firing:

- Entry: `marketUptrend`, `sectorUptrend`, `spyTrendUp`, `spyPriceUptrend`, `breadthEma10Above50`, `marketBreadthEmaAlignment`, `marketBreadthAbove`, `marketBreadthTrending`, `marketBreadthRecovering`, `marketBreadthNearDonchianLow`, `sectorBreadthGreaterThanMarket`, `sectorBreadthEmaAlignment`, `sectorBreadthAccelerating`, `uptrend`, `priceAbove`, `emaAlignment`, `volatilityContracted`, `atrExpanding`, `emaBullishCross`, `emaSpread`, `consecutiveHigherHighsInValueZone`, `priceNearDonchianHigh`, `inValueZone`, `volumeAboveAverage`, `bullishCandle`, `adxRange`, `priceAbovePreviousLow`, `daysSinceEarnings`, `noEarningsWithinDays`, `aboveBearishOrderBlock`, `notInOrderBlock`, `orderBlockBreakout`, `belowOrderBlock`, `minimumPrice`, `minimumHistoryDays`, `ovtlyrBuySignal`, `ovtlyrBuySignalFired`

- Exit: `emaCross`, `stopLoss`, `profitTarget`, `percentGain` (NEW), `priceBelowEma`, `priceBelowEmaMinusAtr`, `priceBelowEmaForDays`, `stagnation`, `ATRTrailingStopLoss` (= `trailingStopLoss`), `gapAndCrap`, `bearishOrderBlockExit`, `beforeEarnings`, `marketAndSectorDowntrendExit`, `marketBreadthDeteriorating`, `sectorBreadthBelow`, `ovtlyrSellSignal`

- Rankers: `Volatility`, `DistanceFrom10Ema`, `SectorEdge`, `SectorEdgeWithTightness`, `SectorStrength`, `CompositeRanker`, `AdaptiveRanker`

- Sizers: `atrRisk`, `percentEquity`, `kelly`, `volatilityTarget`

## Related session work

- `feedback_aliased_regime_sensitivity.md` (memory) — ARS detection signature
- `feedback_parameter_fragility_must_be_verified.md` (memory) — G13 principle
- `feedback_mean_reversion_pullback_known_weakness.md` (memory) — deprecated premise class + revisit trigger
- `feedback_regime_conditional_portfolio_framework.md` (memory) — the broader strategic frame these candidates sit in
- Issue #57 — G13 (Parameter Robustness gate)
- Issue #58 — G14 (Implementation Invariance gate + `/verify-promotion` skill)
- Issue #59 — `/condition-screen` skill (diagnostic pre-screen)
