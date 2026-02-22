<template>
  <UCard>
    <template #header>
      <div v-if="loading">
        <USkeleton class="h-6 w-48" />
      </div>
      <div v-else class="flex items-center justify-between flex-wrap gap-4">
        <div class="flex items-center gap-4">
          <div>
            <h3 class="text-lg font-semibold">
              Equity Curve
            </h3>
            <p v-if="hasPositionSizing" class="text-xs text-muted">
              Portfolio Value ({{ formatCurrency(positionSizing!.startingCapital) }} starting capital)
            </p>
          </div>
          <div v-if="!hasPositionSizing" class="flex items-center gap-2">
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

    <div v-else-if="!equityCurveData || equityCurveData.length === 0">
      <div class="text-center py-8 text-muted">
        No trades to display equity curve
      </div>
    </div>

    <div v-else>
      <!-- Chart Toolbar -->
      <div class="flex items-center justify-between mb-2">
        <div class="flex items-center gap-2">
          <UButton
            icon="i-heroicons-arrows-pointing-out"
            size="sm"
            color="neutral"
            variant="soft"
            @click="fitContent"
          >
            Fit Content
          </UButton>
          <UButton
            icon="i-heroicons-arrow-uturn-left"
            size="sm"
            color="neutral"
            variant="soft"
            @click="resetZoom"
          >
            Reset Zoom
          </UButton>
        </div>

        <!-- Date Range Presets -->
        <div class="flex items-center gap-1">
          <UButton
            v-for="preset in datePresets"
            :key="preset"
            :color="dateRange === preset ? 'primary' : 'neutral'"
            :variant="dateRange === preset ? 'solid' : 'soft'"
            size="sm"
            @click="setDateRange(preset)"
          >
            {{ preset }}
          </UButton>
        </div>
      </div>

      <!-- Chart Container -->
      <div ref="chartContainer" class="w-full h-[500px]" />
    </div>
  </UCard>
</template>

<script setup lang="ts">
import type { EquityCurvePoint, PositionSizingResult } from '~/types'

const props = defineProps<{
  equityCurveData: EquityCurvePoint[]
  loading?: boolean
  positionSizing?: PositionSizingResult | null
}>()

const hasPositionSizing = computed(() => !!props.positionSizing && props.positionSizing.equityCurve.length > 0)
const startingCapital = computed(() => props.positionSizing?.startingCapital ?? 100000)

// Leverage selection
const leverage = ref(1)
const leverageOptions = [
  { value: 1, label: '1x' },
  { value: 2, label: '2x' },
  { value: 3, label: '3x' },
  { value: 4, label: '4x' },
  { value: 5, label: '5x' }
]

// Date range presets
const datePresets = ['1Y', '3Y', '5Y', 'ALL'] as const
const dateRange = ref<string>('ALL')

const chartContainer = ref<HTMLElement | null>(null)
let chart: any = null
let lineSeries: any = null

// Compute equity curve points from profitPercentage data or position sizing
const equityPoints = computed(() => {
  // When position sizing is active, use the backend-computed equity curve
  if (hasPositionSizing.value) {
    const sizingCurve = props.positionSizing!.equityCurve
    return sizingCurve.map(point => ({
      time: new Date(point.date).getTime() / 1000,
      value: point.portfolioValue
    }))
  }

  if (!props.equityCurveData || props.equityCurveData.length === 0) return []

  let equity = startingCapital.value
  const points: { time: number, value: number }[] = []

  // Add starting point using first trade's date
  const firstDate = props.equityCurveData[0]?.date
  if (firstDate) {
    const firstTime = new Date(firstDate).getTime() / 1000
    // Start point is 1 day before first trade
    points.push({ time: firstTime - 86400, value: startingCapital.value })
  }

  props.equityCurveData.forEach((point) => {
    const leveragedProfitPct = point.profitPercentage * leverage.value
    const profitDollars = (equity * leveragedProfitPct) / 100
    equity += profitDollars

    const time = new Date(point.date).getTime() / 1000
    points.push({ time, value: equity })
  })

  return points
})

