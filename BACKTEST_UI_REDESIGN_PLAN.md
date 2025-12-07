# Backtest UI Redesign Plan

## Overview

The backtesting page currently displays all results in a single scrolling view, which is becoming cluttered as more diagnostic metrics are added. This plan reorganizes the page into logical tabs and adds visualizations for the newly added diagnostic metrics.

## Current State

### Existing UI Components
- **Summary Cards**: Win rate, edge, total trades (BacktestingCards.vue)
- **Bar Chart**: Trades profit by start date (ChartsBarChart component)
- **Equity Curve**: Cumulative returns over time (BacktestingEquityCurve.vue)
- **Sector Analysis**: Sector performance breakdown (BacktestingSectorAnalysis.vue)
- **Monte Carlo Section**: Simulation controls and results

### New Metrics Available (Not Yet in UI)
From `BacktestReport` model:

1. **Time-Based Stats** (`timeBasedStats: TimeBasedStats`)
   - `byYear: Map<Int, PeriodStats>` - Annual performance
   - `byQuarter: Map<String, PeriodStats>` - Quarterly breakdown
   - `byMonth: Map<String, PeriodStats>` - Monthly breakdown
   - Each PeriodStats includes: trades, winRate, avgProfit, avgHoldingDays, exitReasons

2. **Exit Reason Analysis** (`exitReasonAnalysis: ExitReasonAnalysis`)
   - `byReason: Map<String, ExitStats>` - Stats per exit reason
   - `byYearAndReason: Map<Int, Map<String, Int>>` - Historical breakdown
   - ExitStats includes: count, avgProfit, avgHoldingDays, winRate

3. **ATR Drawdown Stats** (`atrDrawdownStats: ATRDrawdownStats`)
   - Percentiles: p25, p50 (median), p75, p90, p95, p99
   - Mean/median drawdown
   - Distribution with cumulative percentages
   - Min/max drawdown observed
   - Total winning trades analyzed

4. **Market Condition Averages** (`marketConditionAverages: Map<String, Double>`)
   - `avgSpyHeatmap` - Average fear/greed level
   - `avgMarketBreadth` - Average bull percentage
   - `spyUptrendPercent` - % of trades entered during SPY uptrend

5. **Per-Trade Metrics** (already in Trade model, not visualized)
   - `excursionMetrics: ExcursionMetrics` - MFE/MAE for each trade
   - `marketConditionAtEntry: MarketConditionSnapshot` - Market state at entry

---

## Proposed Tab Structure

Use NuxtUI `<UTabs>` component to organize backtest results into coherent sections:

```vue
<UTabs :items="tabItems" variant="link">
  <template #overview>...</template>
  <template #trades>...</template>
  <template #performance>...</template>
  <template #diagnostics>...</template>
  <template #monte-carlo>...</template>
</UTabs>
```

### Tab 1: Overview 📊
**Purpose**: High-level summary and key metrics

**Icon**: `i-lucide-layout-dashboard`

**Contents**:
- Summary cards (existing BacktestingCards)
- Equity curve chart (existing BacktestingEquityCurve)
- Quick stats:
  - Total trades
  - Win rate & edge
  - Total return %
  - Max drawdown
  - CAGR (calculated)
- Strategy summary (entry/exit strategy used)

**Why**: Users want to see the "headline" results immediately

---

### Tab 2: Trades 📈
**Purpose**: Individual trade analysis and chronological view

**Icon**: `i-lucide-trending-up`

**Contents**:
- Bar chart: Trades profit by start date (existing)
- **NEW: Trade table with sorting/filtering**:
  - Columns: Symbol, Entry Date, Exit Date, Profit %, Exit Reason, Holding Days
  - Sortable by profit, date, symbol
  - Filterable by profit (wins/losses), symbol, exit reason
  - Click row to see trade details
- Trade details modal (existing BacktestingTradeDetailsModal)

**New Component Required**: `BacktestingTradeTable.vue`

**Why**: Users need to drill down into individual trade details and patterns

