<script setup lang="ts">
import { format } from 'date-fns'
import type { Trade } from '~/types'

const props = defineProps<{
  trades: Trade[] | null
  loading?: boolean
}>()

const startingCapital = 100000

// Leverage selection
const leverage = ref(1)
const leverageOptions = [
  { value: 1, label: '1x' },
  { value: 2, label: '2x' },
  { value: 3, label: '3x' },
  { value: 4, label: '4x' },
  { value: 5, label: '5x' }
]

// Calculate equity curve based on $100,000 starting capital with leverage
const equityCurve = computed(() => {
  if (!props.trades || props.trades.length === 0) return []

  let equity = startingCapital

  // Filter out trades with invalid dates
  // Note: exitQuote is the last quote in the quotes array
  const validTrades = props.trades.filter((trade) => {
    const exitQuote = trade.quotes?.[trade.quotes.length - 1]
    const hasExitDate = exitQuote?.date
    const hasStartDate = trade.startDate

    return hasExitDate && hasStartDate
      && !isNaN(new Date(exitQuote.date).getTime())
      && !isNaN(new Date(trade.startDate).getTime())
  })

  if (validTrades.length === 0) {
    console.warn('No valid trades with proper dates. Total trades:', props.trades.length)
    return []
  }

  // Sort trades by exit date to show chronological equity progression
  const sortedTrades = [...validTrades].sort((a, b) => {
    const aExitDate = a.quotes![a.quotes!.length - 1]!.date
    const bExitDate = b.quotes![b.quotes!.length - 1]!.date
    return new Date(aExitDate!).getTime() - new Date(bExitDate!).getTime()
  })

  // Add starting point
  const equityPoints: { date: string, equity: number, profit: number }[] = [{
    date: sortedTrades[0]!.startDate!,
    equity: startingCapital,
    profit: 0
  }]

  // Calculate equity after each trade with leverage applied
  sortedTrades.forEach((trade) => {
    // Apply leverage to the profit percentage
    const leveragedProfitPct = trade.profitPercentage * leverage.value
    const profitDollars = (equity * leveragedProfitPct) / 100
    equity += profitDollars

    const exitQuote = trade.quotes![trade.quotes!.length - 1]
    equityPoints.push({
      date: exitQuote!.date!,
      equity: equity,
      profit: profitDollars
    })
  })

  return equityPoints
})

// Prepare data for equity curve chart
const equitySeries = computed(() => {
  // Filter out any invalid data points
  const validData = equityCurve.value.filter((point) => {
    return point.equity !== null
      && point.equity !== undefined
      && !isNaN(point.equity)
      && isFinite(point.equity)
  })

  const leverageLabel = leverageOptions.find(opt => opt.value === leverage.value)?.label || `${leverage.value}x`

  return [{
    name: `Portfolio Value (${leverageLabel})`,
    data: validData.map(point => Number(point.equity))
  }]
})

const equityCategories = computed(() => {
  // Filter out any invalid data points (same filter as above)
  const validData = equityCurve.value.filter((point) => {
    return point.equity !== null
      && point.equity !== undefined
      && !isNaN(point.equity)
      && isFinite(point.equity)
  })

  return validData.map((point) => {
    try {
      return format(new Date(point.date), 'MMM dd, yyyy')
    } catch (e) {
      console.error('Error formatting date:', point.date, e)
      return String(point.date)
    }
  })
})

// Check if chart data is valid for rendering
const hasValidChartData = computed(() => {
  const firstSeries = equitySeries.value[0]
  return equitySeries.value.length > 0
    && firstSeries
    && firstSeries.data.length > 0
    && equityCategories.value.length > 0
    && firstSeries.data.length === equityCategories.value.length
})

// Debug: Log chart data
watchEffect(() => {
  if (equitySeries.value.length > 0 && equityCategories.value.length > 0) {
    const firstSeries = equitySeries.value[0]
    if (firstSeries) {
      console.log('Equity Curve Data:', {
        seriesLength: firstSeries.data.length,
        categoriesLength: equityCategories.value.length,
        dataMatch: firstSeries.data.length === equityCategories.value.length,
        sampleData: firstSeries.data.slice(0, 3),
        sampleCategories: equityCategories.value.slice(0, 3)
      })
    }
  }
})

