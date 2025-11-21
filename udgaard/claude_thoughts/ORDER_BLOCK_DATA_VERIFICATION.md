# Order Block Calculation - Data Verification

## Overview

This document verifies that all necessary data is available and correctly populated for accurate order block calculations.

## Required Data Fields

The order block calculator requires the following fields from `StockQuote`:

### 1. Open Price (`openPrice`) ✅
- **Source**: Ovtlyr API (`open` field in JSON)
- **Location**: `OvtlyrStockQuote.kt` line 90-91
- **Mapping**: Line 226 - `openPrice = this.openPrice ?: 0.0`
- **Usage**: ROC calculation - `(currentOpen - previousOpen) / previousOpen`
- **Status**: ✅ **AVAILABLE** - Provided by Ovtlyr

### 2. Close Price (`closePrice`) ✅
- **Source**: Ovtlyr API (`close` field in JSON)
- **Location**: `OvtlyrStockQuote.kt` line 84-85
- **Mapping**: Line 225 - `closePrice = this.closePrice`
- **Usage**:
  - Determining candle color (bullish/bearish)
  - Mitigation detection
- **Status**: ✅ **AVAILABLE** - Provided by Ovtlyr

### 3. High Price (`high`) ✅
- **Source**: Ovtlyr API (`high` field in JSON)
- **Location**: `OvtlyrStockQuote.kt` line 100-103
- **Mapping**: Line 257 - `high = high`
- **Usage**: Order block upper boundary
- **Status**: ✅ **AVAILABLE** - Provided by Ovtlyr

### 4. Low Price (`low`) ✅
- **Source**: Ovtlyr API (`low` field in JSON)
- **Location**: `OvtlyrStockQuote.kt` line 95-97
- **Mapping**: Line 258 - `low = low`
- **Usage**: Order block lower boundary
- **Status**: ✅ **AVAILABLE** - Provided by Ovtlyr

### 5. Date (`date`) ✅
- **Source**: Ovtlyr API (`quotedate` field in JSON)
- **Location**: `OvtlyrStockQuote.kt` line 78-79
- **Mapping**: Line 224 - `date = this.date`
- **Usage**: Order block start/end dates, sorting
- **Status**: ✅ **AVAILABLE** - Provided by Ovtlyr

### 6. Volume (`volume`) ⚠️
- **Source**: Alpha Vantage API (enrichment step)
- **Location**: `StockService.kt` line 503
- **Enrichment**: `quote.volume = matchingAlphaQuote.volume`
- **Usage**: Volume strength calculation (relative to 20-day average)
- **Status**: ⚠️ **PARTIALLY AVAILABLE** - Requires Alpha Vantage enrichment
- **Fallback**: Defaults to `0L` if Alpha Vantage data unavailable

## Data Flow

```
1. Ovtlyr API → OvtlyrStockQuote
   ├─ open, close, high, low ✅
   ├─ date ✅
   └─ volume ❌ (not provided)

2. OvtlyrStockQuote.toModel() → StockQuote
   ├─ Maps all price fields ✅
   ├─ Maps date ✅
   └─ volume defaults to 0L

3. StockService.enrichStock()
   ├─ Fetches from Alpha Vantage API
   └─ Enriches quotes with volume data ⚠️

4. OrderBlockCalculator.calculateOrderBlocks()
   ├─ Uses open prices for ROC ✅
   ├─ Uses close for candle color ✅
   ├─ Uses high/low for boundaries ✅
   ├─ Uses date for timestamps ✅
   └─ Uses volume for strength calculation ⚠️
```

## Critical OHLC Data

The Python implementation requires:
```python
# ROC calculation
df['ROC_Open'] = (df['Open'] - df['Open'].shift(4)) / df['Open'].shift(4)

# Candle color detection
if df['Close'].iloc[j] > df['Open'].iloc[j]:  # Bullish candle
if df['Close'].iloc[j] < df['Open'].iloc[j]:  # Bearish candle

# Order block boundaries
high = df['High'].iloc[start_idx]
low = df['Low'].iloc[start_idx]

# Mitigation detection
if df['Close'].iloc[k] > high:  # Bearish block mitigated
if df['Close'].iloc[k] < low:   # Bullish block mitigated
```

### Verification: ✅ ALL OHLC DATA AVAILABLE

| Field | Required | Available | Source |
|-------|----------|-----------|--------|
| Open  | Yes      | ✅ Yes    | Ovtlyr |
| High  | Yes      | ✅ Yes    | Ovtlyr |
| Low   | Yes      | ✅ Yes    | Ovtlyr |
| Close | Yes      | ✅ Yes    | Ovtlyr |
| Date  | Yes      | ✅ Yes    | Ovtlyr |
| Volume| Optional | ⚠️ Partial | Alpha Vantage |

## Volume Data Considerations

### Current Implementation
```kotlin
// StockService.kt:503
val matchingAlphaQuote = alphaQuotes.find { it.date == quote.date }
if (matchingAlphaQuote != null) {
    quote.volume = matchingAlphaQuote.volume
} else {
    unmatchedCount++  // Volume remains 0L
}
```

