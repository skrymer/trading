<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'

export interface ScatterChartSeries {
  name: string
  data: number[][] // Array of [x, y] pairs
}

export interface ScatterChartProps {
  series: ScatterChartSeries[]
  title?: string
  height?: number | string
  showLegend?: boolean
  xAxisLabel?: string
  yAxisLabel?: string
  colors?: string[]
}

const props = withDefaults(defineProps<ScatterChartProps>(), {
  title: '',
  height: 350,
  showLegend: true
})

const colorMode = useColorMode()

const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'scatter',
      height: props.height,
      toolbar: {
        show: true
      },
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280',
      zoom: {
        enabled: true,
        type: 'xy'
      }
    },
    markers: {
      size: 6,
      strokeWidth: 0,
      hover: {
        size: 8
      }
    },
    legend: {
      show: props.showLegend,
      position: 'top',
      labels: {
        colors: isDark ? '#d1d5db' : '#6b7280'
      }
    },
    xaxis: {
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        rotate: -45,
        rotateAlways: false,
        hideOverlappingLabels: true,
        maxHeight: 60
      },
      tickAmount: 8,
      ...(props.xAxisLabel && {
        title: {
          text: props.xAxisLabel,
          style: {
            color: isDark ? '#9ca3af' : '#6b7280'
          }
        }
      })
    },
    yaxis: {
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
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb',
      xaxis: {
        lines: {
          show: true
        }
      },
      yaxis: {
        lines: {
          show: true
        }
      }
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      x: {
        formatter: (val: number) => {
          return val.toFixed(2)
        }
      },
      y: {
        formatter: (val: number) => {
          return val.toFixed(2) + '%'
        }
      }
    },
    colors: props.colors || ['#10b981', '#ef4444', '#3b82f6', '#f59e0b']
  }
})
</script>

<template>
  <UCard :ui="{ body: '!p-4' }">
    <ClientOnly>
      <apexchart
        type="scatter"
        :options="chartOptions"
        :series="series"
        :height="height"
      />
    </ClientOnly>
  </UCard>
</template>
