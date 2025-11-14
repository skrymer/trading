# QQQ Backtest Report - PlanEtf Strategy

**Generated:** 2025-11-10 18:50:24

---

## Executive Summary

The PlanEtf strategy on QQQ shows strong performance with a **71.4% win rate** and **4.17 profit factor** over 56 trades from 2020-04-13 to 2025-09-24.

**Key Highlights:**
- Total Return: **102.42%** ($389.48)
- Expectancy: **2.97%** per trade
- Edge: **1.83%**
- Average Hold Time: **16.1 days**

---

## Performance Metrics

### Win/Loss Statistics

| Metric | Value |
|--------|-------|
| **Total Trades** | 56 |
| **Winning Trades** | 40 (0.71%) |
| **Losing Trades** | 16 (0.29%) |
| **Average Winner** | 3.36% ($12.81) |
| **Average Loser** | 1.99% ($7.68) |
| **Win/Loss Ratio** | 1.69x |

### Risk-Adjusted Returns

| Metric | Value |
|--------|-------|
| **Expectancy** | 2.97% |
| **Edge** | 1.83% |
| **Profit Factor** | 4.17 |
| **Avg Trading Days** | 16.1 days |

---

## Trade Analysis

### Best Trade
- **Entry Date:** 2025-06-23
- **Entry Price:** $531.65
- **Profit:** $70.55 (13.27%)
- **Duration:** 91 days
- **Exit Reason:** Price is 3.0 ATR above 20 EMA

### Worst Trade
- **Entry Date:** 2022-04-05
- **Entry Price:** $360.42
- **Loss:** $-21.61 (-6.00%)
- **Duration:** 7 days
- **Exit Reason:** 10 ema has crossed under the 20 ema

---

## Exit Reason Analysis

| Exit Reason | Count | Wins | Losses | Avg P/L |
|-------------|-------|------|--------|---------|
| Quote is within an order block older tha | 33 | 28 | 5 | 1.48% |
| 10 ema has crossed under the 20 ema | 12 | 1 | 11 | -2.46% |
| Price is 3.0 ATR above 20 EMA | 11 | 11 | 0 | 7.56% |

---

## Yearly Performance

| Year | Trades | Wins | Losses | Win Rate | Total P/L |
|------|--------|------|--------|----------|-----------|
| 2020 | 5 | 4 | 1 | 80.0% | 17.61% |
| 2021 | 11 | 6 | 5 | 54.5% | 5.47% |
| 2022 | 7 | 5 | 2 | 71.4% | 0.52% |
| 2023 | 13 | 8 | 5 | 61.5% | 35.21% |
| 2024 | 15 | 13 | 2 | 86.7% | 22.54% |
| 2025 | 5 | 4 | 1 | 80.0% | 21.05% |

---

## Strategy Configuration

### Entry Strategy: PlanEtf
The PlanEtf entry strategy uses the following conditions:
- Stock is in uptrend
- Has buy signal
- Heatmap < 70
- Price is within value zone (< 20 EMA + 2 ATR)
- Price is at least 2% below an order block older than 30 days
- Stock has not been traded in the last 5 days (cooldown period)

### Exit Strategy: PlanEtf
The PlanEtf exit strategy triggers on:
- 10 EMA crosses under 20 EMA
- Within order block more than 30 days old
- Price extends 3 ATR above 20 EMA (profit target)

---

## Full Trade History

