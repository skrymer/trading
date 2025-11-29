# Stock with Strategy Signals Endpoint

Created a new endpoint that returns stock data with entry/exit signals pre-calculated by the backend, eliminating the need for the UI to duplicate strategy logic.

---

## Problem

The initial approach required the UI to:
1. Fetch strategy metadata
2. Fetch stock data
3. Implement strategy evaluation logic in JavaScript
4. Calculate entry/exit signals client-side

This meant **duplicating the strategy logic** in two places (Kotlin backend + TypeScript frontend), which is:
- Error-prone (logic can diverge)
- Harder to maintain (two codebases to update)
- More complex for UI developers

---

## Solution

Create a new endpoint that evaluates the strategy on the backend and returns stock data with entry/exit signals already calculated.

### New Endpoint

```
GET /api/stocks/{symbol}/signals?strategy={strategyName}&refresh={boolean}
```

**Parameters:**
- `symbol` - Stock symbol (e.g., TQQQ, SPY)
- `strategy` - Strategy name (e.g., VegardPlanEtf, OvtlyrPlanEtf)
- `refresh` - Force refresh stock data from external source (default: false)

**Example Request:**
```bash
GET /udgaard/api/stocks/TQQQ/signals?strategy=VegardPlanEtf
```

**Response:**
```json
{
  "stock": {
    "symbol": "TQQQ",
    "sectorSymbol": "spy",
    "quotes": [...],
    "orderBlocks": [...]
  },
  "strategyName": "VegardPlanEtf",
  "quotesWithSignals": [
    {
      "quote": {
        "symbol": "TQQQ",
        "date": "2020-01-02",
        "closePrice": 10.87,
        "closePriceEMA10": 10.30,
        "closePriceEMA20": 9.95,
        "closePriceEMA50": 9.26,
        "atr": 0.25,
        ...
      },
      "entrySignal": false,
      "exitSignal": false,
      "exitReason": null
    },
    {
      "quote": {
        "date": "2020-01-15",
        "closePrice": 11.25,
        ...
      },
      "entrySignal": true,  // Entry signal detected!
      "exitSignal": false,
      "exitReason": null
    },
    {
      "quote": {
        "date": "2020-02-10",
        "closePrice": 12.50,
        ...
      },
      "entrySignal": false,
      "exitSignal": true,  // Exit signal detected!
      "exitReason": "Price extended 2.9 ATR above 20 EMA"
    }
  ]
}
```

---

## Implementation

### 1. DTOs Created

**File:** `StockWithSignals.kt`

```kotlin
data class QuoteWithSignal(
    val quote: StockQuote,
    val entrySignal: Boolean = false,
    val exitSignal: Boolean = false,
    val exitReason: String? = null
)

data class StockWithSignals(
    val stock: Stock,
    val strategyName: String,
    val quotesWithSignals: List<QuoteWithSignal>
)
```

### 2. Service Created

**File:** `StrategySignalService.kt`

Evaluates a strategy on stock data:
- Takes stock data and strategy name
- Simulates trading through all quotes chronologically
- Tracks position state (in/out of position)
- Marks entry signals when condition met and not in position
- Marks exit signals when condition met and in position
- Captures exit reason for each exit

**Key Method:**
```kotlin
fun evaluateStrategy(stock: Stock, strategyName: String): StockWithSignals?
```

### 3. Controller Endpoint

**File:** `StockController.kt`

Added new endpoint:
```kotlin
@GetMapping("/{symbol}/signals")
fun getStockWithSignals(
    @PathVariable symbol: String,
    @RequestParam strategy: String,
    @RequestParam(defaultValue = "false") refresh: Boolean
): ResponseEntity<StockWithSignals>
```

---

## Benefits

### 1. Single Source of Truth
- Strategy logic lives **only** in Kotlin
- No duplication in TypeScript
- Zero risk of logic divergence

### 2. Simpler UI Implementation
```typescript
// Before: UI had to implement strategy logic
const signals = evaluateStrategy(stock, metadata)  // Complex!

// After: Backend provides signals
const response = await fetch('/udgaard/api/stocks/TQQQ/signals?strategy=VegardPlanEtf')
const { quotesWithSignals } = await response.json()  // Simple!
```