---

### Tab 3: Performance Analysis 📉
**Purpose**: Time-based and sector performance breakdown

**Icon**: `i-lucide-bar-chart-3`

**Contents**:
- **Time-Based Performance** (NEW):
  - Tabs/accordion for Year/Quarter/Month views
  - Table showing: Period, Trades, Win Rate, Avg Profit, Avg Holding Days
  - Visual: Line chart of win rate over time
  - Visual: Bar chart of profit by period

- **Sector Analysis** (existing):
  - Keep existing BacktestingSectorAnalysis component
  - Enhanced with more detailed sector stats

- **Exit Reason Analysis** (NEW):
  - Pie chart: Exit reason distribution
  - Table: Exit reason, Count, Win Rate, Avg Profit, Avg Holding Days
  - Helps identify which exits are most effective

**New Components Required**:
- `BacktestingTimeBasedStats.vue`
- `BacktestingExitReasonAnalysis.vue`

**Why**: Users need to understand performance patterns over time and by sector

---

### Tab 4: Risk Diagnostics 🎯
**Purpose**: Deep dive into risk metrics and trade behavior

**Icon**: `i-lucide-activity`

**Contents**:
- **ATR Drawdown Analysis** (NEW):
  - Histogram: Distribution of ATR drawdowns for winning trades
  - Percentile table: p25, p50, p75, p90, p95, p99
  - Insight box: "X% of winning trades required enduring >Y ATR drawdown"
  - Visual guide for stop loss optimization

- **Market Condition Analysis** (NEW):
  - Cards showing:
    - Average SPY heatmap at entry
    - Average market breadth at entry
    - % of trades entered during SPY uptrend
  - Scatter plot: Trade profit vs market conditions
  - Helps identify if strategy performs better in certain market regimes

- **Excursion Metrics** (NEW):
  - Scatter plot: MFE vs MAE for all trades
  - Identify if exits are too early/late
  - Filter by winning/losing trades

**New Components Required**:
- `BacktestingATRDrawdownStats.vue`
- `BacktestingMarketConditions.vue`
- `BacktestingExcursionAnalysis.vue`

**Why**: Advanced users need deep risk analysis for strategy optimization

---

### Tab 5: Monte Carlo 🎲
**Purpose**: Validate strategy with Monte Carlo simulation

**Icon**: `i-lucide-chart-scatter`

**Contents**:
- Move existing Monte Carlo section here
- Simulation controls (technique selection, run button)
- Results display (existing BacktestingMonteCarloResults)

**Why**: Monte Carlo is a separate workflow, deserves its own tab

---

## Implementation Plan

### Phase 1: Tab Structure & Migration (1-2 hours)
**Goal**: Convert existing page to use tabs

1. **Update TypeScript types** (`types/index.d.ts`):
   - Add TypeScript interfaces for new metrics:
     - `TimeBasedStats`, `PeriodStats`
     - `ExitReasonAnalysis`, `ExitStats`
     - `ATRDrawdownStats`, `DrawdownBucket`
     - `MarketConditionSnapshot`, `ExcursionMetrics`

2. **Refactor `backtesting.vue`**:
   - Import `UTabs` from NuxtUI
   - Define tab items with icons and slots
   - Move existing content into appropriate tab slots:
     - Overview tab: Summary cards + equity curve
     - Trades tab: Bar chart + placeholder for table
     - Performance tab: Sector analysis
     - Monte Carlo tab: Existing Monte Carlo section
   - Create placeholder for Diagnostics tab

3. **Test**: Ensure existing functionality works within tabs

**Acceptance Criteria**:
- ✅ All existing components display correctly in tabs
- ✅ No functionality regression
- ✅ Tab navigation works smoothly

---

### Phase 2: Trade Table (2-3 hours)
**Goal**: Build comprehensive trade table with filtering/sorting

