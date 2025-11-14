# ApexCharts Components

This directory contains reusable ApexCharts components for the application.

## Installation

ApexCharts is already installed and configured via the plugin at `app/plugins/apexcharts.client.ts`.

## Components

### StockChart

Candlestick chart for displaying stock price data (OHLC).

**Props:**
- `data` (required): Array of candlestick data with `{ x: Date, y: [open, high, low, close] }`
- `title` (optional): Chart title, default: "Stock Chart"
- `height` (optional): Chart height, default: 400
- `showVolume` (optional): Display volume bars, default: false
- `volumeData` (optional): Array of volume data `{ x: Date, y: number }`

**Example:**
```vue
<script setup>
const candlestickData = [
  { x: new Date('2024-01-01'), y: [100, 110, 95, 105] }, // [open, high, low, close]
  { x: new Date('2024-01-02'), y: [105, 115, 100, 112] },
  { x: new Date('2024-01-03'), y: [112, 120, 108, 118] },
]

const volumeData = [
  { x: new Date('2024-01-01'), y: 1000000 },
  { x: new Date('2024-01-02'), y: 1500000 },
  { x: new Date('2024-01-03'), y: 1200000 },
]
</script>

<template>
  <ChartsStockChart
    :data="candlestickData"
    :volume-data="volumeData"
    :show-volume="true"
    title="AAPL Stock Price"
    :height="500"
  />
</template>
```

### BarChart

Bar chart for displaying categorical or comparison data.

**Props:**
- `series` (required): Array of series objects with `{ name: string, data: number[] | {x, y}[], color?: string }`
- `categories` (optional): Array of category labels for x-axis
- `title` (optional): Chart title, default: ""
- `height` (optional): Chart height, default: 350
- `horizontal` (optional): Display bars horizontally, default: false
- `stacked` (optional): Stack bars on top of each other, default: false
- `showLegend` (optional): Show legend, default: true
- `showDataLabels` (optional): Show data labels on bars, default: false
- `yAxisLabel` (optional): Y-axis label
- `xAxisLabel` (optional): X-axis label

**Example:**
```vue
<script setup>
const chartSeries = [
  {
    name: 'Revenue',
    data: [44, 55, 57, 56, 61, 58, 63, 60, 66],
    color: '#10b981'
  },
  {
    name: 'Expenses',
    data: [35, 41, 36, 26, 45, 48, 52, 53, 41],
    color: '#ef4444'
  }
]

const categories = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep']
</script>

<template>
  <ChartsBarChart
    :series="chartSeries"
    :categories="categories"
    title="Monthly Revenue vs Expenses"
    y-axis-label="Amount ($)"
    :height="400"
    :stacked="false"
  />
</template>
```

## Features

- **Dark mode support**: Charts automatically adapt to light/dark mode
- **Responsive**: Charts adjust to container width
- **Interactive**: Built-in zoom, pan, and reset tools
- **Client-only**: Components use `.client.vue` suffix for client-side rendering
- **Type-safe**: Full TypeScript support with exported prop types

## Theming

Charts inherit colors from Nuxt UI's color mode and use CSS variables for consistent theming:
- Primary colors for upward/positive trends (green #10b981)
- Danger colors for downward/negative trends (red #ef4444)
- Muted colors for grid lines and labels

## Notes

- All chart components are wrapped in `<ClientOnly>` to avoid SSR hydration issues
- Charts are wrapped in `<UCard>` for consistent styling with the rest of the app
- The plugin is configured as `.client.ts` to only load on the client side
