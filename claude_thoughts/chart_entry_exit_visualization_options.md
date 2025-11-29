# Stock Chart Entry/Exit Visualization Options

A comprehensive guide for displaying trading signals on stock charts.

---

## 1. Markers/Shapes (Most Common)

### Option A: Arrows
```typescript
{
  type: 'scatter',
  data: [
    { x: '2024-01-15', y: 450.50, type: 'entry' },
    { x: '2024-02-20', y: 480.25, type: 'exit' }
  ],
  pointStyle: (ctx) => ctx.raw.type === 'entry' ? 'triangle' : 'triangleDown',
  pointBackgroundColor: (ctx) => ctx.raw.type === 'entry' ? '#10b981' : '#ef4444',
  pointRadius: 10,
  rotation: (ctx) => ctx.raw.type === 'entry' ? 0 : 180
}
```

**Visual:**
```
Price
  ↑
  │     ▼ (red, sell at $480.25)
  │    /
  │   /
  │  /
  │ ▲ (green, buy at $450.50)
  └─────────────────→ Time
```

**Pros:**
- ✓ Universal trading symbol
- ✓ Instantly recognizable
- ✓ Clear direction (up = buy, down = sell)

**Cons:**
- ✗ Can overlap with price action
- ✗ May clutter busy charts

---

## 2. Vertical Lines with Shaded Zones

### Option B: Trade Duration Highlighting
```typescript
{
  annotations: {
    box1: {
      type: 'box',
      xMin: '2024-01-15',  // Entry date
      xMax: '2024-02-20',  // Exit date
      backgroundColor: 'rgba(16, 185, 129, 0.1)',  // Green for profit
      borderColor: '#10b981',
      borderWidth: 2,
      label: {
        content: '+6.6%',
        display: true,
        position: 'top'
      }
    }
  }
}
```

**Visual:**
```
Price
  ↑
  │  ┌─────────────┐
  │  │  Profit     │ +6.6%
  │  │  Zone       │
  │  │             │
  │  └─────────────┘
  │ Entry        Exit
  └─────────────────→ Time
```

**Pros:**
- ✓ Shows trade duration clearly
- ✓ Color indicates profit/loss
- ✓ Non-intrusive background

**Cons:**
- ✗ Multiple overlapping trades can be confusing
- ✗ Can obscure price details

---

## 3. Annotations with Details

### Option C: Information-Rich Callouts
```typescript
{
  annotations: {
    entry1: {
      type: 'label',
      xValue: '2024-01-15',
      yValue: 450.50,
      backgroundColor: '#10b981',
      content: ['ENTRY', '$450.50', 'Uptrend + Buy Signal', 'Heatmap: 75'],
      position: 'bottom',
      callout: {
        display: true,
        position: 'bottom'
      }
    },
    exit1: {
      type: 'label',
      xValue: '2024-02-20',
      yValue: 480.25,
      backgroundColor: '#ef4444',
      content: ['EXIT', '$480.25', 'Profit Target (3.0 ATR)', '+6.6%'],
      position: 'top',
      callout: {
        display: true,
        position: 'top'
      }
    }
  }
}
```

**Visual:**
```
        ┌─────────────────────┐
        │ EXIT                │
        │ $480.25             │
        │ Profit Target       │
        │ +6.6%               │
        └──────────┬──────────┘
                   │
                   ▼
Price             ●
  ↑              /
  │             /
  │            /
  │           ●
  │          ▲
  │  ┌───────┴─────────────┐
  │  │ ENTRY               │
  │  │ $450.50             │
  │  │ Uptrend + Buy       │
  │  │ Heatmap: 75         │
  │  └─────────────────────┘
  └─────────────────────────→ Time
```

**Pros:**
- ✓ Maximum information density
- ✓ Shows reasoning for trade
- ✓ Educational value

**Cons:**
- ✗ Can be cluttered
- ✗ Requires more screen space
- ✗ Best with toggle on/off

---

## 4. Badges/Labels