1. **Create `BacktestingTradeTable.vue`**:
   - Use `UTable` component from NuxtUI
   - Columns: Symbol, Entry Date, Exit Date, Profit %, Exit Reason, Holding Days, Sector
   - Sortable columns (profit, date, symbol)
   - Color-coded profit column (green/red)
   - Click row to open trade details modal

2. **Add filtering controls**:
   - Filter by profit: All / Wins / Losses
   - Filter by symbol (searchable dropdown)
   - Filter by exit reason
   - Date range filter

3. **Integrate into Trades tab**:
   - Bar chart at top (existing)
   - Trade table below
   - Ensure modal integration works

**Acceptance Criteria**:
- ✅ Table displays all trades with correct data
- ✅ Sorting works on all sortable columns
- ✅ Filters work correctly
- ✅ Clicking row opens trade details modal

---

### Phase 3: Time-Based Performance (2-3 hours)
**Goal**: Visualize performance over time periods

1. **Create `BacktestingTimeBasedStats.vue`**:
   - Sub-tabs or accordion for Year/Quarter/Month views
   - Table component showing period stats:
     - Columns: Period, Trades, Win Rate %, Avg Profit %, Avg Holding Days
     - Color-coded win rate and profit
   - Line chart: Win rate trend over time (ApexCharts)
   - Bar chart: Profit by period (positive/negative colors)

2. **Data processing**:
   - Transform `timeBasedStats` from report into chart-friendly format
   - Calculate CAGR if needed
   - Handle missing periods gracefully

3. **Integrate into Performance tab**:
   - Add as first section in Performance tab
   - Position above sector analysis

**Acceptance Criteria**:
- ✅ Year/Quarter/Month data displays correctly
- ✅ Charts render with correct data
- ✅ Table shows accurate statistics
- ✅ Period switching works smoothly

---

### Phase 4: Exit Reason Analysis (1-2 hours)
**Goal**: Understand which exits trigger most often and their effectiveness

1. **Create `BacktestingExitReasonAnalysis.vue`**:
   - Pie chart: Exit reason distribution (ApexCharts)
   - Table: Exit reason stats
     - Columns: Exit Reason, Count, %, Win Rate, Avg Profit %, Avg Holding Days
     - Sortable by count, win rate, avg profit
   - Insights section:
     - Highlight most common exit
     - Highlight most profitable exit
     - Highlight least profitable exit (may need improvement)

2. **Data visualization**:
   - Use `exitReasonAnalysis.byReason` from report
   - Color-code win rates (green > 60%, yellow 40-60%, red < 40%)

3. **Integrate into Performance tab**:
   - Add below time-based stats or sector analysis

**Acceptance Criteria**:
- ✅ Pie chart shows correct distribution
- ✅ Table displays accurate exit stats
- ✅ Insights are correct and helpful
- ✅ Sorting works correctly

---

### Phase 5: ATR Drawdown Analysis (2-3 hours)
**Goal**: Help users understand pain tolerance and optimize stops

1. **Create `BacktestingATRDrawdownStats.vue`**:
   - Histogram chart: Distribution of ATR drawdowns
     - X-axis: ATR drawdown buckets (0-0.5, 0.5-1.0, etc.)
     - Y-axis: Number of trades
     - Show cumulative percentage line overlay
   - Percentile cards:
     - Grid of cards showing p25, p50, p75, p90, p95, p99
     - Color-coded by severity (green < 1 ATR, yellow 1-2 ATR, red > 2 ATR)
   - Insight box:
     - "X% of winning trades stayed under Y ATR drawdown"
     - "If using Z ATR stop loss, you would have cut W% of winners short"
     - Recommendations for stop loss placement

2. **Charts**:
   - Use ApexCharts histogram with dual-axis
   - Distribution bars + cumulative line

3. **Integrate into Diagnostics tab**:
   - Add as first section

**Acceptance Criteria**:
- ✅ Histogram displays correct distribution
- ✅ Percentiles are calculated correctly
- ✅ Insights provide actionable information
- ✅ Cumulative percentage line is accurate

---

