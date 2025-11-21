<template>
  <div class="w-full bg-white dark:bg-gray-900 rounded-lg p-4">
    <!-- Chart Toolbar -->
    <div class="flex items-center justify-between mb-4 gap-4">
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
          :color="dateRange === '1M' ? 'primary' : 'neutral'"
          :variant="dateRange === '1M' ? 'solid' : 'soft'"
          size="sm"
          @click="setDateRange('1M')"
        >
          1M
        </UButton>
        <UButton
          :color="dateRange === '3M' ? 'primary' : 'neutral'"
          :variant="dateRange === '3M' ? 'solid' : 'soft'"
          size="sm"
          @click="setDateRange('3M')"
        >
          3M
        </UButton>
        <UButton
          :color="dateRange === '6M' ? 'primary' : 'neutral'"
          :variant="dateRange === '6M' ? 'solid' : 'soft'"
          size="sm"
          @click="setDateRange('6M')"
        >
          6M
        </UButton>
        <UButton
          :color="dateRange === '1Y' ? 'primary' : 'neutral'"
          :variant="dateRange === '1Y' ? 'solid' : 'soft'"
          size="sm"
          @click="setDateRange('1Y')"
        >
          1Y
        </UButton>
        <UButton
          :color="dateRange === 'ALL' ? 'primary' : 'neutral'"
          :variant="dateRange === 'ALL' ? 'solid' : 'soft'"
          size="sm"
          @click="setDateRange('ALL')"
        >
          ALL
        </UButton>
      </div>
    </div>

    <!-- Chart Container -->
    <div ref="chartContainer" class="w-full h-[600px]" />
  </div>
</template>

<script setup lang="ts">
import { createChart, CandlestickSeries, HistogramSeries, ColorType } from 'lightweight-charts'
import type { StockQuote, OrderBlock } from '~/types'

const props = defineProps<{
  quotes: StockQuote[]
  orderBlocks: OrderBlock[]
  symbol: string
}>()

const chartContainer = ref<HTMLElement | null>(null)
let chart: any = null
let candlestickSeries: any = null
let volumeSeries: any = null
let currentOrderBlockPrimitive: any = null
const dateRange = ref<string>('3M')

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
  if (!chart || !props.quotes || props.quotes.length === 0) return

  dateRange.value = range

  if (range === 'ALL') {
    chart.timeScale().fitContent()
    return
  }

  // Calculate date range
  const now = new Date()
  const startDate = new Date()

  switch (range) {
    case '1M':
      startDate.setMonth(now.getMonth() - 1)
      break
    case '3M':
      startDate.setMonth(now.getMonth() - 3)
      break
    case '6M':
      startDate.setMonth(now.getMonth() - 6)
      break
    case '1Y':
      startDate.setFullYear(now.getFullYear() - 1)
      break
  }

  // Set visible range
  const startTime = startDate.getTime() / 1000
  const endTime = now.getTime() / 1000

  chart.timeScale().setVisibleRange({
    from: startTime,
    to: endTime
  })
}

// Renderer for order blocks
class OrderBlockRenderer {
  _orderBlocks: OrderBlock[]
  _lastQuoteDate: string | null
  _series: any
  _chart: any

  constructor(orderBlocks: OrderBlock[], lastQuoteDate: string | null, series: any, chart: any) {
    this._orderBlocks = orderBlocks
    this._lastQuoteDate = lastQuoteDate
    this._series = series
    this._chart = chart
  }