### 3. Easier Maintenance
- Add new strategy → Works immediately in UI
- Change strategy logic → UI automatically updated
- No TypeScript code to maintain

### 4. Consistency
- Backend calculates signals the **exact same way** as backtests
- No "it works differently in the UI" bugs

### 5. Performance
- Complex calculations done on backend (faster JVM)
- UI just renders the results

---

## UI Usage

### Fetch Stock with Signals
```typescript
async function getStockWithSignals(symbol: string, strategy: string) {
  const response = await fetch(
    `/udgaard/api/stocks/${symbol}/signals?strategy=${strategy}`
  )
  return response.json()
}
```

### Display on Chart
```typescript
const { stock, quotesWithSignals } = await getStockWithSignals('TQQQ', 'VegardPlanEtf')

// Chart data
const chartData = {
  labels: quotesWithSignals.map(q => q.quote.date),
  datasets: [
    // Price line
    {
      label: 'Close Price',
      data: quotesWithSignals.map(q => q.quote.closePrice),
      borderColor: 'blue'
    },
    // Entry markers
    {
      label: 'Entry Signals',
      data: quotesWithSignals
        .filter(q => q.entrySignal)
        .map(q => ({ x: q.quote.date, y: q.quote.closePrice })),
      pointStyle: 'triangle',
      pointBackgroundColor: 'green',
      pointRadius: 8,
      showLine: false
    },
    // Exit markers
    {
      label: 'Exit Signals',
      data: quotesWithSignals
        .filter(q => q.exitSignal)
        .map(q => ({ x: q.quote.date, y: q.quote.closePrice })),
      pointStyle: 'triangle',
      pointRotation: 180,
      pointBackgroundColor: 'red',
      pointRadius: 8,
      showLine: false
    }
  ]
}
```

### Tooltip with Exit Reasons
```typescript
tooltip: {
  callbacks: {
    afterLabel: (context) => {
      const quoteWithSignal = quotesWithSignals[context.dataIndex]

      if (quoteWithSignal.entrySignal) {
        return '🟢 Entry Signal'
      }

      if (quoteWithSignal.exitSignal) {
        return `🔴 Exit: ${quoteWithSignal.exitReason}`
      }

      return ''
    }
  }
}
```

---

## Example Output

```bash
curl "http://localhost:8080/udgaard/api/stocks/TQQQ/signals?strategy=VegardPlanEtf"
```

Returns:
- Stock symbol and metadata
- Strategy name used
- All quotes with signals:
  - `entrySignal: true` when entry conditions met
  - `exitSignal: true` when exit conditions met
  - `exitReason` explaining why (e.g., "10 EMA crossed under 20 EMA", "Price extended 2.9 ATR above 20 EMA")

---

## Files Created/Modified

### Created:
- `StockWithSignals.kt` - DTOs for response
- `StrategySignalService.kt` - Service to evaluate strategies

### Modified:
- `StockController.kt` - Added new `/signals` endpoint

---

## Testing

After server restart:

```bash
# Test with VegardPlanEtf strategy
curl "http://localhost:8080/udgaard/api/stocks/TQQQ/signals?strategy=VegardPlanEtf" | jq '.quotesWithSignals[] | select(.entrySignal == true or .exitSignal == true)'

# Should show entry/exit signals with reasons
```

---

## Strategy Metadata Still Useful

The strategy metadata endpoint is still valuable for:
- Showing strategy details to users
- Displaying what conditions a strategy uses
- Strategy comparison/selection UI

But now the UI doesn't need to **implement** the strategy logic, just **display** the pre-calculated signals.

---

## Summary

✅ **Backend calculates signals** - Single source of truth
✅ **UI just renders** - No strategy logic duplication
✅ **Same logic as backtests** - Perfect consistency
✅ **Exit reasons provided** - Better UX
✅ **Simple UI code** - Just fetch and display

The UI is now much simpler and more maintainable!
