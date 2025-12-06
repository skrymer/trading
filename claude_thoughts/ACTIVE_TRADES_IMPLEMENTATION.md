# Active Trades Visual Indicators Implementation

## Overview
Implemented Option 2: Added badges/indicators to existing charts to show which trades are still active (haven't exited yet).

## Implementation Date
2025-11-13

## What Was Implemented

### 1. Active Trade Identification Logic (`backtesting.vue`)

Added computed properties to identify active trades:

```typescript
const activeTrades = computed(() => {
  // A trade is active if:
  // - Last quote equals entry quote (just entered)
  // - Exit reason is "Still Open" or "Active"
  return allTrades.value.filter(trade => {
    const lastQuote = trade.quotes[trade.quotes.length - 1]
    const entryDate = new Date(trade.entryQuote.date)
    const lastQuoteDate = new Date(lastQuote.date)

    return lastQuoteDate.getTime() === entryDate.getTime() ||
           trade.exitReason === 'Still Open' ||
           trade.exitReason === 'Active'
  })
})
```

### 2. Bar Chart Visual Indicators

**Color Coding:**
- 🟢 Green bars = Profitable closed trades
- 🔴 Red bars = Loss closed trades
- 🔵 Blue bars = Active trades (still open)

**Features:**
- Blue emoji indicator (🔵) in x-axis labels for active trades
- Legend at bottom of chart explaining color coding
- Active trades clearly distinguished from closed trades

**Code Changes:**
- Updated `chartColors` to assign blue (#3b82f6) for active trades
- Updated `chartCategoriesWithIndicator` to add 🔵 emoji prefix
- Added footer slot with color legend

### 3. Equity Curve Enhancements

**Visual Treatment:**
- Active trade portion shown as **dashed blue line**
- Closed trades shown as solid green line
- Legend shows "🔵 Active Trades" for the dashed portion

**Implementation:**
- Split equity data into two series: closed and active
- Applied dash pattern (dashArray: 5) to active trades series
- Used blue color (#3b82f6) for active trades line

### 4. Component Updates

**BarChart.client.vue:**
- Added footer slot support for legend display
- Removed custom hover overlay (per previous request)

**LineChart.client.vue:**
- Updated to support dashed lines via dashArray
- Automatic color assignment based on series name
- Updated TypeScript types to accept `(number | null)[]` for data (needed for line breaks)

**EquityCurve.vue:**
- Added `activeTrades` prop
- Split equity series into closed vs active
- Last N points marked as active based on active trades count

### 5. TypeScript Type Updates

**Trade interface:**
```typescript
export interface Trade {
    // ... existing fields
    isActive?: boolean // True if trade hasn't exited yet
}
```

**LineChartSeries interface:**
```typescript
export interface LineChartSeries {
  name: string
  data: (number | null)[] // Updated to support null for line breaks
}
```

## Visual Result

### Bar Chart
```
[Green] [Red] [Green] [🔵Blue] [🔵Blue]
   ↓       ↓      ↓        ↓          ↓
 Closed  Closed Closed   Active    Active

Legend:
🟢 Profit (Closed)  🔴 Loss (Closed)  🔵 Active Trade
```

### Equity Curve
```
Starting ────────────▲────────────▲────────╱╱╱╱ Active
               Closed trades    ╱╱╱╱  (dashed)
                              ╱╱╱╱
```

## How It Works

1. **Identification**: System identifies active trades by checking:
   - If last quote date equals entry date (just entered)
   - If exit reason indicates "Still Open" or "Active"

2. **Bar Chart**:
   - Groups trades by entry date
   - Marks date groups containing active trades with `hasActiveTrades` flag
   - Applies blue color and 🔵 emoji to those bars

3. **Equity Curve**:
   - Passes active trades to EquityCurve component
   - Last N equity points (where N = number of active trades) are split into separate series
   - Active series rendered as dashed blue line

## Benefits

✅ **Non-intrusive**: No new UI sections, uses existing charts
✅ **Clear visual distinction**: Blue color and dashes make active trades obvious
✅ **Contextual**: See active trades in relation to historical performance
✅ **Informative**: Understand portfolio's current state at a glance

## Testing

To test with SPY 2025 data:
```bash
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["SPY"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2025-01-01",
    "endDate": "2025-11-13"
  }'
```

Expected: If backtest end date is today or recent, trades that haven't exited will show as blue bars with 🔵 indicators.

## Future Enhancements

Potential improvements:
1. Add badge count showing number of active trades
2. Click on active bar to see "why still holding" analysis
3. Show current unrealized P&L for active trades
4. Add tooltip showing days held for active trades
5. Real-time updates if connected to live data

## Files Modified

1. `asgaard/app/pages/backtesting.vue`
2. `asgaard/app/components/backtesting/EquityCurve.vue`
3. `asgaard/app/components/charts/BarChart.client.vue`
4. `asgaard/app/components/charts/LineChart.client.vue`
5. `asgaard/app/types/index.d.ts`

## Notes

- Active trade detection relies on backend marking trades appropriately
- If backend doesn't set exit reason to "Still Open", may need to adjust detection logic
- The equity curve splits active trades based on simple count (last N points) - could be enhanced to match by actual trade dates