### Option D: Minimalist Letter Markers
```typescript
{
  type: 'scatter',
  data: entryExitPoints,
  pointStyle: 'rectRounded',
  pointRadius: 12,
  pointBackgroundColor: (ctx) => ctx.raw.type === 'entry' ? '#10b981' : '#ef4444',
  labels: (ctx) => ctx.raw.type === 'entry' ? 'B' : 'S',
  datalabels: {
    color: 'white',
    font: { weight: 'bold', size: 14 },
    formatter: (value) => value.type === 'entry' ? 'B' : 'S'
  }
}
```

**Visual:**
```
Price
  ↑
  │     [S] (red badge)
  │    /
  │   /
  │  /
  │ [B] (green badge)
  └─────────────────→ Time
```

**Pros:**
- ✓ Very clean appearance
- ✓ Minimal screen space
- ✓ Works on mobile

**Cons:**
- ✗ Less information
- ✗ Requires legend explanation

---

## 5. Trade Lines (Connecting Entry to Exit)

### Option E: Trajectory Lines
```typescript
{
  annotations: {
    tradeLine1: {
      type: 'line',
      xMin: '2024-01-15',
      yMin: 450.50,
      xMax: '2024-02-20',
      yMax: 480.25,
      borderColor: '#10b981',
      borderWidth: 3,
      borderDash: [5, 5],
      label: {
        content: '+6.6% (36 days)',
        display: true,
        position: 'center'
      }
    }
  }
}
```

**Visual:**
```
Price
  ↑
  │        ● Exit
  │       ╱
  │      ╱ +6.6%
  │     ╱  (36 days)
  │    ╱
  │   ● Entry
  └─────────────────→ Time
```

**Pros:**
- ✓ Shows trade path clearly
- ✓ Visual profit/loss slope
- ✓ Shows holding period

**Cons:**
- ✗ Can cross other trades
- ✗ Messy with many trades

---

## 6. Price Bands (Entry/Exit Zones)

### Option F: ATR-Based Zones
```typescript
{
  annotations: {
    entryZone: {
      type: 'box',
      xMin: '2024-01-14',
      xMax: '2024-01-16',
      yMin: 445.00,  // Entry - ATR
      yMax: 455.00,  // Entry + ATR
      backgroundColor: 'rgba(16, 185, 129, 0.2)',
      borderColor: '#10b981',
      label: {
        content: 'Entry Zone',
        display: true
      }
    },
    exitTarget: {
      type: 'box',
      xMin: '2024-02-19',
      xMax: '2024-02-21',
      yMin: 475.00,
      yMax: 485.00,
      backgroundColor: 'rgba(251, 191, 36, 0.2)',
      borderColor: '#f59e0b',
      label: {
        content: 'Profit Target',
        display: true
      }
    },
    stopLoss: {
      type: 'line',
      yMin: 435.00,
      yMax: 435.00,
      borderColor: '#ef4444',
      borderWidth: 2,
      borderDash: [10, 5],
      label: {
        content: 'Stop Loss',
        display: true
      }
    }
  }
}
```

**Visual:**
```
Price
  ↑
  │  ┌──────────┐
  │  │ Profit   │ Target zone (yellow)
  │  │ Target   │
  │  └──────────┘
  │      /
  │     /
  │    /
  │  ┌──────────┐
  │  │ Entry    │ Entry zone (green)
  │  │ Zone     │
  │  └──────────┘
  │
  │  ━━━━━━━━━━━  Stop Loss (red dashed)
  └─────────────────→ Time
```

**Pros:**
- ✓ Shows risk/reward visually
- ✓ Displays ATR-based levels
- ✓ Good for strategy understanding

**Cons:**
- ✗ More complex
- ✗ Can be cluttered

---

## 7. Icon-Based System

### Option G: Contextual Icons
```typescript
const icons = {
  uptrend: '📈',
  profitTarget: '🎯',
  stopLoss: '⚠️',
  sellSignal: '📉',
  timeBased: '⏱️'
}

{
  annotations: {
    entry1: {
      type: 'point',
      xValue: '2024-01-15',
      yValue: 450.50,
      pointStyle: icons.uptrend,
      backgroundColor: '#10b981',
      borderColor: '#059669',
      borderWidth: 2,
      radius: 15
    },
    exit1: {
      type: 'point',
      xValue: '2024-02-20',
      yValue: 480.25,
      pointStyle: icons.profitTarget,
      backgroundColor: '#f59e0b',
      borderColor: '#d97706',
      borderWidth: 2,
      radius: 15
    }
  }
}
```

