# VCP Strategy Development

## Current State (2026-02-27)

### Entry Strategy
```kotlin
entryStrategy {
    // MARKET
    marketUptrend()

    // SECTOR
    sectorUptrend()

    // STOCK
    uptrend()
    volatilityContracted(lookbackDays = 10, maxAtrMultiple = 3.5)
    aboveBearishOrderBlock(consecutiveDays = 1, ageInDays = 0)
    priceNearDonchianHigh(maxDistancePercent = 3.0)
    volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
    minimumPrice(10.0)
}
```
*Changes: `priceAbove(50)` removed (redundant with `uptrend()`), `volatilityContracted` loosened from 2.5→3.5, `sectorUptrend()` added.*

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
| Total Trades | 8,752 |
| Win Rate | 48.6% |
| Edge | 5.84% |
| Avg Win / Loss | 18.44% / -6.12% |
| Win/Loss Ratio | 3.01x |
| Edge Consistency | **96.0/100 (Excellent)** |

### Yearly Edge
| Year | Edge | Tradeable (>=1.5%)? |
|---|---|---|
| 2016 | +5.93% | T |
| 2017 | +7.91% | T |
| 2018 | +2.31% | T |
| 2019 | +6.28% | T |
| 2020 | +11.21% | T |
| 2021 | +2.46% | T |
| 2022 | +0.85% | |
| 2023 | +7.34% | T |
| 2024 | +3.81% | T |
| 2025 | +6.35% | T |

**10/10 years profitable**, 9/10 years have tradeable edge (>=1.5%).

### Exit Reasons
- EMA cross (10/20): 3,429 exits (93.5%), +5.75% avg, 48.9% WR, 51d avg hold
- Stop loss (2.5 ATR): 238 exits (6.5%), -8.47% avg, 0% WR, 8d avg hold

### Sector Performance (all sectors profitable)
| Sector | Trades | WR | Edge |
|---|---|---|---|
| XLY | 366 | 50.3% | +7.67% |
| XLI | 584 | 53.9% | +6.50% |
| XLC | 133 | 39.1% | +6.42% |
| XLE | 157 | 44.6% | +5.07% |
| XLK | 504 | 44.8% | +4.95% |
| XLF | 691 | 44.3% | +4.21% |
| XLV | 492 | 41.7% | +4.17% |
| XLRE | 221 | 44.3% | +3.35% |
| XLU | 147 | 42.9% | +3.23% |
| XLP | 144 | 43.8% | +2.83% |
| (none) | 59 | 45.8% | +1.64% |
| XLB | 169 | 39.6% | +1.56% |

### EC Score Breakdown
- Profitable Periods: 100% (10/10 years positive, weight 40%)
- Stability (Tradeable Edge): 90% (9/10 years >= 1.5%, weight 40%)
- Downside: 100% (worst year +0.85%, weight 20%)
- **Total: 96.0 (Excellent)**

---

## Strategy Design

### Concept
Volatility Contraction Pattern (VCP) inspired by Mark Minervini, with bearish order blocks as resistance levels. The VCP captures stocks in strong uptrends that consolidate with decreasing volatility, then break out above institutional supply zones with volume confirmation.

### Entry Condition Logic

| Condition | Purpose | Parameters |
|---|---|---|
| `marketUptrend()` | Broad market filter | default |
| `sectorUptrend()` | Sector must be in uptrend | default |
| `uptrend()` | Minervini trend template (includes price > 50 EMA) | default |
| `volatilityContracted()` | VCP contraction phase — range/ATR squeeze | lookback=10, maxAtr=3.5 |
| `aboveBearishOrderBlock()` | Breaking above OB resistance | consecutiveDays=1, ageInDays=0 |
| `priceNearDonchianHigh()` | Near new highs (breakout confirmation) | maxDistance=3.0% |
| `volumeAboveAverage()` | Volume surge on breakout | multiplier=1.2, lookback=20 |
| `minimumPrice(10.0)` | Filter penny stocks | $10 |

