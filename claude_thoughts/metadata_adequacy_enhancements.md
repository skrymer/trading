# Strategy Metadata Adequacy Enhancements

Enhanced strategy metadata to ensure all conditions can be properly evaluated client-side using data from the Stock API.

---

## Problem Identified

The initial automatic metadata extraction was missing critical information needed for client-side evaluation:

1. **Cross Detection** - EmaCrossExit requires comparing current vs previous quote to detect crossover
2. **Historical Data** - TrailingStop and PriceBelowEmaForDays need historical quotes
3. **Entry Context** - TrailingStop needs the entry quote to track highest high
4. **Missing Field Mappings** - BuySignal, Heatmap, and OrderBlock conditions had no field mappings

---

## Enhancements Made

### 1. EmaCrossExit - Added Crossover Logic

**File:** `EmaCrossExit.kt`

```kotlin
override fun getMetadata() = ConditionMetadata(
    type = "emaCross",
    description = description(),
    parameters = mapOf(
        "fastEmaField" to "closePriceEMA$fastEma",
        "slowEmaField" to "closePriceEMA$slowEma",
        "requiresPreviousQuote" to true,  // NEW
        "evaluationLogic" to "crossunder"  // NEW - fastEMA crosses under slowEMA
    )
)
```

**Why:** Crossover detection requires comparing two consecutive quotes to determine if the fast EMA was above the slow EMA previously and is now below it.

**Client-Side Logic:**
```typescript
function evaluateEmaCross(currentQuote, previousQuote, params) {
  const fastCurrent = currentQuote[params.fastEmaField]
  const slowCurrent = currentQuote[params.slowEmaField]
  const fastPrevious = previousQuote[params.fastEmaField]
  const slowPrevious = previousQuote[params.slowEmaField]

  // Crossunder: was above, now below
  return fastPrevious >= slowPrevious && fastCurrent < slowCurrent
}
```

### 2. ATRTrailingStopLoss - Added Historical Requirements

**File:** `ATRTrailingStopLoss.kt`

```kotlin
override fun getMetadata() = ConditionMetadata(
    type = "trailingStop",
    description = description(),
    parameters = mapOf(
        "atrField" to "atr",
        "atrMultiplier" to atrMultiplier,
        "priceField" to "closePrice",
        "requiresEntryQuote" to true,  // NEW
        "requiresHistoricalQuotes" to true,  // NEW
        "evaluationLogic" to "price < (highestPriceSinceEntry - (atr * atrMultiplier))"  // NEW
    )
)
```

**Why:** Trailing stop needs to:
1. Know the entry date (to filter quotes since entry)
2. Access all quotes since entry (to find highest price)
3. Calculate: currentPrice < (highestHigh - ATR * multiplier)

**Client-Side Logic:**
```typescript
function evaluateTrailingStop(currentQuote, entryQuote, allQuotes, params) {
  // Filter quotes since entry
  const quotesSinceEntry = allQuotes.filter(q =>
    q.date >= entryQuote.date && q.date <= currentQuote.date
  )

  // Find highest price since entry
  const highestHigh = Math.max(...quotesSinceEntry.map(q => q[params.priceField]))

  // Calculate trailing stop level
  const stopLevel = highestHigh - (currentQuote[params.atrField] * params.atrMultiplier)

  // Exit if price below stop
  return currentQuote[params.priceField] < stopLevel
}
```

### 3. PriceBelowEmaForDaysExit - Added Consecutive Days Logic

**File:** `PriceBelowEmaForDaysExit.kt`

```kotlin
override fun getMetadata() = ConditionMetadata(
    type = "priceBelowEma",
    description = description(),
    parameters = mapOf(
        "emaField" to "closePriceEMA$emaPeriod",
        "emaPeriod" to emaPeriod,
        "consecutiveDays" to consecutiveDays,
        "priceField" to "closePrice",
        "requiresHistoricalQuotes" to true,  // NEW
        "evaluationLogic" to "price < ema for N consecutive days (excluding entry day)"  // NEW
    )
)
```

**Why:** Needs to look back N days to verify price was below EMA consecutively.

**Client-Side Logic:**
```typescript
function evaluatePriceBelowEmaForDays(currentQuote, entryQuote, allQuotes, params) {
  const sortedQuotes = allQuotes.sort((a, b) => a.date - b.date)
  const currentIndex = sortedQuotes.findIndex(q => q.date === currentQuote.date)

  // Check current quote
  if (currentQuote[params.priceField] >= currentQuote[params.emaField]) {
    return false
  }

  // Check previous N-1 days (excluding entry day)
  for (let i = 1; i < params.consecutiveDays; i++) {
    const previousQuote = sortedQuotes[currentIndex - i]

    // Don't count entry day
    if (previousQuote.date === entryQuote.date) {
      return false
    }

    if (previousQuote[params.priceField] >= previousQuote[params.emaField]) {
      return false
    }
  }

  return true
}
```

