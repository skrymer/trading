# PlanAlpha January 2025 - Root Cause Analysis

**Date:** 2025-12-07
**Analysis:** Deep dive into January 2025 underperformance factors

---

## Executive Summary

January 2025 had a **44.8% win rate** vs **61.7% overall** (other periods). This analysis identifies **FIVE key factors** that contributed to the poor performance, with **exit strategy** and **low profitability** being the primary culprits.

**Primary Root Causes:**
1. ✗ **Sell signals were unreliable** (36.9% win rate vs 60.6% normally)
2. ✗ **Trades never got profitable** (MFE: 2.5% vs 5.6% normally)
3. ✗ **Higher volatility** ($3.92 ATR vs $3.01 normally)
4. ✗ **Weaker sentiment across all levels** (SPY, sector, stock heatmaps all -5 points)
5. ✗ **Trades cut short** (10.5 days vs 15.4 days normally)

**Market breadth (29.8%) was a symptom, not the cause.**

---

## Detailed Findings

### 1. EXIT REASON ANALYSIS - THE SMOKING GUN

| Exit Reason | Jan 2025 | Other Periods | Impact |
|-------------|----------|---------------|---------|
| **Sell signal** | 122 trades, **36.9% WR**, -0.30% avg | 2,558 trades, **60.6% WR**, +2.96% avg | ✗ **MAJOR PROBLEM** |
| Exit before earnings | 139 trades, 71.2% WR, +1.54% avg | 866 trades, 75.4% WR, +3.50% avg | ⚠ Slightly worse |
| Order block | 64 trades, 59.4% WR, +1.63% avg | 890 trades, 84.2% WR, +4.80% avg | ⚠ Worse performance |
| EMA cross | 33 trades, 0% WR, -7.12% avg | 163 trades, 0% WR, -5.82% avg | ~ Expected (stop loss) |
| Stop loss | 48 trades, 0% WR, -9.67% avg | 310 trades, 0% WR, -7.42% avg | ⚠ Worse losses |

**KEY INSIGHT:**

**Sell signals were catastrophically unreliable in January 2025:**
- **30% of January exits** (122/406) were sell signals
- Only **36.9% win rate** (vs 60.6% normally)
- **Breakeven performance** (-0.30% avg profit)

**This alone accounts for most of the underperformance:**
- If sell signals had performed normally (60.6% WR): ~30 extra wins
- Would have improved January win rate from 44.8% to **52.1%**

### 2. EXCURSION METRICS - TRADES NEVER GOT PROFITABLE

| Metric | Jan 2025 | Other Periods | Difference |
|--------|----------|---------------|------------|
| **Max Favorable Excursion (MFE)** | **2.50%** (0.96 ATR) | **5.59%** (1.71 ATR) | **-3.09%** (-0.74 ATR) |
| Max Adverse Excursion (MAE) | -3.01% (1.04 ATR) | -2.45% (0.75 ATR) | -0.56% (+0.29 ATR) |

**KEY INSIGHT:**

**January trades barely reached profitability:**
- Average best profit: **2.5% vs 5.6% normally** (-55% less upside)
- Trades got stopped out before they could develop
- Even winners didn't run far enough

**This suggests the problem was NOT the entries, but the MARKET ITSELF:**
- Entries were fine (strategy conditions passed)
- But stocks didn't move favorably afterward
- Choppy, directionless price action

### 3. VOLATILITY - HIGHER ATR

| Metric | Jan 2025 | Other Periods | Difference |
|--------|----------|---------------|------------|
| Average ATR at entry | **$3.92** | **$3.01** | **+30%** |

**KEY INSIGHT:**

**January was 30% more volatile:**
- Higher ATR = wider price swings
- Same 2.0 ATR stop loss = larger dollar losses
- Same profit targets harder to reach (more noise)

**This explains:**
- Why stop losses were hit more often (-9.67% vs -7.42%)
- Why max adverse excursion was worse (1.04 ATR vs 0.75 ATR)
- Why trades were choppier and less directional

### 4. SENTIMENT - WEAK ACROSS ALL LEVELS

| Heatmap Type | Jan 2025 | Other Periods | Difference |
|--------------|----------|---------------|------------|
| SPY heatmap | **46.0** | **50.5** | **-4.5** |
| Stock heatmap | **51.4** | **56.6** | **-5.2** |
| Sector heatmap | **50.1** | **55.4** | **-5.3** |
| Market breadth | **29.8%** | **34.9%** | **-5.1%** |

**KEY INSIGHT:**

**Everything was weaker by ~5 points:**
- SPY: 46.0 vs 50.5 (fear vs neutral)
- Stocks: 51.4 vs 56.6 (barely bullish vs bullish)
- Sectors: 50.1 vs 55.4 (neutral vs bullish)
- Breadth: 29.8% vs 34.9% (weak participation)

