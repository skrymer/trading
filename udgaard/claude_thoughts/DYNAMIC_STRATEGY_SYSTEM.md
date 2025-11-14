# Dynamic Strategy System

## Overview

The trading system now uses a **dynamic strategy discovery system** that eliminates the need to manually update the controller when adding new strategies. The system automatically discovers and registers strategies using Spring component scanning and annotations.

## Architecture

### Components

1. **`@RegisteredStrategy` Annotation**: Marks a strategy class for automatic discovery
2. **`StrategyRegistry` Service**: Manages strategy discovery and instantiation
3. **Controller Integration**: Uses the registry to get available strategies and create instances
4. **Frontend Integration**: Fetches strategies dynamically from the API

### Strategy Types

- **Named Strategies**: Concrete classes (e.g., `PlanAlphaEntryStrategy`, `PlanMoneyExitStrategy`)
- **DSL Strategies**: Dynamically created using the strategy DSL (e.g., `HalfAtr`, `Heatmap`)

## How It Works

### 1. Named Strategies

Named strategies are concrete classes annotated with `@RegisteredStrategy`:

```kotlin
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.ENTRY)
class PlanAlphaEntryStrategy: EntryStrategy {
  private val compositeStrategy = entryStrategy {
    // Strategy conditions...
  }

  override fun description() = "Plan Alpha entry strategy"

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    return compositeStrategy.test(stock, quote)
  }
}
```

The `StrategyRegistry` automatically discovers these classes at startup using Spring's component scanning.

### 2. DSL Strategies

DSL strategies are registered manually in `StrategyRegistry.registerDslExitStrategies()`:

```kotlin
private fun registerDslExitStrategies() {
  exitStrategies["HalfAtr"] = { exitStrategy { stopLoss(0.5) } }
  exitStrategies["Heatmap"] = { exitStrategy { heatmapThreshold() } }
  // ... more DSL strategies
}
```

### 3. API Integration

The controller uses the registry to:
- Get available strategies: `strategyRegistry.getAvailableEntryStrategies()`
- Create strategy instances: `strategyRegistry.createEntryStrategy(name)`

```kotlin
@GetMapping("/strategies/available")
fun getAvailableStrategies(): ResponseEntity<Map<String, List<String>>> {
  return ResponseEntity(
    mapOf(
      "entryStrategies" to strategyRegistry.getAvailableEntryStrategies(),
      "exitStrategies" to strategyRegistry.getAvailableExitStrategies()
    ),
    HttpStatus.OK
  )
}
```

### 4. Frontend Integration

The frontend fetches strategies dynamically:

```typescript
const { data: availableStrategies } = useFetch<{
  entryStrategies: string[]
  exitStrategies: string[]
}>('/udgaard/api/strategies/available')
```

## Adding a New Strategy

### Adding a Named Entry Strategy

1. Create a new class implementing `EntryStrategy`
2. Add the `@RegisteredStrategy` annotation
3. Implement the required methods
4. **That's it!** The strategy will be automatically discovered and available in the UI

```kotlin
@RegisteredStrategy(name = "MyNewStrategy", type = StrategyType.ENTRY)
class MyNewEntryStrategy: EntryStrategy {
  override fun description() = "My new trading strategy"

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    // Your strategy logic
    return true
  }
}
```

### Adding a Named Exit Strategy

Same as entry strategy, but use `StrategyType.EXIT`:

```kotlin
@RegisteredStrategy(name = "MyNewExit", type = StrategyType.EXIT)
class MyNewExitStrategy: ExitStrategy {
  override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
    // Your exit logic
    return false
  }

  override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): String? {
    return "Exit reason"
  }

  override fun description() = "My new exit strategy"
}
```

### Adding a DSL-Based Strategy

1. Open `StrategyRegistry.kt`
2. Add a new entry to `registerDslExitStrategies()` method:

```kotlin
private fun registerDslExitStrategies() {
  // ... existing strategies
  exitStrategies["MyDslExit"] = {
    exitStrategy {
      priceBelowEma(20)
      stopLoss(1.5)
    }
  }
}
```

## Benefits

✅ **No manual controller updates** - Just add the annotation and you're done
✅ **Always in sync** - UI automatically gets new strategies
✅ **Type-safe** - Strategy instances are validated at compile time
✅ **Maintainable** - Single source of truth for available strategies
✅ **Extensible** - Easy to add new strategy types or registration methods
✅ **Testable** - Can easily register test strategies via `registerEntryStrategy()` / `registerExitStrategy()`

## Manual Registration (Advanced)

For testing or dynamic strategy creation, you can manually register strategies:

```kotlin
@Autowired
lateinit var strategyRegistry: StrategyRegistry

fun registerCustomStrategy() {
  strategyRegistry.registerExitStrategy("CustomExit") {
    exitStrategy {
      heatmapThreshold()
      priceBelowEma(10)
    }
  }
}
```

## Troubleshooting

### Strategy not appearing in UI

1. Check that the class has `@RegisteredStrategy` annotation
2. Verify the annotation has the correct `name` and `type`
3. Ensure the class is in a package that's scanned by Spring (should be under `com.skrymer.udgaard`)
4. Check server logs for "Strategy Registry initialized" message

### Strategy name mismatch

The strategy name in the annotation must match the name used in API calls (case-sensitive):
- Annotation: `@RegisteredStrategy(name = "PlanAlpha", ...)`
- API call: `?entryStrategy=PlanAlpha`

### DSL strategy not working

For DSL strategies, ensure they're registered in `registerDslExitStrategies()` method in `StrategyRegistry.kt`.

## Migration from Hardcoded Lists

**Before (Manual):**
```kotlin
// Controller had hardcoded when expressions
val entryStrategy = when (entryStrategyName.lowercase()) {
  "planalpha" -> PlanAlphaEntryStrategy()
  "planbeta" -> PlanBetaEntryStrategy()
  // ... many more cases
}
```

**After (Dynamic):**
```kotlin
// Controller uses registry
val entryStrategy = strategyRegistry.createEntryStrategy(entryStrategyName)
if (entryStrategy == null) {
  logger.error("Unknown entry strategy: $entryStrategyName")
  return ResponseEntity(HttpStatus.BAD_REQUEST)
}
```

## Future Enhancements

Potential improvements to the system:

1. **Strategy Metadata**: Add description, parameters, and constraints to the annotation
2. **Strategy Validation**: Validate strategy configurations before backtesting
3. **Strategy Categories**: Group strategies by type (momentum, mean-reversion, etc.)
4. **Dynamic DSL Registration**: Scan for DSL strategy definitions in config files
5. **Strategy Versioning**: Support multiple versions of the same strategy
