<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'

export interface HistogramChartSeries {
  name: string
  type: 'column' | 'line'
  data: number[]
}

export interface HistogramChartProps {
  series: HistogramChartSeries[]
  categories?: string[]
  title?: string
  height?: number | string
  showLegend?: boolean
  showDataLabels?: boolean
  yAxisLabel?: string
  y2AxisLabel?: string
  xAxisLabel?: string
  colors?: string[]
}

const props = withDefaults(defineProps<HistogramChartProps>(), {
  title: '',
  height: 350,
  showLegend: true,
  showDataLabels: false
})

const colorMode = useColorMode()

const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'line',
      height: props.height,
      toolbar: {
        show: true
      },
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280'
    },
    stroke: {
      width: [0, 3],
      curve: 'smooth'
    },
    plotOptions: {
      bar: {
        columnWidth: '70%',
        borderRadius: 4
      }
    },
    dataLabels: {
      enabled: props.showDataLabels
    },
    legend: {
      show: props.showLegend,
      labels: {
        colors: isDark ? '#d1d5db' : '#6b7280'
      }
    },
    xaxis: {
      categories: props.categories || [],
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        }
      },
      ...(props.xAxisLabel && {
        title: {
          text: props.xAxisLabel,
          style: {
            color: isDark ? '#9ca3af' : '#6b7280'
          }
        }
      })
    },
    yaxis: [
      {
        labels: {
          style: {
            colors: isDark ? '#9ca3af' : '#6b7280'
          },
          formatter: (val: number) => {
            return val.toFixed(2)
          }
        },
        ...(props.yAxisLabel && {
          title: {
            text: props.yAxisLabel,
            style: {
              color: isDark ? '#9ca3af' : '#6b7280'
            }
          }
        })
      },
      {
        opposite: true,
        labels: {
          style: {
            colors: isDark ? '#9ca3af' : '#6b7280'
          },
          formatter: (val: number) => {
            return val.toFixed(0) + '%'
          }
        },
        min: 0,
        max: 100,
        ...(props.y2AxisLabel && {
          title: {
            text: props.y2AxisLabel,
            style: {
              color: isDark ? '#9ca3af' : '#6b7280'
            }
          }
        })
      }
    ],
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      shared: true,
      intersect: false,
      y: {
        formatter: (val: number, opts: any) => {
          // First series (column) - just the number
          if (opts.seriesIndex === 0) {
            return val.toString()
          }
          // Second series (line) - percentage
          return val.toFixed(1) + '%'
        }
      }
    },
    colors: props.colors || ['#3b82f6', '#10b981']
  }
})
</script>

<template>
  <UCard :ui="{ body: '!p-4' }">
    <ClientOnly>
      <apexchart
        type="line"
        :options="chartOptions"
        :series="series"
        :height="height"
      />
    </ClientOnly>
  </UCard>
</template>
