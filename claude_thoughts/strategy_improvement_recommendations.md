# Strategy Improvement Recommendations

**Date:** 2025-11-01
**Analysis Period:** 2021-2025 (791 trades)
**Current Strategy:** PlanAlphaEntryStrategy + PlanMoneyExitStrategy

---

## Executive Summary

The trading strategy shows strong long-term performance (2.25% edge over 4 years) but has experienced an **81% decline in edge** from 2021-2024 average (2.64%) to 2025 (0.50%). Analysis reveals the strategy is **market-regime dependent** - it excels in trending bull markets but struggles in choppy/volatile conditions.

**Key Metrics:**
- 4-year edge: 2.25% (791 trades)
- 2025 edge: 0.50% (144 trades) ⚠️
- Win rate decline: 58.9% (4-year avg) → 48.6% (2025)
- Total profit decline: Strong degradation in recent years

---

## Performance Analysis by Year

### Year-by-Year Breakdown

| Year | Trades | Win Rate | Avg Win | Avg Loss | Edge | Total Profit | Market Condition |
|------|--------|----------|---------|----------|------|--------------|------------------|
| 2021 | 87 | 63.2% | 5.00% | -3.00% | 2.06% | 179.24% | Recovery |
| **2022** | **189** | **73.0%** | **7.60%** | **-4.09%** | **4.45%** | **840.21%** | **Bull Market** 🏆 |
| 2023 | 237 | 53.6% | 7.56% | -3.58% | 2.39% | 567.21% | Transition |
| 2024 | 134 | 56.7% | 4.27% | -3.51% | 0.90% | 120.93% | Choppy ⚠️ |
| **2025** | **144** | **48.6%** | **6.27%** | **-4.96%** | **0.50%** | **71.68%** | **Choppy** ❌ |

### Trend Analysis

- **Edge peaked in 2022** at 4.45%, then declined consistently
- **Win rate declining**: 73% (2022) → 56.7% (2024) → 48.6% (2025)
- **Losses getting bigger**: -3.00% (2021) → -4.96% (2025)
- **2025 is 81% worse** than 2021-2024 average

---

## Root Cause Analysis

### Sector Performance Comparison: 2022 vs 2025

The two largest sectors (XLI and XLK) show dramatic performance collapse:

| Sector | 2022 Win Rate | 2022 Edge | 2025 Win Rate | 2025 Edge | Change |
|--------|---------------|-----------|---------------|-----------|--------|
| **XLI** (Industrials) | **81.8%** | **+6.64%** | **48.8%** | **-0.80%** | **-7.44%** |
| **XLK** (Technology) | **68.8%** | **+4.87%** | **41.7%** | **+0.62%** | **-4.25%** |

**Combined Impact:** These 2 sectors represent 48% of 2025 trades (69 out of 144)

### Why Performance Collapsed

**PlanAlphaEntryStrategy** uses 13 strict conditions optimized for trending markets:

1. Market (SPY) in uptrend
2. SPY buy signal active
3. Market heatmap < 70 and rising
4. Sector in uptrend
5. Sector getting greedier
6. Sector heatmap < 70
7. Sector heatmap > market heatmap
8. Stock buy signal active
9. Stock price > 10 EMA
10. Stock in uptrend
11. Stock heatmap rising
12. Volume confirmation
13. Technical setup valid

**In Bull Markets (2022):**
- All conditions align naturally
- Breakouts follow through
- Trends persist
- High win rates (70-80%)

**In Choppy Markets (2024-2025):**
- Conditions trigger on false breakouts
- Reversals happen quickly
- Whipsaws common
- Low win rates (40-50%)

---

## Sector Performance (4-Year Summary)

### All Sectors Have Positive Edge (Don't Filter by Sector)

| Rank | Sector | Name | Trades | Win Rate | Edge | Total P/L |
|------|--------|------|--------|----------|------|-----------|
| 1 | XLF | Financials | 156 | 67.9% | 3.78% | 590.25% |
| 2 | XLY | Consumer Discretionary | 75 | 66.7% | 3.78% | 283.29% |
| 3 | XLP | Consumer Staples | 14 | 78.6% | 3.50% | 49.03% |
| 4 | XLB | Materials | 27 | 51.9% | 3.06% | 82.53% |
| 5 | XLU | Utilities | 34 | 70.6% | 2.30% | 78.20% |
| 6 | XLI | Industrials | 156 | 53.8% | 1.90% | 296.62% |
| 7 | XLK | Technology | 184 | 51.6% | 1.52% | 279.51% |
| 8 | XLE | Energy | 12 | 58.3% | 1.49% | 17.90% |
| 9 | XLRE | Real Estate | 55 | 69.1% | 1.46% | 80.54% |
| 10 | XLC | Communications | 51 | 47.1% | 0.39% | 19.82% |
| 11 | XLV | Healthcare | 27 | 48.1% | 0.06% | 1.57% |

