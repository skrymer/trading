# VCP Strategy Development

## Current State (2026-03-12)

### Entry Strategy
```kotlin
entryStrategy {
    // MARKET
    marketUptrend()

    // SECTOR
    sectorUptrend()

    // STOCK
    uptrend()  // 5 EMA > 10 EMA > 20 EMA and price > 50 EMA
    volatilityContracted(lookbackDays = 10, maxAtrMultiple = 3.5)
    aboveBearishOrderBlock(consecutiveDays = 1, ageInDays = 0)
    priceNearDonchianHigh(maxDistancePercent = 3.0)
    volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
    minimumPrice(10.0)
}
```
*Changes: `uptrend()` strengthened to require 5 EMA > 10 EMA > 20 EMA (was 10 EMA > 20 EMA), `priceAbove(50)` removed (redundant with `uptrend()`), `volatilityContracted` loosened from 2.5→3.5, `sectorUptrend()` added.*

### Exit Strategy
Uses `MjolnirExitStrategy`:
```kotlin
exitStrategy {
    emaCross(10, 20)
    stopLoss(atrMultiplier = 2.5)
}
```

### Best Configuration (2016-2025, STOCK only, unlimited positions, no sector exclusion)

| Metric | Value |
|---|---|
| Total Trades | 9,159 |
| Win Rate | 49.3% |
| Edge | 5.84% |
| Avg Win / Loss | 18.21% / -6.17% |
| Win/Loss Ratio | 2.95x |
| Profit Factor | 2.40 |
| Edge Consistency | **96.0/100 (Excellent)** |

*Updated 2026-03-12 after uptrend condition strengthened to 5 EMA > 10 EMA > 20 EMA.*

### Yearly Edge
| Year | Edge | Tradeable (>=1.5%)? |
|---|---|---|
| 2016 | +5.93% | T |
| 2017 | +7.73% | T |
| 2018 | +2.65% | T |
| 2019 | +6.40% | T |
| 2020 | +10.96% | T |
| 2021 | +2.45% | T |
| 2022 | +0.78% | |
| 2023 | +7.00% | T |
| 2024 | +3.77% | T |
| 2025 | +6.91% | T |

**10/10 years profitable**, 9/10 years have tradeable edge (>=1.5%).

### Exit Reasons
- EMA cross (10/20): 8,346 exits (91.1%), +7.29% avg, 54.0% WR, 55d avg hold
- Stop loss (2.5 ATR): 813 exits (8.9%), -9.11% avg, 0% WR, 9d avg hold

### Sector Performance (all sectors profitable)
| Sector | Trades | WR | Edge |
|---|---|---|---|
| XLC | 353 | 44.2% | +8.03% |
| XLI | 1,525 | 53.8% | +7.29% |
| XLK | 1,460 | 48.5% | +6.13% |
| XLY | 1,064 | 49.7% | +6.00% |
| XLV | 1,086 | 43.0% | +5.97% |
| XLF | 1,706 | 51.3% | +5.45% |
| XLE | 441 | 51.5% | +5.37% |
| XLU | 259 | 53.3% | +5.02% |
| XLP | 333 | 48.0% | +4.45% |
| XLB | 446 | 46.9% | +3.89% |
| XLRE | 486 | 45.3% | +3.10% |

### EC Score Breakdown
- Profitable Periods: 100% (10/10 years positive, weight 40%)
- Stability (Tradeable Edge): 90% (9/10 years >= 1.5%, weight 40%)
- Downside: 100% (worst year +0.78%, weight 20%)
- **Total: 96.0 (Excellent)**

### Position-Sized Results ($10K Starting Capital)

**Configuration:**
- Starting capital: $10,000
- Max positions: 15
- Ranker: SectorEdge (strategy's preferred ranker, deterministic)
- Entry delay: 1 day (realistic execution — signal fires after close, enter next day)
- Risk per trade: 1.5% of portfolio
- ATR multiplier (nAtr): 2.0
- Leverage: 1.0x (stock only, no options)
- No sector exclusions

**Results (uptrend 5>10>20, VC 3.5 + sectorUptrend, SectorEdge ranker, entry delay 1):**

| Metric | Value |
|---|---|
| Starting Capital | $10,000 |
| Final Capital | **$459,565** |
| Peak Capital | $484,239 |
| Total Return | +4,496% |
| CAGR | **46.4%** |
| Max Drawdown | **21.2%** ($63,094) |
| Total Trades | 893 |
| Win Rate | 48.7% |
| Edge | +5.74% |
| Avg Win / Loss | 17.89% / -5.80% |
| Win/Loss Ratio | 3.09x |
| Profit Factor | 2.46 |
| EC Score | 96.0 (Excellent) |

**Risk-adjusted metrics:**

| Metric | Value | Rating |
|---|---|---|
| Sharpe Ratio | 2.21 | Excellent (>2.0) |
| Sortino Ratio | 3.48 | Excellent (>3.0) |
| Calmar Ratio | 2.18 | Excellent (>1.5) |
| SPY Correlation | 0.50 | Good (mix of alpha and beta) |
| Beta | 0.56 | Below-market exposure |
| Alpha (annualized) | 31.1% | Strong independent return |

**Yearly edge:**

| Year | Trades | Edge | Tradeable? |
|---|---|---|---|
| 2016 | ~90 | +2.50% | T |
| 2017 | ~57 | +6.74% | T |
| 2018 | ~64 | +4.92% | T |
| 2019 | ~74 | +6.96% | T |
| 2020 | ~82 | +16.61% | T |
| 2021 | ~108 | +5.36% | T |
| 2022 | ~114 | +0.60% | |
| 2023 | ~86 | +7.63% | T |
| 2024 | ~85 | +6.48% | T |
| 2025 | ~146 | +3.79% | T |

**10/10 years profitable.** 9/10 tradeable. $10K → $460K (46x) over 10 years.

**Exit reasons:**

| Reason | Count | % | Avg Profit | WR | Avg Hold |
|---|---|---|---|---|---|
| EMA cross (10/20) | 811 | 90.8% | +7.22% | 53.6% | 54d |
| Stop loss (2.5 ATR) | 82 | 9.2% | -8.88% | 0% | 10d |

**Top 5 drawdowns:**

| # | Depth | Period | Decline | Recovery | Total |
|---|---|---|---|---|---|
| 1 | 21.2% | 2022-04 → 2023-03 | 155d | 159d | 314d |
| 2 | 16.2% | 2020-02 → 2020-06 | 61d | 71d | 132d |
| 3 | 14.3% | 2025-02 → 2025-06 | 62d | 64d | 126d |
| 4 | 13.8% | 2025-09 → 2025-12 | 60d | 23d | 83d |
| 5 | 11.5% | 2018-01 → 2018-07 | 66d | 94d | 160d |

**Monte Carlo validation (10K iterations):**

Bootstrap resampling (edge confidence):
| Percentile | Edge |
|---|---|
| p5 (worst case) | +4.50% |
| p50 (median) | +5.72% |
| p95 (best case) | +7.04% |
| Prob of Profit | 100% |

Trade shuffling (drawdown distribution):
| Percentile | Max Drawdown |
|---|---|
| p5 (best case) | 14.0% |
| p50 (median) | 18.4% |
| p95 (worst case) | 25.9% |

Actual DD (21.2%) falls between p50 and p75 — average trade ordering luck.

**With drawdown-responsive scaling** (optional, see Drawdown-Responsive Position Sizing section):

| Metric | Without Scaling | With Scaling | Delta |
|---|---|---|---|
| Final Capital | $459,565 | $412,086 | -$47K (-10%) |
| CAGR | 46.4% | 45.0% | -1.4pp |
| Max Drawdown | 21.2% | **16.2%** | **-5.0pp** |
| Calmar | 2.18 | **2.78** | **+0.60 (+28%)** |
| Trades / WR / Edge | 893 / 48.7% / +5.74% | same | same |

Drawdown scaling reduces risk per trade when in drawdown (5% DD → 0.67x risk, 10% DD → 0.33x risk). Gives up 1.4pp CAGR for 5.0pp DD reduction — all risk-adjusted ratios improve. See the Drawdown-Responsive Position Sizing section for full details and API usage.

**Walk-forward validation (5yr IS / 1yr OOS / 1yr step):**

| OOS Year | IS Edge | OOS Edge | WFE |
|---|---|---|---|
| 2021 | +7.15% | +5.39% | 0.75 |
| 2022 | +8.06% | +0.82% | 0.10 |
| 2023 | +6.01% | +6.42% | 1.07 |
| 2024 | +6.84% | +6.85% | 1.00 |

Aggregate WFE: **0.67** (robust), OOS edge: **+4.70%**, 4/4 OOS windows profitable.

**Evolution across optimizations:**

| Metric | VC 2.5 (original) | VC 3.5 | + sectorUptrend | + SectorEdge + delay 1 | + uptrend 5>10>20 |
|---|---|---|---|---|---|
| Final Capital | $148,124 | $224,840 | $317,756 | $395,432 | **$459,565** |
| CAGR | 30.9% | 36.5% | 41.3% | 44.4% | **46.4%** |
| Max Drawdown | 15.2% | 19.7% | 20.7% | 16.7% | **21.2%** |
| Trades | 855 | 927 | 837 | 899 | 893 |
| Win Rate | 46.3% | 49.1% | 49.5% | 47.3% | 48.7% |
| Edge | +4.86% | +5.46% | +5.74% | +5.24% | **+5.74%** |
| Profit Factor | — | 2.04 | 4.72 | 2.79 | 2.46 |
| EC | 92.0 | 96.0 | 96.0 | 96.0 | **96.0** |
| Sharpe | — | — | — | 2.04 | **2.21** |
| Calmar | — | — | — | 1.61 | **2.18** |

Each optimization compounded on the previous. The latest change — strengthening uptrend from 10>20 to 5>10>20 — added $64K (+16%) while improving CAGR by 2pp, Sharpe from 2.04 to 2.21, and Calmar from 1.61 to 2.18. Max drawdown increased from 16.7% to 21.2% (note: earlier 16.7% figure was pre-P2 M2M fix; post-fix baseline was 25.9%, so 21.2% is actually an improvement).

**Position sizing notes:**
- Initial run without leverage cap blew up — ATR-based sizing allowed 59x leverage on a single trade, leading to -$1.27M final capital
- Adding `leverageRatio: 1.0` caps total notional exposure to 1x portfolio value, producing realistic results
- SectorEdge ranker is deterministic — results are reproducible across runs (unlike Random ranker)

### Comparison to Mjolnir Strategy

| Metric | VCP | Mjolnir |
|---|---|---|
| Trades (10yr) | 8,752 | 868 |
| Win Rate | 48.6% | 44.2% |
| Edge | 5.84% | 5.60% |
| Avg Win / Loss | 18.44% / 6.12% | 21.65% / 7.14% |
| Win/Loss Ratio | 3.01x | 3.03x |
| EC Score | 96.0 (Excellent) | 81.3 (Excellent) |
| Profitable Years | 10/10 | 8/10 |
| Tradeable Years | 9/10 | 6/10 |
| Sectors Profitable | 12/12 (all) | 7/11 (excl 4) |
| Sector Exclusions | None needed | XLV, XLE, XLP, XLB |

**Key differences:**
1. **VCP generates 10x more trades** — much broader signal generation
2. **VCP now has higher edge** (5.84% vs 5.60%) — surpassed Mjolnir after VC 3.5 + sectorUptrend optimization
3. **VCP is profitable across all sectors** — no exclusions needed, more robust
4. **VCP has much better EC** (96.0 vs 81.3) — more consistent year over year
5. **VCP never has a losing year** — Mjolnir has 2022 (-1.34%) and 2024 (-0.18%)
6. Both strategies share the same exit (emaCross + 2.5 ATR stop) and similar win/loss ratios (~3:1)

**Complementary strategies:** VCP casts a wider net (volume + contraction breakouts) while Mjolnir is more selective (ATR expanding + consecutive higher highs in value zone). They filter for different market microstructure — VCP for post-contraction breakouts, Mjolnir for expanding volatility momentum. Could potentially run both simultaneously for diversification.

### Running Backtests

#### Unlimited (statistical analysis)

Best for measuring raw strategy edge, sector analysis, and parameter sweeps. No position limits or sizing — every signal is taken.

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "MjolnirExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31"
  }'
