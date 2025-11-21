# Order Block Exit Source Parameter Update

## Date
2025-11-18

## Overview
Added a `source` parameter to the `OrderBlockExit` condition to allow filtering order blocks by their source (CALCULATED, OVTLYR, or ALL).

## Changes Made

### 1. Stock.kt
**File**: `src/main/kotlin/com/skrymer/udgaard/model/Stock.kt`

Updated the `withinOrderBlock()` method to accept an optional `source` parameter:

```kotlin
fun withinOrderBlock(quote: StockQuote, daysOld: Int, source: OrderBlockSource? = null): Boolean {
    return orderBlocks
      .filter { source == null || it.source == source }
      .filter {
        ChronoUnit.DAYS.between(
          it.startDate,
          it.endDate ?: LocalDate.now()
        ) >= daysOld
      }
      .filter { it.orderBlockType == OrderBlockType.BEARISH }
      .filter { it.startDate.isBefore(quote.date) }
      .filter { it.endDate?.isAfter(quote.date) == true }
      .any { quote.closePrice > it.low && quote.closePrice < it.high }
  }
```

### 2. OrderBlockExit.kt
**File**: `src/main/kotlin/com/skrymer/udgaard/model/strategy/condition/exit/OrderBlockExit.kt`

Added `source` parameter with default value "CALCULATED":

```kotlin
class OrderBlockExit(
    private val orderBlockAgeInDays: Int = 120,
    private val source: String = "CALCULATED"
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        val orderBlockSource = when (source.uppercase()) {
            "CALCULATED" -> OrderBlockSource.CALCULATED
            "OVTLYR" -> OrderBlockSource.OVTLYR
            "ALL" -> null  // null means all sources
            else -> null
        }
        return stock.withinOrderBlock(quote, orderBlockAgeInDays, orderBlockSource)
    }

    override fun exitReason(): String {
        val sourceText = when (source.uppercase()) {
            "CALCULATED" -> " (calculated)"
            "OVTLYR" -> " (Ovtlyr)"
            else -> ""
        }
        return "Quote is within an order block$sourceText older than $orderBlockAgeInDays days"
    }

    override fun description(): String {
        val sourceText = when (source.uppercase()) {
            "CALCULATED" -> " calc"
            "OVTLYR" -> " Ovtlyr"
            else -> ""
        }
        return "Within order block$sourceText (age > ${orderBlockAgeInDays}d)"
    }
}
```

### 3. DynamicStrategyBuilder.kt
**File**: `src/main/kotlin/com/skrymer/udgaard/service/DynamicStrategyBuilder.kt`

Updated to handle the new `source` parameter:

```kotlin
"orderblock" -> {
    val ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120
    val source = config.parameters["source"] as? String ?: "CALCULATED"
    logger.info("  -> OrderBlockExit(ageInDays=$ageInDays, source=$source)")
    OrderBlockExit(ageInDays, source)
}
```

Added metadata for UI:

```kotlin
ConditionMetadata(
    type = "orderBlock",
    displayName = "Order Block",
    description = "Exit when price enters an order block",
    parameters = listOf(
        ParameterMetadata(
            name = "ageInDays",
            displayName = "Age in Days",
            type = "number",
            defaultValue = 120,
            min = 1,
            max = 365
        ),
        ParameterMetadata(
            name = "source",
            displayName = "Source",
            type = "select",
            defaultValue = "CALCULATED",
            options = listOf("CALCULATED", "OVTLYR", "ALL")
        )
    ),
    category = "ProfitTaking"
)
```

### 4. Test Updates

Updated all test files to use the new parameter:

**OrderBlockExitTest.kt**:
- Added comprehensive tests for source filtering
- Tests for CALCULATED, OVTLYR, and ALL sources
- Verified default behavior uses CALCULATED source

**PlanEtfStrategyIntegrationTest.kt**:
- Added `source = OrderBlockSource.CALCULATED` to order block creation
- Added import for `OrderBlockSource`

**PlanMoneyExitStrategyTest.kt**:
- Added `source = OrderBlockSource.CALCULATED` to order block creation
- Updated expected exit reason to include "(calculated)" suffix
- Added import for `OrderBlockSource`

## Usage Examples

### Using Calculated Order Blocks (Default)
```kotlin
val condition = OrderBlockExit(orderBlockAgeInDays = 120)
// or explicitly
val condition = OrderBlockExit(orderBlockAgeInDays = 120, source = "CALCULATED")
```

### Using Ovtlyr Order Blocks
```kotlin
val condition = OrderBlockExit(orderBlockAgeInDays = 120, source = "OVTLYR")
```

### Using All Order Blocks
```kotlin
val condition = OrderBlockExit(orderBlockAgeInDays = 120, source = "ALL")
```

### JSON Configuration
```json
{
  "type": "orderBlock",
  "parameters": {
    "ageInDays": 120,
    "source": "CALCULATED"
  }
}
```

## Default Behavior

**Default**: `source = "CALCULATED"`

This means by default, the exit condition will only consider order blocks that were calculated using the ROC algorithm, not the ones from the Ovtlyr data provider.

## Backward Compatibility

This is a **non-breaking change** for code that creates `OrderBlockExit` instances directly, as the new parameter has a default value.

However, the **default behavior has changed**:
- **Before**: Used all order blocks (both CALCULATED and OVTLYR)
- **After**: Uses only CALCULATED order blocks by default

## Test Results

All 175 tests passing:

```
BUILD SUCCESSFUL in 24s
175 tests completed, 0 failures
```

## Benefits

1. **Flexibility**: Users can now choose which order block source to trust for exit decisions
2. **Comparison**: Allows backtesting strategies using CALCULATED vs OVTLYR order blocks separately
3. **Control**: Fine-grained control over exit conditions based on order block origin
4. **Default to Calculated**: By default uses the algorithmically-calculated order blocks which match the Python reference implementation

## Files Modified

**Source Code**:
- `src/main/kotlin/com/skrymer/udgaard/model/Stock.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/strategy/condition/exit/OrderBlockExit.kt`
- `src/main/kotlin/com/skrymer/udgaard/service/DynamicStrategyBuilder.kt`

**Tests**:
- `src/test/kotlin/com/skrymer/udgaard/model/strategy/condition/exit/OrderBlockExitTest.kt`
- `src/test/kotlin/com/skrymer/udgaard/model/strategy/PlanEtfStrategyIntegrationTest.kt`
- `src/test/kotlin/com/skrymer/udgaard/model/strategy/PlanMoneyExitStrategyTest.kt`

---

*Update completed: 2025-11-18*
*Status: ✅ All Tests Passing (175/175)*
