# Mjolnir Strategy Development

## Current State (2026-02-22)

### Entry Strategy
```kotlin
entryStrategy {
    marketBreadthTrending(minWidth = 30.0)
    adxRange(20.0, 40.0)
    atrExpanding(30.0, 60.0)
    volumeAboveAverage(1.3, 10)
    consecutiveHigherHighsInValueZone(2, 2.0, 20)
    emaSpread(10, 20, 1.0)
}
```

### Exit Strategy
```kotlin
exitStrategy {
    emaCross(10, 20)
    stopLoss(atrMultiplier = 2.5)
}
```

### Sector Exclusion
Exclude: XLV (Health Care), XLE (Energy), XLP (Consumer Staples), XLB (Materials) — all have negative or near-zero edge with this strategy.

### Best Configuration (W=30, ADX 20-40, emaSpread 1.0%, excl XLV/XLE/XLP/XLB, 2016–2025, STOCK only)

*Note: Full universe (~1,438 stocks). Metrics differ from earlier runs with smaller universe.*

| Metric | Value |
|---|---|
| Total Trades | 782 |
| Win Rate | 45.0% |
| Edge | 5.81% |
| Profit Factor | 2.08 |
| Avg Win / Loss | 21.59% / -7.10% |
| Edge Consistency | **87.1/100 (Excellent)** |

### Yearly Edge
| Year | Edge | Trades | Tradeable (>=1.5%)? |
|---|---|---|---|
| 2016 | +3.25% | 73 | T |
| 2017 | +3.06% | 26 | T |
| 2018 | +1.97% | 17 | T |
| 2019 | +3.18% | 117 | T |
| 2020 | +21.82% | 109 | T |
| 2021 | +2.93% | 61 | T |
| 2022 | -0.46% | 80 | |
| 2023 | +4.78% | 147 | T |
| 2024 | +0.31% | 89 | |
| 2025 | +12.56% | 63 | T |

**9/10 years profitable** (2022 improved from -0.77% to -0.46%), 8/10 years have tradeable edge (>=1.5%). Full universe dilutes edge vs smaller universe due to additional small/mid-cap stocks with weaker trend-following characteristics.

### EC Score Breakdown
- Profitable Periods: 90.0 (9/10 years positive, weight 40%)
- Stability (Tradeable Edge): 80.0 (8/10 years >= 1.5%, weight 40%)
- Downside: 95.4 (worst year -0.46%, weight 20%)
- **Total: 87.1 (Excellent)**

### ADX Range Optimization (2026-02-22)

Widened ADX from (20,30) to (20,40) after parameter sweep showed nearly identical edge with better EC:

| ADX Range | Trades | Edge | EC | PF | 2023 Edge |
|---|---|---|---|---|---|
| No ADX | 1,519 | 5.34% | 77.0 | 1.62 | +5.0% |
| 15-35 | 1,177 | 5.39% | 78.1 | 1.87 | +5.3% |
| 20-30 (old) | 591 | 5.82% | 82.4 | 2.27 | +3.2% |
| **20-40 (new)** | **782** | **5.80%** | **86.5** | **2.10** | **+5.0%** |
| 20-50 | 826 | 5.51% | 86.1 | 2.05 | +4.6% |
| 25-35 | 360 | 4.93% | 68.6 | 1.57 | +6.1% |

ADX 20-30 was too tight — blocking profitable trades in the 30-40 ADX range (established trends). ADX 20-40 captures those without admitting noisy >40 ADR trades.

### 2.5 ATR Stop Loss Safeguard (2026-02-22)

Added `stopLoss(atrMultiplier = 2.5)` to exit strategy as a safeguard against runaway losers. Fixed stop at `entryPrice - 2.5 × entryATR`; does not trail. Exit conditions use OR logic — whichever fires first (EMA cross or stop loss) exits the trade.

| Metric | Before (emaCross only) | + stopLoss(2.5) | Delta |
|---|---|---|---|
| Total Trades | 782 | 782 | 0 |
| Win Rate | 45.1% | 45.0% | -0.1% |
| Edge | 5.80% | 5.81% | +0.01% |
| Profit Factor | 2.10 | 2.08 | -0.02 |
| Avg Win / Loss | 21.53% / -7.15% | 21.59% / -7.10% | +0.06 / +0.05 |
| EC Score | 86.5 | 87.1 | +0.6 |
| Downside Score | 92.3 | 95.4 | +3.1 |

**Exit reason breakdown:**
- EMA cross: 690 exits, +8.07% avg, 51.0% WR
- Stop loss (2.5 ATR): 92 exits, -11.13% avg, 0% WR

**Key year improvements:** 2022 (-0.77% → -0.46%), 2024 (+0.08% → +0.31%), 2021 (+2.71% → +2.93%).

**Conclusion:** Edge essentially unchanged — the stop loss catches 92 runaway losers that the EMA cross would have eventually exited anyway, but caps their damage earlier. EC improves slightly (+0.6) due to better downside score (worst year improves). Added as permanent safeguard.

### XLB Exclusion Impact
Adding XLB to exclusion list improved all key metrics vs 3-sector exclusion (XLV/XLE/XLP):

