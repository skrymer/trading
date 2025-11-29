# TQQQ Strategy Comparison: VegardPlanEtf vs OvtlyrPlanEtf

**Asset:** TQQQ (using QQQ signals)  
**Period:** 2020-10-01 to 2025-11-25 (~5.15 years)  
**Starting Capital:** $100,000

## Executive Summary

**Winner: OvtlyrPlanEtf** based on **Return/Drawdown Ratio** (33.78 vs 27.06)

Despite VegardPlanEtf achieving higher absolute returns (+163% more), OvtlyrPlanEtf delivers superior **risk-adjusted performance** with significantly lower drawdown.

---

## Key Metrics Comparison

| Metric | VegardPlanEtf | OvtlyrPlanEtf | Difference |
|--------|---------------|---------------|------------|
| **Cooldown Period** | 10 days | 0 days | +10 days |
| **Total Trades** | 33 | 43 | -10 trades |
| **Win Rate** | 63.64% | 58.14% | +5.50% |
| **Edge per Trade** | 8.47% | 5.72% | +2.74% |
| **Total Return** | 823.78% | 660.32% | +163.47% |
| **CAGR** | 53.99% | 48.27% | +5.71% |
| **Max Drawdown** | 30.44% | 19.55% | +10.89% |
| **Return/Drawdown Ratio** | 27.06 | **33.78** | -6.72 |

---

## Risk-Adjusted Analysis

### Why OvtlyrPlanEtf Wins

1. **Superior Risk-Adjusted Returns**
   - Return/Drawdown Ratio: **33.78 vs 27.06**
   - This is the **primary optimization metric**
   - OvtlyrPlanEtf delivers excellent returns with **37% less drawdown**

2. **Manageable Drawdown**
   - OvtlyrPlanEtf: **19.55%** (excellent - tradeable)
   - VegardPlanEtf: **30.44%** (acceptable but psychologically challenging)
   - Difference: **10.89%** - significantly easier to hold through drawdowns

3. **The Tradeoff is Worth It**
   - VegardPlanEtf gains +163% more return
   - But requires enduring +10.89% more drawdown
   - The marginal return doesn't justify the additional psychological pain
   - 660% return with 19.55% DD is **more sustainable** than 823% with 30.44% DD

---

## What the Cooldown Reveals

### VegardPlanEtf (10-day cooldown):
- **Fewer trades** (33 vs 43) = -23% trade reduction
- **Higher edge** per trade (8.47% vs 5.72%) = +48% edge improvement
- **Better win rate** (63.64% vs 58.14%) = +5.5% improvement
- **Higher returns** (823% vs 660%) = +25% more profit
- **BUT: Higher drawdown** (30.44% vs 19.55%) = +56% more pain

### The Cooldown Effect:
✓ Successfully filters out lower-quality trades  
✓ Improves edge per trade dramatically (+2.74%)  
✓ Increases win rate (+5.5%)  
✓ Boosts absolute returns (+163%)  
✗ **But paradoxically increases drawdown** (+10.89%)

This suggests the cooldown is **filtering out profitable trades during recovery phases**, leaving the strategy more exposed to deeper drawdowns.

---

## Year-by-Year Performance

| Year | Vegard Trades | Vegard Return | Ovtlyr Trades | Ovtlyr Return | Winner |
|------|---------------|---------------|---------------|---------------|--------|
| 2020 | 2 | 43.07% | 2 | 42.97% | VegardPlanEtf 🏆 |
| 2021 | 6 | 18.01% | 10 | 35.24% | OvtlyrPlanEtf 🏆 |
| 2022 | 3 | -0.24% | 4 | -4.41% | VegardPlanEtf 🏆 |
| 2023 | 10 | 70.66% | 9 | 87.12% | OvtlyrPlanEtf 🏆 |
| 2024 | 7 | 85.58% | 11 | 49.32% | VegardPlanEtf 🏆 |
| 2025 | 5 | 73.17% | 7 | 47.23% | VegardPlanEtf 🏆 |

**Years Won:** VegardPlanEtf: 4, OvtlyrPlanEtf: 2

### Observations:
- VegardPlanEtf dominates in 2024 and 2025 (strong trending years)
- OvtlyrPlanEtf wins in 2021 and 2023 (choppy/recovery years)
- VegardPlanEtf performs better in bear markets (2022: -0.24% vs -4.41%)

---

## Exit Strategy Comparison

| Exit Reason | VegardPlanEtf | OvtlyrPlanEtf |
|-------------|---------------|---------------|
| 10 EMA crossed under 20 EMA | 6 | 3 |
| ATR trailing stop (2.7 ATR) | 0 | 3 |
| ATR trailing stop (3.1 ATR) | 3 | 0 |
| Price below 10 EMA for 4 days | 11 | 0 |
| Price 2.9 ATR above 20 EMA | 13 | 0 |
| Price 3.0 ATR above 20 EMA | 0 | 11 |
| Sell signal | 0 | 26 |

### Key Differences:
- **VegardPlanEtf:** Uses more conservative exits (price below EMA confirmation)
- **OvtlyrPlanEtf:** Relies heavily on sell signals (60% of exits)
- VegardPlanEtf exits faster on profit targets (2.9 ATR vs 3.0 ATR)
- VegardPlanEtf has unique "4 consecutive days below EMA" condition

---

## The Paradox: Why Higher Edge Doesn't Mean Better Results

This comparison reveals a fascinating paradox:

**VegardPlanEtf has:**
- ✓ Higher edge per trade (+2.74%)
- ✓ Higher win rate (+5.5%)
- ✓ Higher absolute returns (+163%)
- ✗ **WORSE risk-adjusted returns** (ratio: 27.06 vs 33.78)

**Why?**

The 10-day cooldown creates two effects:
1. **Positive:** Filters out bad trades (improves edge and win rate)
2. **Negative:** Misses recovery trades that could reduce drawdown depth

Result: Bigger wins but also bigger drawdowns, leading to worse risk-adjusted performance.

---

## Recommendation

### For Most Traders: **OvtlyrPlanEtf** ✓

**Reasons:**
1. **Better risk-adjusted returns** (33.78 vs 27.06)
2. **Lower drawdown** (19.55% vs 30.44%) - easier to hold psychologically
3. **More consistent** across different market conditions
4. **Excellent absolute returns** (660% over 5 years = 48% CAGR)
5. **Doesn't require cooldown management**

### When to Use VegardPlanEtf:

Consider VegardPlanEtf if:
- You can handle 30%+ drawdowns psychologically
- You want maximum absolute returns (willing to accept more pain)
- You're in a strong trending market (like 2024-2025)
- You want to avoid overtrading

### Best Practice:

For optimal results, consider:
1. **Start with OvtlyrPlanEtf** as the base strategy
2. **Scale position size up** during confirmed trends
3. **Use VegardPlanEtf** during strong bull markets
4. **Switch to OvtlyrPlanEtf** during choppy/recovery periods

---

## Final Verdict

🏆 **Winner: OvtlyrPlanEtf**

**Core Principle:** Risk-adjusted returns > Absolute returns

OvtlyrPlanEtf delivers outstanding 660% returns with only 19.55% drawdown, achieving a stellar 33.78 Return/Drawdown ratio. While VegardPlanEtf's 823% returns are impressive, the additional 10.89% drawdown isn't justified by the extra return.

**Remember:** A strategy you can stick with through drawdowns is worth more than a strategy that maximizes returns but forces you to exit at the worst time.

---

**Generated:** 2025-11-25  
**Comparison Period:** 2020-10-01 to 2025-11-25  
**Fix Applied:** PriceBelowEmaForDaysExit now correctly excludes entry date