### Volume Strength Calculation
```kotlin
// OrderBlockCalculator.kt:224-253
private fun calculateVolumeStrength(quotes: List<StockQuote>, index: Int): Double {
    if (index < VOLUME_LOOKBACK) {
        return 1.0 // Not enough data
    }

    val currentVolume = quotes[index].volume

    if (currentVolume == 0L) {
        return 1.0  // Fallback for missing volume
    }

    val volumeList = quotes.subList(startIndex, index)
        .map { it.volume }
        .filter { it > 0 } // Filter out zero volumes

    if (volumeList.isEmpty()) {
        return 1.0  // Fallback
    }

    val avgVolume = volumeList.average()
    return if (avgVolume > 0) currentVolume.toDouble() / avgVolume else 1.0
}
```

### Impact of Missing Volume Data

**✅ Order blocks WILL still be calculated correctly** even if volume is missing:
1. ROC calculation doesn't use volume ✅
2. Candle color detection doesn't use volume ✅
3. Order block boundaries don't use volume ✅
4. Mitigation detection doesn't use volume ✅

**⚠️ Volume strength will default to 1.0**:
- Not critical for basic order block detection
- Used only as metadata for filtering/ranking
- Does not affect whether blocks are created

## Data Quality Checks

### 1. OHLC Data Integrity ✅
```kotlin
// All OHLC fields are mandatory in Ovtlyr API
@JsonProperty("open") private val openPrice: Double? = null
@JsonProperty("high") val high: Double = 0.0
@JsonProperty("low") val low: Double = 0.0
@JsonProperty("close") private val closePrice: Double = 0.0
```

### 2. Date Sorting ✅
```kotlin
// OrderBlockCalculator.kt:62
val sortedQuotes = quotes.sortedBy { it.date }
```

### 3. Minimum Data Requirements ✅
```kotlin
// OrderBlockCalculator.kt:58-60
if (quotes.size < LOOKBACK_MAX + ROC_PERIOD) {  // Need at least 19 quotes
    return emptyList()
}
```

### 4. Zero/Null Handling ✅
```kotlin
// ROC calculation
if (previousOpen == 0.0) return 0.0

// Volume fallback
if (currentVolume == 0L) return 1.0

// Empty volume list
if (volumeList.isEmpty()) return 1.0
```

## Potential Issues

### 1. Missing Open Prices ⚠️
**Risk Level**: LOW
**Mitigation**:
- Ovtlyr includes open prices in API
- Fallback to 0.0 if null (line 226)
- ROC calculation returns 0.0 for zero values

**Impact**: If open price is missing:
- ROC will be 0.0
- No order blocks will be detected for that period
- Algorithm continues processing remaining data

### 2. Volume Data Gaps ⚠️
**Risk Level**: VERY LOW
**Mitigation**:
- Volume strength defaults to 1.0
- Only affects metadata, not detection

**Impact**:
- Order blocks still created ✅
- Volume strength less accurate ⚠️

### 3. Date Mismatches ⚠️
**Risk Level**: LOW
**Mitigation**:
- Date matching uses exact equality
- Logs unmatched dates

**Impact**:
- Some quotes may lack volume
- Fallback to 1.0 for strength

## Recommendations

### ✅ Current State is Sufficient
The current implementation has **all required data** for correct order block calculation:

1. **OHLC Data**: Fully available from Ovtlyr ✅
2. **Date Data**: Fully available from Ovtlyr ✅
3. **Volume Data**: Optional enrichment from Alpha Vantage ⚠️

### Optional Improvements

If you want to enhance volume data quality:

1. **Store Volume in Ovtlyr**
   - Add volume field to Ovtlyr data model
   - Reduces dependency on Alpha Vantage

2. **Cache Alpha Vantage Data**
   - Already implemented via Spring Cache
   - 30-minute TTL

3. **Volume Data Validation**
   ```kotlin
   // Add validation logging
   val volumeCoverage = stock.quotes.count { it.volume > 0 } / stock.quotes.size.toDouble()
   if (volumeCoverage < 0.8) {
       logger.warn("Low volume coverage for $symbol: ${volumeCoverage * 100}%")
   }
   ```

4. **Alternative Volume Sources**
   - Yahoo Finance
   - IEX Cloud
   - Twelve Data

## Conclusion

### ✅ YES - We have all necessary data for correct order block calculation

**Critical Data (Required)**:
- ✅ Open, High, Low, Close prices
- ✅ Dates
- ✅ Proper sorting

**Optional Data (Enhanced features)**:
- ⚠️ Volume (for strength calculation)

**The order block algorithm will work correctly with the current data.** Volume is only used for the `volumeStrength` metadata field and does not affect the core detection logic.

### Test Coverage

All tests pass with synthetic data that includes:
- ✅ Open prices
- ✅ Close prices
- ✅ High prices
- ✅ Low prices
- ✅ Dates
- ✅ Volume (optional, defaults to 0)

The calculator correctly:
- ✅ Detects bullish order blocks
- ✅ Detects bearish order blocks
- ✅ Respects sensitivity thresholds
- ✅ Mitigates blocks appropriately
- ✅ Prevents clustering
- ✅ Handles insufficient data

---

*Last Updated: 2025-11-18*
*All critical data fields verified and available*
