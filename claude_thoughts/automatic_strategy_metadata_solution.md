# Automatic Strategy Metadata Extraction

## Problem

Manually maintaining strategy metadata is a maintenance nightmare because:
- Strategies change over time
- New conditions are added
- Parameters get updated
- Easy to get out of sync with actual code

## Solution: Automatic Metadata Extraction

Extract metadata directly from strategy instances at runtime using reflection and introspection.

---

## Approach

### 1. Add Metadata Interface to Conditions

Make all conditions expose their metadata:

```kotlin
// Add to TradingCondition interface
interface TradingCondition {
    fun evaluate(stock: Stock, quote: StockQuote): Boolean
    fun description(): String

    /**
     * Get metadata for this condition (type and parameters).
     * Used by UI to evaluate conditions client-side.
     */
    fun getMetadata(): ConditionMetadata {
        // Default implementation uses reflection
        return ConditionMetadata(
            type = this::class.simpleName ?: "unknown",
            description = description(),
            parameters = extractParameters()
        )
    }

    /**
     * Extract parameters from this condition using reflection.
     * Can be overridden for custom parameter extraction.
     */
    fun extractParameters(): Map<String, Any> {
        val params = mutableMapOf<String, Any>()

        // Use reflection to get constructor parameters
        this::class.constructors.firstOrNull()?.parameters?.forEach { param ->
            try {
                val field = this::class.java.getDeclaredField(param.name ?: return@forEach)
                field.isAccessible = true
                val value = field.get(this)
                if (value != null) {
                    params[param.name!!] = value
                }
            } catch (e: Exception) {
                // Ignore - field not accessible
            }
        }

        return params
    }
}
```

### 2. Override in Specific Conditions

Each condition can override `getMetadata()` to provide rich metadata:

```kotlin
// UptrendCondition
class UptrendCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.closePriceEMA10 > quote.closePriceEMA20
            && quote.closePrice > quote.closePriceEMA50
    }

    override fun description(): String =
        "Stock is in uptrend (10 EMA > 20 EMA and price > 50 EMA)"

    override fun getMetadata() = ConditionMetadata(
        type = "uptrend",
        description = description(),
        parameters = mapOf(
            "ema10Field" to "closePriceEMA10",
            "ema20Field" to "closePriceEMA20",
            "ema50Field" to "closePriceEMA50",
            "priceField" to "closePrice"
        )
    )
}

// ValueZoneCondition
class ValueZoneCondition(
    private val atrMultiplier: Double = 2.0
) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.closePrice <= quote.closePriceEMA20 + (quote.atr * atrMultiplier)
    }

    override fun description(): String =
        "Price within value zone (20 EMA + $atrMultiplier ATR)"

    override fun getMetadata() = ConditionMetadata(
        type = "valueZone",
        description = description(),
        parameters = mapOf(
            "emaField" to "closePriceEMA20",
            "atrField" to "atr",
            "atrMultiplier" to atrMultiplier,
            "priceField" to "closePrice"
        )
    )
}

// ProfitTargetExit
class ProfitTargetExit(
    private val atrMultiplier: Double = 3.5,
    private val emaPeriod: Int = 20
) : ExitCondition {
    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        val ema = when (emaPeriod) {
            5 -> quote.closePriceEMA5
            10 -> quote.closePriceEMA10
            20 -> quote.closePriceEMA20
            50 -> quote.closePriceEMA50
            else -> return false
        }
        return quote.closePrice >= ema + (quote.atr * atrMultiplier)
    }

    override fun exitReason(): String =
        "Price is $atrMultiplier ATR above $emaPeriod EMA"

    override fun description(): String =
        "Price extended $atrMultiplier ATR above $emaPeriod EMA"

    override fun getMetadata() = ConditionMetadata(
        type = "profitTarget",
        description = description(),
        parameters = mapOf(
            "emaField" to "closePriceEMA$emaPeriod",
            "atrField" to "atr",
            "atrMultiplier" to atrMultiplier,
            "emaPeriod" to emaPeriod,
            "priceField" to "closePrice"
        )
    )
}
```

### 3. Extract Metadata from Strategy Instances

Update the controller to extract metadata automatically:

