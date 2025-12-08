<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'

export interface BarChartSeries {
  name: string
  data: number[] | { x: string | number, y: number }[]
  color?: string
}

export interface BarChartProps {
  series: BarChartSeries[]
  categories?: string[]
  title?: string
  height?: number | string
  horizontal?: boolean
  stacked?: boolean
  showLegend?: boolean
  showDataLabels?: boolean
  yAxisLabel?: string
  xAxisLabel?: string
  barColors?: string[]
  distributed?: boolean
}

const props = withDefaults(defineProps<BarChartProps>(), {
  title: '',
  height: 350,
  horizontal: false,
  stacked: false,
  showLegend: true,
  showDataLabels: false,
  distributed: false
})

const emit = defineEmits<{
  barClick: [dataPointIndex: number, seriesIndex: number]
}>()

const colorMode = useColorMode()
const chartRef = ref<HTMLElement | null>(null)

// Handle clicks on the entire chart area
function handleChartClick(event: MouseEvent) {
  if (!chartRef.value || !props.categories || props.categories.length === 0) return

  const chartElement = chartRef.value.querySelector('.apexcharts-canvas')
  if (!chartElement) return

  const svgElement = chartElement.querySelector('svg')
  if (!svgElement) return

  // Get the click position relative to the chart
  const rect = svgElement.getBoundingClientRect()
  const x = event.clientX - rect.left

  // Find the plotArea (where the bars are drawn)
  const plotArea = svgElement.querySelector('.apexcharts-inner')
  if (!plotArea) return

  const plotRect = plotArea.getBoundingClientRect()
  const plotX = event.clientX - plotRect.left

  // Calculate which column was clicked based on x position
  const columnCount = props.categories.length
  const columnWidth = plotRect.width / columnCount
  const dataPointIndex = Math.floor(plotX / columnWidth)

  // Emit if valid index
  if (dataPointIndex >= 0 && dataPointIndex < columnCount) {
    emit('barClick', dataPointIndex, 0)
  }
}

const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  const xaxisConfig: any = {
    categories: props.categories,
    labels: {
      style: {
        colors: isDark ? '#9ca3af' : '#6b7280'
      }
    }
  }

  if (props.xAxisLabel) {
    xaxisConfig.title = {
      text: props.xAxisLabel,
      style: {
        color: isDark ? '#d1d5db' : '#6b7280'
      }
    }
  }

  const yaxisConfig: any = {
    labels: {
      style: {
        colors: isDark ? '#9ca3af' : '#6b7280'
      },
      formatter: (val: number) => val.toFixed(2)
    }
  }

  if (props.yAxisLabel) {
    yaxisConfig.title = {
      text: props.yAxisLabel,
      style: {
        color: isDark ? '#d1d5db' : '#6b7280'
      }
    }
  }

  return {
    chart: {
      type: 'bar',
      height: props.height,
      toolbar: {
        show: true
      },
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280',
      stacked: props.stacked,
      events: {
        dataPointSelection: (event: any, chartContext: any, config: any) => {
          emit('barClick', config.dataPointIndex, config.seriesIndex)
        },
        click: (event: any, chartContext: any, config: any) => {
          // Handle clicks anywhere on the chart area
          if (config.dataPointIndex !== undefined && config.dataPointIndex >= 0) {
            emit('barClick', config.dataPointIndex, config.seriesIndex || 0)
          }
        }
      }
    },
    title: props.title
      ? {
          text: props.title,
          align: 'left',
          style: {
            color: isDark ? '#f3f4f6' : '#111827'
          }
        }
      : undefined,
    plotOptions: {
      bar: {
        horizontal: props.horizontal,
        borderRadius: 4,
        distributed: props.distributed,
        dataLabels: {
          position: 'top'
        },
        columnWidth: '60%',
        barHeight: '100%'
      }
    },
    states: {
      hover: {
        filter: {
          type: 'none'
        }
      },
      active: {
        filter: {
          type: 'none'
        }
      }
    },
    dataLabels: {
      enabled: props.showDataLabels,
      style: {
        colors: [isDark ? '#f3f4f6' : '#111827']
      },
      formatter: (val: number) => val.toFixed(2)
    },
    legend: {
      show: props.showLegend,
      labels: {
        colors: isDark ? '#d1d5db' : '#6b7280'
      }
    },
    xaxis: xaxisConfig,
    yaxis: yaxisConfig,
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      y: {
        formatter: (val: number) => val.toFixed(2),
        title: {
          formatter: (seriesName: string) => seriesName + ' (click to view details)'
        }
      }
    },
    colors: props.barColors || (props.series.map(s => s.color).filter(Boolean) as string[])
  }
})
</script>

<template>
  <UCard :ui="{ body: '!p-4' }">
    <ClientOnly>
      <div
        ref="chartRef"
        class="cursor-pointer bar-chart-wrapper relative"
        @click="handleChartClick"
      >
        <apexchart
          type="bar"
          :options="chartOptions"
          :series="series"
          :height="height"
        />
      </div>
    </ClientOnly>
  </UCard>
</template>

<style scoped>
.bar-chart-wrapper {
  position: relative;
}

/* Only apply cursor to chart elements within this component */
.bar-chart-wrapper :deep(.apexcharts-bar-area) {
  cursor: pointer;
}

.bar-chart-wrapper :deep(.apexcharts-bar-series) {
  pointer-events: all;
}

.bar-chart-wrapper :deep(.apexcharts-series path) {
  cursor: pointer;
}

/* Disable default ApexCharts hover effect */
.bar-chart-wrapper :deep(.apexcharts-series[rel='1'] path:hover) {
  opacity: 1 !important;
  filter: none !important;
}
</style>
