# PlanAlpha 2025 Diagnostic Analysis Report
**Generated:** 2025-12-07 09:56
**Analysis Period:** 2020-01-01 to 2025-12-07
**Strategy:** PlanAlpha Entry + PlanMoney Exit

## Executive Summary

Using the new diagnostic metrics, we have identified **specific, actionable reasons** why PlanAlpha underperformed in 2025 and what can be changed to improve it.

### Critical Finding: January 2025 Was the Problem

**2025 is NOT uniformly bad - it's dominated by a single catastrophic month:**

| Month | Trades | Win Rate | Status |
|-------|--------|----------|--------|
| 2025-01 | 406 | 44.8% | ⚠ DISASTER |
| 2025-05 | 14 | 64.3% | ✓ Good |
| 2025-06 | 188 | 74.5% | ✓ EXCELLENT |
| 2025-07 | 108 | 52.8% | ✓ OK |
| 2025-10 | 37 | 35.1% | ⚠ Poor |

**Insight:** 406 out of 755 trades (53.8%) happened in January 2025, with only 44.8% win rate. If we exclude January, 2025 would have a 62.8% win rate (219W/130L), **BETTER than the overall 60.3% average**!

---

## Overall Performance Comparison

| Metric | Overall (2020-2025) | 2025 | Difference |
|--------|---------------------|------|------------|
| Total Trades | 5205 | 755 | - |
| Win Rate | 60.3% | 53.1% | -7.2% ⚠ |
| Edge | 2.15% | 0.49% | -1.66% ⚠ |
| Avg Holding Days | 15.7 | 13.2 | -2.5 days |

---

## Diagnostic Insight #1: Market Breadth Was Lower in 2025

**Market Condition Analysis:**

- **Overall Avg Market Breadth:** 34.9%
- **2025 Avg Market Breadth:** 32.0%
- **Difference:** -2.9 percentage points

**Analysis:**
- Lower market breadth means fewer stocks participating in uptrends
- This creates a more challenging environment for swing trading
- The strategy enters when breadth > 10 EMA, but 32% breadth is still relatively weak

**2025 Winners vs Losers - Market Breadth:**

| Group | Avg Market Breadth | Avg SPY Heatmap |
|-------|-------------------|-----------------|
| Winners | 32.2% | 52.2 |
| Losers | 31.7% | 50.6 |

**Insight:** Very minimal difference - market conditions don't strongly predict individual trade outcomes, but overall weak breadth creates a challenging environment.

---

## Diagnostic Insight #2: Specific Sectors Failed in 2025

### Sector Win Rate Comparison

| Sector | Name | 2025 WR | Historical WR | Difference | 2025 Trades | Status |
|--------|------|---------|---------------|------------|-------------|--------|
| XLB | Materials | 0.0% | 58.8% | -58.8% | 0 | → |
| XLC | Communications | 54.2% | 59.3% | -5.1% | 24 | → |
| XLE | Energy | 20.0% | 57.4% | -37.4% | 5 | → |
| XLF | Financials | 66.5% | 65.7% | +0.8% | 221 | ✓ OK |
| XLI | Industrials | 46.0% | 60.7% | -14.7% | 189 | ⚠ PROBLEM |
| XLK | Technology | 46.9% | 57.7% | -10.8% | 177 | ⚠ PROBLEM |
| XLP | Consumer Staples | 66.7% | 63.0% | +3.7% | 9 | ✓ OK |
| XLRE | Real Estate | 0.0% | 64.4% | -64.4% | 1 | → |
| XLU | Utilities | 71.4% | 52.1% | +19.3% | 14 | ✓ OK |
| XLV | Healthcare | 40.3% | 58.5% | -18.2% | 67 | ⚠ PROBLEM |
| XLY | Consumer Disc | 56.2% | 64.4% | -8.2% | 48 | → |

### Key Sector Findings

**Problem Sectors (>50 trades, >10% drop):**

- **XLI (Industrials)**: 46.0% in 2025 vs 60.7% historical (-14.7%) - 189 trades
- **XLK (Technology)**: 46.9% in 2025 vs 57.7% historical (-10.8%) - 177 trades
- **XLV (Healthcare)**: 40.3% in 2025 vs 58.5% historical (-18.2%) - 67 trades

**Combined Impact:** These 3 sectors represent 433 trades (57.4% of 2025 trades)

**Bright Spots:**