### 4. BuySignalCondition - Added Signal Field Mapping

**File:** `BuySignalCondition.kt`

```kotlin
override fun getMetadata() = ConditionMetadata(
    type = "buySignal",
    description = description(),
    parameters = mapOf(
        "signalField" to "signal",  // NEW
        "lastBuySignalField" to "lastBuySignal",  // NEW
        "currentOnly" to currentOnly,  // NEW
        "evaluationLogic" to if (currentOnly) {  // NEW
            "signal == 'Buy' OR (lastBuySignal exists AND date - lastBuySignal < 1 day)"
        } else {
            "signal == 'Buy' OR lastBuySignal exists"
        }
    )
)
```

**Why:** BuySignal can be evaluated two ways:
1. Current signal = "Buy"
2. Has recent buy signal (lastBuySignal field)

**Client-Side Logic:**
```typescript
function evaluateBuySignal(quote, params) {
  if (quote[params.signalField] === 'Buy') {
    return true
  }

  if (!params.currentOnly && quote[params.lastBuySignalField]) {
    return true
  }

  if (params.currentOnly && quote[params.lastBuySignalField]) {
    const daysSinceSignal = daysBetween(quote.date, quote[params.lastBuySignalField])
    return daysSinceSignal < 1
  }

  return false
}
```

### 5. HeatmapCondition - Added Heatmap Field Mapping

**File:** `HeatmapCondition.kt`

```kotlin
override fun getMetadata() = ConditionMetadata(
    type = "heatmap",
    description = description(),
    parameters = mapOf(
        "heatmapField" to "heatmap",  // NEW
        "threshold" to threshold,  // NEW
        "operator" to "lessThan",  // NEW
        "evaluationLogic" to "heatmap < threshold"  // NEW
    )
)
```

**Why:** Simple threshold comparison, but field mapping was missing.

**Client-Side Logic:**
```typescript
function evaluateHeatmap(quote, params) {
  return quote[params.heatmapField] < params.threshold
}
```

### 6. BelowOrderBlockCondition - Added Order Block Fields

**File:** `BelowOrderBlockCondition.kt`

```kotlin
override fun getMetadata() = ConditionMetadata(
    type = "belowOrderBlock",
    description = description(),
    parameters = mapOf(
        "priceField" to "closePrice",  // NEW
        "orderBlocksField" to "orderBlocks",  // NEW
        "percentBelow" to percentBelow,  // NEW
        "ageInDays" to ageInDays,  // NEW
        "orderBlockType" to "BEARISH",  // NEW
        "evaluationLogic" to "price <= orderBlock.low * (1 - percentBelow/100) for bearish blocks older than ageInDays"  // NEW
    )
)
```

**Why:** Order blocks are complex - need to filter by type, age, and check if price is below threshold.

**Client-Side Logic:**
```typescript
function evaluateBelowOrderBlock(quote, stock, params) {
  const relevantBlocks = stock[params.orderBlocksField]
    .filter(ob => ob.orderBlockType === params.orderBlockType)
    .filter(ob => daysBetween(ob.startDate, quote.date) >= params.ageInDays)
    .filter(ob => ob.startDate < quote.date)
    .filter(ob => !ob.endDate || ob.endDate > quote.date)
    .filter(ob => ob.low > quote[params.priceField])

  if (relevantBlocks.length === 0) {
    return true  // No blocks = allow entry
  }

  return relevantBlocks.some(ob => {
    const requiredPrice = ob.low * (1 - params.percentBelow / 100)
    return quote[params.priceField] <= requiredPrice
  })
}
```

---

## Stock API Field Verification

All fields referenced in metadata are available in the Stock API response:

### Quote-Level Fields (in each quote)
✅ `closePrice` - Current close price
✅ `closePriceEMA10` - 10-period EMA
✅ `closePriceEMA20` - 20-period EMA
✅ `closePriceEMA50` - 50-period EMA
✅ `atr` - Average True Range
✅ `signal` - Buy/Sell signal
✅ `lastBuySignal` - Date of last buy signal
✅ `heatmap` - Heatmap value (0-100)
✅ `date` - Quote date
✅ `previousQuoteDate` - Previous quote's date

