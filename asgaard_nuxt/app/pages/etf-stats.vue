<script setup lang="ts">
import { format } from 'date-fns'
import type { EtfStatsResponse } from '~/types'
import { Etf, EtfDescriptions } from '~/types/enums'

// Selected ETF (no default - wait for user selection)
const selectedEtf = ref<Etf | undefined>(undefined)
const cacheBuster = ref(0)
const shouldRefresh = ref(false)

// ETF options
const etfOptions = Object.values(Etf).map(etf => ({
  value: etf,
  label: `${etf} - ${EtfDescriptions[etf]}`
}))

// Fetch selected ETF data only when an ETF is selected
// Note: refresh=false means it will only fetch missing stocks, not all stocks
const etfData = ref<EtfStatsResponse | null>(null)
const pending = ref(false)
const error = ref<Error | null>(null)

async function execute() {
  if (!selectedEtf.value) return

  pending.value = true
  error.value = null

  try {
    const url = `/udgaard/api/etf/${selectedEtf.value}/stats`
    const query = new URLSearchParams({
      refresh: String(shouldRefresh.value),
      _t: String(cacheBuster.value)
    })
    etfData.value = await $fetch<EtfStatsResponse>(`${url}?${query}`)
  } catch (e) {
    error.value = e instanceof Error ? e : new Error('Failed to fetch ETF data')
  } finally {
    pending.value = false
  }
}

// Watch for ETF selection changes and fetch data
watch(selectedEtf, (newValue) => {
  if (newValue) {
    shouldRefresh.value = false // Don't refresh on initial load
    cacheBuster.value = Date.now()
    execute()
  }
})

// Reset refresh flag after fetch completes
watch(pending, (isLoading) => {
  if (!isLoading && shouldRefresh.value) {
    shouldRefresh.value = false
  }
})

// Refresh function - fetches missing stocks from the API
// When refresh=true in the backend:
//   1. Gets existing stocks from database
//   2. Identifies missing stocks (e.g., 4 out of 102)
//   3. Fetches ONLY the missing 4 stocks from API
//   4. Returns existing + newly fetched
const refreshData = () => {
  shouldRefresh.value = true // Tell backend to fetch missing stocks
  cacheBuster.value = Date.now() // Bust cache to force new fetch
  execute()
}

// Calculate current ETF metrics
const currentMetrics = computed(() => {
  if (!etfData.value || !etfData.value.currentStats) {
    return null
  }

  const stats = etfData.value.currentStats

  return {
    symbol: etfData.value.symbol,
    name: etfData.value.name,
    bullishPercent: stats.bullishPercent,
    change: stats.change,
    inUptrend: stats.inUptrend,
    stocksInUptrend: stats.stocksInUptrend,
    stocksInDowntrend: stats.stocksInDowntrend,
    stocksInNeutral: stats.stocksInNeutral,
    totalStocks: stats.totalStocks,
    lastUpdated: stats.lastUpdated ? new Date(stats.lastUpdated) : null
  }
})

// Prepare chart data - bullish percentage over time
const chartData = computed(() => {
  if (!etfData.value || !etfData.value.historicalData || etfData.value.historicalData.length === 0) {
    return null
  }

  const dates = etfData.value.historicalData.map((point: { date: string, bullishPercent: number }) => format(new Date(point.date), 'MMM d, yyyy'))
  const bullishPercentages = etfData.value.historicalData.map((point: { date: string, bullishPercent: number }) => point.bullishPercent)

  return {
    dates,
    bullishPercentages
  }
})

// Chart options for ApexCharts
const chartOptions = computed(() => ({
  chart: {
    type: 'area' as const,
    height: 400,
    toolbar: {
      show: true
    },
    zoom: {
      enabled: true
    }
  },
  stroke: {
    curve: 'smooth',
    width: 3
  },
  fill: {
    type: 'gradient',
    gradient: {
      shadeIntensity: 1,
      opacityFrom: 0.7,
      opacityTo: 0.3,
      stops: [0, 90, 100]
    }
  },
  colors: ['#3b82f6'],
  xaxis: {
    categories: chartData.value?.dates ?? [],
    labels: {
      rotate: -45,
      rotateAlways: false,
      style: {
        fontSize: '11px'
      }
    }
  },
  yaxis: {
    title: {
      text: 'Bullish Percentage (%)'
    },
    min: 0,
    max: 100,
    labels: {
      formatter: (value: number) => `${value.toFixed(0)}%`
    }
  },
  dataLabels: {
    enabled: false
  },
  tooltip: {
    y: {
      formatter: (value: number) => `${value.toFixed(1)}%`
    }
  },
  grid: {
    borderColor: '#e5e7eb',
    strokeDashArray: 4
  }
}))

const chartSeries = computed(() => [
  {
    name: 'Bullish %',
    data: chartData.value?.bullishPercentages ?? []
  }
])
</script>