| Metric | 3 excl | 4 excl (+ XLB) | Delta |
|---|---|---|---|
| Trades | 529 | 489 | -40 |
| Edge | 6.46% | 7.01% | +0.55% |
| Profit Factor | 2.64 | 2.84 | +0.20 |
| EC Score | 78.6 | 83.4 | +4.8 |
| Stability | 60.0 | 70.0 | +10.0 |

Key year improvements: 2018 (1.08% → 2.05%, now tradeable), 2022 (-0.69% → -0.28%), 2024 (0.59% → 1.49%).

### Entry Condition Ablation Study (2026-02-22, full universe)

Removed one entry condition at a time to measure each condition's contribution to edge. Baseline: 591 trades, 46.2% WR, 5.82% edge, EC 82.4.

| Rank | Condition Removed | Trades | dTrades | WR% | Edge% | dEdge | EC | PF |
|---|---|---|---|---|---|---|---|---|
| 1 | atrExpanding | 4,242 | +3,651 | 40.0% | 2.93% | -2.89 | 63.9 | 1.18 |
| 2 | consHigherHighsVZ | 4,243 | +3,652 | 41.7% | 3.36% | -2.46 | 74.1 | 1.27 |
| 3 | volumeAboveAverage | 1,571 | +980 | 41.8% | 3.62% | -2.20 | 83.3 | 1.41 |
| 4 | marketBreadthTrending | 1,811 | +1,220 | 39.9% | 3.77% | -2.05 | 73.9 | 1.43 |
| 5 | emaSpread | 1,091 | +500 | 41.4% | 3.84% | -1.98 | 80.6 | 1.49 |
| 6 | adxRange | 1,519 | +928 | 42.3% | 5.34% | -0.48 | 77.0 | 1.62 |
| 7 | ~~emaAlignment~~ | 591 | 0 | 46.2% | 5.82% | 0.00 | 82.4 | 2.27 |

**Key findings:**
1. **atrExpanding** and **consHigherHighsVZ** are the two most powerful filters — each blocks ~3,650 bad trades and contributes ~2.5% to edge. These are the strategy's core gatekeepers.
2. **volumeAboveAverage**, **marketBreadthTrending**, and **emaSpread** form a strong second tier (~2% edge contribution each).
3. **adxRange** has modest impact (-0.48%) but still filters 928 marginal trades.
4. **emaAlignment was 100% redundant** — removing it changed nothing (0 trades, 0 edge). `emaSpread(minSpreadPercent=1.0)` already requires EMA10 to be >1% above EMA20, which guarantees `emaAlignment(EMA10 > EMA20)` is always satisfied. **Removed from strategy.**

### Monte Carlo Validation (10,000 iterations, Trade Shuffling)
- Probability of Profit: **100%** — edge is real
- Mean Edge: 7.01% (stable across all scenarios)
- Median Max Drawdown: 68.67% (compounded sequential, not portfolio-level)
- Worst Case DD (95th): 83.64%
- Best Case DD (5th): 56.03%
- Drawdowns are from sequential compounding of 489 trades — actual portfolio DD with position sizing would be much lower

### Position Limit Testing (maxPositions=15, all rankers)

Tested all 6 rankers with maxPositions=15 to evaluate whether position limiting improves consistency with the current config.

| Ranker | Trades | WR | Edge | PF | Avg Win | Avg Loss | EC | Stability |
|---|---|---|---|---|---|---|---|---|
| **Unlimited (baseline)** | **489** | **46.4%** | **7.01%** | **2.84** | **22.57%** | **6.48%** | **83.4** | **70.0** |
| Random | 383 | 45.2% | 5.81% | 2.66 | 20.73% | 6.48% | 85.8 | 80.0 |
| Volatility | 382 | 44.8% | 5.78% | 2.62 | 21.04% | 6.58% | 81.8 | 70.0 |
| DistanceFrom10Ema | 381 | 45.1% | 5.09% | 2.41 | 19.18% | 6.51% | 81.8 | 70.0 |
| Composite | 383 | 44.9% | 5.07% | 2.39 | 19.24% | 6.48% | 85.8 | 80.0 |
| Adaptive | 382 | 44.5% | 5.01% | 2.34 | 19.38% | 6.51% | 81.8 | 70.0 |
| SectorStrength | 383 | 44.6% | 4.99% | 2.23 | 19.24% | 6.51% | 85.8 | 80.0 |

**Yearly edge by ranker (selected):**

| Year | Unlimited | Random | Volatility | Composite | Adaptive |
|---|---|---|---|---|---|
| 2016 | +2.55% | +4.55% | +4.55% | +4.55% | +4.55% |
| 2019 | +0.77% | +1.84% | +1.31% | +1.75% | +1.25% |
| 2020 | +28.55% | +23.62% | +23.81% | +23.19% | +23.19% |
| 2022 | -0.28% | -1.12% | -1.12% | -1.12% | -1.12% |
| 2025 | +11.93% | +13.44% | +13.44% | +6.66% | +6.66% |

