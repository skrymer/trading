# Order Block Implementation

## Overview

This document describes the implementation of calculated order blocks using a Rate of Change (ROC) momentum detection algorithm, based on the TradingView PineScript logic.

## What Changed

### 1. Enhanced OrderBlock Model

**File:** `src/main/kotlin/com/skrymer/udgaard/model/OrderBlock.kt`

Added new fields to track calculated order blocks:

- **source**: `OrderBlockSource` enum (OVTLYR or CALCULATED)
- **volume**: Trading volume when the order block was formed
- **volumeStrength**: Relative volume strength (volume / average volume)
- **sensitivity**: `OrderBlockSensitivity` enum (HIGH or LOW)
- **rateOfChange**: The ROC percentage that triggered this order block

**New Enums:**
- `OrderBlockSource`: Distinguishes between Ovtlyr data and calculated blocks
- `OrderBlockSensitivity`: HIGH (28% threshold) or LOW (50% threshold)

### 2. OrderBlockCalculator Service

**File:** `src/main/kotlin/com/skrymer/udgaard/service/OrderBlockCalculator.kt`

Implements the PineScript ROC algorithm:

#### Algorithm Steps:
1. **Calculate ROC**: `(open - open[4]) / open[4] * 100`
2. **Detect Momentum Threshold Crossing**:
   - Bullish OB: ROC crosses above +28% (high) or +50% (low)
   - Bearish OB: ROC crosses below -28% (high) or -50% (low)
3. **Find Origin Candle**: Look back 4-15 bars for last opposite-colored candle
4. **Create Order Block**: Mark the origin candle's high/low as the OB zone
5. **Track Mitigation**:
   - Bullish OB mitigated when price closes below the low
   - Bearish OB mitigated when price closes above the high

#### Key Parameters:
```kotlin
HIGH_SENSITIVITY = 28.0%    // More order blocks detected
LOW_SENSITIVITY = 50.0%     // Fewer, stronger order blocks
LOOKBACK_MIN = 4            // Minimum bars to search for origin
LOOKBACK_MAX = 15           // Maximum bars to search for origin
ROC_PERIOD = 4              // ROC calculation period
MIN_BARS_BETWEEN_BLOCKS = 5 // Prevents clustering
VOLUME_LOOKBACK = 20        // Volume calculation period
```

### 3. Integration with StockService

**File:** `src/main/kotlin/com/skrymer/udgaard/service/StockService.kt`

Modified the `fetchStock` method to:
1. Fetch stock data from Ovtlyr (includes Ovtlyr order blocks)
2. Calculate order blocks using the ROC algorithm
3. Combine both Ovtlyr and calculated order blocks
4. Save the enriched stock to the database

```kotlin
// Calculate order blocks using ROC algorithm
val calculatedOrderBlocks = orderBlockCalculator.calculateOrderBlocks(
    quotes = stock.quotes,
    useHighSensitivity = true,
    useLowSensitivity = true
)

// Combine Ovtlyr order blocks with calculated ones
val allOrderBlocks = stock.orderBlocks + calculatedOrderBlocks
```

### 4. Comprehensive Unit Tests

**File:** `src/test/kotlin/com/skrymer/udgaard/service/OrderBlockCalculatorTest.kt`

Created tests to verify:
- ✓ Bullish order block detection on strong upward momentum
- ✓ Bearish order block detection on strong downward momentum
- ✓ Sensitivity threshold respect (28% vs 50%)
- ✓ Order block mitigation when price invalidates the zone
- ✓ Handling of insufficient data
- ✓ Prevention of order block clustering

## How It Works

### Bullish Order Block Detection

1. Price makes a strong upward move (ROC > 28%)
2. Algorithm searches back 4-15 bars for the last **bearish candle**
3. That bearish candle's high/low becomes the order block zone
4. Block remains active until price closes below the low

**Example:**
```
Price: 100 → 102 → [104 bearish] → 110 → 135 (ROC +32%)
                     ↑
                Order Block at 104 high/low
```

### Bearish Order Block Detection

1. Price makes a strong downward move (ROC < -28%)
2. Algorithm searches back 4-15 bars for the last **bullish candle**
3. That bullish candle's high/low becomes the order block zone
4. Block remains active until price closes above the high