**Conclusion:** ALL sectors contribute positively over 4 years. Sector filtering is NOT the solution.

---

## Recommendations (Prioritized)

### Priority 1: Add Market Regime Filter ⭐⭐⭐

**Goal:** Only trade during confirmed bull market conditions to avoid choppy/volatile periods.

**Implementation:** Add to PlanAlphaEntryStrategy.kt

```kotlin
// New conditions to add
fun isStrongMarketRegime(market: Stock, quote: StockQuote): Boolean {
    // 1. SPY above 200-day SMA for at least 20 days (not just current day)
    val spy200DaySMA = calculateSMA(market, 200)
    val daysAbove200 = countConsecutiveDaysAbove(market, spy200DaySMA)
    if (daysAbove200 < 20) return false

    // 2. SPY 50-day EMA above 200-day EMA (golden cross maintained)
    val spy50EMA = quote.closePriceEMA50  // Need to add this if not exists
    val spy200EMA = quote.closePriceEMA200 // Need to add this if not exists
    if (spy50EMA <= spy200EMA) return false

    // 3. Market breadth advancing stocks > 60% (strong participation)
    val marketBreadth = getMarketBreadth(quote.date)
    if (marketBreadth.advancingPercent < 60.0) return false

    return true
}
```

**Expected Impact:**
- Reduce trades by 30-40%
- Increase win rate from 48.6% → 60%+
- Increase edge from 0.50% → 2.0%+
- Filter out most 2024-2025 losing trades

**Backtest Target:** Apply to 2021-2025 data, expect to skip most 2024-2025 trades while keeping 2022 trades

---

### Priority 2: Add Volatility Filter ⭐⭐

**Goal:** Avoid entries during high volatility periods when false breakouts are common.

**Implementation:**

```kotlin
fun isLowVolatilityPeriod(stock: Stock, quote: StockQuote): Boolean {
    // 1. VIX < 25 (avoid panic/uncertainty)
    val vix = getVIX(quote.date)
    if (vix >= 25.0) return false

    // 2. Stock's 20-day ATR as % of price < 5% (avoid choppy stocks)
    val atr20 = calculateATR(stock, 20)
    val atrPercent = (atr20 / quote.closePrice) * 100
    if (atrPercent >= 5.0) return false

    // 3. No large gaps (>5%) in last 5 days
    val recentQuotes = stock.quotes.takeLast(5)
    val hasLargeGap = recentQuotes.any {
        abs((it.openPrice - it.previousClose) / it.previousClose) > 0.05
    }
    if (hasLargeGap) return false

    return true
}
```

**Expected Impact:**
- Filter out 15-20% of worst trades
- Improve edge by 0.5-1.0%
- Avoid entries during market stress

---

### Priority 3: Strengthen Entry Confirmation ⭐⭐

**Goal:** Reduce false breakouts by requiring stronger confirmation signals.

**Implementation:**

```kotlin
fun hasStrongEntryConfirmation(stock: Stock, sector: Stock, quote: StockQuote): Boolean {
    // 1. Volume confirmation (volume > 20-day average on breakout)
    val avgVolume20 = calculateAverageVolume(stock, 20)
    if (quote.volume <= avgVolume20) return false

    // 2. Minimum consolidation period (stock consolidated 10+ days before breakout)
    val consolidationDays = countConsolidationDays(stock, quote)
    if (consolidationDays < 10) return false

    // 3. Price strength relative to sector (stock outperforming sector)
    val stockChange = (quote.closePrice - stock.quotes[quote.date - 20].closePrice) / stock.quotes[quote.date - 20].closePrice
    val sectorChange = (sector.quotes[quote.date].closePrice - sector.quotes[quote.date - 20].closePrice) / sector.quotes[quote.date - 20].closePrice
    if (stockChange <= sectorChange) return false

    return true
}
```