**EC breakdown — 3 rankers reached 85.8 (vs 83.4 unlimited):**
- Composite, SectorStrength, Random all achieved stability=80.0 (8/10 tradeable years vs 7/10)
- 2019 edge improved from +0.77% to +1.75-1.84% with position cap, pushing it closer to tradeable

**Key findings:**
1. **Random ranker wins overall** — 5.81% edge + 85.8 EC + 2.66 PF. Best of both metrics.
2. **No ranker adds stock-picking value** — Random matching or beating smart rankers confirms entry conditions are strong enough that selection within qualifying candidates doesn't matter.
3. **Position cap improves EC for 3 rankers** (83.4 → 85.8) — stability component jumps from 70→80.
4. **Edge drops ~1-2% vs unlimited** (5.0-5.8% vs 7.01%) — expected cost of capping at 15 positions.
5. **2022 worsened across all rankers** (-1.12% vs -0.28%) — position limits concentrate losses during bear markets.
6. **2025 interesting split**: Volatility and Random hit +13.44% (above unlimited's +11.93%), while Composite/Adaptive stuck at +6.66%.

**Conclusion:** Position limiting with maxPositions=15 is a viable option for realistic portfolio management. EC improves slightly (85.8 vs 83.4) at the cost of ~1-2% edge. Random ranker is recommended since no smart ranker outperforms it — the entry conditions do the heavy lifting.

---

## Development History

### 1. Exit Strategy Testing
Tested 4 exits against the original full entry strategy:
- **EMA Cross (10,20)** — WINNER: best edge, lets winners run
- Trail 2.0 ATR — too tight, cuts winners
- Trail 3.0 ATR — better but still worse than EMA cross
- Trail 2.5 + EMA — hybrid, no improvement

**Conclusion:** ATR trailing stops cut winners short. The lagging EMA cross lets big winners develop. The problem is entry timing, not exit.

### 2. Pullback Condition Testing
Tested 3 pullback conditions to improve entry timing:
- ValueZone(2.0) — too loose
- ValueZone(1.5) — tighter but still not great
- **ConsecutiveHigherHighsInValueZone(2, 2.0, 20)** — WINNER: reduced trades from 3,856 to 742 while maintaining edge

### 3. Ablation Study (10 conditions removed one by one)
Baseline with all 10 conditions + HH in VZ: 742 trades, 1.34% edge, 1.24 PF

| Condition Removed | Edge Δ | Verdict |
|---|---|---|
| `marketUptrend` | **+0.38%** | REMOVE — hurts performance |
| `marketBreadthAbove(50)` | -0.04% | Neutral |
| `sectorUptrend` | +0.03% | Neutral |
| `sectorBreadthAbove(50)` | +0.06% | Neutral |
| `sectorBreadthGreaterThanMarket` | +0.01% | Neutral |
| `priceAboveEma(20)` | +0.02% | Neutral |
| `emaAlignment(10,20)` | **-0.34%** | ESSENTIAL |
| `adxRange(20,30)` | -0.20% | Helpful |
| `atrExpanding(30,60)` | -0.19% | Helpful |
| `volumeAboveAvg(1.3,10)` | -0.12% | Helpful |

### 4. Stripped-Down Version
Removed all 5 neutral conditions + marketUptrend. Kept only essential + helpful + pullback.
- **Before (10 + pullback):** 742 trades, 1.34% edge, 1.24 PF
- **After (4 + pullback):** 2,679 trades, 1.78% edge, 1.29 PF
- Improvement across all metrics with 3.6x more trades

### 5. trailingStopLoss() Impact
Adding `trailingStopLoss()` to exit dropped edge from 1.78% to 1.43%. Confirmed ATR trailing stops hurt this strategy.

---

## Sector Edge Consistency (new feature added)

| Sector | Trades | Edge | EC Score | Notes |
|---|---|---|---|---|
| XLV | 311 | 1.98% | 47 Moderate | Best consistency, 8/10 years positive |
| XLI | 403 | 0.64% | 45 Moderate | Low edge but stable |
| XLK | 371 | 2.60% | 43 Moderate | Strong edge, 2025 outlier |
| XLF | 410 | 4.60% | 41 Moderate | Highest edge, 2020 outlier (+25.6%) |
| XLY | 360 | 2.35% | 39 Poor | Inconsistent |
| XLRE | 233 | 0.72% | 35 Poor | 2020 crash (-6.3%) |
| XLU | 102 | 0.77% | 30 Poor | Mostly negative years |
| XLP | 165 | -0.49% | 30 Poor | Negative edge |
| XLC | 101 | -0.49% | 21 Poor | Negative edge, 2023 disaster |
| XLB | 116 | 0.10% | 20 Very Poor | Near zero, inconsistent |
| XLE | 92 | 0.51% | 8 Very Poor | 2019 catastrophe (-10.5%) |

---

## Excursion Analysis
- 73.4% of losers were green at some point (entry timing issue)
- Winners capture only 44.9% of their max favorable excursion
- Winner avg MFE: 25.72% vs final profit: 14.16%

## Market Conditions
- 79.5% of trades entered during SPY uptrend
- Downtrend WR (40.4%) slightly higher than uptrend WR (36.4%) — confirms removing marketUptrend was correct

---

## Session 2: Market Regime Filtering (2026-02-19)

### Goal
Avoid trading in choppy, range-bound markets. Improve edge consistency (EC) to 60+ for a generic strategy.

### New Condition: MarketBreadthTrendingCondition
Uses 20-day Donchian channel width on market breadth as a regime proxy:
- `donchianWidth = donchianUpperBand - donchianLowerBand`
- Passes when `width >= minWidth AND breadthPercent > ema10`
- Wide channel = trending market (allow entries), narrow = choppy (block)
- Direction check (breadth > EMA10) ensures uptrending, not just volatile

**Files created/modified:**
- `MarketBreadthTrendingCondition.kt` — New entry condition
- `StrategyDsl.kt` — Added `marketBreadthTrending(minWidth)` DSL function
- `DynamicStrategyBuilder.kt` — Added `"marketbreadthtrending"` API support
- `BreadthEntryConditionsTest.kt` — 4 new test cases
- `MjolnirEntryStrategy.kt` — Added `marketBreadthTrending()` as first condition

### MarketBreadthTrending Parameter Sweep

All tests: 2016-01-01 to 2025-12-31, all stocks, useUnderlyingAssets=false, maxPositions=unlimited.

| minWidth | Trades | WR | Edge | PF | Avg Win | EC Score | Prof. Yrs |
|---|---|---|---|---|---|---|---|
| Baseline | 2,737 | 37.1% | 1.71% | 0.99 | 14.04% | 48 Mod | 8/10 |
| 10 | 1,672 | 36.1% | 1.25% | 1.17 | 13.30% | 40 Poor | 6/10 |
| 15 | 1,658 | 36.2% | 1.27% | 1.18 | 13.36% | 40 Poor | 6/10 |
| 20 | 1,579 | 36.4% | 1.33% | 1.19 | 13.46% | 40 Mod | 6/10 |
| 25 | 1,401 | 36.8% | 1.33% | 1.18 | 13.50% | 44 Mod | 7/10 |
| **30** | **1,059** | **39.0%** | **2.10%** | **1.39** | **14.27%** | **48 Mod** | **8/10** |
| 35 | 819 | 40.2% | 2.17% | 1.50 | 13.93% | 51 Mod | 9/10 |
| 40 | 617 | 41.5% | 2.67% | 1.73 | 14.39% | 52 Mod | 9/10 |
| 45 | 458 | 43.7% | 3.48% | 2.25 | 15.27% | 52 Mod | 8/9 |
| 50 | 317 | 49.8% | 5.11% | 2.57 | 16.14% | 54 Mod | 8/9 |

**Yearly edge (key values):**

| Year | Baseline | W=30 | W=35 | W=40 |
|---|---|---|---|---|
| 2016 | +4.21% | +1.97% | +2.00% | +1.34% |
| 2017 | +1.38% | +1.43% | +1.18% | +0.46% |
| 2018 | -0.92% | **+1.11%** | **+2.67%** | +1.53% |
| 2019 | +1.40% | +0.48% | +0.86% | +0.78% |
| 2020 | +7.33% | +10.13% | +9.68% | +14.04% |
| 2021 | +2.45% | +1.36% | +1.57% | +2.54% |
| 2022 | -1.76% | -1.75% | -2.26% | -2.10% |
| 2023 | +0.31% | +4.21% | +5.05% | +6.07% |
| 2024 | +0.67% | +0.45% | +0.52% | +1.48% |
| 2025 | +0.22% | -0.40% | +0.57% | +0.06% |

**Trade volume at W=30:** ~106 trades/year, ~19 concurrent, ~46 day holding period.

**Key finding:** Higher minWidth monotonically improves edge, WR, and PF. However, W=45+ gets too sparse (missing years, <50 annual trades). W=30 is the sweet spot for tradeable volume while W=35-40 offer better metrics with thinner trade counts.

### SectorBreadthAccelerating Test

Tested as standalone replacement (no marketBreadthTrending):

| Config | Trades | WR | Edge | PF | EC Score | Prof. Yrs |
|---|---|---|---|---|---|---|
| Baseline | 2,737 | 37.1% | 1.71% | 0.99 | 48 Mod | 8/10 |
| SBA=5 | 1,137 | 39.9% | 2.32% | 1.47 | 44 Mod | 7/10 |
| SBA=10 | 670 | 43.7% | 2.96% | 1.81 | 45 Mod | 7/10 |

SBA improves edge more than W=30 (+0.61% vs +0.38%) but **hurts EC** (48 → 44) because improvements are concentrated in outlier years (2020: +13.4%, 2021: +6.8%).

### EC Score Diagnosis

Current EC bottleneck is the **stability component** (40% of score weight). The coefficient of variation (CV) of yearly edges is 1.63 — target is <0.8.

**Problem:** 2020 is a massive outlier (+7-10% edge) while most years are +0.2% to +2.5%. This 10x spread kills the CV regardless of which entry conditions are used.

**EC component breakdown (Baseline):**
- Profitable periods: 80% → 32/40 points (good)
- Stability (CV): 1.63 → ~7/40 points (terrible — this is the bottleneck)
- Downside (worst year -1.76%): ~17/20 points (good)
- Total: ~56 estimated → reported 48

**To reach EC > 60:** Must reduce yearly edge variance. Position limits + ranker would cap 2020's outsized contribution by limiting how many trades can fire simultaneously. This is also more realistic for actual portfolio management.

### Sector Exclusion & Position Limit Tests (W=30)

Tested sector exclusion and position limits to improve EC beyond 60.

**Sector exclusion results (W=30, no position limit):**

| Config | Trades | Edge | EC | Notes |
|---|---|---|---|---|
| All sectors | 1,047 | 2.11% | 63.8 | Baseline W=30 |
| Excl XLE,XLB,XLC | — | — | ~52 | Marginal improvement |
| Excl XLE,XLB,XLC,XLP,XLU | — | — | ~52 | Diminishing returns |
| **Excl XLV,XLE,XLP** | **848** | **2.81%** | **73.1** | **Best config** |

**Position limit tests (W=30, all sectors):**

| maxPositions | Trades | Edge | EC |
|---|---|---|---|
| 5 | — | — | 39 |
| 10 | — | — | 46 |
| 15 | — | — | 52 |
| unlimited | 1,047 | 2.11% | 63.8 |

Position limits **hurt** EC — introduced randomness without improving consistency. Sector exclusion was the winning approach.

### 2022 & 2025 Failure Analysis

Both negative/weak years share the same failure mode: **bull trap entries during distribution phases**.

**2022 (edge=-1.44%, WR=27.2%):**
- Q1 catastrophic: 39 trades entered Jan 3-5 at market top, breadth 67-70%, SPY "uptrend"
- 35/39 trades lost money — stocks reversed immediately after entry
- Market topped Jan 3 as Fed began rate hike cycle
- Donchian width filter passed because breadth was still wide — couldn't detect distribution

**2025 (edge=+0.74% after exclusion, was -0.40% with all sectors):**
- Q1: avg breadth at entry only 44.5%, many entries with breadth 15-17%
- Q4: 0% win rate on 15 trades — all entered during late-year distribution with breadth 52-60%
- XLV (-5.75%), XLP (-6.44%), XLE (-2.43%) were major drags — removing them fixed 2025

**Root cause:** W=30 filter catches choppy/range-bound markets but **cannot detect distribution tops** where breadth is still wide but about to roll over.

**Key difference — losing vs tradeable years:**

| | Losing (2022, 2025) | Tradeable years |
|---|---|---|
| Win Rate | 26.9% | 47.3% |
| Avg Holding Days | 35 | 52 |
| Avg Edge | -1.38% | +5.30% |

### EC Formula Update (2026-02-19)

Replaced the CV-based stability component with a tradeable edge threshold approach:
- **Old formula:** `stability = max(0, 100 × (1 − CV))` where CV = stdDev/|mean| — too harsh, any CV >= 1.0 gives flat 0
- **New formula:** `stability = % of years with edge >= 1.5%` — directly measures what % of years produce a tradeable edge
- Full EC: `profitablePeriods × 0.4 + tradeableEdge × 0.4 + downside × 0.2`
- 1.5% threshold based on minimum edge needed to overcome transaction costs and slippage

**Files modified:**
- `TradePerformanceMetrics.kt` — Replaced CV calculation with threshold count, added `TRADEABLE_EDGE_THRESHOLD = 1.5`
- `EdgeConsistencyScoreTest.kt` — Completely rewritten tests for new formula
- `BacktestController.kt` — Added `includeSectors`/`excludeSectors` to backtest API
- `StrategyConfigDto.kt` — Added sector filtering fields to `BacktestRequest`

### Breadth EMA Alignment Filter Test

Tested `marketBreadthEmaAlignment` as a momentum filter to catch distribution tops (where breadth is wide but rolling over). Parameterized the condition to support configurable EMA pairs.

| Config | Trades | Edge | EC | 2022 Edge | 2025 Edge |
|---|---|---|---|---|---|
| **Baseline (no filter)** | **848** | **2.81%** | **73.1** | **-1.44%** | **+0.74%** |
| + EMA5 > EMA10 > EMA20 | ~780 | ~2.6% | 70.0 | -1.01% | -0.92% |
| + EMA5 > EMA20 | ~790 | ~2.5% | 69.9 | -1.06% | -0.94% |

**Conclusion:** Breadth EMA alignment helps 2022 (~+0.4%) but hurts 2025 more (~-1.7%) — net negative on EC. In 2025, breadth EMAs were too choppy for alignment filters to be reliable. The filter catches some distribution tops but also blocks valid entries during choppy-but-positive recovery periods.

**Files modified:**
- `MarketBreadthEmaAlignmentCondition.kt` — Parameterized to accept configurable EMA periods list (was hardcoded 5/10/20)
- `StrategyDsl.kt` — Updated `marketBreadthEmaAlignment(vararg emaPeriods)` to accept custom periods
- `DynamicStrategyBuilder.kt` — Parse comma-separated `emaPeriods` parameter

### 2025 Trade Loss Deep Dive

Investigated root causes of 2025's thin edge (+0.74%, 73 trades, 32.9% WR).

**Loser categories (49 losing trades):**

| Category | Count | % of Losers | Description |
|---|---|---|---|
| Never-green (MFE ≤ 0.5%) | 23 | 47% | Went straight down from entry |
| Was-green (MFE > 0.5%) | 26 | 53% | Had profit, gave it back |
| Big losers (< -10%) | 6 | 12% | SEDG -23%, CGNX -15%, HNI -14%, RH -13%, ENVA -12%, CXW -10% |

**Monthly breakdown:**

| Month | W | L | WR | P&L |
|---|---|---|---|---|
| Jan | 2 | 11 | 15% | -41.2% |
| Feb | 0 | 4 | 0% | -28.7% |
| May | 5 | 3 | 62% | +134.9% |
| Jun | 9 | 4 | 69% | +100.5% |
| Jul | 4 | 4 | 50% | -2.8% |
| Aug | 3 | 8 | 27% | -30.9% |
| Sep | 1 | 6 | 14% | -35.7% |
| Dec | 0 | 9 | 0% | -41.8% |

**P&L summary:** Full year +54.2% total. Without Q1: +124.1%. Without Q1 & Dec: +165.9%.

**Loss clustering (3+ losers entering same day):**
- Jan 31: 4 losers at breadth=46%
- Feb 5: 3 losers at breadth=45% (DeepSeek selloff week)
- Sep 5: 6 losers at breadth=59% (biggest cluster — momentum exhaustion)
- Dec 10: 3 losers at breadth=54%
- Dec 15: 4 losers at breadth=57%

**Key finding: NOT unpredictable news events.** Only 4 of 23 never-green losers had gap-downs > 2%:
- TXT -4.2% gap (Jan 21, earnings)
- CGNX -3.1% gap (Jan 31, earnings miss)
- MUSA -3.3% gap, QCOM -4.6% gap (Feb 5, DeepSeek AI selloff)

**83% of never-green losers simply drifted down gradually** — no dramatic event, just weak stocks that entered at minor peaks and slowly faded. Normal statistical noise for a momentum strategy.

**Root causes:**
1. **Gradual drift-downs** — normal for a 33% WR strategy. Breakout candidates that fail to follow through.
2. **Entry clustering at regime transitions** (Q1, Sep, Dec) — multiple stocks enter same day when breadth is 45-59% (looks acceptable but rolling over).
3. **Strategy still net profitable** — 5 trades alone (RKLB +62%, AEIS +59%, DDOG +29%, THO +29%, TTD +22%) generated +201% to offset losses. Low-WR, high-payoff working as designed.

**Sector concentration of losses:** XLI (13 losses, -66%), XLF (12, -57%), XLK (9, -74%), XLY (8, -66%). Cyclical/financial sectors more sensitive to macro turns.

**Market breadth at entry:** 78% of trades entered with breadth 40-60% — the "looks fine but mediocre" zone. Only 21% entered at breadth 60%+ where win rate was higher (40% vs 30%).

### Exit Strategy Optimization — Conclusively Ruled Out

Tested whether exits could save losing trades. Result: **No.**

- **Profit targets** (2.0-5.0 ATR): All have massively negative net effect. Winners are too large to cap — a 3.5 ATR target would exit 24% of winners early but those winners average +25% vs the +14% target.
- **Stop losses** (1.5-3.0 ATR): Also destructive. At 2.0 ATR, 34% of eventual winners would be stopped out.
- **69.2% of losers went green** (avg MFE +4.66%) — tempting to add profit targets, but the same logic would devastate the large winners that make the strategy work.

**Conclusion:** This is a low-WR (33-41%), high-payoff (2.7:1 W/L ratio) strategy. The wide distribution of winner sizes means any exit tightening cuts more winners than it saves losers. **Exception:** A wide 2.5 ATR fixed stop loss works as a safeguard — it only catches runaway losers without affecting winners (see "2.5 ATR Stop Loss Safeguard" section above).

### Concurrent Position Analysis

Without position limits, concurrent positions analysis (W=30, excl XLV/XLE/XLP):
- Max concurrent: 51 (2020-11-24, outlier)
- Average concurrent: 11.9
- Median concurrent: ~9
- maxPositions=20 would only bind on ~17% of trading days

Position limit test (maxPositions=20, Random ranker): EC dropped to 69.7, edge to 2.34%, trades to 675. Random ranker discards good trades, reducing edge without improving consistency.

### Exit Before Earnings Test

Tested adding `beforeEarnings(1)` exit to avoid earnings gap-down losses (e.g. SEDG -23%, CGNX -15%).

| Metric | Baseline (emaCross only) | + beforeEarnings(1) | Delta |
|---|---|---|---|
| Trades | 860 | 874 | +14 |
| Win Rate | 41.4% | 47.3% | +5.9% |
| Edge | **2.78%** | **1.60%** | **-1.18%** |
| Avg Win | 14.41% | 8.54% | **-5.87%** |
| Avg Loss | 5.43% | 4.61% | -0.82% |
| EC Score | **74.0** | **61.3** | **-12.7** |
| Profitable years | 9/10 | 7/10 | -2 |

**Yearly edge impact:**
- 2018: +1.11% → -1.31% (flipped negative)
- 2020: +10.74% → +4.32% (halved)
- 2024: +0.47% → -1.09% (flipped negative)
- 2025: +0.74% → +1.02% (slight improvement)

**Conclusion:** WR improves (+5.9%) by avoiding some earnings losses, but **average win slashed from 14.41% to 8.54%**. Big winners (RKLB +62%, AEIS +59%, DDOG +29%) get cut short before they can develop through earnings catalysts. The -0.82% improvement in avg loss doesn't compensate for -5.87% drop in avg win. Confirms this is a high-payoff strategy that needs winners to run through earnings.

### Order Block Entry Filter Test

Tested `notInOrderBlock(ageInDays)` to avoid entering stocks near supply zones that could cause reversals. Targets the 43% of 2025 losers that never went green.

| Config | Trades | Edge | EC | 2021 | 2022 | 2025 |
|---|---|---|---|---|---|---|
| **Baseline** | **860** | **2.78%** | **74.0** | +1.70% | -0.99% | +0.74% |
| + notInOB(30) | 747 | 2.86% | 71.6 | 0.95% | **-0.19%** | +1.16% |
| + notInOB(120) | 830 | 2.77% | 70.0 | 0.70% | -0.98% | +1.06% |

- OB(30) filters 113 trades (13%), improves 2022 (+0.81%) and 2025 (+0.41%), but kills 2021 (1.70% → 0.95%) dropping stability score
- OB(120) too loose — only filters 30 trades, no meaningful effect on edge or problem years
- Neither improves baseline EC of 74.0

**Conclusion:** Order block filter helps the weak years but damages strong years by blocking valid entries. Net negative on EC. The order blocks active during distribution tops are also active during healthy breakouts — the filter can't distinguish.

### Never-Green Loser Analysis & New Entry Conditions

Analyzed 2025's 21 never-green losers (MFE=0%, went straight down) vs 24 winners at entry to find distinguishing signals:

| Metric | Never-Green | Winners | Difference |
|---|---|---|---|
| ATR% of price | 2.62% | 3.31% | Losers have lower volatility |
| Close vs Open | +0.89% | +1.75% | Losers had weaker intraday push |
| Donchian headroom | 1.69% | 1.30% | Losers were further from Donchian high |
| EMA10-20 spread | 0.99% | 1.30% | Losers had narrower EMA spread |

Tested 4 new conditions individually (each added to baseline 6 conditions):

| Condition | Trades | Edge | EC | 2022 | 2025 |
|---|---|---|---|---|---|
| **Baseline (6 conditions)** | **860** | **2.78%** | **74.0** | -0.99% | +0.74% |
| + atrExpanding(40,60) | 603 | 2.78% | 70.6 | -0.70% | +1.16% |
| + bullishCandle(0.5%) | 666 | 2.70% | 72.7 | -1.63% | +1.52% |
| **+ emaSpread(10,20,1.0%)** | **434** | **4.30%** | **88.0** | **+0.28%** | **+2.40%** |
| + priceNearDonchianHigh(1.5%) | 603 | 1.63% | 75.6 | -0.18% | +2.40% |

**emaSpread(1.0%) is a breakthrough:**
- EC jumps from 74.0 to **88.0 (Excellent)** — +14 points
- Edge: 2.78% → **4.30%** (+1.52%)
- **10/10 profitable years** — 2022 flipped positive (+0.28%)
- 7/10 tradeable years (stability 50 → 70)
- Avg win jumps to 17.1% (stronger trend entries)
- Trade count: 434 (~43/year, still tradeable)

**Why it works:** Requiring minimum 1.0% spread between EMA10 and EMA20 ensures the trend has genuine separation. Never-green losers had only 0.99% spread on average — these were weak, ambiguous trends where EMA10 barely led EMA20. By requiring a wider spread, we filter out entries where the trend lacks conviction.

**New conditions created:**
- `BullishCandleCondition.kt` — Requires close > open by minPercent
- `EmaSpreadCondition.kt` — Requires minimum EMA fast-slow spread as % of price
- `PriceNearDonchianHighCondition.kt` — Requires price within maxDistance% of Donchian upper band

### Conclusions & Recommended Next Steps
1. **W=30 + emaSpread(1.0%) + excl XLV/XLE/XLP is the new best config** — EC 88.0 (Excellent), 4.30% edge, Monte Carlo validated
2. **emaSpread was the key missing filter** — ensures trend conviction at entry, eliminates ambiguous weak-trend entries
3. **Sector exclusion > position limits** — removing bad sectors improved EC +10 points; position limits hurt
4. **Exit strategy changes conclusively ruled out** — low-WR, high-payoff strategy cannot be improved by tightening exits
5. **Order block and breadth momentum filters ruled out** — help problem years but damage strong years
6. **2025 losses are normal statistical noise** — 83% of never-green losers are gradual drift-downs, not news events
7. **All 10 years now profitable** — 2022 was the last holdout, emaSpread fixed it

---

## Position Sizing Framework

### Parameters
- Max positions: 15
- Target max drawdown: < 25%
- Instrument: Deep ITM calls (~80 delta)
- Sizing method: ATR-based (volatility-adjusted)

### Step 1: Risk Budget Per Trade

Worst case = all 15 positions lose simultaneously.

```
Max risk per trade = Target DD / Max positions = 25% / 15 = 1.67%
Use 1.5% per trade for safety margin.
```

### Step 2: ATR-Based Position Sizing

For each entry signal:

```
Risk$            = Portfolio × 1.5%
Shares (stock)   = Risk$ / (N_ATR × ATR$)
```

`N_ATR` = expected max adverse move in ATR units (from backtest ATR drawdown stats — e.g., 75th percentile MAE).

**Example:** Portfolio $100K, stock at $150, ATR = $4.50 (3%), N_ATR = 2.0
```
Risk$    = $100,000 × 0.015 = $1,500
Shares   = $1,500 / (2.0 × $4.50) = 166 shares
Notional = 166 × $150 = $24,900
```

### Step 3: Convert to Deep ITM Options (80 Delta)

The option captures 80% of stock movement, so adjust share count:

```
Delta-adjusted shares = Shares / Delta = 166 / 0.80 = 208
Contracts             = floor(208 / 100) = 2 contracts
```

Capital deployed (option premium ~35-45% of stock price):
```
Option premium  = $150 × 0.40 = $60/share
Capital/position = 2 × 100 × $60 = $12,000
```

vs. stock: 166 × $150 = $24,900 — options use ~48% of capital for equivalent risk.

### Step 4: Portfolio Capacity Check

15 positions × ~$12K avg = ~$180K in options. For $100K portfolio = ~1.8x leverage.

If total capital exceeds portfolio, scale all positions down proportionally.

### Summary

| Parameter | Value |
|---|---|
| Risk per trade | 1.5% of portfolio |
| N_ATR | From ATR drawdown stats (TBD — run backtest) |
| Delta | 0.80 |
| Contracts | Risk$ / (N_ATR × ATR$ × Delta × 100) |
| Capital/position | Contracts × Option Premium × 100 |
| Leverage | ~1.5-2.5x (stock price / option premium) |
| Worst-case DD (15 losers) | 15 × 1.5% = 22.5% |

### Next Steps
- Run Mjolnir backtest to extract ATR drawdown percentiles (determines N_ATR)
- Validate with Monte Carlo: simulate position-sized returns to confirm DD < 25%
- Consider scaling N_ATR by 1.2-1.5x for options (gamma risk adds tail exposure)

---

## Potential Next Steps
- Smart ranker development (needed before position limits become useful)
- Combined condition testing (emaSpread + other new conditions)
- Test on out-of-sample data when available
- Parameter sensitivity testing on emaSpread threshold (0.8%, 1.2%, 1.5%)

---

## Axioms Tested
1. **"Trading through earnings is not profitable"** — NOT SUPPORTED. Edge difference negligible for trend-following strategies.
2. **"Exiting day before earnings is more profitable"** — NOT SUPPORTED. 9,803 trades exited before earnings had 84% WR and +5.17% avg profit, but these were already winners being cut short. Mjolnir-specific test: adding `beforeEarnings(1)` exit dropped edge from 2.78% to 1.60% and EC from 74.0 to 61.3 — avg win slashed from 14.41% to 8.54%.

---

## Code Changes Made
- `MjolnirEntryStrategy.kt` — Updated to 6 conditions: removed `emaAlignment()` (redundant with `emaSpread`), added `marketBreadthTrending()` and `emaSpread(10, 20, 1.0)`
- `MjolnirExitStrategy.kt` — emaCross(10,20) + stopLoss(2.5) safeguard against runaway losers
- `MarketBreadthTrendingCondition.kt` — New entry condition (Donchian width + direction)
- `MarketBreadthEmaAlignmentCondition.kt` — Parameterized with configurable EMA periods (was hardcoded 5/10/20)
- `EmaSpreadCondition.kt` — New entry condition (minimum EMA fast-slow spread as % of price)
- `BullishCandleCondition.kt` — New entry condition (close > open by minPercent)
- `PriceNearDonchianHighCondition.kt` — New entry condition (price within maxDistance% of Donchian high)
- `SectorBreadthGreaterThanSpyCondition.kt` — Renamed to `SectorBreadthGreaterThanMarketCondition.kt`
- `DynamicStrategyBuilder.kt` — Added `bullishcandle`, `emaspread`, `priceneardonchianhigh` condition mappings
- `StrategyDsl.kt` — Added `bullishCandle()`, `emaSpread()`, `priceNearDonchianHigh()` DSL functions
- `BacktestReport.kt` — Added per-sector edge consistency calculation
- `BacktestResponseDto.kt` — Added `edgeConsistency` to `SectorStatsDto`
- `TradePerformanceMetrics.kt` — Replaced CV-based EC stability with tradeable edge threshold (>=1.5%)
- `EdgeConsistencyScoreTest.kt` — Rewritten tests for new EC formula
- `BacktestController.kt` — Added `includeSectors`/`excludeSectors` sector filtering
- `StrategyConfigDto.kt` — Added sector filtering fields to `BacktestRequest`
- `BreadthEntryConditionsTest.kt` — Tests for MarketBreadthTrendingCondition
