<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'

export interface LineChartSeries {
  name: string
  data: number[]
}

export interface LineChartProps {
  series: LineChartSeries[]
  categories?: string[]
  title?: string
  height?: number | string
  showLegend?: boolean
  showDataLabels?: boolean
  yAxisLabel?: string
  xAxisLabel?: string
  lineColors?: string[]
  smooth?: boolean
  percentMode?: boolean
}

const props = withDefaults(defineProps<LineChartProps>(), {
  title: '',
  height: 350,
  showLegend: true,
  showDataLabels: false,
  smooth: true,
  percentMode: false
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
      curve: props.smooth ? 'smooth' : 'straight',
      width: 3
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
      }
    },
    yaxis: {
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        formatter: (val: number) => {
          if (props.percentMode) {
            return val.toFixed(0) + '%'
          }
          if (val >= 1000000) {
            return (val / 1000000).toFixed(1) + 'M'
          } else if (val >= 1000) {
            return (val / 1000).toFixed(1) + 'K'
          }
          return val.toFixed(0)
        }
      },
      ...(props.percentMode ? { min: 0, max: 100 } : {})
    },
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      y: {
        formatter: (val: number) => {
          if (props.percentMode) {
            return val.toFixed(1) + '%'
          }
          return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
          }).format(val)
        }
      }
    },
    colors: props.lineColors || ['#10b981']
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