| # | Entry Date | Entry Price | Exit Price | Days | P/L % | Exit Reason |
|---|------------|-------------|------------|------|-------|-------------|
| 1 | 2020-04-13 | $200.43 | $209.14 | 1 | 4.35% | Quote is within an order block olde |
| 2 | 2020-05-05 | $214.87 | $243.06 | 36 | 13.12% | Price is 3.0 ATR above 20 EMA |
| 3 | 2020-10-07 | $277.21 | $269.01 | 21 | -2.96% | 10 ema has crossed under the 20 ema |
| 4 | 2020-11-09 | $285.79 | $290.36 | 7 | 1.60% | Quote is within an order block olde |
| 5 | 2020-11-18 | $287.30 | $291.62 | 6 | 1.50% | Quote is within an order block olde |
| 6 | 2021-01-07 | $312.49 | $310.36 | 49 | -0.68% | 10 ema has crossed under the 20 ema |
| 7 | 2021-04-01 | $322.41 | $335.74 | 29 | 4.13% | Quote is within an order block olde |
| 8 | 2021-05-03 | $333.95 | $323.59 | 7 | -3.10% | 10 ema has crossed under the 20 ema |
| 9 | 2021-05-26 | $331.90 | $338.08 | 15 | 1.86% | Quote is within an order block olde |
| 10 | 2021-07-15 | $358.53 | $364.54 | 68 | 1.68% | 10 ema has crossed under the 20 ema |
| 11 | 2021-10-19 | $373.82 | $391.45 | 15 | 4.72% | Price is 3.0 ATR above 20 EMA |
| 12 | 2021-11-24 | $397.01 | $397.93 | 5 | 0.23% | Quote is within an order block olde |
| 13 | 2021-11-30 | $392.09 | $384.50 | 6 | -1.93% | 10 ema has crossed under the 20 ema |
| 14 | 2021-12-09 | $392.00 | $385.14 | 7 | -1.75% | 10 ema has crossed under the 20 ema |
| 15 | 2021-12-28 | $400.36 | $400.30 | 1 | -0.01% | Quote is within an order block olde |
| 16 | 2021-12-30 | $399.11 | $400.43 | 4 | 0.33% | Quote is within an order block olde |
| 17 | 2022-03-22 | $356.29 | $366.40 | 8 | 2.84% | Quote is within an order block olde |
| 18 | 2022-03-31 | $361.86 | $368.61 | 4 | 1.86% | Quote is within an order block olde |
| 19 | 2022-04-05 | $360.42 | $338.81 | 7 | -6.00% | 10 ema has crossed under the 20 ema |
| 20 | 2022-07-19 | $298.30 | $303.03 | 1 | 1.59% | Quote is within an order block olde |
| 21 | 2022-07-22 | $301.99 | $306.81 | 5 | 1.60% | Quote is within an order block olde |
| 22 | 2022-07-28 | $309.81 | $304.41 | 32 | -1.74% | 10 ema has crossed under the 20 ema |
| 23 | 2022-11-14 | $285.44 | $286.51 | 30 | 0.37% | Quote is within an order block olde |
| 24 | 2023-01-13 | $280.97 | $288.96 | 10 | 2.84% | Quote is within an order block olde |
| 25 | 2023-01-24 | $288.37 | $287.73 | 1 | -0.22% | Quote is within an order block olde |
| 26 | 2023-01-30 | $290.27 | $311.72 | 3 | 7.39% | Price is 3.0 ATR above 20 EMA |
| 27 | 2023-03-08 | $297.82 | $292.66 | 1 | -1.73% | 10 ema has crossed under the 20 ema |
| 28 | 2023-03-17 | $305.36 | $337.27 | 62 | 10.45% | Price is 3.0 ATR above 20 EMA |
| 29 | 2023-05-23 | $333.36 | $348.40 | 3 | 4.51% | Price is 3.0 ATR above 20 EMA |
| 30 | 2023-07-11 | $368.17 | $379.15 | 2 | 2.98% | Quote is within an order block olde |
| 31 | 2023-08-31 | $377.99 | $377.59 | 1 | -0.11% | Quote is within an order block olde |
| 32 | 2023-09-05 | $378.07 | $376.97 | 6 | -0.29% | Quote is within an order block olde |
| 33 | 2023-09-12 | $372.79 | $377.27 | 2 | 1.20% | Quote is within an order block olde |
| 34 | 2023-10-11 | $371.22 | $359.97 | 8 | -3.03% | 10 ema has crossed under the 20 ema |
| 35 | 2023-11-06 | $369.21 | $378.39 | 4 | 2.49% | Quote is within an order block olde |
| 36 | 2023-12-20 | $403.08 | $438.27 | 114 | 8.73% | Quote is within an order block olde |
| 37 | 2024-05-07 | $440.32 | $440.06 | 1 | -0.06% | Quote is within an order block olde |
| 38 | 2024-05-09 | $441.02 | $442.06 | 1 | 0.24% | Quote is within an order block olde |
| 39 | 2024-05-13 | $443.08 | $476.72 | 31 | 7.59% | Price is 3.0 ATR above 20 EMA |
| 40 | 2024-06-27 | $481.61 | $496.16 | 8 | 3.02% | Price is 3.0 ATR above 20 EMA |
| 41 | 2024-07-11 | $491.93 | $494.82 | 1 | 0.59% | Quote is within an order block olde |
| 42 | 2024-07-15 | $496.15 | $496.34 | 1 | 0.04% | Quote is within an order block olde |
| 43 | 2024-08-20 | $480.26 | $461.04 | 16 | -4.00% | 10 ema has crossed under the 20 ema |
| 44 | 2024-09-16 | $473.24 | $493.15 | 23 | 4.21% | Quote is within an order block olde |
| 45 | 2024-10-10 | $492.59 | $493.36 | 1 | 0.16% | Quote is within an order block olde |
| 46 | 2024-10-15 | $490.85 | $490.91 | 1 | 0.01% | Quote is within an order block olde |
| 47 | 2024-10-17 | $491.25 | $494.47 | 1 | 0.66% | Quote is within an order block olde |
| 48 | 2024-10-21 | $495.42 | $495.96 | 1 | 0.11% | Quote is within an order block olde |
| 49 | 2024-10-24 | $492.32 | $495.32 | 1 | 0.61% | Quote is within an order block olde |
| 50 | 2024-10-28 | $495.40 | $500.16 | 1 | 0.96% | Quote is within an order block olde |
| 51 | 2024-10-30 | $496.38 | $538.17 | 47 | 8.42% | Price is 3.0 ATR above 20 EMA |
| 52 | 2025-01-23 | $532.64 | $539.52 | 27 | 1.29% | Quote is within an order block olde |
| 53 | 2025-02-20 | $537.23 | $514.56 | 6 | -4.22% | 10 ema has crossed under the 20 ema |
| 54 | 2025-04-29 | $475.53 | $515.59 | 14 | 8.42% | Price is 3.0 ATR above 20 EMA |
| 55 | 2025-06-23 | $531.65 | $602.20 | 91 | 13.27% | Price is 3.0 ATR above 20 EMA |
| 56 | 2025-09-24 | $596.10 | $609.74 | 47 | 2.29% | Price is 3.0 ATR above 20 EMA |

---

## Conclusion

The PlanEtf strategy demonstrates strong performance on QQQ with:

✅ **High Win Rate:** 71.4% winning trades  
✅ **Strong Profit Factor:** 4.17, indicating wins significantly outweigh losses  
✅ **Positive Expectancy:** 2.97% per trade  
✅ **Good Risk/Reward:** Average winner is 1.69x the size of average loser  

The strategy's use of multiple confirmation signals (trend, buy signal, heatmap, value zone, order blocks) combined with a cooldown period appears to filter trades effectively, leading to this strong performance.

---

*Report generated by Udgaard Backtesting System*
