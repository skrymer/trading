# UptrendCondition Update

## Changes Made

Updated the `UptrendCondition` to use explicit EMA checks instead of relying on the `trend` field.

### New Logic

An uptrend is now defined as:
1. **10 EMA > 20 EMA** - Short-term trend above medium-term trend
2. **Close Price > 50 EMA** - Price above long-term trend

Both conditions must be `true` for the stock to be considered in an uptrend.

### Code Changes

**Before:**
```kotlin
override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
    return quote.isInUptrend()  // Checked trend field
}
```

**After:**
```kotlin
override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
    // Check if 10 EMA > 20 EMA
    val ema10AboveEma20 = quote.closePriceEMA10 > quote.closePriceEMA20

    // Check if close price > 50 EMA
    val priceAboveEma50 = quote.closePrice > quote.closePriceEMA50

    // Both conditions must be true for uptrend
    return ema10AboveEma20 && priceAboveEma50
}
```

### Test Coverage

Added comprehensive tests covering:
- ✓ Both conditions true (uptrend confirmed)
- ✓ 10 EMA below 20 EMA (not uptrend)
- ✓ Price below 50 EMA (not uptrend)
- ✓ Both conditions fail (definitely not uptrend)
- ✓ Boundary cases (just above thresholds)
- ✓ Exact equality (not considered uptrend)
- ✓ Description updated

### Impact

This change makes the uptrend condition:
- **More explicit** - Clear EMA-based rules
- **More reliable** - Not dependent on trend field calculation
- **More testable** - Concrete numeric comparisons
- **More consistent** - Same logic as other EMA-based conditions

### Files Modified

1. `UptrendCondition.kt` - Updated evaluate() logic and description
2. `UptrendConditionTest.kt` - Replaced old tests with EMA-based tests

All tests passing ✓