### New Condition: VolatilityContractedCondition

Measures how tight recent price action is relative to the stock's ATR. When the price range over a lookback period is small relative to ATR, volatility is contracted (the VCP "squeeze").

**Formula:** `(maxHigh - minLow) / ATR <= maxAtrMultiple` over the last N trading days.

- `lookbackDays = 10` — Number of recent trading days to measure price range
- `maxAtrMultiple = 3.5` — Maximum allowed range as ATR multiple (lower = tighter contraction)

**Rationale:** ATR reflects the stock's normal daily volatility. If the total range over 10 days is less than 3.5x a single day's expected range, price action is relatively tight — the "coiled spring" before a breakout. Loosened from 2.5 to 3.5 after VC sweep showed higher edge and EC at 3.5 (see Volatility Contraction Sweep).

---

## MarketBreadthTrending Parameter Sweep (2026-02-27)

### Goal
Test whether adding `marketBreadthTrending(minWidth)` improves the VCP strategy by filtering out entries during choppy/range-bound markets.

All tests: 2016-01-01 to 2025-12-31, STOCK only, unlimited positions, no sector exclusions.

### Results

| Config | Trades | WR | Edge | PF | Avg Win | Avg Loss | EC | Prof Yrs | Stability | Downside |
|---|---|---|---|---|---|---|---|---|---|---|
| **Baseline** | **3,667** | **45.7%** | **+4.83%** | **0.664** | **16.95%** | **5.38%** | **92.0** | **100%** | **80%** | **100%** |
| MBT(20) | 3,135 | 46.6% | +5.03% | 0.638 | 17.00% | 5.44% | 87.6 | 90% | 80% | 98% |
| **MBT(25)** | **2,691** | **47.3%** | **+5.29%** | **0.523** | **17.23%** | **5.41%** | **91.5** | **90%** | **90%** | **97%** |
| MBT(30) | 2,066 | 49.6% | +6.01% | 0.383 | 17.70% | 5.47% | 87.4 | 90% | 80% | 97% |

### Yearly Edge

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

### Yearly Trades

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

### Key Findings