```

#### Realistic (position-sized with $10K)

Simulates real trading with capital constraints, position sizing, 1-day entry delay, and a 15-position cap. Uses the strategy's preferred SectorEdge ranker for deterministic, reproducible results.

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "MjolnirExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31",
    "maxPositions": 15,
    "entryDelayDays": 1,
    "positionSizing": {
      "startingCapital": 10000,
      "riskPercentage": 1.5,
      "nAtr": 2.0,
      "leverageRatio": 1.0
    }
  }'
```

**Position sizing parameters:**
- `startingCapital`: Initial portfolio value in dollars
- `riskPercentage`: % of portfolio risked per trade (1.5% = risk $150 on a $10K portfolio)
- `nAtr`: ATR multiplier for stop distance — determines position size (size = risk$ / (nAtr × ATR))
- `leverageRatio`: Max total notional as multiple of portfolio (1.0 = no leverage, 2.0 = 2x leverage)

**Notes:**
- Ranker omitted → uses strategy's preferred ranker: SectorEdge (deterministic, reproducible results)
- `entryDelayDays: 1` → signal fires after Day 0 close, entry at Day 1's close (realistic execution)
- Requires ~12GB heap (`-Xmx12288m` in `build.gradle` bootRun task) for full 10-year run

---

## Research & Findings

### Strategy Design

#### Concept
Volatility Contraction Pattern (VCP) with bearish order blocks as resistance levels. The VCP captures stocks in strong uptrends that consolidate with decreasing volatility, then break out above institutional supply zones with volume confirmation.

#### Entry Condition Logic

| Condition | Purpose | Parameters |
|---|---|---|
| `marketUptrend()` | Broad market filter | default |
| `sectorUptrend()` | Sector must be in uptrend | default |
| `uptrend()` | EMA alignment uptrend (5 > 10 > 20, price > 50 EMA) | default |
| `volatilityContracted()` | VCP contraction phase — range/ATR squeeze | lookback=10, maxAtr=3.5 |
| `aboveBearishOrderBlock()` | Breaking above OB resistance | consecutiveDays=1, ageInDays=0 |
| `priceNearDonchianHigh()` | Near new highs (breakout confirmation) | maxDistance=3.0% |
| `volumeAboveAverage()` | Volume surge on breakout | multiplier=1.2, lookback=20 |
| `minimumPrice(10.0)` | Filter penny stocks | $10 |

#### VolatilityContractedCondition

Measures how tight recent price action is relative to the stock's ATR. When the price range over a lookback period is small relative to ATR, volatility is contracted (the VCP "squeeze").

**Formula:** `(maxHigh - minLow) / ATR <= maxAtrMultiple` over the last N trading days.

- `lookbackDays = 10` — Number of recent trading days to measure price range
- `maxAtrMultiple = 3.5` — Maximum allowed range as ATR multiple (lower = tighter contraction)

**Rationale:** ATR reflects the stock's normal daily volatility. If the total range over 10 days is less than 3.5x a single day's expected range, price action is relatively tight — the "coiled spring" before a breakout. Loosened from 2.5 to 3.5 after VC sweep showed higher edge and EC at 3.5 (see Volatility Contraction Sweep).

---

### Entry Condition Ablation Study (2026-02-27)

#### Goal
Measure each entry condition's individual contribution by removing one at a time and comparing to the baseline (3,667 trades, 45.7% WR, +4.83% edge, EC 92.0, 10/10 profitable years).

#### Results

| Condition Removed | Trades | ΔTrades | WR | Edge | ΔEdge | EC | ΔEC | Prof Yrs |
|---|---|---|---|---|---|---|---|---|
| aboveBearishOrderBlock | 25,100 | +21,433 | 32.5% | +0.80% | **-4.02** | 54.9 | -37.1 | 7/10 |
| volumeAboveAverage | 11,083 | +7,416 | 42.9% | +4.28% | -0.55 | 83.4 | -8.6 | 9/10 |
| uptrend | 4,723 | +1,056 | 45.1% | +4.64% | -0.19 | 86.8 | -5.2 | 9/10 |
| marketUptrend | 6,433 | +2,766 | 45.2% | +4.65% | -0.17 | 86.9 | -5.1 | 9/10 |
| priceNearDonchianHigh | 4,919 | +1,252 | 43.8% | +4.79% | -0.03 | 83.4 | -8.6 | 9/10 |
| priceAbove(50) | 3,667 | **+0** | 45.7% | +4.83% | **+0.00** | 92.0 | +0.0 | 10/10 |
| volatilityContracted | 16,329 | +12,662 | 49.2% | +5.47% | +0.64 | 96.0 | +4.0 | 10/10 |
| minimumPrice | 4,296 | +629 | 45.9% | +8.96% | +4.14 | 96.0 | +4.0 | 10/10 |

#### Importance Ranking

| Rank | Condition | ΔEdge | ΔEC | Role |
|---|---|---|---|---|
| 1 | **aboveBearishOrderBlock** | -4.02pp | -37.1 | **CRITICAL** — the strategy's alpha engine. Without it, 21K junk trades flood in and edge collapses to 0.80% |
| 2 | **volumeAboveAverage** | -0.55pp | -8.6 | Important — filters 7K false breakouts lacking volume confirmation |
| 3 | uptrend | -0.19pp | -5.2 | EMA alignment uptrend adds modest quality filtering |
| 4 | marketUptrend | -0.17pp | -5.1 | Market regime filter, protects consistency (removes a losing year when present) |
| 5 | priceNearDonchianHigh | -0.03pp | -8.6 | Tiny edge impact but large EC impact — ensures entries are near breakout levels |
| 6 | ~~priceAbove(50)~~ | +0.00 | +0.0 | **Completely redundant** — `uptrend()` already requires price > 50 EMA. **Removed.** |
| 7 | volatilityContracted | +0.64pp | +4.0 | Quantity filter, not quality — the 12K extra trades it blocks actually have higher edge |
| 8 | minimumPrice | +4.14pp | +4.0 | Blocks 629 cheap stocks that have outsized edge (especially in 2022: +39%) |

#### Yearly Edge (removing each condition)

| Year | Baseline | -OB | -Volume | -Uptrend | -Market | -Donchian | -VolContr | -MinPrice |
|---|---|---|---|---|---|---|---|---|
| 2016 | +4.2% | +0.9% | +4.1% | +4.4% | +4.8% | +3.8% | +7.3% | +5.8% |
| 2017 | +7.9% | +1.8% | +7.0% | +8.0% | +6.8% | +8.0% | +7.4% | +8.3% |
| 2018 | +0.6% | **-1.0%** | +1.1% | +0.8% | +1.1% | +0.9% | +2.6% | +1.1% |
| 2019 | +5.6% | +1.1% | +5.1% | +5.3% | +4.3% | +5.6% | +5.7% | +6.3% |
| 2020 | +8.7% | +3.9% | +9.9% | +9.3% | +10.4% | +9.5% | +11.3% | +13.5% |
| 2021 | +2.0% | **-0.0%** | +1.4% | +1.8% | +2.8% | +0.8% | +2.1% | +3.0% |
| 2022 | +0.1% | **-2.5%** | **-0.3%** | **-0.6%** | **-0.6%** | **-0.3%** | +0.8% | **+39.2%** |
| 2023 | +7.7% | +2.5% | +5.9% | +6.9% | +5.6% | +7.5% | +6.6% | +8.9% |
| 2024 | +2.5% | +0.4% | +2.6% | +2.6% | +3.2% | +3.2% | +3.9% | +3.3% |
| 2025 | +4.0% | +0.6% | +3.4% | +3.8% | +4.2% | +3.8% | +5.2% | +4.7% |

#### Key Findings

1. **`aboveBearishOrderBlock` is the alpha engine.** It's doing almost all the work — filtering 25K → 3.7K trades with 6x edge improvement (0.80% → 4.83%). The order block resistance concept is the core insight of this strategy.

2. **`priceAbove(50)` was completely redundant.** The `uptrend()` condition already requires price above the 50 EMA. Zero trades added, zero edge change, zero EC change. **Removed from strategy.**

3. **`minimumPrice` is technically a drag** — removing it adds 629 cheap stocks with +8.96% edge and an extraordinary +39.2% in 2022. However, penny stocks are practically difficult to trade (wide spreads, low liquidity, hard to get fills). Keeping the filter is a pragmatic choice, not a statistical one.

4. **`volatilityContracted` reduces quantity, not quality.** The 12,662 trades it blocks actually have higher edge (+5.47%). It narrows the strategy to post-squeeze breakouts, which is conceptually the VCP pattern but statistically filters profitable signals. Kept for strategy identity.

5. **Volume confirmation matters.** Removing `volumeAboveAverage` adds 7,416 trades but drops edge by 0.55pp — breakouts without volume surge are unreliable.

6. **Every "important" condition protects 2022.** Removing uptrend, marketUptrend, volume, or Donchian all flip 2022 negative. The conditions work as an ensemble to keep the weakest year barely positive.

#### Action Taken

Removed `priceAbove(50)` from `VcpEntryStrategy.kt` — proven redundant by ablation study. No impact on backtest results.

---

### Volatility Contraction (maxAtrMultiple) Sweep (2026-02-27)

#### Goal
The ablation study showed that removing `volatilityContracted` entirely *improves* edge (+0.64pp) and EC (+4.0). Test whether loosening the parameter (instead of removing) finds a sweet spot that keeps the VCP contraction concept while capturing more profitable trades.

