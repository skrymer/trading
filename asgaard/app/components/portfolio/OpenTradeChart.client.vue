<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'
import type { PortfolioTrade } from '~/types'
import { format } from 'date-fns'

const props = defineProps<{
  trade: PortfolioTrade
}>()

const stockData = ref<any>(null)
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const colorMode = useColorMode()

// Fetch stock data
async function fetchStockData() {
  status.value = 'pending'
  try {
    // Always fetch the main symbol (TQQQ) for chart and P/L
    // The backend handles strategy evaluation using the underlying asset (QQQ)
    const data = await $fetch(`/udgaard/api/stocks/${props.trade.symbol}`)
    stockData.value = data
    status.value = 'success'
  } catch (error) {
    console.error('Error fetching stock data:', error)
    status.value = 'error'
  }
}

// Fetch on mount
onMounted(() => {
  fetchStockData()
})

// Helper function to check if a quote has valid price data
function isValidQuote(quote: any): boolean {
  return quote
    && quote.date
    && typeof quote.closePrice === 'number' && quote.closePrice > 0
    && typeof quote.openPrice === 'number' && quote.openPrice > 0
    && typeof quote.high === 'number' && quote.high > 0
    && typeof quote.low === 'number' && quote.low > 0
}

// Filter quotes since entry date
const filteredQuotes = computed(() => {
  if (!stockData.value?.quotes || stockData.value.quotes.length === 0) {
    return []
  }

  const entryDate = new Date(props.trade.entryDate)

  // Filter by date and valid price data (excludes weekends/holidays/bad data)
  const filtered = stockData.value.quotes.filter((quote: any) => {
    if (!isValidQuote(quote)) return false
    const quoteDate = new Date(quote.date)
    return quoteDate >= entryDate
  }).sort((a: any, b: any) => new Date(a.date).getTime() - new Date(b.date).getTime())

  // If no quotes match the entry date (e.g., entry date is today but no data yet),
  // show the most recent quotes instead (last 30 days or available data)
  if (filtered.length === 0 && stockData.value.quotes.length > 0) {
    const validQuotes = stockData.value.quotes.filter(isValidQuote)
    const sortedQuotes = [...validQuotes].sort((a: any, b: any) =>
      new Date(b.date).getTime() - new Date(a.date).getTime()
    )
    // Return last 30 quotes or all if less than 30
    return sortedQuotes.slice(0, 30).reverse()
  }

  return filtered
})

// Get the last valid quote date for display purposes
const lastValidQuoteDate = computed(() => {
  if (!stockData.value?.quotes || stockData.value.quotes.length === 0) return null

  const validQuotes = stockData.value.quotes.filter(isValidQuote)
  if (validQuotes.length === 0) return null

  return validQuotes[validQuotes.length - 1].date
})

// Check if we're showing fallback data (entry date is in the future)
const isShowingFallbackData = computed(() => {
  if (!lastValidQuoteDate.value) return false

  const entryDate = new Date(props.trade.entryDate)
  const lastQuoteDate = new Date(lastValidQuoteDate.value)

  return entryDate > lastQuoteDate
})

// Chart series - Candlestick + EMAs (using date strings to avoid gaps)
const chartSeries = computed(() => {
  if (!filteredQuotes.value.length) return []

  return [
    {
      type: 'candlestick',
      name: props.trade.symbol,
      data: filteredQuotes.value.map((quote: any) => ({
        x: quote.date,
        y: [quote.openPrice, quote.high, quote.low, quote.closePrice]
      }))
    },
    {
      type: 'line',
      name: 'EMA 10',
      data: filteredQuotes.value.map((quote: any) => ({
        x: quote.date,
        y: quote.closePriceEMA10 ?? null
      }))
    },
    {
      type: 'line',
      name: 'EMA 20',
      data: filteredQuotes.value.map((quote: any) => ({
        x: quote.date,
        y: quote.closePriceEMA20 ?? null
      }))
    },
    {
      type: 'line',
      name: 'EMA 50',
      data: filteredQuotes.value.map((quote: any) => ({
        x: quote.date,
        y: quote.closePriceEMA50 ?? null
      }))
    }
  ]
})

// Calculate current profit/loss
const currentProfitLoss = computed(() => {
  if (!filteredQuotes.value.length) return null

  const latestQuote = filteredQuotes.value[filteredQuotes.value.length - 1]
  const currentPrice = latestQuote.closePrice
  const profit = (currentPrice - props.trade.entryPrice) * props.trade.quantity
  const profitPercentage = ((currentPrice - props.trade.entryPrice) / props.trade.entryPrice) * 100

  return {
    currentPrice,
    profit,
    profitPercentage
  }
})