**Expected Impact:**
- Reduce trade frequency by 20%
- Improve win rate by 5-10%
- Higher quality entry signals

---

### Priority 4: Dynamic Heatmap Thresholds ⭐

**Goal:** Adjust entry strictness based on market regime.

**Implementation:**

```kotlin
fun getHeatmapThreshold(marketRegime: MarketRegime): Double {
    return when (marketRegime) {
        MarketRegime.STRONG_BULL -> 70.0  // More lenient in strong trends
        MarketRegime.NEUTRAL -> 60.0       // More selective in neutral
        MarketRegime.VOLATILE -> 50.0      // Very selective in choppy markets
    }
}

// Replace current fixed heatmap < 70 check with:
val threshold = getHeatmapThreshold(currentMarketRegime)
if (quote.heatmap >= threshold) return false
```

**Expected Impact:**
- Better entry timing
- +0.5% edge improvement
- Adaptive to market conditions

---

### Priority 5: Add Trend Strength Filter (Medium Priority)

**Goal:** Only enter when strong trends are present.

**Implementation:**

```kotlin
fun hasStrongTrend(stock: Stock, quote: StockQuote): Boolean {
    // Use ADX (Average Directional Index)
    val adx = calculateADX(stock, quote, 14)

    // Only enter when ADX > 25 (strong trend present)
    // Avoid ADX < 20 (choppy, directionless)
    return adx >= 25.0
}
```

**Expected Impact:**
- Avoid directionless, choppy stocks
- Improve win rate on individual stocks
- Complement market regime filter

---

### Priority 6: Sector Rotation Awareness (Medium Priority)

**Goal:** Focus on sectors currently in favor.

**Implementation:**

```kotlin
fun isSectorInFavor(sector: Stock): Boolean {
    // Only trade sectors with positive momentum last 3 months
    val currentPrice = sector.quotes.last().closePrice
    val price90DaysAgo = sector.quotes[sector.quotes.size - 90].closePrice
    val return3Month = (currentPrice - price90DaysAgo) / price90DaysAgo

    // Sector must have positive 3-month return
    if (return3Month <= 0) return false

    // Sector must be outperforming market
    val marketReturn = calculateMarketReturn3Month()
    if (return3Month <= marketReturn) return false

    return true
}
```

**Expected Impact:**
- Focus on winning sectors
- Reduce exposure to lagging sectors
- Incremental edge improvement

---

## Exit Strategy Analysis

### Current Exit Strategy (PlanMoneyExitStrategy)

After removing `TenTwentyBearishCross`, the exit strategy uses:

1. **SellSignalExitStrategy** - Exits on sell signal (86.8% of trades)
2. **WithinOrderBlockExitStrategy(120)** - Exits in old resistance (12.5% of trades)

**Impact of Removing TenTwentyBearishCross:**
- Edge improved: 0.42% → 0.50% (+0.08%)
- Average loser improved: -5.11% → -4.96% (+0.15%)
- **Smaller impact than expected** - suggests it wasn't the main issue

### Exit Strategy Recommendations

**No major changes needed to exit strategy.** The current exits are performing well:
- 75.7% of winners exit on sell signal (natural trend exhaustion)
- Exit strategy is working as intended

**Optional Enhancement:**
- Add time-based exit at 45-60 days for trades that aren't progressing
- Add profit target at 8-10% to lock in gains

---

## Performance Targets

### With Recommended Improvements

| Metric | Current (2025) | Target | Improvement |
|--------|---------------|--------|-------------|
| Edge | 0.50% | 2.0%+ | +300% |
| Win Rate | 48.6% | 60%+ | +11.4% |
| Avg Winner | 6.27% | 6.5%+ | Maintain |
| Avg Loser | -4.96% | -4.5% | Reduce |
| Trades/Year | 144 | 80-100 | Quality over quantity |

### Expected Annual Returns

With improved edge and similar position sizing:
- Current 2025: ~72% (0.50% × 144 trades)
- Target: ~160-200% (2.0% × 80-100 trades)

---

## Implementation Plan

### Phase 1: Market Regime Filter (Weeks 1-2)

1. **Add required indicators to Stock/StockQuote:**
   - 50-day EMA
   - 200-day EMA
   - 200-day SMA
   - Days above/below moving averages

