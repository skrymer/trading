# Strategy Metadata Endpoint

Added strategy metadata to the `/api/backtest/strategies` endpoint to enable the UI to evaluate entry/exit signals on stock charts.

---

## Changes Made

### 1. Created DTOs

**File:** `StrategyMetadata.kt`

```kotlin
data class StrategyConditionMetadata(
    val type: String,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
)

data class StrategyMetadata(
    val id: String,
    val name: String,
    val description: String,
    val entryConditions: List<StrategyConditionMetadata>,
    val exitConditions: List<StrategyConditionMetadata>,
    val cooldownDays: Int = 0
)

data class StrategiesResponse(
    val entryStrategies: List<String>,
    val exitStrategies: List<String>,
    val metadata: Map<String, StrategyMetadata>
)
```

### 2. Updated Endpoint

**File:** `BacktestController.kt`

Changed `/api/backtest/strategies` to return rich metadata instead of just strategy names.

---

## API Response

### Before
```json
{
  "entryStrategies": ["VegardPlanEtf", "OvtlyrPlanEtf"],
  "exitStrategies": ["VegardPlanEtf", "OvtlyrPlanEtf"]
}
```

### After
```json
{
  "entryStrategies": ["VegardPlanEtf", "OvtlyrPlanEtf"],
  "exitStrategies": ["VegardPlanEtf", "OvtlyrPlanEtf"],
  "metadata": {
    "VegardPlanEtf": {
      "id": "VegardPlanEtf",
      "name": "Vegard Plan ETF",
      "description": "Conservative ETF strategy with value zone entry and multiple exit conditions",
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
      "exitConditions": [
        {
          "type": "emaCross",
          "description": "10 EMA crossed under 20 EMA",
          "parameters": {
            "fastEmaField": "closePriceEMA10",
            "slowEmaField": "closePriceEMA20"
          }
        },
        {
          "type": "profitTarget",
          "description": "Price extended 2.9 ATR above 20 EMA",
          "parameters": {
            "emaField": "closePriceEMA20",
            "atrField": "atr",
            "atrMultiplier": 2.9,
            "priceField": "closePrice"
          }
        },
        {
          "type": "trailingStop",
          "description": "Trailing stop at 3.1 ATR below highest high since entry",
          "parameters": {
            "atrField": "atr",
            "atrMultiplier": 3.1,
            "priceField": "closePrice"
          }
        },
        {
          "type": "priceBelowEma",
          "description": "Price below 10 EMA for 4 consecutive days",
          "parameters": {
            "emaField": "closePriceEMA10",
            "emaPeriod": 10,
            "consecutiveDays": 4,
            "priceField": "closePrice"
          }
        }
      ],
      "cooldownDays": 10
    },
    "OvtlyrPlanEtf": {
      "id": "OvtlyrPlanEtf",
      "name": "Ovtlyr Plan ETF",
      "description": "Active ETF strategy with sell signal exits",
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
      "exitConditions": [
        {
          "type": "sellSignal",
          "description": "Sell signal triggered",
          "parameters": {
            "signalField": "signal"
          }
        },
        {
          "type": "emaCross",
          "description": "10 EMA crossed under 20 EMA",
          "parameters": {
            "fastEmaField": "closePriceEMA10",
            "slowEmaField": "closePriceEMA20"
          }
        },
        {
          "type": "profitTarget",
          "description": "Price extended 3.0 ATR above 20 EMA",
          "parameters": {
            "emaField": "closePriceEMA20",
            "atrField": "atr",
            "atrMultiplier": 3.0,
            "priceField": "closePrice"
          }
        },
        {
          "type": "trailingStop",
          "description": "Trailing stop at 2.7 ATR below highest high since entry",
          "parameters": {
            "atrField": "atr",
            "atrMultiplier": 2.7,
            "priceField": "closePrice"
          }
        }
      ],
      "cooldownDays": 0
    }
  }
}
```

---

## How UI Can Use This

### 1. Fetch Strategy Metadata
```typescript
const response = await fetch('/api/backtest/strategies')
const data = await response.json()
const vegardMetadata = data.metadata.VegardPlanEtf
```

### 2. Evaluate Entry Conditions
```typescript
function evaluateEntryConditions(quote: StockQuote, prevQuote: StockQuote, metadata: StrategyMetadata): boolean {
  return metadata.entryConditions.every(condition => {
    switch (condition.type) {
      case 'uptrend':
        const ema10 = quote[condition.parameters.ema10Field]
        const ema20 = quote[condition.parameters.ema20Field]
        const ema50 = quote[condition.parameters.ema50Field]
        const price = quote[condition.parameters.priceField]
        return ema10 > ema20 && price > ema50

      case 'valueZone':
        const emaValue = quote[condition.parameters.emaField]
        const atr = quote[condition.parameters.atrField]
        const multiplier = condition.parameters.atrMultiplier
        const closePrice = quote[condition.parameters.priceField]
        return closePrice <= emaValue + (atr * multiplier)

      default:
        return false
    }
  })
}
```