### Phase 6: Market Conditions Analysis (2-3 hours)
**Goal**: Understand if strategy performs better in certain market conditions

1. **Create `BacktestingMarketConditions.vue`**:
   - Summary cards:
     - Avg SPY Heatmap at Entry
     - Avg Market Breadth at Entry
     - % Trades in SPY Uptrend
   - Scatter plots (two charts):
     - Chart 1: Trade Profit % vs SPY Heatmap at Entry
     - Chart 2: Trade Profit % vs Market Breadth at Entry
     - Color-coded by win/loss
     - Trend line to show correlation
   - Insights:
     - "Strategy performs best when SPY heatmap > X"
     - "Win rate is Y% higher during SPY uptrends"
     - "Avoid entries when market breadth < Z%"

2. **Data processing**:
   - Use `marketConditionAverages` for summary
   - Use per-trade `marketConditionAtEntry` for scatter plots
   - Calculate correlations

3. **Integrate into Diagnostics tab**:
   - Add after ATR drawdown section

**Acceptance Criteria**:
- ✅ Summary cards show correct averages
- ✅ Scatter plots display correctly
- ✅ Trend lines show correlation
- ✅ Insights are data-driven and actionable

---

### Phase 7: Excursion Analysis (1-2 hours)
**Goal**: Identify if exits are premature or trailing stops are needed

1. **Create `BacktestingExcursionAnalysis.vue`**:
   - Scatter plot: MFE (Max Favorable Excursion) vs MAE (Max Adverse Excursion)
     - X-axis: MAE (%)
     - Y-axis: MFE (%)
     - Color-coded by win/loss
     - Quadrant lines at 0,0
     - Shows if winning trades had to endure drawdowns
   - Toggle: Filter by Wins/Losses/All
   - Stats table:
     - Avg MFE for winners
     - Avg MAE for winners
     - % of winners that reached positive territory (mfeReached)
   - Insights:
     - "X% of winners reached Y% profit before final exit"
     - "Winners endured avg Z% drawdown"
     - Suggest if trailing stop might improve results

2. **Use per-trade data**:
   - `excursionMetrics` from each trade
   - Filter and visualize

3. **Integrate into Diagnostics tab**:
   - Add as final section

**Acceptance Criteria**:
- ✅ Scatter plot displays correctly
- ✅ Filtering works (wins/losses/all)
- ✅ Stats are calculated correctly
- ✅ Insights help identify exit optimization opportunities

---

### Phase 8: Polish & Optimization (1-2 hours)
**Goal**: Improve UX, performance, and visual consistency

1. **Visual consistency**:
   - Ensure all charts use consistent color scheme
   - Standardize card layouts
   - Add loading skeletons for all new components
   - Consistent spacing and padding

2. **Performance**:
   - Lazy load tab content (unmount inactive tabs)
   - Optimize chart rendering for large datasets
   - Add virtualization to trade table if needed

3. **Mobile responsiveness**:
   - Test on mobile viewport
   - Stack charts vertically on small screens
   - Ensure tables are scrollable

4. **Documentation**:
   - Add tooltips to explain metrics
   - Info icons with explanations
   - Link to run-backtest skill documentation

**Acceptance Criteria**:
- ✅ Consistent visual design across all tabs
- ✅ Smooth performance with large backtests
- ✅ Mobile-friendly layout
- ✅ Helpful tooltips and documentation

---

## Component Architecture

### New Components to Create

```
asgaard/app/components/backtesting/
├── (existing)
│   ├── Cards.vue                      # Summary cards
│   ├── ConfigModal.vue                # Backtest config
│   ├── EquityCurve.vue                # Equity curve chart
│   ├── SectorAnalysis.vue             # Sector breakdown
│   ├── TradeDetailsModal.vue          # Trade details
│   └── MonteCarloResults.vue          # MC results
│
├── (new - Phase 2)
│   └── TradeTable.vue                 # Sortable/filterable trade table
│
├── (new - Phase 3)
│   └── TimeBasedStats.vue             # Year/Quarter/Month performance
│
├── (new - Phase 4)
│   └── ExitReasonAnalysis.vue         # Exit reason breakdown
│
├── (new - Phase 5)
│   └── ATRDrawdownStats.vue           # ATR drawdown distribution
│
├── (new - Phase 6)
│   └── MarketConditions.vue           # Market condition analysis
│
└── (new - Phase 7)
    └── ExcursionAnalysis.vue          # MFE/MAE scatter plots
```

