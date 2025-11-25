<script setup lang="ts">
import type { ApexOptions } from 'apexcharts'
import type { EquityCurveData } from '~/types'
import { format } from 'date-fns'

const props = defineProps<{
  portfolioId: string
  loading?: boolean
}>()

const equityCurveData = ref<EquityCurveData | null>(null)
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const colorMode = useColorMode()

// Fetch equity curve data
async function fetchEquityCurve() {
  if (!props.portfolioId) return

  status.value = 'pending'
  try {
    const data = await $fetch<EquityCurveData>(`/udgaard/api/portfolio/${props.portfolioId}/equity-curve`)
    console.log('Equity curve data:', data)
    equityCurveData.value = data
    status.value = 'success'
  } catch (error) {
    console.error('Error fetching equity curve:', error)
    status.value = 'error'
  }
}

// Fetch on mount and when portfolioId changes
watch(() => props.portfolioId, () => {
  if (props.portfolioId) {
    fetchEquityCurve()
  }
}, { immediate: true })

// Chart series
const chartSeries = computed(() => {
  if (!equityCurveData.value?.dataPoints) return []

  return [{
    name: 'Return %',
    data: equityCurveData.value.dataPoints.map(point => point.returnPercentage)
  }]
})

// Chart categories (dates)
const chartCategories = computed(() => {
  if (!equityCurveData.value?.dataPoints) return []

  return equityCurveData.value.dataPoints.map(point =>
    format(new Date(point.date), 'MMM dd, yyyy')
  )
})

// Chart options
const chartOptions = computed<ApexOptions>(() => {
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'line',
      height: 400,
      toolbar: {
        show: true
      },
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280'
    },
    stroke: {
      curve: 'smooth',
      width: 3
    },
    dataLabels: {
      enabled: false
    },
    legend: {
      show: false
    },
    xaxis: {
      categories: chartCategories.value,
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        rotate: -45,
        rotateAlways: true
      }
    },
    yaxis: {
      labels: {
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        },
        formatter: (val: number) => {
          return val.toFixed(2) + '%'
        }
      },
      title: {
        text: 'Return %',
        style: {
          color: isDark ? '#9ca3af' : '#6b7280'
        }
      }
    },
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      y: {
        formatter: (val: number) => {
          return val.toFixed(2) + '%'
        }
      }
    },
    colors: ['#10b981'],
    annotations: {
      yaxis: [{
        y: 0,
        borderColor: isDark ? '#6b7280' : '#9ca3af',
        strokeDashArray: 4
      }]
    }
  }
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">
          Equity Curve
        </h3>
        <UButton
          icon="i-lucide-refresh-cw"
          size="sm"
          color="neutral"
          variant="ghost"
          :loading="status === 'pending'"
          @click="fetchEquityCurve"
        />
      </div>
    </template>

    <!-- Loading State -->
    <div v-if="status === 'pending' || loading" class="flex items-center justify-center h-96">
      <UIcon name="i-lucide-loader-2" class="w-8 h-8 text-primary animate-spin" />
    </div>

    <!-- Error State -->
    <div v-else-if="status === 'error'" class="flex flex-col items-center justify-center h-96">
      <UIcon name="i-lucide-alert-circle" class="w-12 h-12 text-red-500 mb-2" />
      <p class="text-muted">
        Failed to load equity curve
      </p>
    </div>

    <!-- Empty State -->
    <div v-else-if="!equityCurveData?.dataPoints || equityCurveData.dataPoints.length === 0" class="flex flex-col items-center justify-center h-96">
      <UIcon name="i-lucide-trending-up" class="w-12 h-12 text-muted mb-2" />
      <p class="text-muted">
        No data available
      </p>
    </div>

    <!-- Chart -->
    <ClientOnly v-else>
      <apexchart
        type="line"
        :options="chartOptions"
        :series="chartSeries"
        :height="400"
      />
    </ClientOnly>
  </UCard>
</template>
