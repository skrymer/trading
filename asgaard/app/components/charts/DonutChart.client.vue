<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'

export interface DonutChartProps {
  series: number[]
  labels?: string[]
  title?: string
  height?: number | string
  showLegend?: boolean
  showDataLabels?: boolean
  colors?: string[]
}

const props = withDefaults(defineProps<DonutChartProps>(), {
  title: '',
  height: 350,
  showLegend: true,
  showDataLabels: true
})

const colorMode = useColorMode()

const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'donut',
      height: props.height,
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280'
    },
    labels: props.labels || [],
    dataLabels: {
      enabled: props.showDataLabels,
      style: {
        colors: [isDark ? '#1f2937' : '#ffffff']
      }
    },
    legend: {
      show: props.showLegend,
      position: 'bottom',
      labels: {
        colors: isDark ? '#d1d5db' : '#6b7280'
      }
    },
    colors: props.colors || ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316'],
    plotOptions: {
      pie: {
        donut: {
          size: '65%',
          labels: {
            show: true,
            total: {
              show: true,
              label: 'Total',
              color: isDark ? '#9ca3af' : '#6b7280',
              formatter: () => {
                return props.series.reduce((a, b) => a + b, 0).toString()
              }
            }
          }
        }
      }
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      y: {
        formatter: (val: number) => {
          const total = props.series.reduce((a, b) => a + b, 0)
          const percentage = ((val / total) * 100).toFixed(1)
          return `${val} (${percentage}%)`
        }
      }
    },
    responsive: [{
      breakpoint: 480,
      options: {
        chart: {
          width: 300
        },
        legend: {
          position: 'bottom'
        }
      }
    }]
  }
})
</script>

<template>
  <UCard :ui="{ body: '!p-4' }">
    <ClientOnly>
      <apexchart
        type="donut"
        :options="chartOptions"
        :series="series"
        :height="height"
      />
    </ClientOnly>
  </UCard>
</template>