#### Results

| Config | Trades | WR | Edge | Avg Win | Avg Loss | EC | Prof Yrs | Tradeable Yrs |
|---|---|---|---|---|---|---|---|---|
| VC 2.5 (original) | 3,667 | 45.7% | +4.83% | 16.95% | 5.38% | 92.0 | 10/10 | 8/10 |
| VC 3.0 | 7,062 | 46.5% | +5.27% | 17.98% | 5.77% | 88.0 | 9/10 | 8/10 |
| **VC 3.5** | **10,037** | **47.8%** | **+5.51%** | **18.14%** | **6.04%** | **96.0** | **10/10** | **9/10** |
| VC 4.0 | 12,336 | 48.2% | +5.37% | 17.90% | 6.31% | 96.0 | 10/10 | 9/10 |
| VC 5.0 | 14,927 | 48.9% | +5.40% | 17.96% | 6.60% | 96.0 | 10/10 | 9/10 |
| No VC | 16,329 | 49.2% | +5.47% | 18.10% | 6.74% | 96.0 | 10/10 | 9/10 |

#### Yearly Edge

| Year | VC 2.5 | VC 3.0 | VC 3.5 | VC 4.0 | VC 5.0 | No VC |
|---|---|---|---|---|---|---|
| 2016 | +4.24% | +5.23% | +5.85% | +6.10% | +6.80% | +7.28% |
| 2017 | +7.86% | +8.21% | +7.88% | +7.97% | +7.34% | +7.39% |
| 2018 | +0.58% | +1.10% | **+1.79%** | +1.51% | +1.64% | +2.57% |
| 2019 | +5.60% | +6.29% | +6.13% | +5.90% | +5.70% | +5.65% |
| 2020 | +8.71% | +10.19% | +10.82% | +10.55% | +10.81% | +11.31% |
| 2021 | +2.03% | +2.58% | +2.55% | +2.58% | +2.35% | +2.06% |
| 2022 | +0.08% | **-0.00%** | +0.49% | +0.73% | +0.72% | +0.82% |
| 2023 | +7.70% | +7.39% | +7.05% | +7.00% | +6.69% | +6.58% |
| 2024 | +2.49% | +2.88% | +3.84% | +3.60% | +4.00% | +3.85% |
| 2025 | +4.03% | +4.88% | +5.45% | +5.08% | +5.25% | +5.17% |

#### EC Breakdown

| Config | Score | Prof Periods | Stability | Downside |
|---|---|---|---|---|
| VC 2.5 | 92.0 | 100.0 | 80.0 | 100.0 |
| VC 3.0 | 88.0 | 90.0 | 80.0 | 100.0 |
| **VC 3.5** | **96.0** | **100.0** | **90.0** | **100.0** |
| VC 4.0 | 96.0 | 100.0 | 90.0 | 100.0 |
| VC 5.0 | 96.0 | 100.0 | 90.0 | 100.0 |
| No VC | 96.0 | 100.0 | 90.0 | 100.0 |

#### Key Findings

1. **VC 3.5 is the optimal value** — highest edge (+5.51%), EC 96.0, 10/10 profitable years, 9/10 tradeable years. It's the tightest contraction that achieves the maximum EC score.
2. **VC 3.0 is a trap** — the only value that breaks a profitable year (2022: -0.00%). Worse than both 2.5 and 3.5.
3. **Diminishing returns above 3.5** — VC 4.0/5.0/No VC all plateau at EC 96.0 with similar edge but progressively more trades and higher avg losses.
4. **2018 becomes tradeable at VC 3.5** — jumps from +0.58% (VC 2.5) to +1.79% (VC 3.5), crossing the 1.5% threshold.
5. **The original VC 2.5 was too tight** — it filtered 6,370 trades (vs VC 3.5) that had higher edge, artificially constraining the strategy.

#### Action Taken

Updated `VcpEntryStrategy.kt` to use `maxAtrMultiple = 3.5`. This is the new default configuration.

**Impact vs original (VC 2.5):**
- Edge: 4.83% → 5.51% (+0.68pp)
- EC: 92.0 → 96.0 (+4.0)
- Tradeable years: 8/10 → 9/10
- Trades: 3,667 → 10,037 (~2.7x more signals)

---

### Donchian Distance (maxDistancePercent) Sweep (2026-03-04)

#### Goal
Test whether tightening or loosening `priceNearDonchianHigh` improves the VCP strategy. Current default is 3.0%. Sweep: 1.5%, 3.0%, 5.0%.

All tests: 2016-01-01 to 2025-12-31, STOCK only, unlimited positions, no sector exclusions.

#### Results (Unlimited)

| Config | Trades | WR | Edge | Avg Win | Avg Loss | W/L Ratio | PF | EC |
|---|---|---|---|---|---|---|---|---|
| **DC 1.5%** | 6,981 | **50.2%** | **+5.83%** | 17.59% | 6.01% | 2.92x | 2.30 | **96.0** |
| **DC 3.0% (current)** | 9,564 | 48.7% | +5.72% | 18.07% | 6.01% | 3.01x | **2.41** | **96.0** |
| DC 5.0% | 11,057 | 47.2% | +5.43% | 18.19% | 5.99% | 3.04x | 2.37 | **96.0** |

All three variants: 10/10 profitable years, 9/10 tradeable years, EC 96.0 (identical breakdown: 100% profitable periods, 90% stability, 100% downside).

#### Yearly Edge

| Year | DC 1.5% | DC 3.0% | DC 5.0% |
|---|---|---|---|
| 2016 | **+6.04%** | +5.75% | +5.25% |
| 2017 | **+8.46%** | +7.71% | +7.33% |
| 2018 | **+3.02%** | +2.63% | +2.47% |
| 2019 | **+6.48%** | +6.29% | +6.21% |
| 2020 | +10.57% | +10.61% | **+10.77%** |
| 2021 | +1.70% | **+2.50%** | +2.11% |
| 2022 | **+1.11%** | +0.66% | +0.52% |
| 2023 | **+7.26%** | +6.92% | +6.51% |
| 2024 | **+4.36%** | +3.73% | +3.40% |
| 2025 | +6.15% | **+6.75%** | +6.31% |

DC 1.5% wins 7/10 years outright. DC 3.0% wins in 2020, 2021, and 2025.

#### Yearly Trades

| Year | DC 1.5% | DC 3.0% | DC 5.0% |
|---|---|---|---|
| 2016 | 518 | 685 | 764 |
| 2017 | 454 | 560 | 615 |
| 2018 | 327 | 456 | 513 |
| 2019 | 639 | 828 | 937 |
| 2020 | 733 | 1,035 | 1,241 |
| 2021 | 536 | 767 | 914 |
| 2022 | 581 | 795 | 943 |
| 2023 | 1,105 | 1,479 | 1,678 |
| 2024 | 859 | 1,184 | 1,385 |
| 2025 | 1,229 | 1,775 | 2,067 |

#### Exit Reasons

| Config | EMA Cross Exits | EMA Avg | EMA WR | SL Exits | SL Avg |
|---|---|---|---|---|---|
| DC 1.5% | 6,301 (90.3%) | +7.41% | 55.6% | 680 (9.7%) | -8.77% |
| DC 3.0% | 8,752 (91.5%) | +7.10% | 53.2% | 812 (8.5%) | -9.08% |
| DC 5.0% | 10,210 (92.3%) | +6.65% | 51.2% | 847 (7.7%) | -9.27% |

#### Position-Sized Comparison (DC 1.5% vs DC 3.0%)

Tested both with $10K start, 15 max positions, SectorEdge ranker, 1-day entry delay, 1.5% risk, 1.0x leverage.

| Metric | DC 1.5% | DC 3.0% (current) | Delta |
|---|---|---|---|
| Final Capital | $446,281 | **$474,300** | -$28K (-5.9%) |
| CAGR | 46.2% | **47.1%** | -0.9pp |
| Max Drawdown | **18.7%** | 19.8% | -1.1pp |
| Return/DD Ratio | 233.2 | 234.4 | ~equal |
| Trades | 897 | 966 | -69 |
| Win Rate | **46.2%** | 43.6% | +2.6pp |
| Edge | **+5.93%** | +4.69% | +1.24pp |
| EC Score | **91.7** | 87.3 | +4.4 |

**Yearly edge (position-sized):**

| Year | DC 1.5% | DC 3.0% | Winner |
|---|---|---|---|
| 2016 | +6.60% | +5.44% | DC 1.5% |
| 2017 | +10.30% | +6.61% | DC 1.5% (+3.7pp) |
| 2018 | +2.75% | +1.33% | DC 1.5% |
| 2019 | +6.76% | +6.92% | DC 3.0% |
| 2020 | +19.72% | +13.50% | DC 1.5% (+6.2pp) |
| 2021 | +4.16% | +2.58% | DC 1.5% |
| 2022 | -0.15% | -0.34% | DC 1.5% (both negative) |
| 2023 | +5.43% | +6.58% | DC 3.0% |
| 2024 | +5.27% | +3.28% | DC 1.5% |
| 2025 | +3.14% | +3.72% | DC 3.0% |

DC 1.5% wins 7/10 years in position-sized mode too.

#### Key Findings

1. **Tighter Donchian monotonically improves per-trade edge and WR** — DC 1.5% has the best edge (+5.83%) and WR (50.2%) in unlimited mode, while DC 5.0% is weakest (+5.43%, 47.2%)
2. **All three share identical EC (96.0)** — No differentiation on consistency. All are 10/10 profitable, 9/10 tradeable
3. **DC 1.5% has better trade quality but fewer signals** — Filters 27% of trades (9,564 → 6,981), keeping only entries closest to breakout levels
4. **DC 3.0% produces more dollars in position-sized mode** — $474K vs $446K (+5.9%). The 69 extra trades compound into more capital despite lower per-trade edge
5. **Risk profiles are essentially identical** — Return/DD ratios of 233 vs 234. DC 1.5% has slightly lower max DD (18.7% vs 19.8%)
6. **The SectorEdge ranker benefits from a larger pool** — With position limits, having more signals to choose from (DC 3.0%) gives the ranker better selection, offsetting the lower per-trade quality

#### Conclusion

**Keeping DC 3.0% as the default.** Despite DC 1.5% having superior per-trade quality (+0.11pp edge, +1.5pp WR in unlimited), the position-sized results show DC 3.0% produces more total capital ($474K vs $446K). The larger signal pool gives the SectorEdge ranker more candidates, and the compounding effect of additional trades outweighs the per-trade edge difference. Risk profiles are equivalent (Return/DD ~234 for both).

DC 1.5% would be preferable only if trading without a ranker (taking every signal), where per-trade edge dominates.

---

### MarketBreadthTrending Parameter Sweep (2026-02-27)

#### Goal
Test whether adding `marketBreadthTrending(minWidth)` improves the VCP strategy by filtering out entries during choppy/range-bound markets.

