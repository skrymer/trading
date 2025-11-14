<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'
import type { Trade } from '~/types'

const props = withDefaults(defineProps<{
  trade: Trade
  height?: number | string
}>(), {
  height: 350
})

const colorMode = useColorMode()

// Transform trade quotes into candlestick data (using formatted date strings to avoid gaps)
const candlestickData = computed(() => {
  return props.trade.quotes.map((quote, index) => ({
    x: quote.date,
    y: [
      quote.openPrice,
      quote.high,
      quote.low,
      quote.closePrice
    ]
  }))
})

// EMA 10 data
const ema10Data = computed(() => {
  return props.trade.quotes.map((quote, index) => ({
    x: quote.date,
    y: quote.closePriceEMA10
  }))
})

// EMA 20 data
const ema20Data = computed(() => {
  return props.trade.quotes.map((quote, index) => ({
    x: quote.date,
    y: quote.closePriceEMA20
  }))
})

const series = computed(() => [
  {
    name: 'Price',
    type: 'candlestick',
    data: candlestickData.value
  },
  {
    name: 'EMA 10',
    type: 'line',
    data: ema10Data.value
  },
  {
    name: 'EMA 20',
    type: 'line',
    data: ema20Data.value
  }
])

const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'
  const exitQuote = props.trade.quotes[props.trade.quotes.length - 1]

  return {
    chart: {
      type: 'candlestick',
      height: props.height,
      toolbar: {
        show: true,
        tools: {
          download: true,
          zoom: true,
          zoomin: true,
          zoomout: true,
          pan: true,
          reset: true
        }
      },
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280'
    },
    title: {
      text: `${props.trade.stockSymbol} - ${props.trade.profitPercentage >= 0 ? '+' : ''}${props.trade.profitPercentage.toFixed(2)}%`,
      align: 'left',
      style: {
        color: props.trade.profitPercentage >= 0 ? '#10b981' : '#ef4444',
        fontSize: '16px',
        fontWeight: 600
      }
    },
    xaxis: {
      type: 'category',
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        formatter: (value) => {
          const date = new Date(value)
          return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
        }
      },
      tickPlacement: 'on'
    },
    yaxis: {
      tooltip: {
        enabled: true
      },
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        formatter: (value) => `$${value.toFixed(2)}`
      }
    },
    plotOptions: {
      candlestick: {
        colors: {
          upward: '#10b981',
          downward: '#ef4444'
        },
        wick: {
          useFillColor: true
        }
      }
    },
    stroke: {
      width: [1, 2, 2],
      curve: 'smooth'
    },
    colors: ['#3b82f6', '#8b5cf6', '#f59e0b'],
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      shared: true,
      intersect: false,
      x: {
        format: 'MMM dd, yyyy'
      }
    },
    legend: {
      show: true,
      position: 'top',
      horizontalAlign: 'right',
      labels: {
        colors: isDark ? '#d1d5db' : '#6b7280'
      }
    },
    annotations: {
      points: [
        // Entry point
        {
          x: props.trade.entryQuote.date,
          y: props.trade.entryQuote.closePrice,
          marker: {
            size: 8,
            fillColor: '#10b981',
            strokeColor: '#fff',
            strokeWidth: 2,
            shape: 'circle'
          },
          label: {
            borderColor: '#10b981',
            offsetY: 0,
            style: {
              color: '#fff',
              background: '#10b981',
              fontSize: '11px',
              fontWeight: 600
            },
            text: `Entry: $${props.trade.entryQuote.closePrice.toFixed(2)}`
          }
        },
        // Exit point
        {
          x: exitQuote.date,
          y: exitQuote.closePrice,
          marker: {
            size: 8,
            fillColor: props.trade.profitPercentage >= 0 ? '#3b82f6' : '#ef4444',
            strokeColor: '#fff',
            strokeWidth: 2,
            shape: 'circle'
          },
          label: {
            borderColor: props.trade.profitPercentage >= 0 ? '#3b82f6' : '#ef4444',
            offsetY: 0,
            style: {
              color: '#fff',
              background: props.trade.profitPercentage >= 0 ? '#3b82f6' : '#ef4444',
              fontSize: '11px',
              fontWeight: 600
            },
            text: `Exit: $${exitQuote.closePrice.toFixed(2)}`
          }
        }
      ]
    }
  }
})
</script>

<template>
  <UCard :ui="{ body: '!p-3' }">
    <ClientOnly>
      <apexchart
        :options="chartOptions"
        :series="series"
        :height="height"
      />
    </ClientOnly>
  </UCard>
</template>
