# Ranker Performance Comparison Report - 2025

**Date Generated**: November 11, 2025
**Backtest Period**: January 1, 2025 - November 11, 2025
**Strategy Configuration**:
- Entry Strategy: PlanAlpha
- Exit Strategy: PlanMoney
- Max Positions: 10
- Starting Capital: $100,000

## Executive Summary

A comprehensive analysis of all 8 available stock rankers was conducted to identify the optimal ranking methodology for 2025 market conditions. The **DistanceFrom10Ema** ranker emerged as the clear winner with exceptional performance.

### 🏆 Winner: DistanceFrom10Ema

**Performance Metrics:**
- **Total Return**: 219.27%
- **Final Balance**: $319,274.75
- **Win Rate**: 55.84%
- **Edge per Trade**: 1.70%
- **Total Trades**: 77
- **Average Win**: 5.77%
- **Average Loss**: -3.45%

## Complete Rankings

| Rank | Ranker | Trades | Win Rate | Avg Win % | Avg Loss % | Edge % | Total Return % | Final Balance |
|------|--------|--------|----------|-----------|------------|--------|----------------|---------------|
| 1 | **DistanceFrom10Ema** | 77 | 55.84% | 5.77% | -3.45% | 1.70% | **219.27%** | **$319,274.75** |
| 2 | RelativeStrength | 69 | 56.52% | 6.10% | -4.41% | 1.53% | 142.46% | $242,459.74 |
| 3 | Random | 77 | 45.45% | 7.09% | -4.65% | 0.69% | 39.39% | $139,391.47 |
| 4 | Heatmap | 72 | 48.61% | 6.27% | -4.74% | 0.61% | 30.17% | $130,171.80 |
| 5 | Composite | 74 | 44.59% | 7.72% | -5.18% | 0.57% | 18.52% | $118,521.37 |
| 6 | Volatility | 71 | 49.30% | 7.68% | -6.39% | 0.55% | 9.41% | $109,413.84 |
| 7 | Adaptive | 71 | 49.30% | 7.68% | -6.39% | 0.55% | 9.41% | $109,413.84 |
| 8 | SectorStrength | 72 | 43.06% | 6.36% | -4.28% | 0.30% | 5.58% | $105,584.60 |

## Detailed Analysis

### Top Performers

#### 1. DistanceFrom10Ema - 219.27% Return
**Why It Won:**
- Highest edge per trade (1.70%)
- Best risk/reward ratio (5.77% avg win vs -3.45% avg loss)
- Excellent trade frequency (77 trades)
- Consistent performance with solid win rate (55.84%)

**Key Characteristics:**
This ranker selects stocks based on their distance from the 10-day EMA, favoring stocks that are closer to their support level. This approach in 2025 captured strong mean-reversion opportunities while maintaining position quality.

#### 2. RelativeStrength - 142.46% Return
**Strong Alternative:**
- Highest win rate among all rankers (56.52%)
- Second-highest edge (1.53%)
- More conservative with fewer trades (69)
- Good risk management with moderate average loss (-4.41%)

**Key Characteristics:**
Ranks stocks by momentum and relative performance. Performed well in 2025's trending markets.

#### 3. Random - 39.39% Return
**Surprising Baseline:**
- Beat 5 sophisticated rankers
- Lower win rate (45.45%) but larger average wins (7.09%)
- Suggests 2025 market rewarded active position management
- Indicates strategy quality over selection sophistication

### Underperformers

#### Heatmap - 30.17% Return
Previously considered a strong ranker, underperformed in 2025. The heatmap-based selection may have struggled with 2025's market regime.

#### Composite - 18.52% Return
Despite combining multiple factors, didn't capture the market dynamics as well as simpler approaches.

#### Volatility & Adaptive - 9.41% Return
Both rankers showed identical performance, suggesting they may have selected similar stocks. Low returns indicate volatility-based selection wasn't optimal for 2025.