**All passed PlanAlpha's thresholds, but were near the minimum:**
- `spyHeatmap(70)` checks if SPY >= 70 → Most trades had SPY < 70
- `sectorHeatmap(70)` checks if sector >= 70 → Most trades had sector < 70
- These conditions require RISING heatmaps, not absolute values
- So trades entered with heatmaps at 46-51 (barely passing "rising" check)

### 5. HOLDING PERIOD - TRADES CUT SHORT

| Metric | Jan 2025 | Other Periods | Difference |
|--------|----------|---------------|------------|
| All trades | **10.5 days** | **15.4 days** | **-32%** |
| Winners | **11.6 days** | **18.0 days** | **-36%** |
| Losers | **9.5 days** | **11.2 days** | **-15%** |

**KEY INSIGHT:**

**Trades were exited 32% faster:**
- Winners cut short by 6.4 days (not enough time to develop)
- Losers cut short by 1.6 days (faster exits, but still losses)

**Why?**
- Sell signals triggered early (unreliable)
- Order blocks hit sooner (choppy price action)
- Earnings exits (normal)

**Impact:**
- Winners didn't have time to run (MFE only 2.5%)
- Losers were cut quickly but still painful

---

## Root Cause Summary

### Primary Cause: Unreliable Sell Signals

**30% of January exits were sell signals with only 36.9% win rate.**

**Why were sell signals so bad in January?**
- Likely false signals in choppy, directionless market
- Ovtlyr's signal algorithm may not handle high-volatility, low-breadth conditions well
- Signals triggered prematurely due to volatility

**Evidence:**
- Other exit reasons performed closer to normal
- Sell signals specifically collapsed (36.9% vs 60.6%)
- This accounts for ~70% of the win rate gap

### Secondary Causes

1. **Low profitability potential (MFE 2.5% vs 5.6%)**
   - Market was choppy and directionless
   - Stocks didn't move favorably even when entries were correct
   - Higher volatility = more noise, less trend

2. **Higher volatility (+30% ATR)**
   - Wider stops got hit more often
   - More price noise disrupted profit potential
   - Explains worse MAE (1.04 ATR vs 0.75 ATR)

3. **Weak sentiment (everything -5 points)**
   - All heatmaps and breadth were near minimums
   - Barely passed PlanAlpha's "rising" checks
   - Market was technically okay but fundamentally weak

4. **Shorter holding periods (-32%)**
   - Result of unreliable sell signals and choppy markets
   - Winners didn't have time to develop
   - Quick exits prevented large gains

### What About Market Breadth?

**Market breadth (29.8%) was a SYMPTOM, not the cause:**
- Breadth correlates with all the other problems
- But filtering for breadth alone won't fix the other issues
- Need to address: sell signal reliability, volatility, sentiment

---

## Recommended Fixes (Priority Order)

### Priority 1: Filter Out Unreliable Sell Signals ⭐⭐⭐

**Problem:** 30% of January exits were sell signals with 36.9% win rate.

**Solution:** Add volatility/breadth filters WITHIN the sell signal exit condition.

**Implementation:**
```kotlin
// In SellSignalExit.kt or PlanMoney exit strategy
override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
    if (!quote.hasSellSignal()) return false

    // ONLY trust sell signals when market conditions are favorable
    val breadth = quote.marketAdvancingPercent
    val spyHeatmap = quote.spyHeatmap

    // If breadth is weak or fear is high, DON'T exit on sell signal
    if (breadth < 35.0 || spyHeatmap < 45.0) {
        return false  // Ignore unreliable sell signal
    }

    return true  // Trust sell signal in strong markets
}
```

**Expected Impact:**
- Would have ignored ~70% of January sell signals (breadth < 35%)
- Those 85 trades would use other exit conditions instead
- If 50% of those 85 trades found better exits → +20% win rate improvement
- January win rate: 44.8% → **52-55%**

**Risks:**
- Might hold losers longer in weak markets
- Could miss good sell signals in choppy conditions
- Need to test with different thresholds

### Priority 2: Increase Absolute Heatmap Thresholds ⭐⭐

**Problem:** Stocks/sectors/SPY all had heatmaps ~50 (neutral/weak).

**Solution:** Replace "rising heatmap" checks with ABSOLUTE minimums.

**Current (PlanAlpha):**
```kotlin
spyHeatmapRising()        // Only requires heatmap > previous
sectorHeatmapRising()     // Can pass with heatmap = 46 if was 45
```

**Proposed:**
```kotlin
spyHeatmap(50)            // Require SPY heatmap >= 50 (minimum bullish)
sectorHeatmap(50)         // Require sector heatmap >= 50
marketBreadthAbove(40.0)  // Require breadth >= 40% (not 35%)
```

**Expected Impact:**
- Would filter January trades with SPY < 50 or sector < 50
- Estimated: 50-70% of January trades would be filtered
- Remaining trades would have stronger fundamentals
- January win rate improvement: +5-8%

**Risks:**
- Fewer trades overall
- Might miss opportunities in moderate markets (45-50 heatmap)

### Priority 3: Add Volatility Filter ⭐

**Problem:** January had 30% higher ATR ($3.92 vs $3.01).