1. **Higher minWidth monotonically improves edge and WR** — MBT(30) has the best per-trade edge (+6.01%) and WR (49.6%)
2. **MBT(25) is the sweet spot** — EC 91.5 (nearly matching baseline's 92.0), stability jumps to 90% (9/10 tradeable years), edge +5.29%
3. **MBT(30) kills 2021** — flips it negative (-0.31%), dropping EC to 87.4 despite highest edge. Same failure mode as Mjolnir's MBT testing
4. **2018 is the biggest beneficiary** — all MBT variants improve it significantly (0.58% → 2.48-3.64%), making it tradeable
5. **2022 is the weak spot** — flips slightly negative at MBT(20) and MBT(25) (-0.18%, -0.27%); MBT(30) stays barely positive (+0.21%)
6. **2025 improves across all variants** — baseline +4.03% → MBT(25) +6.07% → MBT(30) +7.27%

### Conclusion

The MBT filter improves per-trade quality but introduces marginal losing years. The tradeoff:
- **Baseline**: 10/10 profitable years, lower edge (4.83%), EC 92.0 — most robust
- **MBT(25)**: 9/10 profitable years (2022: -0.27%), higher edge (5.29%), EC 91.5, best stability (90%) — best risk-adjusted
- **MBT(30)**: 9/10 profitable years (2021: -0.31%), highest edge (6.01%), EC 87.4 — too aggressive

**Not adding MBT to the default strategy.** The baseline's 10/10 profitable years and 92.0 EC is the VCP strategy's signature strength. MBT(25) is a viable alternative for traders who prefer higher edge over zero losing years.

---

## Volatility Contraction (maxAtrMultiple) Sweep (2026-02-27)

### Goal
The ablation study showed that removing `volatilityContracted` entirely *improves* edge (+0.64pp) and EC (+4.0). Test whether loosening the parameter (instead of removing) finds a sweet spot that keeps the VCP contraction concept while capturing more profitable trades.

### Results

| Config | Trades | WR | Edge | Avg Win | Avg Loss | EC | Prof Yrs | Tradeable Yrs |
|---|---|---|---|---|---|---|---|---|
| VC 2.5 (original) | 3,667 | 45.7% | +4.83% | 16.95% | 5.38% | 92.0 | 10/10 | 8/10 |
| VC 3.0 | 7,062 | 46.5% | +5.27% | 17.98% | 5.77% | 88.0 | 9/10 | 8/10 |
| **VC 3.5** | **10,037** | **47.8%** | **+5.51%** | **18.14%** | **6.04%** | **96.0** | **10/10** | **9/10** |
| VC 4.0 | 12,336 | 48.2% | +5.37% | 17.90% | 6.31% | 96.0 | 10/10 | 9/10 |
| VC 5.0 | 14,927 | 48.9% | +5.40% | 17.96% | 6.60% | 96.0 | 10/10 | 9/10 |
| No VC | 16,329 | 49.2% | +5.47% | 18.10% | 6.74% | 96.0 | 10/10 | 9/10 |

### Yearly Edge

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

### EC Breakdown

| Config | Score | Prof Periods | Stability | Downside |
|---|---|---|---|---|
| VC 2.5 | 92.0 | 100.0 | 80.0 | 100.0 |
| VC 3.0 | 88.0 | 90.0 | 80.0 | 100.0 |
| **VC 3.5** | **96.0** | **100.0** | **90.0** | **100.0** |
| VC 4.0 | 96.0 | 100.0 | 90.0 | 100.0 |
| VC 5.0 | 96.0 | 100.0 | 90.0 | 100.0 |
| No VC | 96.0 | 100.0 | 90.0 | 100.0 |

### Key Findings

1. **VC 3.5 is the optimal value** — highest edge (+5.51%), EC 96.0, 10/10 profitable years, 9/10 tradeable years. It's the tightest contraction that achieves the maximum EC score.
2. **VC 3.0 is a trap** — the only value that breaks a profitable year (2022: -0.00%). Worse than both 2.5 and 3.5.
3. **Diminishing returns above 3.5** — VC 4.0/5.0/No VC all plateau at EC 96.0 with similar edge but progressively more trades and higher avg losses.
4. **2018 becomes tradeable at VC 3.5** — jumps from +0.58% (VC 2.5) to +1.79% (VC 3.5), crossing the 1.5% threshold.
5. **The original VC 2.5 was too tight** — it filtered 6,370 trades (vs VC 3.5) that had higher edge, artificially constraining the strategy.

### Action Taken

Updated `VcpEntryStrategy.kt` to use `maxAtrMultiple = 3.5`. This is the new default configuration.

**Impact vs original (VC 2.5):**
- Edge: 4.83% → 5.51% (+0.68pp)
- EC: 92.0 → 96.0 (+4.0)
- Tradeable years: 8/10 → 9/10
- Trades: 3,667 → 10,037 (~2.7x more signals)

---

## Exit Analysis & Excursion Study (2026-02-27)

### Excursion Analysis (3,667 trades, 2016-2025)

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

### Stop Loss ATR Sweep

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

### Key Findings

1. **Tighter stops hurt.** SL 1.5 ATR stops out 868 trades (23%) — killing 73 eventual winners and dropping edge by -0.33%. EC falls from 92.0 to 87.5 and 2022 flips negative.
2. **SL 2.5 ATR is well-calibrated.** 95% of winners never dip below 1.53 ATR, so the 2.5 ATR stop gives all winners room to breathe while catching fast failures within 8 days.
3. **SL 3.0 ATR is marginally better** (+0.02% edge) but barely fires (121 exits) — not worth the looser protection.
4. **Profit targets would be destructive.** 56% of losers were green at +5% avg — tempting to capture, but winners peak at +28.9% avg. Capping gains would destroy the 3.2:1 win/loss ratio.
5. **This is a low-WR, high-payoff strategy.** The exit must let winners run. The EMA cross captures 58.6% of MFE — acceptable for a lagging indicator that enables +74.7% winners at the 95th percentile.

**Conclusion:** 2.5 ATR stop loss confirmed as optimal. No exit changes recommended.

---

## Sector Exclusion Analysis (2026-02-27)

### Goal
With 3,667 trades, the VCP strategy can afford to be selective. Test whether excluding the weakest sectors improves overall performance.

### Sector Performance (sorted by edge, with EC and yearly consistency)

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

### Exclusion Candidates

**XLB (Materials)** — Weakest sector: 1.56% edge, EC 38.3 (Poor), negative in 5 of 10 years. Clear drag on performance.

**Unknown sector** — Only 59 trades, EC 46.1, 4 losing years. Small sample, unreliable.

**XLE (Energy)** — Edge looks decent (5.07%) but EC is only 48.0 with wild swings (-13.6% in 2017, +23.8% in 2020). Very inconsistent. However, it contributes a critical +2.7% edge in 2022.

### Test: Exclude XLB + Unknown + XLE

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

### Key Findings

1. **Per-trade improvement is marginal** — edge only goes from 4.83% to 4.98% (+0.15pp), WR from 45.7% to 46.1%
2. **2022 flips negative** — XLE's +2.7% edge in 2022 was keeping that marginal year positive. Without it, 2022 goes to -0.13%
3. **EC drops significantly** — 92.0 → 87.9, losing the "10/10 profitable years" signature strength
4. **Paradox of sector exclusion** — XLE is individually inconsistent (EC 48.0) but its 2022 contribution is essential for the portfolio-level perfect year streak

### Conclusion

**No sector exclusions for the VCP strategy.** The per-trade improvement (+0.15pp) is negligible compared to the consistency damage (EC -4.1, lost 10/10 streak). Unlike Mjolnir (which benefits from excluding 4 sectors), VCP's broad signal generation across all sectors is its strength — every sector contributes positive edge, and the weak sectors provide diversification that smooths out individual year results.

---

## Entry Condition Ablation Study (2026-02-27)

### Goal
Measure each entry condition's individual contribution by removing one at a time and comparing to the baseline (3,667 trades, 45.7% WR, +4.83% edge, EC 92.0, 10/10 profitable years).

### Results

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

### Importance Ranking

| Rank | Condition | ΔEdge | ΔEC | Role |
|---|---|---|---|---|
| 1 | **aboveBearishOrderBlock** | -4.02pp | -37.1 | **CRITICAL** — the strategy's alpha engine. Without it, 21K junk trades flood in and edge collapses to 0.80% |
| 2 | **volumeAboveAverage** | -0.55pp | -8.6 | Important — filters 7K false breakouts lacking volume confirmation |
| 3 | uptrend | -0.19pp | -5.2 | Minervini trend template adds modest quality filtering |
| 4 | marketUptrend | -0.17pp | -5.1 | Market regime filter, protects consistency (removes a losing year when present) |
| 5 | priceNearDonchianHigh | -0.03pp | -8.6 | Tiny edge impact but large EC impact — ensures entries are near breakout levels |
| 6 | ~~priceAbove(50)~~ | +0.00 | +0.0 | **Completely redundant** — `uptrend()` already requires price > 50 EMA. **Removed.** |
| 7 | volatilityContracted | +0.64pp | +4.0 | Quantity filter, not quality — the 12K extra trades it blocks actually have higher edge |
| 8 | minimumPrice | +4.14pp | +4.0 | Blocks 629 cheap stocks that have outsized edge (especially in 2022: +39%) |

### Yearly Edge (removing each condition)

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

### Key Findings

1. **`aboveBearishOrderBlock` is the alpha engine.** It's doing almost all the work — filtering 25K → 3.7K trades with 6x edge improvement (0.80% → 4.83%). The order block resistance concept is the core insight of this strategy.

2. **`priceAbove(50)` was completely redundant.** The `uptrend()` condition (Minervini trend template) already requires price above the 50 EMA. Zero trades added, zero edge change, zero EC change. **Removed from strategy.**

3. **`minimumPrice` is technically a drag** — removing it adds 629 cheap stocks with +8.96% edge and an extraordinary +39.2% in 2022. However, penny stocks are practically difficult to trade (wide spreads, low liquidity, hard to get fills). Keeping the filter is a pragmatic choice, not a statistical one.

4. **`volatilityContracted` reduces quantity, not quality.** The 12,662 trades it blocks actually have higher edge (+5.47%). It narrows the strategy to post-squeeze breakouts, which is conceptually the VCP pattern but statistically filters profitable signals. Kept for strategy identity.

5. **Volume confirmation matters.** Removing `volumeAboveAverage` adds 7,416 trades but drops edge by 0.55pp — breakouts without volume surge are unreliable.

6. **Every "important" condition protects 2022.** Removing uptrend, marketUptrend, volume, or Donchian all flip 2022 negative. The conditions work as an ensemble to keep the weakest year barely positive.

### Action Taken

Removed `priceAbove(50)` from `VcpEntryStrategy.kt` — proven redundant by ablation study. No impact on backtest results.

---

## Position Limit Testing (maxPositions=15, Adaptive ranker)

Tested with 15-position cap for comparison to unlimited:

| Metric | 15 Positions | Unlimited |
|---|---|---|
| Total Trades | 587 | 3,667 |
| Win Rate | 39.7% | 45.7% |
| Edge | +3.36% | +4.83% |
| Avg Win / Loss | 17.89% / -6.21% | 16.95% / -5.38% |
| Win/Loss Ratio | 2.88x | 3.15x |
| Edge Consistency | 77.1 (Good) | 92.0 (Excellent) |
| Profitable Years | 5/6 (2020-2025) | 10/10 |
| Missed Opportunities | 2,038 | 0 |

**Yearly edge (15 positions, 2020-2025 only):**
| Year | 15 Positions | Unlimited |
|---|---|---|
| 2020 | +10.53% | +8.71% |
| 2021 | +1.31% | +2.03% |
| 2022 | +2.35% | +0.08% |
| 2023 | +2.88% | +7.70% |
| 2024 | +6.54% | +2.49% |
| 2025 | -1.43% | +4.03% |

**Key findings:**
1. Position cap cuts 84% of trades — entry conditions are generating far more signals than 15 slots allow
2. Unlimited has better EC (92 vs 77) and higher edge (+4.83% vs +3.36%)
3. 15-position cap introduced a negative year (2025: -1.43%) that doesn't exist in unlimited (+4.03%)
4. 2,038 missed opportunities averaged +5.12% — confirming the entry signals find real opportunities

---

## Comparison to Mjolnir Strategy

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

---

## Position-Sized Backtest ($10K Starting Capital)

### Configuration
- Starting capital: $10,000
- Max positions: 15
- Ranker: Random
- Risk per trade: 1.5% of portfolio
- ATR multiplier (nAtr): 2.0
- Leverage: 1.0x (stock only, no options)
- No sector exclusions

### Current Results (VC 3.5 + sectorUptrend)

| Metric | Value |
|---|---|
| Starting Capital | $10,000 |
| Final Capital | **$317,756** |
| Peak Capital | $317,756 (still at peak) |
| Total Return | +3,078% |
| CAGR | **41.3%** |
| Max Drawdown | **20.7%** ($18,806) |
| Total Trades | 837 |
| Win Rate | 49.5% |
| Edge | +5.74% |
| Avg Win / Loss | 18.40% / -6.65% |
| Profit Factor | 4.72 |
| EC Score | 96.0 (Excellent) |

### Yearly Edge

| Year | Trades | Edge | Tradeable? |
|---|---|---|---|
| 2016 | 89 | +4.03% | T |
| 2017 | 61 | +8.38% | T |
| 2018 | 77 | +2.00% | T |
| 2019 | 65 | +10.11% | T |
| 2020 | 65 | +12.94% | T |
| 2021 | 96 | +4.75% | T |
| 2022 | 99 | +0.67% | |
| 2023 | 89 | +8.12% | T |
| 2024 | 74 | +8.61% | T |
| 2025 | 122 | +3.29% | T |

**10/10 years profitable.** 9/10 tradeable. $10K → $318K (31.8x) over 10 years.

### Evolution: Position-Sized Results Across Optimizations

| Metric | VC 2.5 (original) | VC 3.5 | VC 3.5 + sectorUptrend |
|---|---|---|---|
| Final Capital | $148,124 | $224,840 | **$317,756** |
| CAGR | 30.9% | 36.5% | **41.3%** |
| Max Drawdown | **15.2%** | 19.7% | 20.7% |
| Trades | 855 | 927 | 837 |
| Win Rate | 46.3% | 49.1% | **49.5%** |
| Edge | +4.86% | +5.46% | **+5.74%** |
| Profit Factor | — | 2.04 | **4.72** |
| EC | 92.0 | 96.0 | **96.0** |

Each optimization compounded on the previous: loosening VC from 2.5→3.5 added $76K, then adding sectorUptrend added another $93K — while maintaining 10/10 profitable years and EC 96.0. Drawdown increased modestly from 15.2% to 20.7% across all changes.

### Position Sizing Notes
- Initial run without leverage cap blew up — ATR-based sizing allowed 59x leverage on a single trade, leading to -$1.27M final capital
- Adding `leverageRatio: 1.0` caps total notional exposure to 1x portfolio value, producing realistic results
- Results vary slightly between runs due to random ranker — run multiple times for confidence

---

## Code Changes Made
- `VolatilityContractedCondition.kt` — New entry condition (range/ATR squeeze detection)
- `VcpEntryStrategy.kt` — New registered strategy (`@RegisteredStrategy(name = "Vcp", type = StrategyType.ENTRY)`)
- `StrategyDsl.kt` — Added `volatilityContracted(lookbackDays, maxAtrMultiple)` DSL function
- `DynamicStrategyBuilder.kt` — Added `"volatilitycontracted"` condition mapping
- `VolatilityContractedConditionTest.kt` — 11 unit tests covering pass/fail/boundary/metadata/detailed evaluation

---

## Potential Next Steps
- ~~Entry condition ablation study~~ — Done. `priceAbove(50)` removed as redundant (see Ablation Study)
- ~~Parameter sensitivity on `volatilityContracted`~~ — Done. maxAtrMultiple changed from 2.5 to 3.5 (see VC Sweep)
- Parameter sensitivity on `priceNearDonchianHigh` (1.5% vs 3.0% vs 5.0%)
- Parameter sensitivity on `volumeAboveAverage` multiplier (1.0 vs 1.2 vs 1.5)
- ~~Sector exclusion testing~~ — Done. No exclusions recommended (see Sector Exclusion Analysis)
- Monte Carlo validation (10,000 iterations)
- Test with different exit strategies (PlanAlpha, PlanMoney)
- Combined portfolio simulation — run VCP + Mjolnir together to measure diversification benefit
- Position sizing framework with ATR drawdown stats

---

## Running Backtests

### Unlimited (statistical analysis)

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

### Realistic (position-sized with $10K)

Simulates real trading with capital constraints, position sizing, and a 15-position cap. Use this to estimate actual returns, drawdowns, and equity curves.

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
    "ranker": "Random",
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
- `ranker: "Random"` randomizes which signals get filled when more signals than slots — avoids selection bias
- Requires ~12GB heap (`-Xmx12288m` in `build.gradle` bootRun task) for full 10-year run
- Position-sized results vary slightly between runs due to random ranker — run multiple times for confidence