2. **Implement market regime detection:**
   - Create `MarketRegimeDetector.kt`
   - Integrate with `PlanAlphaEntryStrategy.kt`

3. **Backtest on 2021-2025 data:**
   - Verify it filters out most 2024-2025 trades
   - Ensure it keeps most 2022 trades
   - Check edge improvement

### Phase 2: Volatility Filter (Weeks 3-4)

1. **Add VIX data integration**
2. **Add ATR calculation**
3. **Implement gap detection**
4. **Backtest and measure impact**

### Phase 3: Entry Confirmation (Weeks 5-6)

1. **Add volume analysis**
2. **Add consolidation detection**
3. **Add relative strength calculation**
4. **Backtest and combine with previous filters**

### Phase 4: Testing & Validation (Weeks 7-8)

1. **Full backtest on 2021-2025 data**
2. **Walk-forward analysis**
3. **Out-of-sample testing**
4. **Paper trading for 1-2 months**

### Phase 5: Live Deployment (Week 9+)

1. **Start with small position sizes**
2. **Monitor performance vs. backtest expectations**
3. **Gradually scale up if performance matches**

---

## Risk Considerations

### Overfitting Risk

**Concern:** Optimizing on recent poor performance may overfit to 2024-2025 conditions.

**Mitigation:**
- Test all filters on full 2021-2025 dataset
- Ensure filters are based on sound market principles, not curve-fitting
- Validate on out-of-sample data
- Paper trade before live deployment

### Reduced Trade Frequency

**Concern:** Filters may reduce trades by 30-50%.

**Mitigation:**
- This is actually positive - "trade less, but better"
- Target 80-100 quality trades/year vs 144 mediocre trades
- Higher edge compensates for fewer trades
- Less time in market = less risk exposure

### Market Regime Changes

**Concern:** What if market enters permanent choppy state?

**Mitigation:**
- Strategy will sit in cash during unfavorable periods
- This is a feature, not a bug
- Avoid drawdowns during difficult markets
- Re-enter when conditions improve

---

## Success Metrics

### Key Performance Indicators (KPIs)

**After implementing changes, measure:**

1. **Edge:** Should reach 2.0%+ (vs 0.50% current)
2. **Win Rate:** Should reach 60%+ (vs 48.6% current)
3. **Trade Quality:**
   - Avg winner maintained or improved (>6.0%)
   - Avg loser reduced (<4.5%)
   - Risk/Reward ratio >1.3

4. **Consistency:**
   - Edge consistent across years
   - No single year with negative edge
   - Lower variance in annual returns

5. **Market Regime Response:**
   - Significantly fewer trades in choppy markets
   - Win rate in choppy markets >50% (vs current 48.6%)
   - Edge remains positive even in difficult years

---

## Conclusion

The strategy's recent underperformance (0.50% edge in 2025 vs 2.64% historical) is primarily due to **lack of market regime filtering**. The PlanAlphaEntryStrategy works excellently in trending bull markets (4.45% edge in 2022) but struggles in choppy conditions.

**Key Takeaways:**

✅ **Don't filter by sector** - All sectors are profitable over 4 years
✅ **Do filter by market regime** - This is the critical missing piece
✅ **Focus on quality over quantity** - Fewer, higher-quality trades
✅ **Implement incrementally** - Test each filter individually
✅ **Validate thoroughly** - Backtest, paper trade, then scale up

**Expected Outcome:** By adding market regime filtering and volatility controls, the strategy should return to its historical 2.0%+ edge even in mixed market conditions, while avoiding the severe drawdowns seen in 2024-2025.

---

## Appendix: Data Sources

### Analysis Data
- **Period:** 2021-01-01 to 2025-11-01
- **Total Trades:** 791
- **Data Source:** UdgaardController `/api/report/period` endpoint
- **Strategies Analyzed:** PlanAlphaEntryStrategy + PlanMoneyExitStrategy (after removing TenTwentyBearishCross)

### Key Files
- `PlanAlphaEntryStrategy.kt` - Entry logic (13 conditions)
- `PlanMoneyExitStrategy.kt` - Exit logic (SellSignal + OrderBlock)
- `StockService.kt` - Backtesting engine
- `MarketBreadthService.kt` - Market metrics

---

**Generated:** 2025-11-01
**Author:** Trading Strategy Analysis
**Next Review:** After Phase 1 implementation and backtest results