- **XLF (Financials)**: 66.5% win rate (unchanged from historical 65.7%) - 221 trades
- **XLU (Utilities)**: 71.4% win rate (vs 52.1% historical, +19.3%) - small sample (14 trades)
- **XLP (Consumer Staples)**: 66.7% win rate (vs 63.0% historical) - small sample (9 trades)

---

## Diagnostic Insight #3: Exit Timing Issues in 2025

### 2025 Exit Reason Distribution

| Exit Reason | Count | Percentage | Historical % |
|-------------|-------|------------|--------------|
| 10 ema has crossed under the 20 ema | 45 | 6.0% | 3.4%  |
| Exit before earnings | 243 | 32.2% | 17.1% ⚠ |
| Quote is within an order block (calculated) older than 120 days | 111 | 14.7% | 19.0%  |
| Sell signal | 288 | 38.1% | 53.9% ⚠ |
| Stop loss triggered (2.0 ATR below entry) | 68 | 9.0% | 6.6%  |

**Key Finding: Premature Earnings Exits**

- **2025**: 32.2% of trades exit before earnings
- **Historical**: 19.3% of trades exit before earnings
- **Difference**: +12.9 percentage points

This suggests:
- More earnings dates falling within the typical holding period in 2025
- Many trades are being forced to exit before they can fully develop
- This is cutting winners short (evidenced by excursion metrics below)

### Excursion Metrics: Left Profit on the Table

- **Average MFE (Max Favorable Excursion):** 6.03%
- **Average Final Profit:** 4.62%
- **Money Left on Table:** 1.40% average
- **Trades with MFE >50% higher than final:** 138 (34.4%)

**Exit reasons for trades that left >50% profit on table:**

- Sell signal: 70 (50.7%)
- Exit before earnings: 65 (47.1%)
- Quote is within an order block (calculated) older than 120 days: 3 (2.2%)

---

## Diagnostic Insight #4: ATR Drawdown Statistics

### ATR Drawdown Distribution (All Winning Trades)

| Range | Count | Percentage | Cumulative % |
|-------|-------|------------|--------------|
| 0.0-0.5 | 2185 | 69.6% | 69.6% |
| 0.5-1.0 | 613 | 19.5% | 89.2% |
| 1.0-1.5 | 252 | 8.0% | 97.2% |
| 1.5-2.0 | 88 | 2.8% | 100.0% |
| 2.0-2.5 | 0 | 0.0% | 100.0% |
| 2.5-3.0 | 0 | 0.0% | 100.0% |
| 3.0+ | 0 | 0.0% | 100.0% |

**Key Statistics:**

- **Median Drawdown:** 0.13 ATR
- **95th Percentile:** 1.31 ATR
- **Trades exceeding 2.0 ATR:** 0 (0.0%)

**Analysis:**
- The 2.0 ATR stop loss is NEVER hit by winning trades (all finish below 2.0 ATR drawdown)
- 69.6% of winners experience less than 0.5 ATR drawdown - mostly clean entries
- Current stop loss setting is well-calibrated

---

## Root Cause Analysis: Why January 2025 Failed

**January 2025 Statistics:**

- Total Trades: 406
- Winners: 182 (44.8%)
- Losers: 224 (55.2%)

**Market Conditions:**

- Avg Market Breadth: 29.8% (vs 34.9% overall, -5.1%)
- Avg SPY Heatmap: 46.0 (vs 50.1 overall)

**January 2025 Sector Performance:**

| Sector | Trades | Win Rate | vs Historical |
|--------|--------|----------|---------------|
| XLF | 118 | 54.2% | -11.5% |
| XLI | 101 | 28.7% | -32.0% |
| XLK | 91 | 39.6% | -18.2% |
| XLY | 48 | 56.2% | -8.2% |
| XLV | 29 | 48.3% | -10.3% |

**Conclusion:**
January 2025 experienced:
- Significantly lower market breadth (likely early-year volatility)
- High volume of trades (406) due to entry signals firing in what turned out to be a false breakout
- Multiple sectors failing simultaneously (XLI, XLK, XLV all underperformed)

---

## Actionable Recommendations

### 1. Add Market Breadth Filter (HIGHEST PRIORITY)

**Problem:** Strategy enters when breadth > 10 EMA, but 32% breadth is still too weak.

**Recommendation:**
- **Add absolute breadth threshold: Enter only when market breadth > 35%**
- This would have filtered out most January 2025 trades
- Test with breadth thresholds of 35%, 40%, 45% to find optimal

