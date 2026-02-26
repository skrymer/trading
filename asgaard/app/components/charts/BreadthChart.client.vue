<template>
  <div class="relative">
    <div ref="chartContainer" class="w-full" :style="{ height: `${height}px` }" />

    <!-- Crosshair Tooltip -->
    <div
      v-if="tooltipVisible"
      class="absolute top-2 left-2 bg-gray-900/90 text-white text-xs rounded px-3 py-2 pointer-events-none z-10 space-y-0.5"
    >
      <div class="font-medium mb-1">
        {{ tooltipData.date }}
      </div>
      <div>
        <span class="inline-block w-3 h-0.5 bg-yellow-500 mr-1.5 align-middle" />
        Breadth: {{ tooltipData.breadth }}%
      </div>
      <div>
        <span class="inline-block w-3 h-0.5 bg-green-500 mr-1.5 align-middle" />
        EMA 10: {{ tooltipData.ema10 }}%
      </div>
      <div>
        <span class="inline-block w-3 h-0.5 bg-red-500 mr-1.5 align-middle" />
        EMA 20: {{ tooltipData.ema20 }}%
      </div>
      <div>
        <span class="inline-block w-3 h-0.5 bg-blue-500 mr-1.5 align-middle border-dashed" />
        Donchian: {{ tooltipData.donchianUpper }}–{{ tooltipData.donchianLower }}%
        <span class="text-gray-400 ml-1">(width: {{ tooltipData.donchianWidth }})</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MarketBreadthDaily } from '~/types'

const props = withDefaults(defineProps<{
  data: MarketBreadthDaily[]
  height?: number
}>(), {
  height: 400
})

const chartContainer = ref<HTMLElement | null>(null)
let chart: any = null
let breadthSeries: any = null
let ema10Series: any = null
let ema20Series: any = null
let donchianUpperSeries: any = null
let donchianLowerSeries: any = null
let donchianPrimitive: any = null

// Tooltip state
const tooltipVisible = ref(false)
const tooltipData = reactive({
  date: '',
  breadth: '',
  ema10: '',
  ema20: '',
  donchianUpper: '',
  donchianLower: '',
  donchianWidth: ''
})

// Transform data to lightweight-charts format
function toLineData(data: MarketBreadthDaily[], accessor: (d: MarketBreadthDaily) => number) {
  return data.map(d => ({
    time: new Date(d.quoteDate).getTime() / 1000,
    value: accessor(d)
  }))
}

// Donchian Channel Fill Primitive
class DonchianChannelRenderer {
  _upperData: { time: number, value: number }[]
  _lowerData: { time: number, value: number }[]
  _series: any
  _chart: any

  constructor(
    upperData: { time: number, value: number }[],
    lowerData: { time: number, value: number }[],
    series: any,
    chart: any
  ) {
    this._upperData = upperData
    this._lowerData = lowerData
    this._series = series
    this._chart = chart
  }

  draw(target: any) {
    target.useBitmapCoordinateSpace((scope: any) => {
      const ctx = scope.context
      const timeScale = this._chart.timeScale()

      if (this._upperData.length === 0 || this._lowerData.length === 0) return

      // Build a map of lower data by time for fast lookup
      const lowerMap = new Map<number, number>()
      this._lowerData.forEach(d => lowerMap.set(d.time, d.value))

      // Collect visible points with both upper and lower values
      const points: { x: number, yUpper: number, yLower: number }[] = []

      for (const d of this._upperData) {
        const lowerValue = lowerMap.get(d.time)
        if (lowerValue === undefined) continue

        const x = timeScale.timeToCoordinate(d.time)
        const yUpper = this._series.priceToCoordinate(d.value)
        const yLower = this._series.priceToCoordinate(lowerValue)

        if (x === null || yUpper === null || yLower === null) continue

        points.push({
          x: x * scope.horizontalPixelRatio,
          yUpper: yUpper * scope.verticalPixelRatio,
          yLower: yLower * scope.verticalPixelRatio
        })
      }

      if (points.length < 2) return

      // Draw filled polygon between upper and lower bands
      ctx.beginPath()
      ctx.moveTo(points[0]!.x, points[0]!.yUpper)

      // Upper line left to right
      for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i]!.x, points[i]!.yUpper)
      }

      // Lower line right to left
      for (let i = points.length - 1; i >= 0; i--) {
        ctx.lineTo(points[i]!.x, points[i]!.yLower)
      }

      ctx.closePath()
      ctx.fillStyle = 'rgba(59, 130, 246, 0.1)'
      ctx.fill()
    })
  }
}

class DonchianChannelPaneView {
  _upperData: { time: number, value: number }[]
  _lowerData: { time: number, value: number }[]
  _series: any
  _chart: any

  constructor(
    upperData: { time: number, value: number }[],
    lowerData: { time: number, value: number }[],
    series: any,
    chart: any
  ) {
    this._upperData = upperData
    this._lowerData = lowerData
    this._series = series
    this._chart = chart
  }

  renderer() {
    return new DonchianChannelRenderer(this._upperData, this._lowerData, this._series, this._chart)
  }
}

class DonchianChannelPrimitive {
  _upperData: { time: number, value: number }[]
  _lowerData: { time: number, value: number }[]
  _series: any
  _paneView: DonchianChannelPaneView | null
  _requestUpdate: (() => void) | null

  constructor(
    upperData: { time: number, value: number }[],
    lowerData: { time: number, value: number }[]
  ) {
    this._upperData = upperData
    this._lowerData = lowerData
    this._series = null
    this._paneView = null
    this._requestUpdate = null
  }