// Calculate performance metrics
const performanceMetrics = computed(() => {
  if (!equityCurve.value || equityCurve.value.length === 0) return null

  const finalEquity = equityCurve.value[equityCurve.value.length - 1]!.equity
  const totalReturn = ((finalEquity - startingCapital) / startingCapital) * 100
  const totalProfit = finalEquity - startingCapital

  // Calculate max drawdown
  let maxEquity = startingCapital
  let maxDrawdown = 0

  equityCurve.value.forEach((point) => {
    if (point.equity > maxEquity) {
      maxEquity = point.equity
    }
    const drawdown = ((maxEquity - point.equity) / maxEquity) * 100
    if (drawdown > maxDrawdown) {
      maxDrawdown = drawdown
    }
  })

  // Calculate CAGR (Compound Annual Growth Rate)
  const startDate = new Date(equityCurve.value[0]!.date)
  const endDate = new Date(equityCurve.value[equityCurve.value.length - 1]!.date)
  const years = (endDate.getTime() - startDate.getTime()) / (365.25 * 24 * 60 * 60 * 1000)
  const cagr = years > 0 ? (Math.pow(finalEquity / startingCapital, 1 / years) - 1) * 100 : 0

  return {
    startingCapital,
    finalEquity,
    totalReturn,
    totalProfit,
    maxDrawdown,
    cagr
  }
})

// Format currency
const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(value)
}

// Format percentage
const formatPercentage = (value: number) => {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}
</script>

<template>
  <UCard>
    <template #header>
      <div v-if="loading">
        <USkeleton class="h-6 w-48" />
      </div>
      <div v-else class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <h3 class="text-lg font-semibold">
            Equity Curve
          </h3>
          <div class="flex items-center gap-2">
            <span class="text-sm text-muted">Leverage:</span>
            <USelect
              v-model="leverage"
              :items="leverageOptions"
              value-key="value"
              size="xs"
              class="w-20"
            />
          </div>
        </div>
        <div v-if="performanceMetrics" class="flex gap-6 text-sm">
          <div>
            <span class="text-muted">Starting:</span>
            <span class="ml-2 font-medium">{{ formatCurrency(performanceMetrics.startingCapital) }}</span>
          </div>
          <div>
            <span class="text-muted">Final:</span>
            <span class="ml-2 font-medium">{{ formatCurrency(performanceMetrics.finalEquity) }}</span>
          </div>
          <div>
            <span class="text-muted">Return:</span>
            <span class="ml-2 font-medium" :class="performanceMetrics.totalReturn >= 0 ? 'text-green-600' : 'text-red-600'">
              {{ formatPercentage(performanceMetrics.totalReturn) }}
            </span>
          </div>
          <div>
            <span class="text-muted">CAGR:</span>
            <span class="ml-2 font-medium" :class="performanceMetrics.cagr >= 0 ? 'text-green-600' : 'text-red-600'">
              {{ formatPercentage(performanceMetrics.cagr) }}
            </span>
          </div>
          <div>
            <span class="text-muted">Max DD:</span>
            <span class="ml-2 font-medium text-red-600">
              -{{ performanceMetrics.maxDrawdown.toFixed(2) }}%
            </span>
          </div>
        </div>
      </div>
    </template>

    <div v-if="loading">
      <USkeleton class="h-96 w-full" />
    </div>

    <div v-else-if="!trades || trades.length === 0 || equityCurve.length === 0">
      <div class="text-center py-8 text-muted">
        No trades to display equity curve
      </div>
    </div>

    <ChartsLineChart
      v-else-if="hasValidChartData"
      :series="equitySeries"
      :categories="equityCategories"
      :height="400"
      :show-legend="true"
      y-axis-label="Portfolio Value ($)"
    />

    <div v-else>
      <div class="text-center py-8 text-muted">
        Invalid data for equity curve
      </div>
    </div>
  </UCard>
</template>
