<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'

export interface CandlestickData {
  x: Date
  y: [number, number, number, number] // [open, high, low, close]
}

export interface StockChartProps {
  data: CandlestickData[]
  title?: string
  height?: number | string
  showVolume?: boolean
  volumeData?: { x: Date, y: number }[]
}

const props = withDefaults(defineProps<StockChartProps>(), {
  title: 'Stock Chart',
  height: 400,
  showVolume: false
})

const colorMode = useColorMode()

const series = computed(() => {
  const chartSeries = [
    {
      name: 'candlestick',
      type: 'candlestick',
      data: props.data
    }
  ]

  if (props.showVolume && props.volumeData) {
    chartSeries.push({
      name: 'volume',
      type: 'bar',
      data: props.volumeData
    } as any)
  }

  return chartSeries
})

const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'candlestick',
      height: props.height,
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
    title: {
      text: props.title,
      align: 'left',
      style: {
        color: isDark ? '#f3f4f6' : '#111827'
      }
    },
    xaxis: {
      type: 'datetime',
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        }
      }
    },
    yaxis: [
      {
        tooltip: {
          enabled: true
        },
        labels: {
          style: {
            colors: isDark ? '#9ca3af' : '#6b7280'
          }
        }
      },
      ...(props.showVolume
        ? [
            {
              opposite: true,
              labels: {
                show: false
              }
            }
          ]
        : [])
    ],
    plotOptions: {
      candlestick: {
        colors: {
          upward: '#10b981', // green
          downward: '#ef4444' // red
        },
        wick: {
          useFillColor: true
        }
      },
      bar: {
        columnWidth: '80%'
      }
    },
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light'
    }
  }
})
</script>

<template>
  <UCard :ui="{ body: '!p-4' }">
    <ClientOnly>
      <apexchart
        :options="chartOptions"
        :series="series"
        :height="height"
      />
    </ClientOnly>
  </UCard>
</template>