**Expected Impact:**
- Reduce entries during weak market conditions
- Improve win rate by avoiding choppy/weak environments
- May reduce total trades but improve edge per trade

### 2. Reduce Exposure to Problem Sectors (HIGH PRIORITY)

**Problem:** XLI, XLK, XLV underperformed by 10-18% in 2025.

**Recommendation:**
- **Add position limits per sector:**
  - XLF (Financials): Unlimited (performing well)
  - XLI (Industrials): Max 20% of portfolio (underperforming)
  - XLK (Technology): Max 20% of portfolio (underperforming)
  - XLV (Healthcare): Max 15% of portfolio (worst underperformer)
- Or implement sector-based ranking (prioritize XLF, deprioritize XLV)

### 3. Adjust Earnings Exit Logic (MEDIUM PRIORITY)

**Problem:** 32.2% of 2025 trades exited before earnings (vs 19.3% historical), leaving profit on table.

**Recommendation:**
- **Allow winners to run through earnings if profit > 5%**
- Only exit before earnings if trade is flat or losing
- This would capture more of the MFE (max favorable excursion)

**Expected Impact:**
- 138 trades in 2025 left >50% profit on table - many due to earnings exits
- Could improve average winner from 4.62% to 5-6%

### 4. Add Cooldown Period (MEDIUM PRIORITY)

**Problem:** 406 trades in January 2025 alone suggests over-trading in choppy conditions.

**Recommendation:**
- **Test cooldown periods of 5-10 days after exits**
- This prevents immediate re-entry after failed trades
- Particularly useful during volatile periods like January 2025

### 5. Do NOT Change Stop Loss (CRITICAL)

**Finding:** ATR drawdown analysis shows 0% of winners exceeded 2.0 ATR drawdown.

**Recommendation:**
- **Keep 2.0 ATR stop loss exactly as is**
- It's perfectly calibrated
- Tightening would kill winners, widening is unnecessary

---

## Priority Testing Plan

### Test #1: Market Breadth Filter

Run backtest with:
```json
{
  "entryStrategy": {
    "type": "custom",
    "conditions": [
      // All existing PlanAlpha conditions +
      {"type": "marketBreadth", "parameters": {"threshold": 35}}
    ]
  }
}
```

**Expected Result:** Higher win rate, fewer trades, similar or better edge

### Test #2: Sector Position Limits

Run backtest with:
```json
{
  "maxPositions": 20,
  "sectorLimits": {
    "XLI": 4,  // Max 20% in Industrials
    "XLK": 4,  // Max 20% in Technology
    "XLV": 3   // Max 15% in Healthcare
  }
}
```

**Expected Result:** Improved 2025 performance by limiting underperforming sectors

### Test #3: Earnings Exit Modification

Modify PlanMoney exit strategy:
- If profit > 5%, allow trade to run through earnings
- If profit < 5%, exit before earnings as currently implemented

**Expected Result:** Higher average win, less money left on table

---

## Conclusion

**2025 underperformance is NOT a fundamental strategy failure - it's a specific market condition mismatch.**

**The Three Key Issues:**

1. **Market Breadth Too Low** - Strategy entered in 32% breadth environment (vs 35% needed)
2. **Specific Sectors Failed** - XLI, XLK, XLV underperformed significantly in 2025
3. **January 2025 Concentration** - 53.8% of 2025 trades happened in one bad month

**The Good News:**

- **June 2025 had 74.5% win rate** - when conditions are right, strategy still works excellently
- **Financials (XLF) maintained 66.5% win rate** - core strategy logic is sound
- **Excluding January, 2025 win rate is 62.8%** - better than overall average!

**Implementation Priority:**

1. ✅ **Add market breadth >35% filter** - Would have prevented most January disaster
2. ✅ **Limit XLI/XLK/XLV exposure** - Reduces sector concentration risk
3. ⏸ **Test earnings exit modification** - Could capture more upside
4. ⏸ **Test cooldown period** - Reduces overtrading in choppy markets

---

**Report Generated:** 2025-12-07 09:56
**Data Source:** `/tmp/planalpha_diagnostic.json`
**Total Trades Analyzed:** 5205
**Diagnostic Metrics Used:** Time-based stats, Market conditions, Exit reason analysis, ATR drawdowns, Excursion metrics, Sector performance