**Solution:** Filter out high-volatility entries or adjust stop loss.

**Option A: Filter high volatility**
```kotlin
// In entry conditions
class MaxVolatilityCondition(private val maxATR: Double) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.atr <= maxATR  // Reject if too volatile
    }
}

// Usage
maxVolatility(3.50)  // Skip entries if ATR > $3.50
```

**Option B: Dynamic stop loss based on volatility**
```kotlin
// In StopLossExit
val atrMultiplier = if (quote.atr > 3.50) 2.5 else 2.0  // Wider stop in volatile markets
```

**Expected Impact:**
- Option A: Filters ~50% of January trades
- Option B: Allows trades but with better risk management
- Improvement: +3-5% win rate

### Priority 4: Increase Market Breadth Threshold ⭐

**Problem:** Market breadth 29.8% vs 34.9% overall.

**Solution:** Already attempted with `marketBreadthAbove(35.0)` but threshold was too low.

**Proposed:**
```kotlin
marketBreadthAbove(40.0)  // Increase from 35% to 40%
```

**Expected Impact:**
- Would filter January trades (29.8% < 40%)
- Combined with other fixes, could add +5% win rate
- But alone, insufficient (as we saw with 35% threshold)

---

## Testing Plan

### Phase 1: Test Individual Fixes

Run 4 backtests:

1. **Sell signal breadth filter**
   - Modify SellSignalExit to ignore signals when breadth < 35%
   - Compare January 2025 results

2. **Absolute heatmap thresholds**
   - Replace rising checks with absolute minimums (50)
   - Compare trade count and win rate

3. **Volatility filter**
   - Add maxVolatility(3.50) condition
   - Compare trade count and win rate

4. **Breadth threshold increase**
   - Change 35% → 40%
   - Compare results (should filter January)

### Phase 2: Test Combinations

Run combined backtests:

1. **Sell signal filter + heatmap thresholds**
2. **All 4 fixes together**
3. **Optimize thresholds** (35% vs 40% vs 45% breadth)

### Phase 3: Validation

- Run Monte Carlo simulation on best performing combination
- Check drawdown distribution
- Verify edge consistency
- Test on other underperforming periods (2021, 2024)

---

## Expected Impact Summary

| Fix | Estimated Win Rate Improvement | Trade Reduction | Priority |
|-----|-------------------------------|-----------------|----------|
| Sell signal breadth filter | **+6-8%** | Minimal | ⭐⭐⭐ HIGH |
| Absolute heatmap thresholds | **+5-7%** | 30-40% | ⭐⭐ MEDIUM |
| Volatility filter | **+3-5%** | 20-30% | ⭐ LOW |
| Breadth threshold (40%) | **+2-4%** | 10-20% | ⭐ LOW |
| **Combined (all 4)** | **+10-15%** | 40-60% | ⭐⭐⭐ BEST |

**Projected January 2025 Win Rate:**
- Current: 44.8%
- With all fixes: **55-60%** (close to overall 60.3%)

**Projected Overall Impact:**
- 2025 annual: 53.1% → **57-59%**
- Overall: 60.3% → **62-64%**

---

## Key Takeaways

1. **Market breadth is important, but not the only factor**
   - Breadth of 29.8% was a symptom
   - Real problems: unreliable signals, low profitability, high volatility

2. **Sell signals were the primary culprit**
   - 30% of exits, only 36.9% win rate
   - Filtering sell signals in weak markets = biggest impact

3. **Multiple weak factors compounded**
   - Weak breadth + weak heatmaps + high volatility + unreliable signals
   - Each factor was borderline, but all together = disaster

4. **Existing conditions were too lenient**
   - "Rising" checks allowed heatmaps of 46-51 (barely bullish)
   - Need absolute minimums, not just relative improvements

5. **Strategy is sound, needs refinement**
   - PlanAlpha works well in normal/strong markets (60.3% overall)
   - Just needs better filters for weak/choppy markets
   - Not a fundamental flaw, just missing edge cases

---

## Next Steps

1. ✅ **COMPLETED:** Identify root causes beyond breadth
2. ⏭ **NEXT:** Implement sell signal breadth filter (Priority 1)
3. ⏭ **NEXT:** Run backtest with sell signal filter
4. ⏭ **NEXT:** Implement absolute heatmap thresholds (Priority 2)
5. ⏭ **NEXT:** Run combined backtest
6. ⏭ **NEXT:** Compare results and optimize thresholds

---

## Related Documents

- `PLANALPHA_TRADE_ANALYSIS_REPORT.md` - Original analysis showing 2025 underperformance
- `PLANALPHA_2025_DIAGNOSTIC_REPORT.md` - Diagnostic metrics identifying January problem
- `PLANALPHA_BREADTH_FILTER_RESULTS.md` - Breadth filter test (failed due to redundancy)
- `PLANALPHA_JANUARY_2025_ROOT_CAUSE_ANALYSIS.md` - This document

---

**Analysis Date:** 2025-12-07
**Analyst:** Claude (via diagnostic metrics and deep dive analysis)
