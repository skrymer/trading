<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'
import type { PortfolioTrade } from '~/types'
import { format } from 'date-fns'

const props = defineProps<{
  trade: PortfolioTrade
}>()

interface OptionPricePoint {
  date: string
  price: number
}

const priceData = ref<OptionPricePoint[]>([])
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const errorMessage = ref<string | null>(null)
const colorMode = useColorMode()

// Fetch option price data
async function fetchOptionPrices() {
  status.value = 'pending'
  errorMessage.value = null

  try {
    const endDate = props.trade.exitDate || new Date().toISOString().split('T')[0]

    const response = await $fetch<OptionPricePoint[]>(
      `/udgaard/api/options/historical-prices?` +
      `symbol=${props.trade.symbol}&` +
      `strike=${props.trade.strikePrice}&` +
      `expiration=${props.trade.expirationDate}&` +
      `type=${props.trade.optionType}&` +
      `startDate=${props.trade.entryDate}&` +
      `endDate=${endDate}`
    )

    priceData.value = response
    status.value = 'success'
  } catch (error: any) {
    console.error('Error fetching option prices:', error)
    errorMessage.value = error.message || 'Failed to load option price data'
    status.value = 'error'
  }
}

// Fetch on mount
onMounted(() => {
  fetchOptionPrices()
})

// Calculate current P/L
const currentProfitLoss = computed(() => {
  if (!priceData.value.length) return null

  const latestPoint = priceData.value[priceData.value.length - 1]
  if (!latestPoint) return null

  const latestPrice = latestPoint.price
  const contracts = props.trade.contracts || 1
  const multiplier = props.trade.multiplier || 100

  const profit = (latestPrice - props.trade.entryPrice) * contracts * multiplier
  const profitPercentage = ((latestPrice - props.trade.entryPrice) / props.trade.entryPrice) * 100

  return {
    currentPrice: latestPrice,
    profit,
    profitPercentage
  }
})

// Chart series - Simple line chart for option premium
const chartSeries = computed(() => {
  if (!priceData.value.length) return []

  return [
    {
      name: 'Option Premium',
      data: priceData.value.map((point) => ({
        x: point.date,
        y: point.price
      }))
    }
  ]
})

// Chart options
const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'line',
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
    stroke: {
      width: 3,
      curve: 'smooth'
    },
    dataLabels: {
      enabled: false
    },
    markers: {
      size: 4,
      colors: ['#3b82f6'],
      strokeColors: '#fff',
      strokeWidth: 2,
      hover: {
        size: 6
      }
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
      },
      y: {
        formatter: (value: number) => {
          return '$' + value.toFixed(2)
        }
      }
    },
    colors: ['#3b82f6'],
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

// Format option details
const optionDetails = computed(() => {
  const type = props.trade.optionType?.toLowerCase()
  const strike = props.trade.strikePrice?.toFixed(2)
  const expiration = props.trade.expirationDate ? format(new Date(props.trade.expirationDate), 'MMM dd, yyyy') : 'N/A'

  return `${props.trade.symbol} ${strike} ${type} exp ${expiration}`
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
      <p class="text-sm text-muted mb-2">
        Failed to load option price data
      </p>
      <p v-if="errorMessage" class="text-xs text-muted">
        {{ errorMessage }}
      </p>
    </div>

    <!-- Chart -->
    <div v-else-if="priceData.length > 0">
      <!-- Option Details Header -->
      <div class="mb-4 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-trending-up" class="w-4 h-4 text-blue-600 dark:text-blue-400" />
          <p class="text-sm text-blue-700 dark:text-blue-300">
            <span class="font-semibold">{{ optionDetails }}</span>
            <span class="ml-2">{{ trade.contracts }} contract{{ trade.contracts !== 1 ? 's' : '' }}</span>
          </p>
        </div>
      </div>

      <!-- Stats -->
      <div class="grid grid-cols-3 gap-4 mb-4">
        <div class="text-center p-3 rounded-lg bg-background">
          <p class="text-xs text-muted mb-1">
            Entry Premium
          </p>
          <p class="font-semibold">
            ${{ trade.entryPrice.toFixed(2) }}
          </p>
        </div>
        <div class="text-center p-3 rounded-lg bg-background">
          <p class="text-xs text-muted mb-1">
            Current Premium
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
          type="line"
          :options="chartOptions"
          :series="chartSeries"
          :height="400"
        />
      </ClientOnly>
    </div>

    <!-- No Data -->
    <div v-else class="flex flex-col items-center justify-center h-64">
      <UIcon name="i-lucide-bar-chart-2" class="w-8 h-8 text-muted mb-2" />
      <p class="text-sm text-muted">
        No option price data available
      </p>
      <p class="text-xs text-muted mt-1">
        Historical data may not be available for this date range
      </p>
    </div>
  </div>
</template>
