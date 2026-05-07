# Plan: Chart-marker module deepening (candidate #1)

Status: **Planned** · Branch: `feature-architectur-tidyup` · See [deepening-candidates.md](deepening-candidates.md) for the full list.

## Why

`StockPriceChart.client.vue` exposes its interface as three separate signal-source props (`signals`, `conditionSignals`, `exitConditionSignals`). The implementation builds markers in three near-identical `forEach` blocks, with three separate `watch()` blocks to re-render. The chart "knows about" strategy signals, condition signals, and exit-condition signals — coupling the visualization to the *origin* of each marker rather than the *visual concern* (place a marker on a candle).

Concrete pain:

- **Adding a 4th signal source** (e.g. proximity-aware near-exit markers — already deferred from PR #9) costs: 1 new prop + 1 new `forEach` + 1 new `watch()` in the chart, plus the source wiring. The chart's interface widens with every feature.
- **The `signalDataMap` first-writer-wins behaviour** is implicit in the order of the three `forEach` blocks. Future contributors have no way to predict which payload survives a same-bar collision without reading the file end-to-end.
- **Untestable in isolation.** The mapping logic from `StockConditionSignals → marker` is buried inside the chart's `updateMarkers()` — it can't be unit-tested without mounting Lightweight Charts.

## What deepens

The chart's job is **render N markers on a price series**. The decision *which markers exist* belongs upstream (the page that owns the data sources). Replace the three signal-source props with a single `markers: ChartMarker[]` prop. The page maps each signal source to `ChartMarker[]` via small pure functions; the chart iterates over the merged array.

**Result:**

- Chart interface: 6 props → 4 props. Implementation: 3 `forEach` + 3 watchers → 1 `forEach` + 1 watcher.
- Mapper functions live as pure TypeScript next to the page — testable as plain functions, no chart mount.
- Adding source #4: write a new mapper, append its output to `chartMarkers` computed in the page. **Zero chart changes.**
- Same-bar collision behaviour becomes the page's explicit decision (e.g. `markers: [...entryMarkers, ...exitMarkers]` — declared order = visible precedence). The first-writer-wins rule moves from "comment + reading order" to "array spread order at the call site."

## Locked design

### `ChartMarker` shape

New type in `asgaard/app/types/index.d.ts`:

```ts
export interface ChartMarker {
  time: number                                            // unix-seconds; lightweight-charts time format
  position: 'aboveBar' | 'belowBar' | 'inBar'
  color: string                                           // hex; #2563eb / #22c55e / #ef4444 / #f97316 today
  shape: 'arrowUp' | 'arrowDown' | 'circle' | 'square'
  text?: string                                           // optional bar label (e.g. "1/2", exit reason)
  detail?: ChartMarkerDetail                              // optional click-through payload
}

export interface ChartMarkerDetail {
  date: string
  price: number
  entryDetails: {                                         // shape kept identical to existing modal-consumer signalDataMap value
    strategyName: string
    strategyDescription: string
    conditions: ConditionEvaluationResult[]
    allConditionsMet: boolean
  }
}
```

Naming note: keeping `entryDetails` as the field name even for exit-condition markers because that's the existing shape `ChartsSignalDetailsModal` consumes. Renaming the modal contract is out of scope here.

### Mapper functions

New file `asgaard/app/utils/chart-markers.ts`:

```ts
export function strategySignalsToMarkers(signals: StockWithSignals | null | undefined): ChartMarker[]
export function conditionSignalsToMarkers(signals: StockConditionSignals | null | undefined): ChartMarker[]
export function exitConditionSignalsToMarkers(signals: StockExitConditionSignals | null | undefined): ChartMarker[]
```

Pure functions. Each handles its source's null/undefined case and returns `[]`. Direct copies of the existing in-chart logic — no behaviour change, just relocation.

### Chart prop change

```ts
// before
defineProps<{
  quotes: StockQuote[]
  orderBlocks: OrderBlock[]
  symbol: string
  signals?: any
  entryStrategy?: string
  conditionSignals?: StockConditionSignals | null
  exitConditionSignals?: StockExitConditionSignals | null
}>()

// after
defineProps<{
  quotes: StockQuote[]
  orderBlocks: OrderBlock[]
  symbol: string
  entryStrategy?: string                  // kept — used elsewhere for chart label/title
  markers?: ChartMarker[]
}>()
```

`updateMarkers()` collapses to:

```ts
function updateMarkers() {
  if (!seriesMarkersPlugin) return
  signalDataMap.clear()
  for (const m of props.markers ?? []) {
    if (m.detail) signalDataMap.set(m.time, m.detail)
  }
  seriesMarkersPlugin.setMarkers(props.markers ?? [])
}
```

Three `watch()` blocks → one `watch(() => props.markers, updateMarkers, { deep: true })`.

### Page wiring (`stock-data/[[symbol]].vue`)

Replace the three `:condition-signals="..."` style props with a single computed:

```ts
const chartMarkers = computed<ChartMarker[]>(() => [
  ...strategySignalsToMarkers(signalsData.value),
  ...conditionSignalsToMarkers(conditionSignalsData.value),
  ...exitConditionSignalsToMarkers(exitConditionSignalsData.value),
])
```

Same-bar collision precedence becomes the order in this array — entry-condition wins over exit-condition (matches today's implicit behaviour). If we want a different precedence later, reorder the spread.

## Critical files

- `asgaard/app/components/StockPriceChart.client.vue` — drop 3 props, add `markers` prop, collapse `updateMarkers()` + watchers
- `asgaard/app/utils/chart-markers.ts` — **new**, pure mapper functions
- `asgaard/app/types/index.d.ts` — add `ChartMarker` + `ChartMarkerDetail` interfaces
- `asgaard/app/pages/stock-data/[[symbol]].vue` — replace 3 prop bindings with one `:markers="chartMarkers"`; add the `chartMarkers` computed

No backend changes. No new tests (per the project's no-UI-tests-for-now convention; the mapper functions are pure but follow the existing pattern of in-place verification).

## Out of scope

- Adding the deferred proximity-warning markers — that's a follow-up that becomes a 4th mapper, no chart changes
- Renaming `ChartMarkerDetail.entryDetails` — keep the existing modal contract
- Tightening `signals?: any` to `StockWithSignals` — orthogonal type-cleanup, separate PR
- Migrating `signalDataMap` away from `Map<time, detail>` — current shape works for the modal

## Verification

1. `pnpm typecheck` + `pnpm lint` clean
2. Manual smoke (the only meaningful test for this change):
   - Visit `/stock-data/AAPL`
   - **Strategy signals**: pick an entry + exit strategy, click "Show Signals" → blue ▲ entry markers below bar + orange ▼ exit markers above bar render correctly; clicking a marker opens the details modal with the right payload
   - **Entry-condition signals**: click "Show Conditions" → green ○ markers below bar with `N/M` count; click-through detail still works
   - **Exit-condition signals**: click "Show Exit Conditions", pick an entry date, evaluate → red ▼ markers above bar with `N/M` count; click-through detail still works
   - **All three layers active simultaneously**: all three marker types coexist on the chart; same-bar collisions show the entry-condition modal (explicit precedence in `chartMarkers` spread order)
   - **Clear any signal source**: that layer's markers disappear, the others stay
   - **Switch symbol**: chart re-renders for new symbol, no stale markers leak across (already handled by the `clearAllSignals()` helper from PR #9)