### 3. Evaluate Exit Conditions
```typescript
function evaluateExitConditions(
  quote: StockQuote,
  prevQuote: StockQuote,
  metadata: StrategyMetadata,
  context: { highestHigh: number; consecutiveDays: number }
): { triggered: boolean; reason?: string } {

  for (const condition of metadata.exitConditions) {
    let triggered = false

    switch (condition.type) {
      case 'emaCross':
        const fastEma = quote[condition.parameters.fastEmaField]
        const slowEma = quote[condition.parameters.slowEmaField]
        const prevFastEma = prevQuote[condition.parameters.fastEmaField]
        const prevSlowEma = prevQuote[condition.parameters.slowEmaField]
        triggered = prevFastEma >= prevSlowEma && fastEma < slowEma
        break

      case 'profitTarget':
        const ema = quote[condition.parameters.emaField]
        const atr = quote[condition.parameters.atrField]
        const mult = condition.parameters.atrMultiplier
        const price = quote[condition.parameters.priceField]
        triggered = price >= ema + (atr * mult)
        break

      case 'trailingStop':
        const stopAtr = quote[condition.parameters.atrField]
        const stopMult = condition.parameters.atrMultiplier
        const stopPrice = quote[condition.parameters.priceField]
        const stopLevel = context.highestHigh - (stopAtr * stopMult)
        triggered = stopPrice <= stopLevel
        break

      case 'priceBelowEma':
        const days = condition.parameters.consecutiveDays
        triggered = context.consecutiveDays >= days
        break

      case 'sellSignal':
        const signal = quote[condition.parameters.signalField]
        triggered = signal === 'Sell'
        break
    }

    if (triggered) {
      return { triggered: true, reason: condition.description }
    }
  }

  return { triggered: false }
}
```

### 4. Display on Chart
```typescript
// Evaluate strategy on stock data
const { signals } = useStrategySignals(stock, strategyMetadata)

// Chart data with entry/exit markers
const chartData = {
  datasets: [
    // Price line
    priceDataset,

    // Entry markers
    {
      label: 'Entries',
      data: signals.entries.map(e => ({ x: e.date, y: e.price })),
      pointStyle: 'triangle',
      pointBackgroundColor: 'green'
    },

    // Exit markers
    {
      label: 'Exits',
      data: signals.exits.map(e => ({ x: e.date, y: e.price })),
      pointStyle: 'triangle',
      pointRotation: 180,
      pointBackgroundColor: 'red'
    }
  ]
}

// Tooltip with condition details
tooltip: {
  callbacks: {
    label: (context) => {
      const signal = findSignal(context.label)
      if (signal) {
        return [
          `Price: $${signal.price.toFixed(2)}`,
          '',
          'Conditions Met:',
          ...signal.conditions.map(c => `✓ ${c}`)
        ]
      }
    }
  }
}
```

---

## Benefits

1. **Rich Signal Display**
   - Show exactly which conditions were met
   - Display condition parameters (e.g., "2.9 ATR above 20 EMA")
   - Tooltip with full condition breakdown

2. **Client-Side Evaluation**
   - No backend changes needed for new strategies
   - Can evaluate on any stock data
   - Fast and responsive

3. **Educational Value**
   - Users understand strategy logic
   - See exact thresholds and calculations
   - Learn what triggers entries/exits

4. **Flexibility**
   - Can add new condition types easily
   - Parameters are exposed for customization
   - Easy to extend

5. **Consistency**
   - Same logic as backend
   - Parameters match exactly
   - Field names explicit

---

## Next Steps

To add a new strategy to the metadata:

1. Add entry to `getStrategyMetadata()` map
2. Define all entry conditions with types and parameters
3. Define all exit conditions with types and parameters
4. Specify cooldown days
5. Restart backend
6. Frontend automatically picks up new strategy

---

## Example: Adding PlanAlpha Strategy

```kotlin
"PlanAlpha" to StrategyMetadata(
    id = "PlanAlpha",
    name = "Plan Alpha",
    description = "Original Plan strategy",
    entryConditions = listOf(
        StrategyConditionMetadata(
            type = "uptrend",
            description = "Stock is in uptrend",
            parameters = mapOf(
                "ema10Field" to "closePriceEMA10",
                "ema20Field" to "closePriceEMA20",
                "ema50Field" to "closePriceEMA50",
                "priceField" to "closePrice"
            )
        ),
        StrategyConditionMetadata(
            type = "buySignal",
            description = "Buy signal confirmed",
            parameters = mapOf(
                "signalField" to "signal"
            )
        )
    ),
    exitConditions = listOf(
        StrategyConditionMetadata(
            type = "sellSignal",
            description = "Sell signal triggered",
            parameters = mapOf(
                "signalField" to "signal"
            )
        )
    ),
    cooldownDays = 0
)
```

---

## Files Modified

1. **Created:** `StrategyMetadata.kt` - DTOs for strategy metadata
2. **Modified:** `BacktestController.kt` - Updated `/strategies` endpoint with metadata

All code compiled successfully ✓