**Visual:**
```
Price
  ↑
  │     🎯 (profit target exit)
  │    /
  │   /
  │  /
  │ 📈 (uptrend entry)
  │
  │ Other exit types:
  │ ⚠️  Stop loss triggered
  │ 📉 Sell signal
  │ ⏱️  Time-based exit
  └─────────────────→ Time
```

**Pros:**
- ✓ Visual and intuitive
- ✓ Different reasons = different icons
- ✓ Friendly user experience

**Cons:**
- ✗ May look unprofessional
- ✗ Font support issues
- ✗ Accessibility concerns

---

## 8. Interactive Tooltips

### Option H: Hover-Activated Details
```typescript
{
  plugins: {
    tooltip: {
      enabled: true,
      callbacks: {
        title: (tooltipItems) => {
          const point = tooltipItems[0].raw
          return point.type === 'entry' ? 'ENTRY SIGNAL' : 'EXIT SIGNAL'
        },
        label: (context) => {
          const point = context.raw
          return [
            `Price: $${point.y.toFixed(2)}`,
            `Date: ${point.x}`,
            `Reason: ${point.reason}`,
            point.profit ? `Profit: ${point.profit}%` : ''
          ]
        },
        afterLabel: (context) => {
          const point = context.raw
          if (point.type === 'entry') {
            return [
              '',
              'Entry Conditions:',
              '✓ Stock in uptrend',
              '✓ Buy signal confirmed',
              `✓ Heatmap: ${point.heatmap}/100`,
              `✓ Above 10 EMA ($${point.ema10})`,
              `✓ In value zone (${point.atrDistance} ATR)`
            ]
          }
          return []
        }
      }
    }
  }
}
```

**Visual:**
```
Hover over marker:

┌─────────────────────────────┐
│ EXIT SIGNAL                 │
├─────────────────────────────┤
│ Price: $480.25              │
│ Date: 2024-02-20            │
│ Reason: Profit Target       │
│ Profit: +6.6%               │
│                             │
│ Exit Conditions:            │
│ ✓ Price 3.0 ATR above 20 EMA│
│ ✓ Gain target reached       │
│ ATR: $12.50                 │
│ Days held: 36               │
└─────────────────────────────┘
```

**Pros:**
- ✓ Clean chart by default
- ✓ Rich details on demand
- ✓ Best of both worlds

**Cons:**
- ✗ Requires user interaction
- ✗ Not visible in screenshots

---

## 9. Colored Candlesticks

### Option I: Highlight Active Positions
```typescript
{
  datasets: [
    {
      type: 'candlestick',
      data: priceData.map(candle => ({
        ...candle,
        borderColor: isInActiveTrade(candle.x) ? '#10b981' : '#64748b',
        borderWidth: isInActiveTrade(candle.x) ? 3 : 1,
        backgroundColor: getTradeColor(candle.x)
      }))
    }
  ]
}

function isInActiveTrade(date) {
  return trades.some(t => date >= t.entry && date <= t.exit)
}

function getTradeColor(date) {
  const trade = trades.find(t => date >= t.entry && date <= t.exit)
  if (!trade) return 'rgba(0,0,0,0.1)'
  return trade.profit > 0
    ? 'rgba(16, 185, 129, 0.2)'  // Green for winning
    : 'rgba(239, 68, 68, 0.2)'   // Red for losing
}
```

**Visual:**
```
Price
  ↑
  │  ║ ║ ║  (thicker green borders during trade)
  │  ║ ║ ║
  │  ║ ║ ║
  │  │ │ │  (normal gray before/after)
  └─────────────────→ Time
```

**Pros:**
- ✓ Subtle and professional
- ✓ Shows trade period clearly
- ✓ Works with candles

**Cons:**
- ✗ Can be too subtle
- ✗ Doesn't show exact entry/exit points

---

## 10. Multi-Layer Toggle System