### Component Props Pattern

All new components should follow this pattern:

```typescript
interface ComponentProps {
  report: BacktestReport | null
  loading: boolean
}
```

This ensures consistency and makes it easy to show loading states.

---

## TypeScript Type Additions

Add to `asgaard/app/types/index.d.ts`:

```typescript
// Time-based performance
export interface TimeBasedStats {
  byYear: Record<number, PeriodStats>
  byQuarter: Record<string, PeriodStats>
  byMonth: Record<string, PeriodStats>
}

export interface PeriodStats {
  trades: number
  winRate: number
  avgProfit: number
  avgHoldingDays: number
  exitReasons: Record<string, number>
}

// Exit reason analysis
export interface ExitReasonAnalysis {
  byReason: Record<string, ExitStats>
  byYearAndReason: Record<number, Record<string, number>>
}

export interface ExitStats {
  count: number
  avgProfit: number
  avgHoldingDays: number
  winRate: number
}

// Sector performance
export interface SectorPerformance {
  sector: string
  trades: number
  winRate: number
  avgProfit: number
  avgHoldingDays: number
}

// ATR drawdown statistics
export interface ATRDrawdownStats {
  medianDrawdown: number
  meanDrawdown: number
  percentile25: number
  percentile50: number
  percentile75: number
  percentile90: number
  percentile95: number
  percentile99: number
  minDrawdown: number
  maxDrawdown: number
  distribution: Record<string, DrawdownBucket>
  totalWinningTrades: number
}

export interface DrawdownBucket {
  range: string
  count: number
  percentage: number
  cumulativePercentage: number
}

// Market conditions
export interface MarketConditionSnapshot {
  spyClose: number
  spyHeatmap: number | null
  spyInUptrend: boolean
  marketBreadthBullPercent: number | null
  entryDate: string
}

// Excursion metrics
export interface ExcursionMetrics {
  maxFavorableExcursion: number
  maxFavorableExcursionATR: number
  maxAdverseExcursion: number
  maxAdverseExcursionATR: number
  mfeReached: boolean
}

// Update BacktestReport interface
export interface BacktestReport {
  // ... existing fields ...

  // New diagnostic fields
  timeBasedStats?: TimeBasedStats
  exitReasonAnalysis?: ExitReasonAnalysis
  sectorPerformance: SectorPerformance[]
  atrDrawdownStats?: ATRDrawdownStats
  marketConditionAverages?: Record<string, number>
}

// Update Trade interface
export interface Trade {
  // ... existing fields ...

  // New per-trade fields
  excursionMetrics?: ExcursionMetrics
  marketConditionAtEntry?: MarketConditionSnapshot
}
```

---

## Chart Library Recommendations

Use **ApexCharts** (already in project) for all visualizations:

- **Line charts**: Time-based performance trends
- **Bar charts**: Period performance, exit reasons
- **Pie/Donut charts**: Exit reason distribution
- **Histogram**: ATR drawdown distribution
- **Scatter plots**: MFE/MAE, market conditions vs profit
- **Mixed charts**: Dual-axis for distribution + cumulative line

ApexCharts provides:
- Responsive design
- Interactive tooltips
- Zoom/pan capabilities
- Consistent styling

---

## Design Considerations

### Color Scheme
- **Profits/Wins**: `#10b981` (green-500)
- **Losses**: `#ef4444` (red-500)
- **Neutral**: `#6b7280` (gray-500)
- **Primary accent**: Use NuxtUI primary color
- **Charts**: Use color-blind friendly palette