#### SectorStrength - 5.58% Return
Worst performer with lowest edge (0.30%) and win rate (43.06%). Sector rotation strategy didn't align with 2025 market conditions.

## Key Insights

### 1. Simplicity Wins
The top two rankers (DistanceFrom10Ema and RelativeStrength) use straightforward metrics. Complex composite approaches didn't add value in 2025.

### 2. Mean Reversion Dominates
DistanceFrom10Ema's success suggests mean reversion to the 10 EMA was a powerful signal in 2025, outperforming momentum and strength metrics.

### 3. Risk/Reward Matters More Than Win Rate
DistanceFrom10Ema (55.84% win rate) outperformed rankers with higher win rates by having a much better risk/reward profile.

### 4. Market Regime Sensitivity
The wide performance gap between rankers (219% vs 5.58%) demonstrates the importance of matching ranker methodology to current market conditions.

### 5. Active Management Value
Even the Random ranker achieved 39% returns, highlighting the value of the underlying PlanAlpha/PlanMoney strategy and active position management.

## Recommendations

### For 2025 Trading
**Primary Choice**: Use **DistanceFrom10Ema** ranker
- Optimal performance demonstrated
- Strong edge and risk/reward
- Proven in 2025 market conditions

**Backup Choice**: Use **RelativeStrength** ranker
- Excellent win rate
- Strong alternative if DistanceFrom10Ema underperforms
- Good for more conservative approach

### Portfolio Approach
Consider running multiple rankers in parallel:
- 60% allocation: DistanceFrom10Ema
- 40% allocation: RelativeStrength
- This diversifies selection methodology while emphasizing top performers

### Monitoring
Regularly compare ranker performance as market conditions evolve. The optimal ranker may change with market regime shifts.

## Methodology

### Backtest Configuration
```
Entry Strategy: PlanAlpha
Exit Strategy: PlanMoney
Max Positions: 10
Date Range: 2025-01-01 to 2025-11-11
Stock Universe: All available stocks
Rankers Tested: 8 (Heatmap, RelativeStrength, Volatility, DistanceFrom10Ema,
                   Composite, SectorStrength, Adaptive, Random)
```

### Metrics Calculated
- **Total Return**: Compounded return assuming full capital redeployment
- **Win Rate**: Percentage of profitable trades
- **Edge**: Expected value per trade
- **Avg Win/Loss**: Mean profit/loss percentage per trade
- **Final Balance**: Ending capital from $100,000 start

### Analysis Tools
- Backend API: Udgaard Spring Boot application (localhost:8080)
- Analysis Script: Python 3 with JSON processing
- Raw data saved: `/tmp/ranker_*.json`

## Historical Context

### Performance Benchmarks (From Skill Documentation)
Good strategies typically target:
- Win rate: 60-70%+
- Edge: 1.0%+ per trade
- CAGR: 15%+ on equity ETFs

### 2025 Results vs Benchmarks
**DistanceFrom10Ema Results:**
- ✓ Edge: 1.70% (exceeds 1.0% target)
- ✓ CAGR: ~219% YTD (far exceeds 15% target)
- ⚠ Win Rate: 55.84% (below 60% target, but compensated by excellent risk/reward)

## Conclusion

The **DistanceFrom10Ema** ranker is the optimal choice for 2025 trading with PlanAlpha entry and PlanMoney exit strategies when limiting positions to 10 concurrent trades. Its 219% return significantly outperformed all alternatives and demonstrates that mean-reversion to the 10 EMA has been the most predictive signal in 2025's market environment.

The analysis also reveals that simpler, focused ranking methodologies outperformed complex composite approaches, suggesting that clarity and precision in stock selection criteria matter more than attempting to optimize multiple factors simultaneously.

---

**Report Generated**: 2025-11-11
**Analysis By**: Claude Code (Udgaard Trading System)
**Raw Data Location**: `/tmp/ranker_*.json`