### Stock-Level Fields
✅ `orderBlocks` - Array of order blocks with:
  - `low` - Order block low price
  - `high` - Order block high price
  - `startDate` - When block started
  - `endDate` - When block ended (null if active)
  - `orderBlockType` - BEARISH or BULLISH
  - `source` - OVTLYR, etc.

✅ `quotes` - Full array of historical quotes

---

## Metadata Capabilities Summary

### Entry Conditions

| Condition | Requires | Can Evaluate Client-Side |
|-----------|----------|--------------------------|
| Uptrend | Current quote only | ✅ Yes |
| ValueZone | Current quote only | ✅ Yes |
| BuySignal | Current quote only | ✅ Yes (with new metadata) |
| Heatmap | Current quote only | ✅ Yes (with new metadata) |
| BelowOrderBlock | Current quote + order blocks | ✅ Yes (with new metadata) |

### Exit Conditions

| Condition | Requires | Can Evaluate Client-Side |
|-----------|----------|--------------------------|
| EmaCross | Current + previous quote | ✅ Yes (with requiresPreviousQuote flag) |
| ProfitTarget | Current quote only | ✅ Yes |
| TrailingStop | Entry quote + all quotes since entry | ✅ Yes (with requiresEntryQuote and requiresHistoricalQuotes flags) |
| PriceBelowEmaForDays | Historical quotes | ✅ Yes (with requiresHistoricalQuotes flag) |
| SellSignal | Current quote only | ✅ Yes |

---

## New Metadata Fields

### Evaluation Requirements

**`requiresPreviousQuote`** (boolean)
- Indicates condition needs previous quote for comparison
- Example: EmaCrossExit needs previous EMAs to detect crossover

**`requiresEntryQuote`** (boolean)
- Indicates condition needs entry quote context
- Example: TrailingStop needs entry date to filter quotes

**`requiresHistoricalQuotes`** (boolean)
- Indicates condition needs access to historical quotes
- Example: PriceBelowEmaForDays needs N previous days
- Example: TrailingStop needs all quotes since entry

**`evaluationLogic`** (string)
- Human-readable description of evaluation formula
- Helps UI developers understand condition logic
- Example: "price < (highestPriceSinceEntry - (atr * atrMultiplier))"

### Operator Information

**`operator`** (string)
- Comparison operator used
- Values: "lessThan", "greaterThan", "equals", etc.
- Example: Heatmap uses "lessThan"

---

## Files Modified

1. `EmaCrossExit.kt` - Added crossover detection metadata
2. `ATRTrailingStopLoss.kt` - Added historical requirement metadata
3. `PriceBelowEmaForDaysExit.kt` - Added consecutive days metadata
4. `BuySignalCondition.kt` - Added signal field mappings
5. `HeatmapCondition.kt` - Added heatmap field mappings
6. `BelowOrderBlockCondition.kt` - Added order block field mappings

---

## Testing Required

After server restart:

1. **Verify Enhanced Metadata**
   ```bash
   curl http://localhost:8080/udgaard/api/backtest/strategies | jq '.metadata.VegardPlanEtf.exitConditions[0]'
   ```
   Should show `requiresPreviousQuote: true` for emaCross

2. **Verify Buy Signal Metadata**
   ```bash
   curl http://localhost:8080/udgaard/api/backtest/strategies | jq '.metadata.OvtlyrPlanEtf.entryConditions[] | select(.type == "buySignal")'
   ```
   Should show signalField, lastBuySignalField, and evaluationLogic

3. **Verify Heatmap Metadata**
   ```bash
   curl http://localhost:8080/udgaard/api/backtest/strategies | jq '.metadata.OvtlyrPlanEtf.entryConditions[] | select(.type == "heatmap")'
   ```
   Should show heatmapField, threshold, and operator

4. **Verify Order Block Metadata**
   ```bash
   curl http://localhost:8080/udgaard/api/backtest/strategies | jq '.metadata.OvtlyrPlanEtf.entryConditions[] | select(.type == "belowOrderBlock")'
   ```
   Should show orderBlocksField, percentBelow, ageInDays

---

## Summary

✅ **All conditions can now be evaluated client-side**
✅ **Metadata indicates which quotes are needed** (current, previous, historical, entry)
✅ **All field names mapped to Stock API fields**
✅ **Evaluation logic documented** for UI developers
✅ **Complex conditions explained** (crossover, trailing stop, consecutive days)

The metadata is now **adequate** for the UI to:
1. Know which data to fetch
2. Understand evaluation requirements
3. Implement client-side strategy evaluation
4. Display entry/exit signals on charts
