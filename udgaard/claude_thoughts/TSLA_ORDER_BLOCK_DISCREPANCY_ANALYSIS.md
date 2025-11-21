# TSLA Order Block Calculation Discrepancy Analysis

## Executive Summary

**CRITICAL ISSUE FOUND**: The Kotlin order block calculator is severely under-detecting order blocks compared to the Python reference implementation.

- **Python Implementation**: 81 order blocks detected
- **Kotlin Implementation (CALCULATED)**: 5 order blocks detected
- **Discrepancy**: 76 blocks missing (93.8% under-detection)

## Data Overview

### TSLA Stock Data
- **Symbol**: TSLA
- **Date Range**: 2020-05-11 to 2025-11-17
- **Total Quotes**: 1,389
- **OHLC Completeness**: 100% (all quotes have valid open, high, low, close prices)

### Order Block Counts

| Source | Total | Bullish | Bearish |
|--------|-------|---------|---------|
| **Python** | 81 | 35 | 46 |
| **Kotlin (CALCULATED)** | 5 | 4 | 1 |
| **Kotlin (OVTLYR)** | 81 | 43 | 38 |
| **Kotlin (TOTAL)** | 86 | 47 | 39 |

## Key Findings

### 1. Massive Under-Detection
The Kotlin calculator detected only **5 blocks** compared to Python's **81 blocks** - a **93.8% under-detection rate**.

### 2. Zero Matches
**0 blocks matched** between Python and Kotlin implementations, indicating the algorithms are detecting completely different patterns.

### 3. Different Detection Patterns

**Python-detected blocks (first 10):**
```
1. bear | 2020-05-11 | H:  54.93 L:  52.33 | ROC: -0.69%
2. bear | 2020-06-09 | H:  63.63 L:  61.60 | ROC: -2.36%
3. bear | 2020-07-10 | H: 103.26 L:  91.73 | ROC: -8.77%
4. bear | 2020-08-27 | H: 153.04 L: 142.83 | ROC:-11.28%
5. bull | 2020-09-08 | H: 122.91 L: 109.96 | ROC:  7.01%
```

**Kotlin-detected blocks (all 5):**
```
1. bull | 2020-06-26 | H:  66.33 L:  63.66 | ROC: 31.75%
2. bull | 2020-08-11 | H:  94.67 L:  91.00 | ROC: 29.18%
3. bear | 2020-08-31 | H: 166.71 L: 146.70 | ROC:-29.10%
4. bull | 2024-04-19 | H: 150.94 L: 146.22 | ROC: 31.46%
5. bull | 2024-11-04 | H: 248.90 L: 238.88 | ROC: 40.01%
```

### 4. ROC Threshold Difference

Notice the ROC values:
- **Python blocks**: ROC ranges from -0.69% to -11.28% for bears, 7.01% for bulls
- **Kotlin blocks**: ROC ranges from 29.18% to 40.01% for bulls, -29.10% for bears

The Kotlin implementation appears to only detect **very large momentum moves** (>29% ROC), while Python detects smaller moves as well.

## Root Cause Analysis

### Hypothesis: ROC Calculation Discrepancy

Looking at the Python script:
```python
sensitivity /= 100  # 28 becomes 0.28
df['ROC_Open'] = (df['Open'] - df['Open'].shift(4)) / df['Open'].shift(4) * 100  # Returns percentage
```

This means:
- ROC is calculated as percentage (e.g., 29.63 for 29.63%)
- But sensitivity is 0.28 (divided by 100)
- Comparison: `29.63 > 0.28` ✓ (would always be true for any move > 0.28%)

**This is the bug in the Python script!** It should either:
1. NOT multiply ROC by 100, OR
2. NOT divide sensitivity by 100

### What We Implemented in Kotlin

We assumed the Python code had a bug and implemented:
```kotlin
// ROC returns decimal (0.2963 for 29.63%)
return (currentOpen - previousOpen) / previousOpen

// Sensitivity is decimal (0.28)
val threshold = sensitivity / 100.0

// Comparison: 0.2963 > 0.28 ✓
```

But looking at the actual Python results, it seems the Python code is working as written, which means there's an inconsistency in the Python script itself that we need to understand.

## The Python Script Behavior

Let me re-examine the Python code more carefully:

```python
sensitivity /= 100  # INPUT: 28 → OUTPUT: 0.28
df['ROC_Open'] = (df['Open'] - df['Open'].shift(4)) / df['Open'].shift(4) * 100

# Crossing conditions:
df['ROC_Open'].iloc[i - 1] > -sensitivity  # e.g., -0.5 > -0.28 = True
df['ROC_Open'].iloc[i] < -sensitivity      # e.g., -0.69 > -0.28 = False (wrong!)
```

Wait! Looking at the Python results, block #1 has ROC: -0.69%. This means:
- `df['ROC_Open'].iloc[i]` = -0.69 (the percentage form)
- `sensitivity` = 0.28 (the decimal form)
- Comparison: `-0.69 < -0.28` → True ✓

This actually works! Because -0.69 IS less than -0.28.

So the Python code compares:
- **ROC in percentage** (like -0.69)
- **Sensitivity in decimal** (like -0.28)

And it works because of the way negative/positive comparisons work:
- `-0.69 < -0.28` is TRUE (further left on number line)
- `0.69 > 0.28` is TRUE (further right on number line)

## What Our Kotlin Code Does

Our Kotlin code:
```kotlin
// ROC returns decimal (like -0.0069)
return (currentOpen - previousOpen) / previousOpen

// Sensitivity is decimal (0.28)
val threshold = 28.0 / 100.0 = 0.28

// Comparison: -0.0069 < -0.28 = FALSE ❌
```

This is wrong! `-0.0069` is NOT less than `-0.28` (it's actually greater/closer to zero).

## The Fix

We need to multiply ROC by 100 in the Kotlin code to match Python behavior:

```kotlin
// Return as percentage (like -0.69 for -0.69%)
return ((currentOpen - previousOpen) / previousOpen) * 100.0
```

Then the comparison becomes:
- ROC: -0.69 (percentage)
- Threshold: -0.28 (decimal, but used as percentage comparison)
- `-0.69 < -0.28` = TRUE ✓

## Conclusion

**The issue is in our Kotlin implementation.** We incorrectly removed the `* 100` from the ROC calculation, thinking the Python code had a bug. But the Python code actually works due to the way the comparisons are structured.

### Required Fix

1. Change `calculateRateOfChange()` to multiply by 100
2. Keep sensitivity division by 100
3. This creates an "apples to oranges" comparison that actually works correctly

### Action Items

1. ✅ Identify the root cause
2. ⏳ Update `OrderBlockCalculator.kt` to multiply ROC by 100
3. ⏳ Update the storage to divide by 100 again for display (or keep as percentage)
4. ⏳ Re-run tests to verify fix
5. ⏳ Re-test with TSLA data

---

*Analysis Date: 2025-11-18*
*TSLA data range: 2020-05-11 to 2025-11-17*
*Critical bug identified in Kotlin ROC calculation*
