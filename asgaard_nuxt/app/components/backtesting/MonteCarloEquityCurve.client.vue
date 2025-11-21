<script setup lang="ts">
import type { MonteCarloResult } from '@/types'
import VueApexCharts from 'vue3-apexcharts'

const props = defineProps<{
  result: MonteCarloResult
}>()

const series = computed(() => {
  const percentiles = props.result.percentileEquityCurves

  return [
    {
      name: '95th Percentile (Best)',
      data: percentiles.p95.map(p => p.cumulativeReturnPercentage)
    },
    {
      name: '75th Percentile',
      data: percentiles.p75.map(p => p.cumulativeReturnPercentage)
    },
    {
      name: '50th Percentile (Median)',
      data: percentiles.p50.map(p => p.cumulativeReturnPercentage)
    },
    {
      name: '25th Percentile',
      data: percentiles.p25.map(p => p.cumulativeReturnPercentage)
    },
    {
      name: '5th Percentile (Worst)',
      data: percentiles.p5.map(p => p.cumulativeReturnPercentage)
    }
  ]
})

const chartOptions = computed(() => ({
  chart: {
    type: 'line' as const,
    height: 400,
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
    animations: {
      enabled: true,
      easing: 'easeinout',
      speed: 800
    }
  },
  stroke: {
    curve: 'smooth' as const,
    width: [3, 2, 4, 2, 3]
  },
  colors: ['#22c55e', '#3b82f6', '#a855f7', '#fb923c', '#ef4444'],
  fill: {
    type: 'gradient',
    gradient: {
      shadeIntensity: 1,
      opacityFrom: 0.2,
      opacityTo: 0.1,
      stops: [0, 100]
    }
  },
  xaxis: {
    title: {
      text: 'Trade Number'
    },
    categories: props.result.percentileEquityCurves.p50.map((_, i) => i + 1),
    labels: {
      rotate: 0
    }
  },
  yaxis: {
    title: {
      text: 'Cumulative Return (%)'
    },
    labels: {
      formatter: (value: number) => `${value.toFixed(1)}%`
    }
  },
  tooltip: {
    shared: true,
    intersect: false,
    y: {
      formatter: (value: number) => `${value.toFixed(2)}%`
    }
  },
  legend: {
    position: 'bottom' as const,
    horizontalAlign: 'center' as const
  },
  grid: {
    borderColor: '#e5e7eb',
    strokeDashArray: 4
  }
}))
</script>

<template>
  <div>
    <h3 class="text-lg font-semibold mb-4">
      Equity Curve Distribution
    </h3>
    <UCard>
      <VueApexCharts
        type="line"
        height="400"
        :options="chartOptions"
        :series="series"
      />
      <template #footer>
        <UAlert color="info" variant="subtle" icon="i-lucide-info">
          <template #description>
            <p class="text-sm">
              This fan chart shows how equity curves evolve across different Monte Carlo scenarios.
              The median (purple) shows the typical path, while the colored bands show the range of possible outcomes.
            </p>
          </template>
        </UAlert>
      </template>
    </UCard>
  </div>
</template>