```kotlin
@GetMapping("/strategies")
fun getAvailableStrategies(): ResponseEntity<StrategiesResponse> {
    logger.info("Retrieving available strategies with metadata")

    val response = StrategiesResponse(
        entryStrategies = strategyRegistry.getAvailableEntryStrategies(),
        exitStrategies = strategyRegistry.getAvailableExitStrategies(),
        metadata = extractStrategyMetadata()
    )

    logger.info("Returning ${response.entryStrategies.size} entry and ${response.exitStrategies.size} exit strategies with metadata")
    return ResponseEntity.ok(response)
}

/**
 * Extract metadata from registered strategy instances.
 * This uses the strategy's actual conditions to build metadata automatically.
 */
private fun extractStrategyMetadata(): Map<String, StrategyMetadata> {
    val metadata = mutableMapOf<String, StrategyMetadata>()

    // Get all registered strategy names
    val strategyNames = strategyRegistry.getAvailableEntryStrategies()

    strategyNames.forEach { strategyName ->
        try {
            // Create instances of the strategies
            val entryStrategy = strategyRegistry.createEntryStrategy(strategyName)
            val exitStrategy = strategyRegistry.createExitStrategy(strategyName)

            if (entryStrategy != null && exitStrategy != null) {
                metadata[strategyName] = StrategyMetadata(
                    id = strategyName,
                    name = formatStrategyName(strategyName),
                    description = entryStrategy.description(),
                    entryConditions = extractEntryConditions(entryStrategy),
                    exitConditions = extractExitConditions(exitStrategy),
                    cooldownDays = extractCooldownDays(strategyName)
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract metadata for strategy: $strategyName", e)
        }
    }

    return metadata
}

/**
 * Extract entry conditions from a strategy instance.
 */
private fun extractEntryConditions(strategy: EntryStrategy): List<StrategyConditionMetadata> {
    return when (strategy) {
        is CompositeEntryStrategy -> {
            strategy.getConditions().map { condition ->
                val metadata = condition.getMetadata()
                StrategyConditionMetadata(
                    type = metadata.type,
                    description = metadata.description,
                    parameters = metadata.parameters
                )
            }
        }
        else -> {
            // Non-composite strategy - create single condition
            listOf(
                StrategyConditionMetadata(
                    type = strategy::class.simpleName ?: "unknown",
                    description = strategy.description(),
                    parameters = emptyMap()
                )
            )
        }
    }
}

/**
 * Extract exit conditions from a strategy instance.
 */
private fun extractExitConditions(strategy: ExitStrategy): List<StrategyConditionMetadata> {
    return when (strategy) {
        is CompositeExitStrategy -> {
            strategy.getConditions().map { condition ->
                val metadata = condition.getMetadata()
                StrategyConditionMetadata(
                    type = metadata.type,
                    description = metadata.description,
                    parameters = metadata.parameters
                )
            }
        }
        else -> {
            // Non-composite strategy - create single condition
            listOf(
                StrategyConditionMetadata(
                    type = strategy::class.simpleName ?: "unknown",
                    description = strategy.description(),
                    parameters = emptyMap()
                )
            )
        }
    }
}

/**
 * Extract cooldown days for a strategy.
 * This could come from strategy configuration or annotations.
 */
private fun extractCooldownDays(strategyName: String): Int {
    // For now, hardcode known cooldown values
    // TODO: Make this configurable via annotations or strategy properties
    return when (strategyName) {
        "VegardPlanEtf" -> 10
        else -> 0
    }
}

private fun formatStrategyName(name: String): String {
    // Convert "VegardPlanEtf" to "Vegard Plan ETF"
    return name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
}
```

### 4. Add getConditions() to CompositeExitStrategy

```kotlin
class CompositeExitStrategy(
    private val conditions: List<ExitCondition>,
    private val operator: LogicalOperator = LogicalOperator.OR,
    private val strategyDescription: String? = null
) : ExitStrategy {

    // ... existing code ...

    /**
     * Returns all conditions in this composite strategy.
     */
    fun getConditions(): List<ExitCondition> = conditions
}
```

---

## Benefits

1. **Zero Maintenance**
   - Metadata extracted from actual strategy code
   - No manual updates needed
   - Always in sync with reality

2. **Type Safe**
   - Uses actual condition instances
   - Parameters come from constructor
   - Compile-time safety

3. **Automatic Updates**
   - Add new condition → automatically exposed in API
   - Change parameter → automatically reflected
   - Remove condition → automatically removed from metadata

4. **DRY Principle**
   - Single source of truth (the condition classes)
   - No duplication
   - Easier to maintain

5. **Rich Metadata**
   - Field names for UI to use
   - Parameter values
   - Descriptions from actual code

---

## Implementation Steps

1. Add `ConditionMetadata` data class
2. Add `getMetadata()` to `TradingCondition` interface
3. Add `getMetadata()` to `ExitCondition` interface
4. Implement `getMetadata()` in each condition class
5. Add `getConditions()` to `CompositeExitStrategy`
6. Update controller to use `extractStrategyMetadata()`
7. Remove manual metadata map

---

## Example Output

Same as before, but now automatically extracted:

```json
{
  "metadata": {
    "VegardPlanEtf": {
      "entryConditions": [
        {
          "type": "uptrend",
          "description": "Stock is in uptrend (10 EMA > 20 EMA and price > 50 EMA)",
          "parameters": {
            "ema10Field": "closePriceEMA10",
            "ema20Field": "closePriceEMA20",
            "ema50Field": "closePriceEMA50",
            "priceField": "closePrice"
          }
        },
        {
          "type": "valueZone",
          "description": "Price within value zone (20 EMA + 1.4 ATR)",
          "parameters": {
            "emaField": "closePriceEMA20",
            "atrField": "atr",
            "atrMultiplier": 1.4,
            "priceField": "closePrice"
          }
        }
      ],
      "exitConditions": [ /* automatically extracted */ ]
    }
  }
}
```

---

## Migration Path

1. Implement `getMetadata()` in conditions (can be gradual)
2. Update controller extraction logic
3. Test with existing strategies
4. Remove manual metadata map
5. All future conditions automatically work

This is much better! No manual maintenance required.