  draw(target: any) {
    target.useBitmapCoordinateSpace((scope: any) => {
      const ctx = scope.context

      this._orderBlocks.forEach((block) => {
        const startTime = new Date(block.startDate).getTime() / 1000
        const endTime = block.endDate
          ? new Date(block.endDate).getTime() / 1000
          : (this._lastQuoteDate ? new Date(this._lastQuoteDate).getTime() / 1000 : Date.now() / 1000)

        // Get pixel coordinates for time using chart timeScale
        const x1 = this._chart.timeScale().timeToCoordinate(startTime)
        const x2 = this._chart.timeScale().timeToCoordinate(endTime)

        // Get pixel coordinates for price using series
        const y1 = this._series?.priceToCoordinate(block.high)
        const y2 = this._series?.priceToCoordinate(block.low)

        if (x1 === null || x2 === null || y1 === null || y2 === null) {
          return
        }

        // Scale for high DPI displays
        const scaledX1 = x1 * scope.horizontalPixelRatio
        const scaledX2 = x2 * scope.horizontalPixelRatio
        const scaledY1 = y1 * scope.verticalPixelRatio
        const scaledY2 = y2 * scope.verticalPixelRatio

        // Draw filled rectangle with different opacity for calculated blocks
        const isCalculated = block.source === 'CALCULATED'
        const fillOpacity = isCalculated ? 0.4 : 0.2 // More opaque for calculated
        const fillColor = block.orderBlockType === 'BULLISH'
          ? `rgba(16, 185, 129, ${fillOpacity})`
          : `rgba(239, 68, 68, ${fillOpacity})`

        const strokeColor = block.orderBlockType === 'BULLISH'
          ? '#059669'
          : '#dc2626'

        ctx.fillStyle = fillColor
        ctx.fillRect(
          scaledX1,
          scaledY1,
          scaledX2 - scaledX1,
          scaledY2 - scaledY1
        )

        // Draw border - thicker for calculated blocks
        ctx.strokeStyle = strokeColor
        ctx.lineWidth = (isCalculated ? 3 : 2) * scope.verticalPixelRatio
        if (isCalculated) {
          // Draw dashed line for calculated blocks
          ctx.setLineDash([5 * scope.horizontalPixelRatio, 3 * scope.horizontalPixelRatio])
        } else {
          ctx.setLineDash([])
        }
        ctx.strokeRect(
          scaledX1,
          scaledY1,
          scaledX2 - scaledX1,
          scaledY2 - scaledY1
        )
        ctx.setLineDash([]) // Reset line dash

        // Draw label
        const label = `${block.orderBlockType} (${block.source})`
        ctx.font = `${10 * scope.verticalPixelRatio}px sans-serif`
        ctx.fillStyle = strokeColor
        ctx.fillText(label, scaledX1 + 5, scaledY1 + 15 * scope.verticalPixelRatio)
      })
    })
  }
}

// View for order blocks
class OrderBlockPaneView {
  _orderBlocks: OrderBlock[]
  _lastQuoteDate: string | null
  _series: any
  _chart: any

  constructor(orderBlocks: OrderBlock[], lastQuoteDate: string | null, series: any, chart: any) {
    this._orderBlocks = orderBlocks
    this._lastQuoteDate = lastQuoteDate
    this._series = series
    this._chart = chart
  }

  renderer() {
    return new OrderBlockRenderer(this._orderBlocks, this._lastQuoteDate, this._series, this._chart)
  }
}

// Custom primitive for drawing rectangles
class OrderBlockPrimitive {
  _orderBlocks: OrderBlock[]
  _lastQuoteDate: string | null
  _series: any
  _paneView: OrderBlockPaneView | null

  constructor(orderBlocks: OrderBlock[], lastQuoteDate: string | null) {
    this._orderBlocks = orderBlocks
    this._lastQuoteDate = lastQuoteDate
    this._series = null
    this._paneView = null
  }

  attached({ chart, series, requestUpdate }: any) {
    this._series = series
    this._paneView = new OrderBlockPaneView(this._orderBlocks, this._lastQuoteDate, series, chart)
    requestUpdate()
  }

  detached() {
    this._series = null
    this._paneView = null
  }

  updateAllViews() {
    // Update view state before rendering
  }

  paneViews() {
    return this._paneView ? [this._paneView] : []
  }