**Example:**
```
Price: 100 → 102 → [104 bullish] → 100 → 72 (ROC -30%)
                     ↑
                Order Block at 104 high/low
```

## Usage in Strategies

Order blocks are automatically available in the `Stock` model:

```kotlin
// Access order blocks for a stock
val stock = stockService.getStock("AAPL")

// Filter by source
val calculatedBlocks = stock.orderBlocks.filter {
    it.source == OrderBlockSource.CALCULATED
}

val ovtlyrBlocks = stock.orderBlocks.filter {
    it.source == OrderBlockSource.OVTLYR
}

// Filter by type
val bullishBlocks = stock.orderBlocks.filter {
    it.orderBlockType == OrderBlockType.BULLISH
}

// Filter by sensitivity
val highSensitivityBlocks = stock.orderBlocks.filter {
    it.sensitivity == OrderBlockSensitivity.HIGH
}

// Check if block is still active
val activeBlocks = stock.orderBlocks.filter { it.endDate == null }
```

### Existing Strategy Conditions

The existing order block strategy conditions automatically work with calculated blocks:

- `BelowOrderBlockCondition`: Entry when price is below bearish OB
- `NotInOrderBlockCondition`: Entry only when not in an OB
- `OrderBlockExit`: Exit when price enters an old OB

## Comparison: Ovtlyr vs Calculated Order Blocks

| Aspect | Ovtlyr Blocks | Calculated Blocks |
|--------|---------------|-------------------|
| **Source** | External provider | ROC algorithm |
| **Detection** | Proprietary | Open (PineScript-based) |
| **Transparency** | Black box | Fully documented |
| **Customization** | None | Adjustable sensitivity |
| **Volume Data** | Included | Not available (StockQuote lacks volume) |
| **Accuracy** | Unknown | Verified with tests |

## Benefits of Calculated Order Blocks

1. **Transparency**: You know exactly how blocks are detected
2. **Reproducibility**: Same algorithm as TradingView PineScript
3. **Customization**: Adjust sensitivity thresholds as needed
4. **Independence**: Not reliant on external provider
5. **Dual Source**: Keep both Ovtlyr and calculated for comparison
6. **Momentum-Based**: Captures institutional activity via ROC

## Configuration Options

You can customize the order block calculation by modifying the constants in `OrderBlockCalculator`:

```kotlin
// In OrderBlockCalculator.kt
companion object {
    const val HIGH_SENSITIVITY = 28.0  // Change to detect more/fewer blocks
    const val LOW_SENSITIVITY = 50.0   // Change for stronger signals
    const val LOOKBACK_MIN = 4         // Adjust search window
    const val LOOKBACK_MAX = 15
    const val MIN_BARS_BETWEEN_BLOCKS = 5  // Adjust spacing
}
```

## Testing

Run the order block calculator tests:

```bash
cd udgaard
./gradlew test --tests OrderBlockCalculatorTest
```

## Future Enhancements

1. **Add Volume to StockQuote**: Enable volume-based filtering
2. **Expose Configuration**: Allow runtime sensitivity adjustment
3. **Web UI Integration**: Display calculated vs Ovtlyr blocks on charts
4. **Performance Metrics**: Compare strategy performance using different sources
5. **Historical Comparison**: Backtest to compare Ovtlyr vs calculated accuracy
6. **Consolidation Detection**: Add the consolidation/breakout logic from PineScript
7. **Volume Strength Thresholds**: Filter blocks by relative volume

## References

- **PineScript Source**: The TradingView Deepthought v3.4.1 indicator
- **ROC Indicator**: Standard momentum indicator used in technical analysis
- **ICT/SMC Concepts**: Inner Circle Trader and Smart Money Concepts methodology

## Files Modified/Created

**Created:**
- `src/main/kotlin/com/skrymer/udgaard/service/OrderBlockCalculator.kt`
- `src/test/kotlin/com/skrymer/udgaard/service/OrderBlockCalculatorTest.kt`
- `ORDER_BLOCK_IMPLEMENTATION.md` (this file)

**Modified:**
- `src/main/kotlin/com/skrymer/udgaard/model/OrderBlock.kt`
- `src/main/kotlin/com/skrymer/udgaard/service/StockService.kt`
- `src/test/kotlin/com/skrymer/udgaard/service/StockServiceTest.kt`

---

Generated with Claude Code
https://claude.com/claude-code