All tests: 2016-01-01 to 2025-12-31, STOCK only, unlimited positions, no sector exclusions.

#### Results

| Config | Trades | WR | Edge | PF | Avg Win | Avg Loss | EC | Prof Yrs | Stability | Downside |
|---|---|---|---|---|---|---|---|---|---|---|
| **Baseline** | **3,667** | **45.7%** | **+4.83%** | **0.664** | **16.95%** | **5.38%** | **92.0** | **100%** | **80%** | **100%** |
| MBT(20) | 3,135 | 46.6% | +5.03% | 0.638 | 17.00% | 5.44% | 87.6 | 90% | 80% | 98% |
| **MBT(25)** | **2,691** | **47.3%** | **+5.29%** | **0.523** | **17.23%** | **5.41%** | **91.5** | **90%** | **90%** | **97%** |
| MBT(30) | 2,066 | 49.6% | +6.01% | 0.383 | 17.70% | 5.47% | 87.4 | 90% | 80% | 97% |

#### Yearly Edge

| Year | Baseline | MBT(20) | MBT(25) | MBT(30) |
|---|---|---|---|---|
| 2016 | +4.24% | +4.36% | +4.39% | +4.73% |
| 2017 | +7.86% | +7.46% | +7.50% | +7.82% |
| 2018 | +0.58% | +1.39% | **+2.48%** | +3.64% |
| 2019 | +5.60% | +5.75% | +5.75% | +6.92% |
| 2020 | +8.71% | +8.30% | +8.95% | +10.56% |
| 2021 | +2.03% | +2.11% | +1.77% | **-0.31%** |
| 2022 | +0.08% | -0.18% | -0.27% | +0.21% |
| 2023 | +7.70% | +7.02% | +7.14% | +7.31% |
| 2024 | +2.49% | +2.54% | +2.86% | +2.94% |
| 2025 | +4.03% | +5.68% | +6.07% | +7.27% |

#### Yearly Trades

| Year | Baseline | MBT(20) | MBT(25) | MBT(30) |
|---|---|---|---|---|
| 2016 | 291 | 253 | 246 | 230 |
| 2017 | 292 | 221 | 117 | 93 |
| 2018 | 195 | 121 | 83 | 46 |
| 2019 | 320 | 289 | 237 | 176 |
| 2020 | 428 | 386 | 359 | 277 |
| 2021 | 272 | 268 | 176 | 106 |
| 2022 | 250 | 247 | 223 | 134 |
| 2023 | 553 | 494 | 459 | 413 |
| 2024 | 382 | 281 | 261 | 234 |
| 2025 | 684 | 575 | 530 | 357 |

#### Key Findings

