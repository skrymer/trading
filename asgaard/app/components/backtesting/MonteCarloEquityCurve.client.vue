<script setup lang="ts">
import type { MonteCarloResult } from '@/types'
import type { ApexOptions } from 'apexcharts'

const props = defineProps<{
  result: MonteCarloResult
}>()

// Check if we have valid data
const hasValidData = computed(() => {
  const curves = props.result?.percentileEquityCurves
  if (!curves) {
    console.error('percentileEquityCurves is undefined')
    return false
  }
  if (!curves.p5 || !curves.p25 || !curves.p50 || !curves.p75 || !curves.p95) {
    console.error('One or more percentile arrays are undefined', curves)
    return false
  }
  if (curves.p50.length === 0) {
    console.error('p50 array is empty')
    return false
  }
  return true
})

const series = computed(() => {
  if (!hasValidData.value) return []

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

const chartOptions = computed<ApexOptions>(() => {
  if (!hasValidData.value) return {}

  const categories = props.result.percentileEquityCurves.p50.map((_, i) => i + 1)

  const options: ApexOptions = {
    chart: {
      type: 'line' as const,
      height: 400,
      toolbar: {
        show: true
      },
      animations: {
        enabled: true
      }
    },
    stroke: {
      curve: 'smooth' as const,
      width: [3, 2, 4, 2, 3]
    },
    colors: ['#22c55e', '#3b82f6', '#a855f7', '#fb923c', '#ef4444'],
    dataLabels: {
      enabled: false
    },
    xaxis: {
      type: 'numeric' as const,
      title: {
        text: 'Trade Number'
      },
      categories: categories,
      tickAmount: 10
    },
    yaxis: {
      title: {
        text: 'Cumulative Return (%)'
      },
      labels: {
        formatter: function (value: number) {
          return value ? `${value.toFixed(1)}%` : '0%'
        }
      }
    },
    tooltip: {
      shared: true,
      intersect: false,
      y: {
        formatter: function (value: number) {
          return value ? `${value.toFixed(2)}%` : '0%'
        }
      }
    },
    legend: {
      position: 'bottom' as const,
      horizontalAlign: 'center' as const,
      show: true
    },
    grid: {
      show: true,
      borderColor: '#e5e7eb'
    }
  }

  return options
})
</script>

<template>
  <div>
    <h3 class="text-lg font-semibold mb-4">
      Equity Curve Distribution
    </h3>
    <UCard>
      <div v-if="!hasValidData">
        <UAlert color="warning" variant="subtle" icon="i-lucide-alert-circle">
          <template #description>
            <p class="text-sm">
              No equity curve data available. Check the browser console for details.
            </p>
          </template>
        </UAlert>
      </div>
      <div v-else class="w-full" style="min-height: 400px;">
        <ClientOnly>
          <apexchart
            type="line"
            :options="chartOptions"
            :series="series"
            height="400"
          />
        </ClientOnly>
      </div>
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