  attached({ chart, series, requestUpdate }: any) {
    this._series = series
    this._requestUpdate = requestUpdate
    this._paneView = new DonchianChannelPaneView(this._upperData, this._lowerData, series, chart)
    requestUpdate()
  }

  detached() {
    this._series = null
    this._paneView = null
    this._requestUpdate = null
  }

  updateData(
    upperData: { time: number, value: number }[],
    lowerData: { time: number, value: number }[]
  ) {
    this._upperData = upperData
    this._lowerData = lowerData
    if (this._paneView) {
      this._paneView._upperData = upperData
      this._paneView._lowerData = lowerData
    }
    if (this._requestUpdate) {
      this._requestUpdate()
    }
  }

  updateAllViews() {
    // Called before rendering
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

function updateChartData() {
  if (!breadthSeries || props.data.length === 0) return

  const breadthData = toLineData(props.data, d => d.breadthPercent)
  const ema10Data = toLineData(props.data, d => d.ema10)
  const ema20Data = toLineData(props.data, d => d.ema20)
  const upperData = toLineData(props.data, d => d.donchianUpperBand)
  const lowerData = toLineData(props.data, d => d.donchianLowerBand)

  breadthSeries.setData(breadthData)
  ema10Series.setData(ema10Data)
  ema20Series.setData(ema20Data)
  donchianUpperSeries.setData(upperData)
  donchianLowerSeries.setData(lowerData)

  // Update the Donchian fill primitive
  if (donchianPrimitive) {
    donchianPrimitive.updateData(upperData, lowerData)
  }
}

function handleCrosshairMove(param: any) {
  if (!param || !param.time || !param.seriesData) {
    tooltipVisible.value = false
    return
  }

  const breadthValue = param.seriesData.get(breadthSeries)?.value
  const ema10Value = param.seriesData.get(ema10Series)?.value
  const ema20Value = param.seriesData.get(ema20Series)?.value
  const upperValue = param.seriesData.get(donchianUpperSeries)?.value
  const lowerValue = param.seriesData.get(donchianLowerSeries)?.value

  if (breadthValue === undefined) {
    tooltipVisible.value = false
    return
  }

  const date = new Date(param.time * 1000)
  tooltipData.date = date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  tooltipData.breadth = breadthValue?.toFixed(1) ?? '-'
  tooltipData.ema10 = ema10Value?.toFixed(1) ?? '-'
  tooltipData.ema20 = ema20Value?.toFixed(1) ?? '-'
  tooltipData.donchianUpper = upperValue?.toFixed(1) ?? '-'
  tooltipData.donchianLower = lowerValue?.toFixed(1) ?? '-'
  tooltipData.donchianWidth = (upperValue != null && lowerValue != null)
    ? (upperValue - lowerValue).toFixed(1)
    : '-'

  tooltipVisible.value = true
}

onMounted(async () => {
  if (!chartContainer.value) return

  const LightweightCharts = await import('lightweight-charts')
  const { createChart, ColorType } = LightweightCharts
  const LineSeries = LightweightCharts.LineSeries || 'Line'

  chart = createChart(chartContainer.value, {
    width: chartContainer.value.clientWidth,
    height: props.height,
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
        top: 0.05,
        bottom: 0.05
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

  // All series share the same right price scale (0-100% range)
  const priceScaleOptions = {
    priceScaleId: 'right',
    lastValueVisible: false,
    priceLineVisible: false
  }

  const percentFormatter = {
    type: 'custom' as const,
    formatter: (price: number) => `${price.toFixed(0)}%`
  }

  breadthSeries = chart.addSeries(LineSeries, {
    color: '#eab308',
    lineWidth: 2,
    title: 'Breadth %',
    ...priceScaleOptions,
    priceFormat: percentFormatter,
    lastValueVisible: true
  })

  ema10Series = chart.addSeries(LineSeries, {
    color: '#10b981',
    lineWidth: 1,
    title: 'EMA 10',
    ...priceScaleOptions
  })

  ema20Series = chart.addSeries(LineSeries, {
    color: '#ef4444',
    lineWidth: 1,
    title: 'EMA 20',
    ...priceScaleOptions
  })

  donchianUpperSeries = chart.addSeries(LineSeries, {
    color: '#3b82f6',
    lineWidth: 1,
    lineStyle: 2, // Dashed
    title: 'DC Upper',
    ...priceScaleOptions
  })

  donchianLowerSeries = chart.addSeries(LineSeries, {
    color: '#3b82f6',
    lineWidth: 1,
    lineStyle: 2, // Dashed
    title: 'DC Lower',
    ...priceScaleOptions
  })

  // Attach Donchian channel fill primitive to the upper band series
  const upperData = toLineData(props.data, d => d.donchianUpperBand)
  const lowerData = toLineData(props.data, d => d.donchianLowerBand)
  donchianPrimitive = new DonchianChannelPrimitive(upperData, lowerData)
  donchianUpperSeries.attachPrimitive(donchianPrimitive)

  // Subscribe to crosshair for tooltip
  chart.subscribeCrosshairMove(handleCrosshairMove)

  updateChartData()
  chart.timeScale().fitContent()

  // Handle resize
  const resizeObserver = new ResizeObserver(() => {
    if (chart && chartContainer.value) {
      chart.applyOptions({
        width: chartContainer.value.clientWidth
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

// Watch for data changes and update chart
watch(() => props.data, () => {
  updateChartData()
  if (chart) {
    chart.timeScale().fitContent()
  }
})
</script>