1. **Higher minWidth monotonically improves edge and WR** — MBT(30) has the best per-trade edge (+6.01%) and WR (49.6%)
2. **MBT(25) is the sweet spot** — EC 91.5 (nearly matching baseline's 92.0), stability jumps to 90% (9/10 tradeable years), edge +5.29%
3. **MBT(30) kills 2021** — flips it negative (-0.31%), dropping EC to 87.4 despite highest edge. Same failure mode as Mjolnir's MBT testing
4. **2018 is the biggest beneficiary** — all MBT variants improve it significantly (0.58% → 2.48-3.64%), making it tradeable
5. **2022 is the weak spot** — flips slightly negative at MBT(20) and MBT(25) (-0.18%, -0.27%); MBT(30) stays barely positive (+0.21%)
6. **2025 improves across all variants** — baseline +4.03% → MBT(25) +6.07% → MBT(30) +7.27%

#### Conclusion

The MBT filter improves per-trade quality but introduces marginal losing years. The tradeoff:
- **Baseline**: 10/10 profitable years, lower edge (4.83%), EC 92.0 — most robust
- **MBT(25)**: 9/10 profitable years (2022: -0.27%), higher edge (5.29%), EC 91.5, best stability (90%) — best risk-adjusted
- **MBT(30)**: 9/10 profitable years (2021: -0.31%), highest edge (6.01%), EC 87.4 — too aggressive

**Not adding MBT to the default strategy.** The baseline's 10/10 profitable years and 92.0 EC is the VCP strategy's signature strength. MBT(25) is a viable alternative for traders who prefer higher edge over zero losing years.

---

### Exit Analysis & Excursion Study (2026-02-27)

#### Excursion Analysis (3,667 trades, 2016-2025)

**Winners (1,676):**
| Metric | Value |
|---|---|
| Avg MFE (max profit reached) | +28.90% |
| Avg final profit | +16.95% |
| Profit capture efficiency | **58.6%** |
| Avg MAE (max drawdown during trade) | -1.03% |

**Winner MFE Distribution (how far do winners run?):**
| Percentile | MFE |
|---|---|
| 25th | +12.41% |
| 50th (median) | +19.09% |
| 75th | +31.24% |
| 90th | +49.84% |
| 95th | +74.71% |

**Winner MAE Distribution (how deep do winners dip?):**
| Percentile | MAE (%) | MAE (ATR) |
|---|---|---|
| 25th | -1.53% | 0.00 ATR |
| 50th (median) | -0.22% | 0.09 ATR |
| 75th | 0.00% | 0.60 ATR |
| 90th | 0.00% | 1.10 ATR |
| 95th | 0.00% | 1.53 ATR |

**75% of winners never dip below entry.** 95th percentile MAE is only 1.53 ATR — the 2.5 ATR stop has ample room.

**Losers (1,991):**
| Metric | Value |
|---|---|
| Were green (MFE > 0.5%) | 1,121 (56.3%) — avg +5.02% MFE before reversing to -4.71% |
| Never green (MFE <= 0.5%) | 870 (43.7%) — avg -6.23% final loss |
| Avg MFE | +2.84% |
| Avg MAE | -5.66% |
| Avg final loss | -5.38% |

**63.2% of losers were green at some point** — they averaged +5.02% profit before reversing. This creates temptation for profit targets, but capping at +5% would devastate winners that average +28.9% MFE.

**Overall MFE efficiency: 49.6%** — capturing about half of the maximum favorable excursion. This is the inherent cost of using a lagging EMA cross exit.

#### Stop Loss ATR Sweep

Tested stop loss from 1.5 to 3.0 ATR to see if a tighter stop improves results.

| Config | Trades | WR | Edge | EC | SL Exits | Winners | Avg Loss |
|---|---|---|---|---|---|---|---|
| **SL 1.5 ATR** | 3,701 | 43.3% | +4.50% | **87.5** | 868 (23%) | 1,603 | 4.95% |
| SL 2.0 ATR | 3,671 | 45.0% | +4.79% | 92.0 | 471 (13%) | 1,652 | 5.24% |
| **SL 2.5 ATR (current)** | **3,667** | **45.7%** | **+4.83%** | **92.0** | **238 (6%)** | **1,676** | **5.38%** |
| SL 3.0 ATR | 3,664 | 45.9% | +4.85% | 92.0 | 121 (3%) | 1,682 | 5.40% |

**Exit reason detail:**

| Config | EMA Cross Exits | EMA WR | EMA Avg | SL Exits | SL Avg |
|---|---|---|---|---|---|
| SL 1.5 ATR | 2,833 | 56.6% | +7.68% | 868 | -5.86% |
| SL 2.0 ATR | 3,200 | 51.6% | +6.55% | 471 | -7.17% |
| SL 2.5 ATR | 3,429 | 48.9% | +5.75% | 238 | -8.47% |
| SL 3.0 ATR | 3,543 | 47.5% | +5.35% | 121 | -9.65% |

**Yearly edge:**

| Year | SL 1.5 | SL 2.0 | SL 2.5 | SL 3.0 |
|---|---|---|---|---|
| 2016 | +4.17% | +4.20% | +4.24% | +4.29% |
| 2017 | +7.58% | +7.89% | +7.86% | +7.88% |
| 2018 | +0.69% | +0.71% | +0.58% | +0.70% |
| 2019 | +5.22% | +5.43% | +5.60% | +5.58% |
| 2020 | +7.35% | +8.49% | +8.71% | +8.69% |
| 2021 | +2.25% | +2.14% | +2.03% | +2.02% |
| 2022 | **-0.25%** | +0.14% | +0.08% | +0.06% |
| 2023 | +7.59% | +7.61% | +7.70% | +7.70% |
| 2024 | +1.98% | +2.54% | +2.49% | +2.67% |
| 2025 | +3.86% | +4.02% | +4.03% | +4.03% |

#### Key Findings

1. **Tighter stops hurt.** SL 1.5 ATR stops out 868 trades (23%) — killing 73 eventual winners and dropping edge by -0.33%. EC falls from 92.0 to 87.5 and 2022 flips negative.
2. **SL 2.5 ATR is well-calibrated.** 95% of winners never dip below 1.53 ATR, so the 2.5 ATR stop gives all winners room to breathe while catching fast failures within 8 days.
3. **SL 3.0 ATR is marginally better** (+0.02% edge) but barely fires (121 exits) — not worth the looser protection.
4. **Profit targets would be destructive.** 56% of losers were green at +5% avg — tempting to capture, but winners peak at +28.9% avg. Capping gains would destroy the 3.2:1 win/loss ratio.
5. **This is a low-WR, high-payoff strategy.** The exit must let winners run. The EMA cross captures 58.6% of MFE — acceptable for a lagging indicator that enables +74.7% winners at the 95th percentile.

**Conclusion:** 2.5 ATR stop loss confirmed as optimal. No exit changes recommended.

#### ATR Trailing Stop & Faster EMA Sweep (2026-03-09)

Tested adding an ATR trailing stop alongside the existing EMA(10/20) cross, and a faster EMA(8/15) cross. All tests: 2016-2025, STOCK only, unlimited.

| Config | Edge | EC | TS Fire Rate | TS Avg Profit | Delta vs Baseline |
|---|---|---|---|---|---|
| Baseline (EMA 10/20 + SL 2.5) | 5.70% | 96.0 | — | — | — |
| + Trailing Stop 3.0 ATR | 5.12% | 92.0 | 36.1% | +10.29% | **-0.58pp edge, -4 EC** |
| + Trailing Stop 4.0 ATR | 5.69% | 96.0 | 7.7% | +11.79% | ~0 (no impact) |
| + Trailing Stop 5.0 ATR | 5.69% | 96.0 | 1.0% | +14.22% | ~0 (no impact) |
| EMA 8/15 + SL 2.5 | 5.70% | 96.0 | — | — | ~0 (no impact) |

**Findings:** TS 3.0 ATR fires too aggressively (36% of exits), clipping winners and dropping avg win from 18.07% to 16.47%. TS 4.0 and 5.0 ATR are too wide — the EMA cross fires first, making the trailing stop redundant. EMA 8/15 produces identical results to 10/20. **The current MjolnirExitStrategy is already optimal.**

---

### Sector Exclusion Analysis (2026-02-27)

#### Goal
With 3,667 trades, the VCP strategy can afford to be selective. Test whether excluding the weakest sectors improves overall performance.

#### Sector Performance (sorted by edge, with EC and yearly consistency)

| Sector | Trades | WR | Edge | EC | Losing Yrs | Best Year | Worst Year |
|---|---|---|---|---|---|---|---|
| XLY (Cons. Disc.) | 366 | 50.3% | +7.67% | 88.9 | 1 | +16.4% (2020) | -1.5% (2021) |
| XLI (Industrials) | 584 | 53.9% | +6.50% | 81.5 | 2 | +12.3% (2020) | -1.3% (2018) |
| XLC (Comm. Svc.) | 133 | 39.1% | +6.42% | 71.0 | 2 | +18.0% (2021) | -2.5% (2022) |
| XLE (Energy) | 157 | 44.6% | +5.07% | 48.0 | 3 | +23.8% (2020) | -13.6% (2017) |
| XLK (Technology) | 504 | 44.8% | +4.95% | 72.4 | 2 | +9.3% (2017) | -5.8% (2021) |
| XLF (Financials) | 691 | 44.3% | +4.21% | 72.5 | 3 | +10.7% (2017) | -1.7% (2022) |
| XLV (Healthcare) | 492 | 41.7% | +4.17% | 69.8 | 3 | +11.2% (2023) | -3.1% (2021) |
| XLRE (Real Estate) | 221 | 44.3% | +3.35% | 67.7 | 3 | +15.1% (2016) | -2.2% (2018) |
| XLU (Utilities) | 147 | 42.9% | +3.23% | 71.1 | 2 | +13.1% (2016) | -2.5% (2021) |
| XLP (Cons. Staples) | 144 | 43.8% | +2.83% | 62.4 | 2 | +15.0% (2022) | -4.8% (2021) |
| (unknown) | 59 | 45.8% | +1.64% | 46.1 | 4 | +12.6% (2021) | -8.9% (2025) |
| XLB (Materials) | 169 | 39.6% | +1.56% | 38.3 | 5 | +4.6% (2023) | -8.8% (2016) |

#### Exclusion Candidates

**XLB (Materials)** — Weakest sector: 1.56% edge, EC 38.3 (Poor), negative in 5 of 10 years. Clear drag on performance.

**Unknown sector** — Only 59 trades, EC 46.1, 4 losing years. Small sample, unreliable.

**XLE (Energy)** — Edge looks decent (5.07%) but EC is only 48.0 with wild swings (-13.6% in 2017, +23.8% in 2020). Very inconsistent. However, it contributes a critical +2.7% edge in 2022.

#### Test: Exclude XLB + Unknown + XLE

| Metric | Baseline | Excl XLB/XLE/Unknown | Delta |
|---|---|---|---|
| Trades | 3,667 | 3,341 | -326 |
| Win Rate | 45.7% | 46.1% | +0.4pp |
| Edge | 4.83% | 4.98% | +0.15pp |
| EC | **92.0** | 87.9 | **-4.1** |
| Profitable Years | **10/10** | 9/10 | **lost 2022** |

**Yearly edge comparison:**

| Year | Baseline | Filtered |
|---|---|---|
| 2016 | +4.24% | +4.80% |
| 2017 | +7.86% | +8.52% |
| 2018 | +0.58% | +0.79% |
| 2019 | +5.60% | +5.93% |
| 2020 | +8.71% | +8.53% |
| 2021 | +2.03% | +1.90% |
| 2022 | +0.08% | **-0.13%** |
| 2023 | +7.70% | +7.97% |
| 2024 | +2.49% | +2.68% |
| 2025 | +4.03% | +3.78% |

#### Key Findings

1. **Per-trade improvement is marginal** — edge only goes from 4.83% to 4.98% (+0.15pp), WR from 45.7% to 46.1%
2. **2022 flips negative** — XLE's +2.7% edge in 2022 was keeping that marginal year positive. Without it, 2022 goes to -0.13%
3. **EC drops significantly** — 92.0 → 87.9, losing the "10/10 profitable years" signature strength
4. **Paradox of sector exclusion** — XLE is individually inconsistent (EC 48.0) but its 2022 contribution is essential for the portfolio-level perfect year streak

#### Conclusion

**No sector exclusions for the VCP strategy.** The per-trade improvement (+0.15pp) is negligible compared to the consistency damage (EC -4.1, lost 10/10 streak). Unlike Mjolnir (which benefits from excluding 4 sectors), VCP's broad signal generation across all sectors is its strength — every sector contributes positive edge, and the weak sectors provide diversification that smooths out individual year results.

---

### Position Limit & Ranker Testing (maxPositions=15, 2026-03-01)

Tested with 15-position cap comparing Random vs SectorEdge rankers against the unlimited baseline.

#### Ranker Comparison

| Metric | SectorEdge | Random | Unlimited |
|---|---|---|---|
| Total Trades | 925 | 945 | 9,555 |
| Win Rate | 47.2% | 47.3% | 48.6% |
| Edge | **+5.50%** | +4.24% | +5.70% |
| Avg Win / Loss | 18.55% / -6.18% | 15.98% / -6.30% | 18.07% / -6.01% |
| Profit Factor | **2.71** | 2.30 | 2.41 |
| EC Score | **91.7** | 85.2 | 96.0 |
| Profitable Years | 9/10 | 9/10 | 10/10 |
| Missed Opportunities | 8,783 | 8,782 | 0 |
| Missed Avg Profit | +5.74% | +5.86% | — |

*SectorEdge ranking: XLC, XLI, XLK, XLY, XLV, XLF, XLE, XLU, XLP, XLB, XLRE (ordered by unlimited baseline edge).*

#### Yearly Edge

| Year | SectorEdge | Random | Unlimited |
|---|---|---|---|
| 2016 | +2.53% | +4.78% | +5.75% |
| 2017 | +6.87% | +8.28% | +7.70% |
| 2018 | **+3.87%** | +0.99% | +2.63% |
| 2019 | **+8.88%** | +4.66% | +6.29% |
| 2020 | +12.09% | +12.62% | +10.61% |
| 2021 | **+5.26%** | +2.06% | +2.50% |
| 2022 | -0.13% | -1.39% | +0.67% |
| 2023 | +8.71% | +8.90% | +6.92% |
| 2024 | **+8.80%** | +3.91% | +3.74% |
| 2025 | +2.99% | +2.15% | +6.66% |

#### Key Findings

1. **SectorEdge is significantly better than Random** — +1.26pp edge improvement (5.50% vs 4.24%), EC 91.7 vs 85.2
2. **SectorEdge nearly matches unlimited edge** — 5.50% vs 5.70% (-0.20pp), remarkable given it takes only 10% of trades
3. **SectorEdge dominates in difficult years** — 2018 (+3.87% vs +0.99%), 2021 (+5.26% vs +2.06%), 2024 (+8.80% vs +3.91%)
4. **2022 still negative for both** — SectorEdge (-0.13%) better than Random (-1.39%), but unlimited stays positive (+0.67%)
5. **Profit factor highest with SectorEdge** (2.71) — even higher than unlimited (2.41), showing the ranker selects higher-quality trades
6. **Random results vary between runs** — SectorEdge is deterministic and reproducible
7. Position cap still cuts ~90% of trades — 8,783 missed opportunities averaging +5.74%

---

## Appendix

### Code Changes
- `VolatilityContractedCondition.kt` — New entry condition (range/ATR squeeze detection)
- `VcpEntryStrategy.kt` — New registered strategy (`@RegisteredStrategy(name = "Vcp", type = StrategyType.ENTRY)`)
- `StrategyDsl.kt` — Added `volatilityContracted(lookbackDays, maxAtrMultiple)` DSL function
- `DynamicStrategyBuilder.kt` — Added `"volatilitycontracted"` condition mapping
- `VolatilityContractedConditionTest.kt` — 11 unit tests covering pass/fail/boundary/metadata/detailed evaluation

### Strategy Validation (2026-03-06)

Five critical methodological fixes were applied to the backtesting framework before re-running the VCP strategy. This section documents the impact on unlimited backtest results and Monte Carlo validation.

#### Fixes Applied

1. **P1: Order Block Look-Ahead Bias Fix** — Added `triggerDate` to OrderBlock model. OBs are now only visible from the ROC crossing date (4-15 bars after origin), not the origin candle date. All 6 OB conditions updated to use `triggerDate` for age calculations.

2. **P2: Daily Mark-to-Market Drawdown** — PositionSizingService rewritten to iterate day-by-day through all trading dates, computing daily portfolio value as `cash + unrealized P/L of open positions`. Captures intra-trade drawdowns that exit-based tracking misses.

3. **P3: Survivorship Bias Filter** — Added `listingDate` and `delistingDate` to Stock model. Backtest entry evaluation now skips stocks not yet listed or already delisted on the current date. Dates derived from quote date range during ingestion.

#### Unlimited Backtest: Before vs After

| Metric | Before | After | Delta |
|---|---|---|---|
| Total Trades | 9,555 | 9,476 | -79 (-0.8%) |
| Win Rate | 48.6% | 48.8% | +0.2pp |
| Edge | 5.70% | 5.67% | -0.03pp |
| Avg Win / Loss | 18.07% / -6.01% | 17.91% / -6.02% | minor |
| Profit Factor | 2.41 | 2.41 | same |
| EC Score | 96.0 | 96.0 | same |

#### Yearly Edge (After Fixes)

| Year | Edge | Tradeable? |
|---|---|---|
| 2016 | +5.42% | T |
| 2017 | +7.75% | T |
| 2018 | +2.52% | T |
| 2019 | +6.22% | T |
| 2020 | +10.73% | T |
| 2021 | +2.35% | T |
| 2022 | +0.80% | |
| 2023 | +6.86% | T |
| 2024 | +3.67% | T |
| 2025 | +6.65% | T |

**10/10 years profitable, 9/10 tradeable.** Pattern preserved.

#### Sector Performance (After Fixes)

| Sector | Trades | WR | Edge |
|---|---|---|---|
| XLI | 1,568 | 53.3% | +7.16% |
| XLC | 381 | 44.1% | +6.53% |
| XLY | 1,094 | 49.4% | +6.16% |
| XLK | 1,506 | 48.0% | +6.05% |
| XLV | 1,111 | 43.1% | +5.66% |
| XLE | 451 | 51.0% | +5.38% |
| XLF | 1,756 | 51.0% | +5.30% |
| XLU | 280 | 50.4% | +4.51% |
| XLP | 366 | 46.7% | +4.14% |
| XLB | 460 | 47.8% | +3.90% |
| XLRE | 503 | 44.7% | +3.05% |

All 11 sectors profitable. Exit reasons unchanged: EMA cross 91.6% (8,675 exits, +7.03% avg, 53.3% WR), stop loss 8.4% (801 exits, -9.04% avg).

#### P1-P3 Impact Assessment

- **P1 (triggerDate)**: Minimal impact as predicted. The `aboveBearishOrderBlock(ageInDays=0)` condition with price-above filter meant most affected OBs were already correctly excluded — during the 4-15 bar window between origin and trigger, a bearish OB origin (bullish candle at the top) almost always has price below the OB zone, so the "above OB" check fails anyway. Only 79 trades lost.
- **P2 (M2M drawdown)**: No impact on unlimited results (no position sizing). Will show impact in position-sized backtest.
- **P3 (survivorship)**: Negligible effect — most delisted stocks had no quote data in the tested period.
- **Overall**: Strategy edge is real, not inflated by methodological artifacts. EC 96.0 unchanged.

#### Monte Carlo Validation (Bootstrap Resampling, 10K iterations)

Bootstrap resampling draws random samples with replacement from the original 9,476 trades to measure confidence intervals on the strategy's edge and win rate.

**Edge Confidence Interval:**

| Percentile | Edge |
|---|---|
| p5 (worst case) | **+5.28%** |
| p25 | +5.51% |
| p50 (median) | +5.67% |
| p75 | +5.83% |
| p95 (best case) | +6.08% |

**Win Rate Confidence Interval:**

| Percentile | Win Rate |
|---|---|
| p5 | 48.0% |
| p50 | 48.8% |
| p95 | 49.7% |

**Key findings:**
1. **Edge is statistically robust.** Even at p5 (worst-case resampling), edge is +5.28% — well above the 1.5% tradeable threshold.
2. **Tight confidence interval** (5.28% - 6.08%) indicates the edge is not driven by a few outlier trades. The strategy's alpha is broadly distributed across the 9,476 trade sample.
3. **Original edge (5.67%) sits at the median** — the backtest sequence was neither lucky nor unlucky.
4. **Win rate is stable** — the narrow 48.0%-49.7% range confirms the win rate is a genuine property of the strategy, not sampling noise.

#### Position-Sized Results (After Fixes, $10K Starting Capital)

**Configuration:** $10K start, 15 max positions, SectorEdge ranker, 1-day entry delay, 1.5% risk, 2.0 nAtr, 1.0x leverage.

| Metric | Before Fixes | After Fixes | Delta |
|---|---|---|---|
| Starting Capital | $10,000 | $10,000 | — |
| Final Capital | $395,432 | **$327,469** | -$67,963 (-17%) |
| Peak Capital | $395,432 | $350,693 | -$44,739 |
| CAGR | 44.4% | **41.7%** | -2.7pp |
| Max Drawdown | 16.7% | **25.9%** ($42,052) | +9.2pp |
| Total Trades | 899 | 906 | +7 |
| Win Rate | 47.3% | 48.2% | +0.9pp |
| Edge | +5.24% | +5.22% | -0.02pp |
| Avg Win / Loss | 17.57% / -5.82% | 17.12% / -5.87% | minor |
| Profit Factor | 2.79 | 2.81 | +0.02 |
| EC Score | 96.0 | 92.0 | -4.0 |

**Yearly edge (position-sized, after fixes):**

| Year | Trades | Edge | Tradeable? |
|---|---|---|---|
| 2016 | ~90 | +2.63% | T |
| 2017 | ~57 | +5.10% | T |
| 2018 | ~64 | +4.37% | T |
| 2019 | ~74 | +5.51% | T |
| 2020 | ~82 | +13.69% | T |
| 2021 | ~108 | +6.22% | T |
| 2022 | ~114 | -0.00% | |
| 2023 | ~86 | +8.39% | T |
| 2024 | ~85 | +6.40% | T |
| 2025 | ~146 | +3.41% | T |

**9/10 years profitable** (2022 essentially flat at -0.004%), 9/10 tradeable.

**Exit reasons:** EMA cross 92.6% (839 exits, +6.47% avg, 52.1% WR, 52d hold), stop loss 7.4% (67 exits, -10.48% avg, 10.8d hold).

**Impact analysis:**
- **Edge nearly unchanged** (-0.02pp) — P1 triggerDate and P3 survivorship had minimal effect on trade selection, consistent with unlimited results.
- **Max drawdown increased** (16.7% → 25.9%) — The P2 daily M2M fix captures intra-trade drawdowns that exit-based tracking missed. With 15 concurrent positions, simultaneous declines create portfolio drawdowns invisible to exit-only tracking. Note: an initial M2M implementation had a bug where `processExit()` compared `cash` against a `peakCapital` that included unrealized P/L, producing a phantom 33.0% DD. Fixed by removing drawdown tracking from exit handler — the daily M2M block correctly computes `cash + unrealizedPnl` for all open positions.
- **Final capital lower** (-17%) — Partly from marginally fewer winning trades, but mainly because the daily equity curve is now more accurate, affecting peak capital tracking and position sizing calculations.
- **EC dropped from 96.0 to 92.0** — 2022 flipped from +0.14% to essentially zero, losing the "10/10 profitable" status.

#### Monte Carlo: Position-Sized Trades (10K iterations)

**Trade Shuffling (drawdown distribution):**

| Metric | Value |
|---|---|
| Probability of Profit | **100%** |
| Max Drawdown p5 (best case) | 15.5% |
| Max Drawdown p25 | 18.0% |
| Max Drawdown p50 (median) | 20.2% |
| Max Drawdown p75 | 23.0% |
| Max Drawdown p95 (worst case) | **28.3%** |

The actual 25.9% max drawdown falls between p75 (23.0%) and p95 (28.3%) of the shuffled distribution. The historical trade ordering produced a somewhat worse-than-average drawdown, consistent with correlated losing trades clustering in 2022, but within the expected range.

**Bootstrap Resampling (edge confidence):**

| Percentile | Edge | Win Rate |
|---|---|---|
| p5 (worst case) | **+4.16%** | 45.5% |
| p25 | +4.77% | 47.1% |
| p50 (median) | +5.20% | 48.2% |
| p75 | +5.66% | 49.3% |
| p95 (best case) | +6.34% | 51.0% |
| Probability of Profit | **100%** | — |

Even at p5, the position-sized edge (+4.16%) is well above the 1.5% tradeable threshold. 100% probability of profit across all 10K bootstrap iterations.

#### Walk-Forward Validation (5yr IS / 1yr OOS, step 1yr)

Walk-forward tests whether the strategy's edge persists out-of-sample. Each window trains on 5 years of in-sample data, then tests on the next 1 year out-of-sample. OOS backtests use 15 max positions with 1-day entry delay (realistic execution).

**Per-Window Results:**

| Window | IS Period | OOS Year | IS Edge | OOS Edge | IS Trades | OOS Trades | WFE |
|---|---|---|---|---|---|---|---|
| 1 | 2016-2020 | 2021 | +6.94% | **+4.20%** | 3,517 | 110 | 0.61 |
| 2 | 2017-2021 | 2022 | +6.30% | **-0.18%** | 3,548 | 117 | -0.03 |
| 3 | 2018-2022 | 2023 | +4.98% | **+6.90%** | 3,741 | 95 | 1.39 |
| 4 | 2019-2023 | 2024 | +5.53% | **+4.85%** | 4,797 | 95 | 0.88 |

**Aggregate:**

| Metric | Value |
|---|---|
| OOS Trades | 417 |
| OOS Edge (trade-weighted) | **+3.74%** |
| OOS Win Rate | 45.6% |
| Walk-Forward Efficiency | **0.63** |

**Derived Sector Rankings (top 5 per window):**

| Window | OOS Year | Top Sectors (from IS) |
|---|---|---|
| 1 | 2021 | XLY, XLK, XLV, XLP, XLF |
| 2 | 2022 | XLY, XLK, XLI, XLF, XLV |
| 3 | 2023 | XLY, XLI, XLK, XLE, XLF |
| 4 | 2024 | XLI, XLY, XLF, XLK, XLP |

**Key findings:**

1. **WFE = 0.63 (robust).** A WFE above 0.50 indicates the strategy retains more than half its in-sample edge out-of-sample. The VCP strategy retains 63% — strong evidence of genuine alpha rather than curve-fitting.
2. **3 of 4 OOS windows are profitable.** Only 2022 is negative (-0.18%), consistent with the strategy's known weakness in that year across all testing modes.
3. **OOS edge (+3.74%) remains well above tradeable threshold** (1.5%). The strategy works on unseen data.
4. **Window 3 (OOS 2023) outperforms in-sample** — OOS edge +6.90% vs IS +4.98% (WFE 1.39). This means the strategy discovered patterns in 2018-2022 that worked even better in 2023.
5. **Sector rankings are stable** — XLY, XLK, XLI, XLF consistently appear in top 5 across all windows. The strategy's sector preferences are not random.
6. **2022 is the strategy's Achilles' heel** — negative in walk-forward, barely positive in full backtest, and the source of the worst drawdown. This is a known characteristic of VCP (trend-following struggles in sustained bear markets).

#### Drawdown Duration Analysis

Analysis of the top 10 drawdown episodes from the position-sized equity curve (2,486 daily M2M points).

| # | Depth | Peak Date | Peak $ | Trough Date | Trough $ | Recovery | Decline | Recov | Total |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **25.9%** | 2022-04-20 | $101,476 | 2022-09-23 | $75,171 | 2023-05-04 | 156d | 223d | **379d** |
| 2 | 16.4% | 2020-02-12 | $32,455 | 2020-04-13 | $27,144 | 2020-06-02 | 61d | 50d | 111d |
| 3 | 14.5% | 2016-09-07 | $12,668 | 2016-11-04 | $10,827 | 2017-01-11 | 58d | 68d | 126d |
| 4 | 13.7% | 2025-02-05 | $258,231 | 2025-04-21 | $222,879 | 2025-06-24 | 75d | 64d | 139d |
| 5 | 13.4% | 2025-09-18 | $314,471 | 2025-11-20 | $272,420 | 2025-12-03 | 63d | 13d | 76d |

**Worst drawdown detail:**
- Peak: $101,476 on 2022-04-20 → Trough: $75,171 on 2022-09-23 ($26,305 lost)
- Decline phase: 156 days (5 months) — slow grind, not a crash
- Recovery phase: 223 days (7 months) — took longer to recover than to decline
- Total underwater: **379 days (12 months)** — one full year peak-to-recovery

**Key findings:**
1. All drawdowns recovered. No open-ended losses in the backtest period.
2. The worst drawdown (25.9%) took 12 months total — psychologically demanding but manageable.
3. COVID drawdown (16.4%) was short: 111 days total with a fast 50-day recovery (V-shape).
4. Most drawdowns recover in 2-5 months. Only the 2022 bear market took longer.
5. Decline phases are typically 1-5 months. Recoveries are often comparable or faster.

#### SPY Correlation & Risk-Adjusted Metrics

Daily return correlation analysis between VCP strategy (position-sized) and SPY over 2,485 overlapping trading days.

| Metric | Value |
|---|---|
| Correlation with SPY | **0.502** |
| Beta | **0.560** |
| Alpha (annualized) | **+27.7%** |
| Strategy ann. return | 37.1% |
| SPY ann. return | 16.7% |
| Sharpe Ratio | **2.04** |
| Sortino Ratio | **3.20** |
| Calmar Ratio | **1.61** (CAGR 41.7% / MaxDD 25.9%) |

**Interpretation:**
1. **Moderate correlation (0.502)** — the strategy is a mix of alpha and beta. About half the daily return variance is explained by market exposure, the other half is genuine stock selection alpha.
2. **Beta of 0.56** — the strategy has less market exposure than a buy-and-hold SPY portfolio. This makes sense: the `marketUptrend()` filter keeps the strategy out of the market during sustained downtrends, reducing drawdown exposure.
3. **Alpha of +27.7% annualized** — the vast majority of returns come from genuine alpha, not market beta. Even if SPY returned 0%, the strategy would generate 27.7% annually from stock selection and timing.
4. **Sharpe 2.04** — excellent risk-adjusted returns. Above 1.0 is good, above 2.0 is exceptional.
5. **Sortino 3.20** — even better on downside-adjusted basis, confirming the strategy's losses are well-controlled relative to gains.
6. **Calmar 1.61** — strong return per unit of drawdown risk. Above 1.0 is considered good for trend-following strategies.

#### Validation Summary

The VCP strategy passes all validation checks:

1. **Edge is real** — P1 (OB look-ahead) and P3 (survivorship) reduced edge by only 0.03pp in unlimited mode. The strategy's alpha is not inflated by methodological artifacts.
2. **Edge is statistically robust** — Bootstrap p5 edge is +5.28% (unlimited) and +4.16% (position-sized), both well above tradeable threshold. Tight confidence intervals confirm edge is broadly distributed, not driven by outliers.
3. **Edge persists out-of-sample** — Walk-forward efficiency of 0.63, with OOS edge +3.74% across 417 trades. 3/4 OOS windows profitable.
4. **Drawdown was underreported** — P2 (daily M2M) revealed true max drawdown of 25.9% vs previously reported 16.7%. Traders should size positions expecting ~25% peak-to-trough drawdowns, not ~17%.
5. **Drawdown is within expected range** — Monte Carlo shows the median shuffled drawdown is 20.2%, and the actual 25.9% falls between p75 (23.0%) and p95 (28.3%). Somewhat worse than average due to correlated losses in 2022, but not extreme.
6. **100% probability of profit** across all Monte Carlo scenarios (both techniques).
7. **Genuine alpha, not just beta** — SPY correlation 0.502, beta 0.56, annualized alpha +27.7%. The majority of returns come from stock selection, not market exposure. Sharpe 2.04, Sortino 3.20, Calmar 1.61.
8. **Drawdowns recover** — Worst drawdown (25.9%) took 12 months peak-to-recovery. All drawdowns in the backtest period recovered fully.

---

### Pre-2016 Out-of-Era Test (2006-2015) (2026-03-09)

#### Goal

Test whether the VCP strategy's edge persists in a completely different market regime — one that includes the 2008 Global Financial Crisis, the 2011 European debt crisis, and the 2015 China/Fed correction. The quant analyst flagged "Pre-2016 regimes: Never tested in 2008-style crash or liquidity crisis" as a gap.

#### Unlimited Backtest (2006-2015)

| Metric | 2006-2015 | 2016-2025 (baseline) | Delta |
|---|---|---|---|
| Total Trades | 4,206 | 9,555 | -56% |
| Win Rate | 51.7% | 48.6% | +3.1pp |
| Edge | 4.33% | 5.70% | -1.37pp |
| Avg Win / Loss | 13.21% / -5.16% | 18.07% / -6.01% | smaller swings |
| W/L Ratio | 2.56x | 3.01x | -0.45x |
| Profit Factor | 16.91 | 2.41 | — |
| EC Score | **76.7 (Good)** | **96.0 (Excellent)** | -19.3 |
| Profitable Years | 9/10 | 10/10 | -1 |
| Tradeable Years | 7/10 | 9/10 | -2 |

**Yearly edge:**

| Year | Edge | Tradeable? |
|---|---|---|
| 2006 | +4.27% | T |
| 2007 | +0.71% | |
| **2008** | **-3.63%** | |
| 2009 | +3.94% | T |
| 2010 | +8.87% | T |
| 2011 | +1.98% | T |
| 2012 | +6.80% | T |
| 2013 | +6.51% | T |
| 2014 | +2.20% | T |
| 2015 | +1.15% | |

**Sector performance (all profitable):**

| Sector | Trades | WR | Edge |
|---|---|---|---|
| XLE | 151 | 48.3% | +5.56% |
| XLY | 493 | 55.2% | +5.53% |
| XLK | 562 | 50.7% | +5.28% |
| XLI | 743 | 54.1% | +5.15% |
| XLV | 488 | 53.3% | +5.09% |
| XLC | 122 | 51.6% | +4.51% |
| XLRE | 234 | 56.4% | +3.71% |
| XLP | 222 | 45.9% | +3.06% |
| XLU | 210 | 52.9% | +3.06% |
| XLF | 752 | 49.2% | +2.87% |
| XLB | 229 | 45.0% | +2.12% |

**Exit reasons:** EMA cross 3,905 (92.8%, +5.28% avg, 55.6% WR, 57d hold), stop loss 301 (7.2%, -7.97% avg, 9d hold).

#### Position-Sized Results (2006-2015, $10K Starting Capital)

**Configuration:** $10K start, 15 max positions, SectorEdge ranker, 1-day entry delay, 1.5% risk, 2.0 nAtr, 1.0x leverage.

| Metric | 2006-2015 | 2016-2025 | Delta |
|---|---|---|---|
| Starting → Final | $10K → $71K | $10K → $327K | -$256K |
| CAGR | **21.7%** | **41.7%** | -20.0pp |
| Max Drawdown | **24.0%** ($9,142) | **25.9%** ($42,052) | -1.9pp (better) |
| Total Trades | 844 | 906 | -62 |
| Win Rate | 45.6% | 48.2% | -2.6pp |
| Edge | 3.01% | 5.22% | -2.21pp |
| W/L Ratio | 2.48x | 2.92x | -0.44x |
| EC Score | 82.3 (Excellent) | 92.0 (Excellent) | -9.7 |

**Yearly edge (position-sized):**

| Year | Trades | Edge | Tradeable? |
|---|---|---|---|
| 2006 | — | +4.27% | T |
| 2007 | — | +2.16% | T |
| **2008** | — | **-2.87%** | |
| 2009 | — | +3.90% | T |
| 2010 | — | +6.65% | T |
| 2011 | — | +2.99% | T |
| 2012 | — | +2.56% | T |
| 2013 | — | +7.67% | T |
| 2014 | — | +5.28% | T |
| 2015 | — | +0.78% | |

9/10 profitable, 8/10 tradeable.

**Exit reasons:** EMA cross 776 (91.9%, +3.98% avg, 49.6% WR, 52d hold), stop loss 68 (8.1%, -8.04% avg, 11d hold).

**Sector performance (10/11 profitable):**

| Sector | Trades | WR | Edge |
|---|---|---|---|
| XLE | 29 | 51.7% | +6.96% |
| XLC | 51 | 56.9% | +5.15% |
| XLY | 94 | 44.7% | +4.11% |
| XLU | 36 | 55.6% | +4.04% |
| XLI | 211 | 47.4% | +3.50% |
| XLK | 123 | 44.7% | +3.41% |
| XLRE | 35 | 48.6% | +2.47% |
| XLV | 94 | 38.3% | +2.20% |
| XLF | 103 | 44.7% | +1.16% |
| XLB | 32 | 34.4% | +0.31% |
| **XLP** | 36 | 38.9% | **-1.07%** |

#### Risk-Adjusted Metrics

| Metric | 2006-2015 | 2016-2025 | Rating |
|---|---|---|---|
| Sharpe Ratio | **1.27** | **2.04** | Good vs Excellent |
| Sortino Ratio | **1.27** | **3.20** | Concerning vs Excellent |
| Calmar Ratio | **0.90** | **1.61** | Below threshold vs Strong |
| SPY Correlation | 0.608 | 0.502 | Borderline vs Moderate |
| Beta | 0.539 | 0.560 | Similar |
| Alpha (ann.) | +18.1% | +27.7% | Excellent vs Excellent |

#### Top 5 Drawdowns

| # | Depth | Period | Decline | Recovery | Total |
|---|---|---|---|---|---|
| 1 | **24.0%** | Aug 2008 → Sep 2009 | 145d | 138d | **283d** |
| 2 | 15.6% | Dec 2007 → May 2008 | 55d | 47d | 102d |
| 3 | 15.5% | Apr 2010 → Oct 2010 | 63d | 71d | 134d |
| 4 | 14.2% | Mar 2006 → Oct 2006 | 85d | 64d | 149d |
| 5 | 12.9% | Nov 2014 → Jun 2015 | 63d | 82d | 145d |

Worst drawdown (24.0%) took 283 days — shorter than the 2022 drawdown (379 days) in the modern period.

#### Monte Carlo Validation (10K iterations)

**Bootstrap Resampling (edge confidence):**

| Percentile | Edge | Win Rate |
|---|---|---|
| p5 (worst case) | **+2.27%** | 42.8% |
| p25 | +2.69% | 44.4% |
| p50 (median) | +3.00% | 45.6% |
| p75 | +3.31% | 46.8% |
| p95 (best case) | +3.76% | 48.5% |
| Probability of Profit | **100%** | — |

**Trade Shuffling (drawdown distribution):**

| Percentile | Max Drawdown |
|---|---|
| p5 (best case) | 13.2% |
| p25 | 15.4% |
| p50 (median) | 17.4% |
| p75 | 20.1% |
| p95 (worst case) | 25.0% |

Actual 24.0% DD falls between p75 (20.1%) and p95 (25.0%) — moderate correlation clustering from GFC.

#### Key Findings

1. **The edge is real across eras.** 4.33% unlimited edge and 3.01% position-sized edge in a completely different market regime. 100% Monte Carlo probability of profit. p5 edge (2.27%) above tradeable threshold.

2. **2008 is the first and only losing year** (-3.63% unlimited, -2.87% position-sized). The `marketUptrend()` filter contained GFC damage to -24% max DD during a -56% SPY crash — impressive for a long-only momentum strategy.

3. **Strategy was weaker in 2006-2015.** Roughly half the CAGR (21.7% vs 41.7%), lower edge (3.01% vs 5.22%), worse risk-adjusted metrics (Calmar 0.90 vs 1.61). Smaller average winners (12.72% vs 17.12%) suggest less explosive breakouts in that era.

4. **Higher market dependency.** SPY correlation 0.608 vs 0.502 — more of the returns came from market exposure. Alpha still excellent (+18.1% annualized) but narrower than 2016-2025 (+27.7%).

5. **Sortino of 1.27 is concerning** — downside volatility nearly equals total volatility, meaning losing days were as volatile as winning days. This improved dramatically in 2016-2025 (3.20).

6. **GFC drawdown recovered faster than 2022.** 283 days (9 months) vs 379 days (12 months). The V-shaped GFC recovery was faster than the 2022 grind.

7. **All sectors profitable in unlimited mode**, but XLP turns negative (-1.07%) in position-sized mode (only 36 trades — small sample).

8. **Addresses quant analyst gap.** "Pre-2016 regimes: Never tested in 2008-style crash" is now resolved. The strategy survives the GFC with a manageable drawdown and preserves positive edge across the full period.

---

### Drawdown-Responsive Position Sizing (2026-03-13)

#### Concept

Reduce risk per trade when the portfolio is in drawdown, scaling back up automatically as equity recovers to new highs. This is a position sizing overlay — it does not change entry signals or trade selection, only the dollar amount risked per trade.

**Thresholds tested:**

| Drawdown Depth | Risk Multiplier | Effective Risk (base 1.5%) |
|---|---|---|
| < 5% | 1.0 (full) | 1.5% |
| >= 5% | 0.67 | 1.005% |
| >= 10% | 0.33 | 0.495% |

**Implementation:** Optional `drawdownScaling` field in `PositionSizingConfig`. When present, `PositionSizingService` computes current drawdown % from peak capital vs last-known portfolio value (cash + unrealized P/L) at each entry and applies the deepest matching threshold's risk multiplier. Input validation enforces `drawdownPercent > 0` and `riskMultiplier` in 0.0..1.0. When absent, behavior is unchanged.

#### Results (2016-2025, $10K, 15 max positions, SectorEdge, entry delay 1)

| Metric | Baseline | DD Scaling | Delta |
|---|---|---|---|
| Total Trades | 893 | 893 | same |
| Win Rate | 48.7% | 48.7% | same |
| Edge | +5.74% | +5.74% | same |
| EC Score | 96.0 | 96.0 | same |
| **Final Capital** | **$459,565** | **$412,086** | **-$47K (-10%)** |
| **CAGR** | **46.4%** | **45.0%** | **-1.4pp** |
| **Max Drawdown** | **21.2%** | **16.2%** | **-5.0pp (-24%)** |
| Calmar | 2.18 | **2.78** | **+0.60 (+28%)** |

#### Key Findings

1. **Excellent risk-return tradeoff.** Giving up just 1.4pp of CAGR buys 5.0pp of drawdown reduction. Calmar improves from 2.18 to 2.78 (+28%).
2. **Max DD cut from 21.2% to 16.2%.** Moves into the "psychologically sustainable" <20% zone.
3. **Compounding drag is modest.** $460K → $412K (-10%) over 10 years — far less than the pre-fix estimate (-38%) which was caused by a drawdown calculation bug (using cash instead of portfolio value).
4. **Trade metrics are identical.** Same trades, same edge, same EC. Only position sizes change.
5. **The feature is optional and configurable.** Omit `drawdownScaling` for baseline behavior. Different threshold levels (e.g., 7%/15% instead of 5%/10%) could shift the tradeoff.

#### Conclusion

Drawdown-responsive sizing is a net positive for risk-adjusted returns. The Calmar improvement from 2.18 to 2.78 is the strongest signal — significantly more return per unit of drawdown risk, at a modest -1.4pp CAGR cost. Threshold sweep pending to find optimal levels.

#### API Usage

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "riskPercentage": 1.5,
    "nAtr": 2.0,
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        {"drawdownPercent": 5.0, "riskMultiplier": 0.67},
        {"drawdownPercent": 10.0, "riskMultiplier": 0.33}
      ]
    }
  }
}
```

---

### Potential Next Steps
- ~~Entry condition ablation study~~ — Done. `priceAbove(50)` removed as redundant (see Ablation Study)
- ~~Parameter sensitivity on `volatilityContracted`~~ — Done. maxAtrMultiple changed from 2.5 to 3.5 (see VC Sweep)
- ~~Parameter sensitivity on `priceNearDonchianHigh`~~ — Done. 3.0% confirmed as optimal (see Donchian Distance Sweep)
- Parameter sensitivity on `volumeAboveAverage` multiplier (1.0 vs 1.2 vs 1.5)
- ~~Sector exclusion testing~~ — Done. No exclusions recommended (see Sector Exclusion Analysis)
- ~~Monte Carlo validation (10,000 iterations)~~ — Done. Edge robust at p5=+4.16% position-sized (see Strategy Validation)
- ~~Strategy validation (P1-P3 fixes)~~ — Done. Edge is real, drawdown was underreported (see Strategy Validation)
- ~~Walk-forward validation~~ — Done. WFE=0.63, OOS edge +3.74% (see Walk-Forward Validation)
- ~~Pre-2016 regime testing~~ — Done. 2006-2015 backtest: CAGR 21.7%, MaxDD 24%, edge 3.01%, 100% MC profit probability (see Pre-2016 Out-of-Era Test)
- ~~Test with different exit strategies~~ — Done. Trailing stop (3.0/4.0/5.0 ATR) and faster EMA (8/15) all fail to beat baseline (see ATR Trailing Stop & Faster EMA Sweep)
- ~~Drawdown-responsive position sizing~~ — Done. Calmar +33%, max DD -36%, CAGR -6.6pp (see Drawdown-Responsive Position Sizing)
- Drawdown scaling threshold sweep — test different threshold levels (e.g., 7%/15%, 3%/8%, single threshold)
- Combined portfolio simulation — run VCP + Mjolnir together to measure diversification benefit
- **Options-based position sizing** — With $10K and 1.5% risk, stock positions cost ~$1,875 each, so only 4-5 fit before 100% capital utilization. Options (calls or debit spreads) would use ~$200-400 per position, enabling the full 15 concurrent positions at small account sizes. Explore: delta target (e.g., 0.70 calls), expiration selection (45-60 DTE to cover avg 54-day hold), stop-loss translation (% of premium vs ATR-based)

---

### Quant Analyst Recommendations (2026-03-06)

Independent review of the Strategy Validation section. Findings organized by priority.

#### Assessment: Conditionally Ready for Live Trading

The strategy demonstrates genuine statistical edge backed by rigorous validation. The P1-P3 bias corrections cost only 0.03pp of edge — a strong result. However, several issues must be addressed before full deployment.

#### Risk Summary

| Finding | Assessment | Action |
|---|---|---|
| Edge existence | Confirmed (5.67% unlimited, 5.22% position-sized) | None |
| Edge robustness | Strong (bootstrap p5 = +5.28% unlimited, +4.16% position-sized) | None |
| WFE 0.63 | Good but only 4 OOS windows; per-window WFEs dispersed (-0.03 to 1.39) | Extend backtest period for more windows |
| Max drawdown 25.9% | Corrected from phantom 33.0% (processExit bug). Plan for 35-40% worst case | Resize positions |
| DD within MC range | Actual 25.9% between p75 (23.0%) and p95 (28.3%). Some correlation clustering but not extreme | Monitor, no urgent action |
| 2022 vulnerability | Near-zero or negative across all testing modes | Accept; plan for worse bear markets |
| SectorEdge ranker | Potentially look-ahead contaminated — full-sample ranking used over same period | Re-run with walk-forward-derived rankings |
| ~~Pre-2016 regimes~~ | Tested: 2006-2015 backtest survives GFC with 24% DD, 21.7% CAGR, edge 3.01% | Done (see Pre-2016 Out-of-Era Test) |
| Execution costs | Unmodeled (commissions, slippage) | Quantify before live trading |
| Alpha concentration | Single factor (order blocks) provides all alpha (-4.02pp if removed) | Test OB parameter sensitivity |
| SPY correlation | 0.502 (moderate). Beta 0.56, alpha +27.7% | Confirmed: mostly alpha, not beta |
| Risk-adjusted | Sharpe 2.04, Sortino 3.20, Calmar 1.61 | All excellent |
| DD duration | Worst: 12 months (5mo decline + 7mo recovery). All recovered. | Manageable |

#### Priority Validations Before Going Live

1. **Stress-test SectorEdge ranker OOS** — Run position-sized backtest using walk-forward-derived sector rankings only (not full-sample ranking). If CAGR drops significantly, the ranker contributes look-ahead bias. Highest priority gap.
2. **Quantify execution costs** — Run position-sized backtest with estimated round-trip commissions + slippage (e.g., 0.05-0.10% per trade). With +5.22% avg edge, even 0.20% costs are fine, but confirm it.
3. **Drawdown duration analysis** — How long did the worst drawdown last? 33% over 3 months vs 12 months is very different psychologically. Recovery time matters as much as depth.
4. **OB parameter sensitivity** — Since order blocks are the alpha engine, test ROC momentum threshold/lookback stability. If edge is sensitive to these parameters, the strategy is more fragile than it appears.
5. **Index correlation analysis** — Compute daily return correlation with SPY/QQQ. If correlation > 0.6-0.7, much of the "alpha" is beta (market exposure, not stock selection skill).
6. **Paper trade 3-6 months** — Validate live signal generation matches backtest. Confirm execution quality. Build psychological comfort with trade frequency (~2/week) and drawdown profile.

#### Position Sizing Adjustments

1. **Reduce risk per trade from 1.5% to 1.0% initially** — Scales positions down by 1/3. Expected CAGR ~28-30%, expected drawdown ~17-20%. Scale back up after 6-12 months of confirmed live performance.
2. **Reduce max positions from 15 to 10** — Mechanically limits correlation clustering exposure.
3. **Portfolio heat limit** — Cap total open risk (sum of all position risk amounts) at 10-15% of portfolio.
4. **Drawdown response plan** — Define in advance: 15% DD = review, 20% DD = reduce risk to 1.0%, 30% DD = half-size, 40% DD = stop trading, post-mortem.

#### WFE Interpretation Notes

- 0.63 is above the 0.50 robustness threshold — strategy retains 63% of IS edge OOS
- Only 4 windows is a small sample; per-window WFEs are dispersed (-0.03 to 1.39)
- 2022 OOS window (WFE = -0.03) is a structural failure mode, not just a bad year — the strategy cannot predict bear-market performance from bull-market training data
- WFE tested fixed parameters across time (more realistic than re-optimizing), meaning 0.63 confirms parameter durability

#### Drawdown Clustering Interpretation

The actual 25.9% max drawdown falls between MC p75 (23.0%) and p95 (28.3%), indicating the historical trade ordering was somewhat unlucky but within expected range. Some correlation clustering exists due to:
- Market-wide selloffs hitting multiple concurrent positions simultaneously
- Sector correlation during stress (all sectors sold off together in 2022)
- Stop-loss clustering (multiple positions hitting 2.5 ATR stops in a narrow window)

Use 25.9% as the baseline expectation, plan for 35-40% in a regime worse than any seen in 2016-2025.