### Loading States
- All components should show skeleton loaders
- Use NuxtUI `<USkeleton>` component
- Maintain layout consistency during loading

### Empty States
- Show helpful messages when no data available
- Suggest running a backtest first
- Use NuxtUI icons

### Error States
- Handle missing/null data gracefully
- Show warning if diagnostic metrics not available (old backtest data)
- Provide fallbacks for optional metrics

---

## Testing Strategy

### Manual Testing Checklist
- [ ] Run backtest with small dataset (5 stocks, 1 year)
- [ ] Run backtest with large dataset (100+ stocks, 5 years)
- [ ] Verify all tabs load correctly
- [ ] Test sorting/filtering in trade table
- [ ] Verify charts render with correct data
- [ ] Test on mobile viewport
- [ ] Test with empty results (no trades)
- [ ] Test with partial data (missing diagnostics)

### Edge Cases
- No winning trades
- No losing trades
- Single trade
- Missing ATR data
- Missing market condition data
- Very long time periods
- Very short time periods

---

## Migration Notes

### Breaking Changes
- None - this is additive only
- Old backtest results may not have diagnostic metrics
- Need to handle gracefully (show "N/A" or hide sections)

### Data Compatibility
- New metrics are optional in BacktestReport
- Frontend should check if metrics exist before rendering
- Show message: "Run a new backtest to see enhanced diagnostics"

---

## Future Enhancements (Out of Scope)

1. **Export functionality**: Download backtest results as PDF/CSV
2. **Comparison mode**: Compare two backtests side-by-side
3. **Saved backtests**: Store and retrieve historical backtests
4. **Custom metrics**: Let users define custom performance metrics
5. **Strategy optimizer**: Automated parameter optimization
6. **Real-time updates**: Stream backtest progress updates

---

## Success Metrics

### User Experience
- ✅ Users can navigate backtest results intuitively
- ✅ All key insights are visible without scrolling
- ✅ Diagnostic metrics help users optimize strategies
- ✅ Page loads quickly even with large datasets

### Technical
- ✅ All components follow NuxtUI patterns
- ✅ TypeScript types are complete and accurate
- ✅ No console errors or warnings
- ✅ Mobile responsive design
- ✅ All existing tests pass

---

## Timeline Estimate

| Phase | Task | Time Estimate |
|-------|------|---------------|
| 1 | Tab structure & migration | 1-2 hours |
| 2 | Trade table | 2-3 hours |
| 3 | Time-based performance | 2-3 hours |
| 4 | Exit reason analysis | 1-2 hours |
| 5 | ATR drawdown analysis | 2-3 hours |
| 6 | Market conditions analysis | 2-3 hours |
| 7 | Excursion analysis | 1-2 hours |
| 8 | Polish & optimization | 1-2 hours |
| **TOTAL** | | **12-20 hours** |

---

## Priority Order

If time is limited, implement in this order:

1. **Must Have** (Phase 1, 2, 3): Tab structure, trade table, time-based stats
2. **Should Have** (Phase 4, 5): Exit reason analysis, ATR drawdown
3. **Nice to Have** (Phase 6, 7, 8): Market conditions, excursions, polish

---

## Questions to Resolve

1. Should we persist active tab in URL query params? (e.g., `/backtesting?tab=diagnostics`)
2. Do we need data export functionality in Phase 1?
3. Should trade table be virtualized for performance with 1000+ trades?
4. Do we want to support comparing multiple backtests?
5. Should we add keyboard shortcuts for tab navigation?

---

## Conclusion

This redesign will:
- ✅ Organize cluttered backtest results into logical tabs
- ✅ Add visualizations for all new diagnostic metrics
- ✅ Improve user experience with better navigation
- ✅ Enable advanced strategy optimization with deep analytics
- ✅ Maintain backward compatibility with existing backtests
- ✅ Follow NuxtUI design patterns and best practices

The tab structure provides room for future enhancements while keeping the current experience clean and focused.