### Option J: Comprehensive Layered Display
```vue
<template>
  <div class="chart-container">
    <!-- Main chart -->
    <Line :data="chartData" :options="chartOptions" />

    <!-- Layer controls -->
    <div class="mt-4 flex flex-wrap gap-2">
      <UButton
        :color="layers.zones ? 'primary' : 'gray'"
        @click="layers.zones = !layers.zones"
        size="sm"
      >
        Trade Zones
      </UButton>
      <UButton
        :color="layers.markers ? 'primary' : 'gray'"
        @click="layers.markers = !layers.markers"
        size="sm"
      >
        Entry/Exit Markers
      </UButton>
      <UButton
        :color="layers.lines ? 'primary' : 'gray'"
        @click="layers.lines = !layers.lines"
        size="sm"
      >
        Trade Lines
      </UButton>
      <UButton
        :color="layers.labels ? 'primary' : 'gray'"
        @click="layers.labels = !layers.labels"
        size="sm"
      >
        Profit Labels
      </UButton>
      <UButton
        :color="layers.annotations ? 'primary' : 'gray'"
        @click="layers.annotations = !layers.annotations"
        size="sm"
      >
        Detailed Info
      </UButton>
    </div>
  </div>
</template>

<script setup lang="ts">
const layers = reactive({
  zones: true,      // Background shading
  markers: true,    // Entry/exit arrows
  lines: false,     // Connecting lines
  labels: true,     // Profit percentages
  annotations: false // Detailed callouts
})

const chartOptions = computed(() => ({
  plugins: {
    annotation: {
      annotations: {
        // Layer 1: Background zones
        ...(layers.zones ? tradeZones.value : {}),

        // Layer 2: Trade lines
        ...(layers.lines ? tradeLines.value : {}),

        // Layer 3: Detailed annotations
        ...(layers.annotations ? detailedAnnotations.value : {})
      }
    }
  }
}))

const chartData = computed(() => ({
  datasets: [
    // Price data
    priceDataset.value,

    // Layer: Entry/exit markers
    ...(layers.markers ? [entryMarkers.value, exitMarkers.value] : []),

    // Layer: Profit labels
    ...(layers.labels ? [profitLabels.value] : [])
  ]
}))
</script>
```

**Pros:**
- ✓ User controls complexity
- ✓ Flexible for different use cases
- ✓ Can combine best approaches

**Cons:**
- ✗ More complex implementation
- ✗ Requires UI space for controls

---

## Recommended Combinations

### For Clean Trading Dashboard
```typescript
// Default layers
{
  markers: true,        // Simple arrows (B/S badges)
  tooltips: true,       // Hover for details
  profitLabels: false,  // Toggle on demand
  zones: false          // Toggle on demand
}
```

### For Educational/Analysis View
```typescript
// Detailed layers
{
  zones: true,          // Show trade duration
  markers: true,        // Entry/exit arrows
  annotations: true,    // Show conditions
  tooltips: true,       // Full details on hover
  profitLabels: true    // Show performance
}
```

### For Mobile/Small Screens
```typescript
// Minimal layers
{
  markers: true,        // Simple badges only
  tooltips: true,       // Tap for details
  profitLabels: false,  // Too cluttered
  zones: false          // Save space
}
```

### For Strategy Development
```typescript
// Full diagnostic layers
{
  zones: true,          // Trade periods
  markers: true,        // Entry/exit points
  lines: true,          // Trade paths
  bands: true,          // ATR zones
  annotations: true,    // Full conditions
  tooltips: true        // Everything on hover
}
```

---