// Calculate performance metrics
const performanceMetrics = computed(() => {
  // When position sizing is active, use backend-computed metrics
  if (hasPositionSizing.value) {
    const ps = props.positionSizing!
    const points = equityPoints.value
    if (points.length < 2) return null

    const startDate = new Date(points[0]!.time * 1000)
    const endDate = new Date(points[points.length - 1]!.time * 1000)
    const years = (endDate.getTime() - startDate.getTime()) / (365.25 * 24 * 60 * 60 * 1000)
    const cagr = years > 0 ? (Math.pow(ps.finalCapital / ps.startingCapital, 1 / years) - 1) * 100 : 0

    return {
      startingCapital: ps.startingCapital,
      finalEquity: ps.finalCapital,
      totalReturn: ps.totalReturnPct,
      totalProfit: ps.finalCapital - ps.startingCapital,
      maxDrawdown: ps.maxDrawdownPct,
      cagr
    }
  }

  if (equityPoints.value.length < 2) return null

  const start = startingCapital.value
  const finalEquity = equityPoints.value[equityPoints.value.length - 1]!.value
  const totalReturn = ((finalEquity - start) / start) * 100
  const totalProfit = finalEquity - start

  // Calculate max drawdown
  let maxEquity = start
  let maxDrawdown = 0

  equityPoints.value.forEach((point) => {
    if (point.value > maxEquity) {
      maxEquity = point.value
    }
    const drawdown = ((maxEquity - point.value) / maxEquity) * 100
    if (drawdown > maxDrawdown) {
      maxDrawdown = drawdown
    }
  })

  // Calculate CAGR
  const startDate = new Date(equityPoints.value[0]!.time * 1000)
  const endDate = new Date(equityPoints.value[equityPoints.value.length - 1]!.time * 1000)
  const years = (endDate.getTime() - startDate.getTime()) / (365.25 * 24 * 60 * 60 * 1000)
  const cagr = years > 0 ? (Math.pow(finalEquity / start, 1 / years) - 1) * 100 : 0

  return {
    startingCapital: start,
    finalEquity,
    totalReturn,
    totalProfit,
    maxDrawdown,
    cagr
  }
})

// Format helpers
const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(value)
}

const formatPercentage = (value: number) => {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

// Chart control functions
function fitContent() {
  if (chart) {
    chart.timeScale().fitContent()
  }
}

function resetZoom() {
  if (chart) {
    chart.timeScale().resetTimeScale()
    dateRange.value = 'ALL'
  }
}

function setDateRange(range: string) {
  if (!chart || equityPoints.value.length === 0) return

  dateRange.value = range

  if (range === 'ALL') {
    chart.timeScale().fitContent()
    return
  }

  const now = new Date()
  const startDate = new Date()

  switch (range) {
    case '1Y':
      startDate.setFullYear(now.getFullYear() - 1)
      break
    case '3Y':
      startDate.setFullYear(now.getFullYear() - 3)
      break
    case '5Y':
      startDate.setFullYear(now.getFullYear() - 5)
      break
  }

  chart.timeScale().setVisibleRange({
    from: startDate.getTime() / 1000,
    to: now.getTime() / 1000
  })
}

function updateChartData() {
  if (!lineSeries || equityPoints.value.length === 0) return
  lineSeries.setData(equityPoints.value)
}

onMounted(async () => {
  if (!chartContainer.value) return

  const LightweightCharts = await import('lightweight-charts')
  const { createChart, ColorType } = LightweightCharts
  const LineSeries = LightweightCharts.LineSeries || 'Line'

  chart = createChart(chartContainer.value, {
    width: chartContainer.value.clientWidth,
    height: chartContainer.value.clientHeight,
    layout: {
      background: { type: ColorType.Solid, color: 'transparent' },
      textColor: '#6B7280'
    },
    grid: {
      vertLines: { color: 'rgba(229, 231, 235, 0.1)' },
      horzLines: { color: 'rgba(229, 231, 235, 0.1)' }
    },
    crosshair: {
      mode: 1
    },
    timeScale: {
      timeVisible: false,
      borderColor: '#D1D5DB',
      rightOffset: 5,
      barSpacing: 3,
      minBarSpacing: 0.5,
      fixLeftEdge: false,
      fixRightEdge: false
    },
    rightPriceScale: {
      borderColor: '#D1D5DB',
      scaleMargins: {
        top: 0.1,
        bottom: 0.1
      }
    },
    handleScroll: {
      mouseWheel: true,
      pressedMouseMove: true,
      horzTouchDrag: true,
      vertTouchDrag: true
    },
    handleScale: {
      mouseWheel: true,
      pinch: true,
      axisPressedMouseMove: {
        time: true,
        price: true
      },
      axisDoubleClickReset: {
        time: true,
        price: true
      }
    }
  })

  lineSeries = chart.addSeries(LineSeries, {
    color: '#2563eb',
    lineWidth: 2,
    priceFormat: {
      type: 'custom',
      formatter: (price: number) => formatCurrency(price)
    },
    lastValueVisible: true,
    priceLineVisible: true
  })

  updateChartData()
  chart.timeScale().fitContent()

  // Handle resize
  const resizeObserver = new ResizeObserver(() => {
    if (chart && chartContainer.value) {
      chart.applyOptions({
        width: chartContainer.value.clientWidth,
        height: chartContainer.value.clientHeight
      })
    }
  })
  resizeObserver.observe(chartContainer.value)

  onBeforeUnmount(() => {
    resizeObserver.disconnect()
    if (chart) {
      chart.remove()
    }
  })
})

// Watch for data/leverage changes and update chart
watch([equityPoints], () => {
  updateChartData()
})
</script>