<template>
  <UDashboardPanel id="etf-stats">
    <template #header>
      <UDashboardNavbar title="ETF Statistics">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6">
        <!-- ETF Selector -->
        <UCard>
          <div class="flex items-center gap-4">
            <label class="text-sm font-medium">Select ETF:</label>
            <USelectMenu
              v-model="selectedEtf"
              :items="etfOptions"
              value-key="value"
              placeholder="Choose an ETF..."
              size="lg"
              class="w-80"
            />
            <UButton
              icon="i-lucide-refresh-cw"
              size="sm"
              variant="soft"
              :loading="pending"
              :disabled="!selectedEtf"
              @click="refreshData()"
            >
              Refresh
            </UButton>
          </div>
        </UCard>

        <!-- Empty State - No ETF Selected -->
        <UCard v-if="!selectedEtf">
          <div class="text-center py-12">
            <UIcon name="i-lucide-pie-chart" class="w-16 h-16 text-muted mx-auto mb-4" />
            <h3 class="text-lg font-semibold mb-2">
              Select an ETF to view statistics
            </h3>
            <p class="text-sm text-muted">
              Choose an ETF from the dropdown above to see its performance metrics and trends
            </p>
          </div>
        </UCard>

        <!-- Warning Alert -->
        <UAlert
          v-else-if="etfData?.warning"
          icon="i-lucide-alert-triangle"
          color="warning"
          variant="soft"
          :title="`Data Incomplete (${etfData?.actualStockCount}/${etfData?.expectedStockCount} stocks)`"
          :description="etfData?.warning ?? ''"
          :close-button="{ icon: 'i-lucide-x', color: 'neutral', variant: 'link' }"
        >
          <template #actions>
            <UButton
              color="warning"
              variant="solid"
              size="sm"
              icon="i-lucide-refresh-cw"
              :loading="pending"
              @click="refreshData()"
            >
              Refresh Data
            </UButton>
          </template>
        </UAlert>

        <!-- Loading State -->
        <div v-else-if="pending" class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <UCard v-for="i in 3" :key="i">
              <USkeleton class="h-6 w-24 mb-2" />
              <USkeleton class="h-8 w-32" />
            </UCard>
          </div>
        </div>

        <!-- Error State -->
        <UCard v-else-if="error">
          <div class="text-center py-8">
            <UIcon name="i-lucide-alert-circle" class="w-12 h-12 text-error mx-auto mb-2" />
            <p class="text-error">
              Failed to load ETF data
            </p>
          </div>
        </UCard>

        <!-- Main Content -->
        <div v-else-if="currentMetrics" class="space-y-6">
          <!-- Summary Cards -->
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <!-- Bullish Percentage -->
            <UCard>
              <div class="flex items-start justify-between">
                <div>
                  <p class="text-sm text-muted">
                    Bullish Percentage
                  </p>
                  <div class="flex items-baseline gap-2 mt-1">
                    <p class="text-2xl font-bold">
                      {{ currentMetrics.bullishPercent.toFixed(1) }}%
                    </p>
                    <span
                      class="text-sm font-medium"
                      :class="currentMetrics.change >= 0 ? 'text-green-600' : 'text-red-600'"
                    >
                      {{ currentMetrics.change >= 0 ? '+' : '' }}{{ currentMetrics.change.toFixed(1) }}%
                    </span>
                  </div>
                </div>
                <UIcon
                  :name="currentMetrics.bullishPercent > 50 ? 'i-lucide-trending-up' : 'i-lucide-trending-down'"
                  :class="currentMetrics.bullishPercent > 50 ? 'text-green-500' : 'text-red-500'"
                  class="w-8 h-8"
                />
              </div>
            </UCard>

            <!-- Trend Status -->
            <UCard>
              <div class="flex items-start justify-between">
                <div>
                  <p class="text-sm text-muted">
                    Trend Status
                  </p>
                  <div class="flex items-center gap-2 mt-1">
                    <UBadge
                      :color="currentMetrics.inUptrend ? 'success' : 'error'"
                      variant="subtle"
                      size="lg"
                    >
                      {{ currentMetrics.inUptrend ? 'Uptrend' : 'Downtrend' }}
                    </UBadge>
                  </div>
                </div>
                <UIcon
                  :name="currentMetrics.inUptrend ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down'"
                  :class="currentMetrics.inUptrend ? 'text-green-500' : 'text-red-500'"
                  class="w-8 h-8"
                />
              </div>
            </UCard>

            <!-- Stock Count -->
            <UCard>
              <div>
                <p class="text-sm text-muted">
                  Total Stocks
                </p>
                <div class="mt-1">
                  <p class="text-2xl font-bold">
                    {{ currentMetrics.totalStocks }}
                  </p>
                  <div class="flex items-center gap-2 mt-1 text-sm">
                    <span class="text-green-600">↑ {{ currentMetrics.stocksInUptrend }}</span>
                    <span class="text-muted">•</span>
                    <span class="text-gray-600">→ {{ currentMetrics.stocksInNeutral }}</span>
                    <span class="text-muted">•</span>
                    <span class="text-red-600">↓ {{ currentMetrics.stocksInDowntrend }}</span>
                  </div>
                </div>
              </div>
            </UCard>
          </div>

          <!-- Bullish Percentage Chart -->
          <UCard>
            <template #header>
              <h3 class="text-lg font-semibold">
                Stocks in Uptrend Over Time
              </h3>
              <p class="text-xs text-muted mt-1">
                Percentage of stocks where 10 EMA > 20 EMA and Close > 50 EMA
              </p>
            </template>
            <ClientOnly>
              <apexchart
                v-if="chartSeries && chartOptions"
                type="area"
                height="400"
                :options="chartOptions"
                :series="chartSeries"
              />
              <template #fallback>
                <div class="h-[400px] flex items-center justify-center">
                  <USkeleton class="h-full w-full" />
                </div>
              </template>
            </ClientOnly>
          </UCard>

          <!-- Last Updated -->
          <div v-if="currentMetrics.lastUpdated" class="text-center text-sm text-muted">
            Last updated: {{ format(currentMetrics.lastUpdated, 'PPpp') }}
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