## Implementation Example (Nuxt UI + Chart.js)

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">{{ symbol }} Strategy Performance</h3>
        <UButtonGroup size="sm">
          <UButton
            :color="displayMode === 'clean' ? 'primary' : 'gray'"
            @click="displayMode = 'clean'"
          >
            Clean
          </UButton>
          <UButton
            :color="displayMode === 'detailed' ? 'primary' : 'gray'"
            @click="displayMode = 'detailed'"
          >
            Detailed
          </UButton>
        </UButtonGroup>
      </div>
    </template>

    <!-- Chart -->
    <Line
      :data="chartData"
      :options="chartOptions"
      class="h-96"
    />

    <!-- Custom layer toggles (shown in detailed mode) -->
    <div v-if="displayMode === 'detailed'" class="mt-4 space-y-2">
      <div class="flex flex-wrap gap-2">
        <UBadge
          :color="layers.zones ? 'green' : 'gray'"
          class="cursor-pointer"
          @click="layers.zones = !layers.zones"
        >
          Trade Zones {{ layers.zones ? '✓' : '' }}
        </UBadge>
        <UBadge
          :color="layers.lines ? 'green' : 'gray'"
          class="cursor-pointer"
          @click="layers.lines = !layers.lines"
        >
          Trade Lines {{ layers.lines ? '✓' : '' }}
        </UBadge>
        <UBadge
          :color="layers.annotations ? 'green' : 'gray'"
          class="cursor-pointer"
          @click="layers.annotations = !layers.annotations"
        >
          Annotations {{ layers.annotations ? '✓' : '' }}
        </UBadge>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between text-sm text-gray-600">
        <span>{{ trades.length }} trades shown</span>
        <span>Avg profit: {{ averageProfit }}%</span>
      </div>
    </template>
  </UCard>
</template>

<script setup lang="ts">
import { Line } from 'vue-chartjs'
import { computed, reactive, ref } from 'vue'

const props = defineProps<{
  symbol: string
  priceData: Array<{ x: string; y: number }>
  trades: Array<{
    entryDate: string
    exitDate: string
    entryPrice: number
    exitPrice: number
    profit: number
    reason: string
  }>
}>()

const displayMode = ref<'clean' | 'detailed'>('clean')

const layers = reactive({
  zones: true,
  markers: true,
  lines: false,
  annotations: false,
  labels: true
})

// Auto-configure layers based on display mode
watch(displayMode, (mode) => {
  if (mode === 'clean') {
    layers.zones = false
    layers.markers = true
    layers.lines = false
    layers.annotations = false
    layers.labels = false
  } else {
    layers.zones = true
    layers.markers = true
    layers.lines = true
    layers.annotations = true
    layers.labels = true
  }
})

