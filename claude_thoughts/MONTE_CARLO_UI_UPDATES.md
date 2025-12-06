# Monte Carlo UI Updates - Technique Selector

## Changes Made

### 1. Added Technique Selector to Backtesting Page

**File**: `asgaard/app/pages/backtesting.vue`

**Changes**:
- Added `selectedTechnique` state variable (defaults to TRADE_SHUFFLING)
- Replaced simple "Run Monte Carlo" button with a UCard containing:
  - Technique selector dropdown (USelectMenu)
  - Description that updates based on selected technique
  - Run Simulation button
- Updated `runMonteCarloSimulation()` to use selected technique

**UI Features**:
- Users can now select between:
  - **Trade Shuffling** (default)
  - **Bootstrap Resampling**
- Dynamic description shows what each technique does
- Clean card-based layout with icon

### 2. Enhanced Results Display

**File**: `asgaard/app/components/backtesting/MonteCarloMetrics.vue`

**Changes**:
- Added contextual information based on technique used
- Trade Shuffling: Shows "Returns are constant (same trades, different order)"
- Bootstrap Resampling: Shows "Returns vary (different trade combinations)"

## How It Works

### Trade Shuffling
```
Selected by default
- Same 26 trades, randomly reordered
- Tests if edge holds regardless of sequence
- Returns: CONSTANT (367.85% in all scenarios)
- Drawdowns: VARY (20% to 44.50%)
- Use case: Validate edge is real, not lucky
```

### Bootstrap Resampling
```
Optional technique
- Samples 26 trades WITH replacement
- Some trades appear multiple times, others not at all
- Returns: VARY (wide distribution)
- Drawdowns: VARY
- Use case: Test robustness to different combinations
```

## User Flow

1. Run a backtest (as before)
2. See "Monte Carlo Simulation" card
3. Select technique from dropdown:
   - "Trade Shuffling" (default)
   - "Bootstrap Resampling"
4. Read description of selected technique
5. Click "Run Simulation"
6. Results show technique name and explanation

## Technical Implementation

### State Management
```typescript
const selectedTechnique = ref<MonteCarloTechnique>(
  MonteCarloTechnique.TRADE_SHUFFLING
)
```

### Technique Selection
```vue
<USelectMenu
  v-model="selectedTechnique"
  :options="[
    { 
      value: MonteCarloTechnique.TRADE_SHUFFLING, 
      label: 'Trade Shuffling' 
    },
    { 
      value: MonteCarloTechnique.BOOTSTRAP_RESAMPLING, 
      label: 'Bootstrap Resampling' 
    }
  ]"
>
  <template #label>
    {{ MonteCarloTechniqueDescriptions[selectedTechnique].name }}
  </template>
</USelectMenu>
```

### API Call
```typescript
const result = await $fetch('/udgaard/api/monte-carlo/simulate', {
  method: 'POST',
  body: {
    trades: allTrades.value,
    technique: selectedTechnique.value,  // Uses selected value
    iterations: 10000,
    includeAllEquityCurves: false
  }
})
```

## Expected Behavior

### Trade Shuffling Results:
- Probability of Profit: 100%
- Mean Return: 367.85%
- All percentiles: 367.85%
- Std Deviation: 0.00%
- Drawdown varies: 20-44.50%

### Bootstrap Resampling Results:
- Probability of Profit: Varies (e.g., 95-100%)
- Mean Return: Higher variance (e.g., 572%)
- Percentiles vary widely
- Std Deviation: High (e.g., 672%)
- Both returns and drawdowns vary

## Benefits

1. **User Education**: Descriptions explain what each technique does
2. **Flexibility**: Users can choose validation method
3. **Complete Analysis**: Can run both techniques for full validation
4. **Clear Results**: Explanatory text helps interpret results

## Future Enhancements

- Add comparison view (run both techniques, show side-by-side)
- Add "Price Path Randomization" when implemented
- Add iteration count selector (currently fixed at 10,000)
- Add export/download results feature