// Chart options
const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'candlestick',
      height: 400,
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
    plotOptions: {
      candlestick: {
        colors: {
          upward: '#10b981',
          downward: '#ef4444'
        }
      }
    },
    stroke: {
      width: [1, 2, 2, 2],
      curve: 'smooth'
    },
    dataLabels: {
      enabled: false
    },
    xaxis: {
      type: 'category',
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        formatter: (value: string) => {
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
        formatter: (val: number) => {
          if (val == null || val === undefined) return ''
          return '$' + val.toFixed(2)
        }
      }
    },
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    legend: {
      show: true,
      position: 'top',
      horizontalAlign: 'left',
      labels: {
        colors: isDark ? '#d1d5db' : '#6b7280'
      }
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      x: {
        format: 'MMM dd, yyyy'
      }
    },
    colors: ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b'],
    annotations: {
      yaxis: [{
        y: props.trade.entryPrice,
        borderColor: '#10b981',
        strokeDashArray: 4,
        label: {
          text: `Entry: $${props.trade.entryPrice.toFixed(2)}`,
          style: {
            color: '#fff',
            background: '#10b981'
          }
        }
      }]
    }
  }
})
</script>

<template>
  <div class="p-4 bg-muted/30">
    <!-- Loading State -->
    <div v-if="status === 'pending'" class="flex items-center justify-center h-64">
      <UIcon name="i-lucide-loader-2" class="w-8 h-8 text-primary animate-spin" />
    </div>

    <!-- Error State -->
    <div v-else-if="status === 'error'" class="flex flex-col items-center justify-center h-64">
      <UIcon name="i-lucide-alert-circle" class="w-8 h-8 text-red-500 mb-2" />
      <p class="text-sm text-muted">
        Failed to load chart data
      </p>
    </div>

    <!-- Chart -->
    <div v-else-if="filteredQuotes.length > 0">
      <!-- Warning for future entry date -->
      <div v-if="isShowingFallbackData && lastValidQuoteDate" class="mb-4 p-3 rounded-lg bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-calendar-clock" class="w-4 h-4 text-orange-600 dark:text-orange-400" />
          <p class="text-sm text-orange-700 dark:text-orange-300">
            Entry date is <span class="font-semibold">{{ format(new Date(trade.entryDate), 'MMM dd, yyyy') }}</span> but data is only available until <span class="font-semibold">{{ format(new Date(lastValidQuoteDate), 'MMM dd, yyyy') }}</span>. Showing last 30 days for reference.
          </p>
        </div>
      </div>

      <!-- Header for trades with underlying assets -->
      <div v-if="trade.underlyingSymbol" class="mb-4 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-info" class="w-4 h-4 text-blue-600 dark:text-blue-400" />
          <p class="text-sm text-blue-700 dark:text-blue-300">
            Trading <span class="font-semibold">{{ trade.symbol }}</span> using <span class="font-semibold">{{ trade.underlyingSymbol }}</span> signals
          </p>
        </div>
      </div>

      <!-- Stats -->
      <div class="grid grid-cols-3 gap-4 mb-4">
        <div class="text-center p-3 rounded-lg bg-background">
          <p class="text-xs text-muted mb-1">
            Entry Price
          </p>
          <p class="font-semibold">
            ${{ trade.entryPrice.toFixed(2) }}
          </p>
        </div>
        <div class="text-center p-3 rounded-lg bg-background">
          <p class="text-xs text-muted mb-1">
            Current Price
          </p>
          <p class="font-semibold">
            ${{ currentProfitLoss?.currentPrice.toFixed(2) }}
          </p>
        </div>
        <div class="text-center p-3 rounded-lg" :class="currentProfitLoss && currentProfitLoss.profit >= 0 ? 'bg-green-50 dark:bg-green-900/20' : 'bg-red-50 dark:bg-red-900/20'">
          <p class="text-xs text-muted mb-1">
            P&L
          </p>
          <p class="font-semibold" :class="currentProfitLoss && currentProfitLoss.profit >= 0 ? 'text-green-600' : 'text-red-600'">
            {{ currentProfitLoss ? (currentProfitLoss.profit >= 0 ? '+' : '') + currentProfitLoss.profit.toFixed(2) : '0.00' }}
            ({{ currentProfitLoss ? (currentProfitLoss.profitPercentage >= 0 ? '+' : '') + currentProfitLoss.profitPercentage.toFixed(2) : '0.00' }}%)
          </p>
        </div>
      </div>

      <!-- Chart -->
      <ClientOnly>
        <apexchart
          type="candlestick"
          :options="chartOptions"
          :series="chartSeries"
          :height="400"
        />
      </ClientOnly>
    </div>

    <!-- No Data -->
    <div v-else class="flex flex-col items-center justify-center h-64">
      <UIcon name="i-lucide-trending-up" class="w-8 h-8 text-muted mb-2" />
      <p class="text-sm text-muted">
        No chart data available
      </p>
    </div>
  </div>
</template>