const chartData = computed(() => ({
  labels: props.priceData.map(d => d.x),
  datasets: [
    // Price line
    {
      label: props.symbol,
      data: props.priceData,
      borderColor: '#3b82f6',
      backgroundColor: 'rgba(59, 130, 246, 0.1)',
      fill: true
    },

    // Entry markers
    ...(layers.markers ? [{
      label: 'Entries',
      data: props.trades.map(t => ({
        x: t.entryDate,
        y: t.entryPrice,
        type: 'entry'
      })),
      pointStyle: 'triangle',
      pointRadius: 8,
      pointBackgroundColor: '#10b981',
      pointBorderColor: '#059669',
      pointBorderWidth: 2,
      showLine: false
    }] : []),

    // Exit markers
    ...(layers.markers ? [{
      label: 'Exits',
      data: props.trades.map(t => ({
        x: t.exitDate,
        y: t.exitPrice,
        type: 'exit',
        profit: t.profit
      })),
      pointStyle: 'triangle',
      pointRotation: 180,
      pointRadius: 8,
      pointBackgroundColor: (ctx) => {
        const profit = ctx.raw?.profit || 0
        return profit > 0 ? '#10b981' : '#ef4444'
      },
      pointBorderColor: (ctx) => {
        const profit = ctx.raw?.profit || 0
        return profit > 0 ? '#059669' : '#dc2626'
      },
      pointBorderWidth: 2,
      showLine: false
    }] : [])
  ]
}))

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index',
    intersect: false
  },
  plugins: {
    legend: {
      display: true,
      position: 'top'
    },
    tooltip: {
      enabled: true,
      callbacks: {
        title: (items) => {
          const point = items[0]?.raw
          if (point?.type === 'entry') return 'ENTRY SIGNAL'
          if (point?.type === 'exit') return 'EXIT SIGNAL'
          return items[0]?.label || ''
        },
        label: (context) => {
          const point = context.raw
          if (!point?.type) {
            return `Price: $${context.parsed.y.toFixed(2)}`
          }

          const trade = props.trades.find(t =>
            (point.type === 'entry' && t.entryDate === point.x) ||
            (point.type === 'exit' && t.exitDate === point.x)
          )

          if (point.type === 'entry') {
            return [
              `Price: $${point.y.toFixed(2)}`,
              `Date: ${point.x}`,
              'Reason: Entry conditions met'
            ]
          } else {
            return [
              `Price: $${point.y.toFixed(2)}`,
              `Date: ${point.x}`,
              `Profit: ${trade?.profit.toFixed(2)}%`,
              `Reason: ${trade?.reason || 'Exit conditions met'}`
            ]
          }
        }
      }
    },

    // Annotations plugin
    annotation: layers.zones || layers.lines || layers.annotations ? {
      annotations: {
        // Trade zones
        ...(layers.zones ? props.trades.reduce((acc, trade, i) => ({
          ...acc,
          [`zone${i}`]: {
            type: 'box',
            xMin: trade.entryDate,
            xMax: trade.exitDate,
            backgroundColor: trade.profit > 0
              ? 'rgba(16, 185, 129, 0.05)'
              : 'rgba(239, 68, 68, 0.05)',
            borderColor: trade.profit > 0 ? '#10b981' : '#ef4444',
            borderWidth: 1,
            borderDash: [5, 5]
          }
        }), {}) : {}),

        // Trade lines
        ...(layers.lines ? props.trades.reduce((acc, trade, i) => ({
          ...acc,
          [`line${i}`]: {
            type: 'line',
            xMin: trade.entryDate,
            yMin: trade.entryPrice,
            xMax: trade.exitDate,
            yMax: trade.exitPrice,
            borderColor: trade.profit > 0 ? '#10b981' : '#ef4444',
            borderWidth: 2,
            borderDash: [5, 5],
            label: layers.labels ? {
              content: `${trade.profit > 0 ? '+' : ''}${trade.profit.toFixed(1)}%`,
              display: true,
              position: 'center',
              backgroundColor: trade.profit > 0 ? '#10b981' : '#ef4444',
              color: 'white',
              font: { size: 11, weight: 'bold' }
            } : undefined
          }
        }), {}) : {}),

        // Detailed annotations
        ...(layers.annotations ? props.trades.reduce((acc, trade, i) => ({
          ...acc,
          [`annotation${i}`]: {
            type: 'label',
            xValue: trade.exitDate,
            yValue: trade.exitPrice,
            content: [
              `${trade.profit > 0 ? '+' : ''}${trade.profit.toFixed(1)}%`,
              trade.reason
            ],
            backgroundColor: trade.profit > 0 ? '#10b981' : '#ef4444',
            color: 'white',
            font: { size: 10 },
            padding: 4,
            position: trade.profit > 0 ? 'top' : 'bottom',
            callout: {
              display: true,
              borderColor: trade.profit > 0 ? '#10b981' : '#ef4444'
            }
          }
        }), {}) : {})
      }
    } : {}
  },
  scales: {
    x: {
      type: 'time',
      time: {
        unit: 'day'
      },
      grid: {
        display: false
      }
    },
    y: {
      beginAtZero: false,
      ticks: {
        callback: (value) => `$${value.toFixed(2)}`
      }
    }
  }
}))

const averageProfit = computed(() => {
  if (props.trades.length === 0) return 0
  const sum = props.trades.reduce((acc, t) => acc + t.profit, 0)
  return (sum / props.trades.length).toFixed(2)
})
</script>
```

---

## My Top Recommendation

For your trading platform, I recommend this combination:

### Default View (Clean)
1. **Simple arrow markers** (▲ green for entry, ▼ for exit colored by profit)
2. **Hover tooltips** with full details
3. **Toggle button** to switch to detailed view

### Detailed View (On Demand)
1. **Background trade zones** (light shading)
2. **Arrow markers** (same as clean)
3. **Profit labels** on exit points
4. **Optional trade lines** (toggle)

### Why This Works
- ✓ Clean by default (not overwhelming)
- ✓ Rich details available on demand
- ✓ Professional appearance
- ✓ Works on all screen sizes
- ✓ Educational for learning strategies
- ✓ Easy to implement with Chart.js + Nuxt UI

Would you like me to implement this for your backtesting page?