  priceAxisViews() {
    return []
  }

  timeAxisViews() {
    return []
  }
}

onMounted(() => {
  if (!chartContainer.value) return

  // Create chart with correct v5 API
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
      timeVisible: true,
      secondsVisible: false,
      borderColor: '#D1D5DB',
      // Enable panning and zooming
      rightOffset: 5,
      barSpacing: 6,
      minBarSpacing: 0.5,
      fixLeftEdge: false,
      fixRightEdge: false,
      lockVisibleTimeRangeOnResize: true,
      rightBarStaysOnScroll: true,
      borderVisible: true,
      visible: true,
      shiftVisibleRangeOnNewBar: true
    },
    rightPriceScale: {
      borderColor: '#D1D5DB',
      // Enable price scale interaction
      scaleMargins: {
        top: 0.1,
        bottom: 0.3 // More space for volume
      }
    },
    leftPriceScale: {
      visible: true,
      borderColor: '#D1D5DB',
      scaleMargins: {
        top: 0.7, // Volume takes top 70% space, leaving 30% at bottom
        bottom: 0
      }
    },
    // Enable mouse/touch interactions
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

  // Add candlestick series using correct v5 API
  candlestickSeries = chart.addSeries(CandlestickSeries, {
    upColor: '#10b981',
    downColor: '#ef4444',
    borderVisible: false,
    wickUpColor: '#10b981',
    wickDownColor: '#ef4444',
    priceScaleId: 'right'
  })

  // Add volume series as histogram on left scale
  volumeSeries = chart.addSeries(HistogramSeries, {
    priceFormat: {
      type: 'volume'
    },
    priceScaleId: 'left'
  })

  // Update chart data
  updateChartData()

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

// Watch for data changes
watch(() => [props.quotes, props.orderBlocks], () => {
  updateChartData()
}, { deep: true })

function updateChartData() {
  if (!chart || !candlestickSeries) return

  // Prepare candlestick data
  const candlestickData = props.quotes
    .filter(q => q.date && q.openPrice && q.closePrice && q.high && q.low)
    .map(quote => ({
      time: (new Date(quote.date!).getTime() / 1000) as any,
      open: quote.openPrice,
      high: quote.high,
      low: quote.low,
      close: quote.closePrice
    }))
    .sort((a, b) => a.time - b.time)

  // Set candlestick data
  candlestickSeries.setData(candlestickData)

  // Prepare volume data
  if (volumeSeries) {
    const volumeData = props.quotes
      .filter(q => q.date && q.volume !== undefined)
      .map(quote => ({
        time: (new Date(quote.date!).getTime() / 1000) as any,
        value: quote.volume,
        color: quote.closePrice >= quote.openPrice ? '#10b98180' : '#ef444480' // Green for up, red for down
      }))
      .sort((a, b) => a.time - b.time)

    volumeSeries.setData(volumeData)
  }

  // Remove old order block primitive if it exists
  if (currentOrderBlockPrimitive) {
    candlestickSeries.detachPrimitive(currentOrderBlockPrimitive)
    currentOrderBlockPrimitive = null
  }

  // Add order blocks primitive
  console.log('Rendering order blocks:', props.orderBlocks.length, 'blocks')
  console.log('Order blocks by source:', {
    OVTLYR: props.orderBlocks.filter(b => b.source === 'OVTLYR').length,
    CALCULATED: props.orderBlocks.filter(b => b.source === 'CALCULATED').length
  })

  const lastQuoteDate = props.quotes[props.quotes.length - 1]?.date || null
  currentOrderBlockPrimitive = new OrderBlockPrimitive(props.orderBlocks, lastQuoteDate)
  candlestickSeries.attachPrimitive(currentOrderBlockPrimitive)

  // Set default date range to 3 months
  setDateRange(dateRange.value)
}
</script>

<style scoped>
/* Lightweight Charts styling */
</style>
