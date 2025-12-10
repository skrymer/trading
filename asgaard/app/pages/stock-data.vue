<template>
  <UDashboardPanel id="stock-data">
    <template #header>
      <UDashboardNavbar title="Stock Data Viewer" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <UInputMenu
            v-model="selectedSymbol"
            :items="stockSymbols"
            :placeholder="loadingSymbols ? 'Loading symbols...' : 'Type to search stocks...'"
            :disabled="loadingSymbols"
            :loading="loadingSymbols"
            icon="i-lucide-search"
            class="w-64"
          />
          <UButton
            v-if="selectedStock"
            icon="i-heroicons-arrow-path"
            :loading="loading"
            @click="refreshStock"
          >
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-12">
        <UIcon name="i-heroicons-arrow-path" class="animate-spin text-4xl" />
      </div>

      <!-- Stock Data Display -->
      <div v-else-if="selectedStock" class="space-y-4">
        <StockPriceChart
          v-if="selectedStock.quotes && selectedStock.quotes.length > 0"
          :quotes="selectedStock.quotes"
          :order-blocks="selectedStock.orderBlocks || []"
          :symbol="selectedStock.symbol"
          :signals="signalsData"
          :entry-strategy="selectedEntryStrategy"
        />

        <!-- Heatmap Charts -->
        <div v-if="selectedStock" class="space-y-4">
          <!-- Time Range Selector (shared by both charts) -->
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold">Heatmap Analysis</h3>
            <div class="flex items-center gap-2">
              <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
                Time Range:
              </label>
              <USelectMenu
                v-model="heatmapMonths"
                :items="[
                  { label: '3 Months', value: 3 },
                  { label: '6 Months', value: 6 },
                  { label: '1 Year', value: 12 },
                  { label: '2 Years', value: 24 },
                  { label: 'All Time', value: 1000 }
                ]"
                value-attribute="value"
                class="w-36"
              />
            </div>
          </div>

          <!-- Stock Heatmap Chart -->
          <div>
            <h4 class="text-md font-medium mb-2">Stock Heatmap (Fear & Greed)</h4>
            <ChartsBarChart
              v-if="heatmapChartSeries.length > 0"
              :series="heatmapChartSeries"
              :categories="heatmapChartCategories"
              :bar-colors="heatmapBarColors"
              :distributed="true"
              y-axis-label="Stock Heatmap"
              :height="300"
              :show-legend="false"
              :show-data-labels="false"
              :y-axis-max="100"
            />
          </div>

          <!-- Sector Heatmap Chart -->
          <div>
            <h4 class="text-md font-medium mb-2">
              Sector Heatmap (Fear & Greed)
              <span v-if="selectedStock.sectorSymbol" class="text-muted"> - {{ getSectorName(selectedStock.sectorSymbol) }}</span>
            </h4>
            <ChartsBarChart
              v-if="sectorHeatmapChartSeries.length > 0"
              :series="sectorHeatmapChartSeries"
              :categories="heatmapChartCategories"
              :bar-colors="sectorHeatmapBarColors"
              :distributed="true"
              y-axis-label="Sector Heatmap"
              :height="300"
              :show-legend="false"
              :show-data-labels="false"
              :y-axis-max="100"
            />
          </div>
        </div>

        <!-- Strategy Selection Row -->
        <div class="flex items-center gap-4 p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
          <div class="flex items-center gap-2 flex-1">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
              Entry Strategy:
            </label>
            <USelectMenu
              v-model="selectedEntryStrategy"
              :items="entryStrategies"
              :loading="loadingStrategies"
              placeholder="Select entry strategy"
              class="flex-1 max-w-xs"
            />
          </div>

          <div class="flex items-center gap-2 flex-1">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
              Exit Strategy:
            </label>
            <USelectMenu
              v-model="selectedExitStrategy"
              :items="exitStrategies"
              :loading="loadingStrategies"
              placeholder="Select exit strategy"
              class="flex-1 max-w-xs"
            />
          </div>

          <div class="flex items-center gap-3">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
              Cooldown Days:
            </label>
            <UInput
              v-model.number="cooldownDays"
              type="number"
              min="0"
              max="100"
              class="w-24"
              placeholder="0"
            />
          </div>

          <UButton
            icon="i-heroicons-chart-bar"
            :loading="loadingSignals"
            :disabled="!selectedEntryStrategy || !selectedExitStrategy"
            @click="fetchSignals"
          >
            Show Signals
          </UButton>
        </div>

        <!-- Strategy Signals Table -->
        <ChartsStrategySignalsTable
          :signals="signalsData"
          :entry-strategy="selectedEntryStrategy"
          :exit-strategy="selectedExitStrategy"
        />
      </div>

      <!-- No Selection State -->
      <div v-else class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-database" class="w-16 h-16 text-muted mb-4" />
        <h3 class="text-lg font-semibold mb-2">
          No Stock Selected
        </h3>
        <p class="text-muted text-center">
          Search for a stock symbol to view data
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

<script setup lang="ts">
import type { Stock, StockQuote, OrderBlock } from '~/types'
import { getSectorName } from '~/types/enums'

// Page meta
definePageMeta({
  layout: 'default'
})

// State
const selectedSymbol = ref<string>('')
const selectedStock = ref<Stock | null>(null)
const loading = ref(false)
const loadingSymbols = ref(true)
const loadingStrategies = ref(false)
const loadingSignals = ref(false)
const stockSymbols = ref<string[]>([])
const entryStrategies = ref<string[]>([])
const exitStrategies = ref<string[]>([])
const selectedEntryStrategy = ref<string>('')
const selectedExitStrategy = ref<string>('')
const cooldownDays = ref<number>(0)
const signalsData = ref<any>(null)
const heatmapMonths = ref<{ label: string; value: number }>({ label: '3 Months', value: 3 })

// Computed properties for heatmap charts (shared time range)
const filteredHeatmapQuotes = computed(() => {
  if (!selectedStock.value?.quotes) return []

  const quotes = selectedStock.value.quotes
  const today = new Date()
  const monthsAgo = new Date()
  monthsAgo.setMonth(today.getMonth() - heatmapMonths.value.value)

  return quotes.filter(q => {
    if (!q.date) return false
    const quoteDate = new Date(q.date)
    return quoteDate >= monthsAgo
  })
})

// Stock Heatmap
const heatmapChartSeries = computed(() => {
  if (filteredHeatmapQuotes.value.length === 0) return []

  return [{
    name: 'Stock Heatmap',
    data: filteredHeatmapQuotes.value.map(q => q.heatmap || 0)
  }]
})

const heatmapChartCategories = computed(() => {
  if (filteredHeatmapQuotes.value.length === 0) return []

  return filteredHeatmapQuotes.value.map(q => {
    const date = new Date(q.date)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  })
})

const heatmapBarColors = computed(() => {
  if (filteredHeatmapQuotes.value.length === 0) return []

  // Color bars based on heatmap value (0-100 scale)
  // Green if > 50, Red if <= 50
  return filteredHeatmapQuotes.value.map(q => {
    const heatmap = q.heatmap || 0
    return heatmap > 50 ? '#10b981' : '#ef4444' // Green : Red
  })
})

// Sector Heatmap
const sectorHeatmapChartSeries = computed(() => {
  if (filteredHeatmapQuotes.value.length === 0) return []

  return [{
    name: 'Sector Heatmap',
    data: filteredHeatmapQuotes.value.map(q => q.sectorHeatmap || 0)
  }]
})

const sectorHeatmapBarColors = computed(() => {
  if (filteredHeatmapQuotes.value.length === 0) return []

  // Color bars based on sector heatmap value (0-100 scale)
  // Green if > 50, Red if <= 50
  return filteredHeatmapQuotes.value.map(q => {
    const heatmap = q.sectorHeatmap || 0
    return heatmap > 50 ? '#10b981' : '#ef4444' // Green : Red
  })
})

// Methods
const fetchStockSymbols = async () => {
  loadingSymbols.value = true
  try {
    const data = await $fetch<string[]>('/udgaard/api/stocks')
    console.log('Fetched stock symbols:', data.length, 'symbols')
    stockSymbols.value = data
  } catch (error) {
    console.error('Failed to fetch stock symbols:', error)
    useToast().add({
      title: 'Error',
      description: 'Failed to load stock symbols',
      color: 'error'
    })
  } finally {
    loadingSymbols.value = false
  }
}

const fetchStrategies = async () => {
  loadingStrategies.value = true
  try {
    const data = await $fetch<any>('/udgaard/api/backtest/strategies')
    console.log('Fetched strategies:', data)
    entryStrategies.value = data.entryStrategies || []
    exitStrategies.value = data.exitStrategies || []

    // Set default strategies
    if (entryStrategies.value.includes('VegardPlanEtf')) {
      selectedEntryStrategy.value = 'VegardPlanEtf'
    }
    if (exitStrategies.value.includes('VegardPlanEtf')) {
      selectedExitStrategy.value = 'VegardPlanEtf'
    }
  } catch (error) {
    console.error('Failed to fetch strategies:', error)
    useToast().add({
      title: 'Error',
      description: 'Failed to load strategies',
      color: 'error'
    })
  } finally {
    loadingStrategies.value = false
  }
}

const fetchSignals = async () => {
  if (!selectedSymbol.value || !selectedEntryStrategy.value || !selectedExitStrategy.value) {
    return
  }

  loadingSignals.value = true
  try {
    const url = `/udgaard/api/stocks/${selectedSymbol.value}/signals?entryStrategy=${selectedEntryStrategy.value}&exitStrategy=${selectedExitStrategy.value}&cooldownDays=${cooldownDays.value}`
    const data = await $fetch(url)
    console.log('Fetched signals:', data)
    signalsData.value = data

    useToast().add({
      title: 'Success',
      description: 'Signals loaded successfully',
      color: 'success'
    })
  } catch (error) {
    console.error('Failed to fetch signals:', error)
    useToast().add({
      title: 'Error',
      description: 'Failed to load signals',
      color: 'error'
    })
    signalsData.value = null
  } finally {
    loadingSignals.value = false
  }
}

const fetchStockData = async (symbol: string, refresh = false) => {
  loading.value = true
  try {
    const url = `/udgaard/api/stocks/${symbol}${refresh ? '?refresh=true' : ''}`
    selectedStock.value = await $fetch(url)
    console.log('Fetched stock data for', symbol, ':', {
      quotes: selectedStock.value?.quotes?.length || 0,
      orderBlocks: selectedStock.value?.orderBlocks?.length || 0
    })
    if (selectedStock.value?.orderBlocks) {
      console.log('Order blocks:', selectedStock.value.orderBlocks)
    }
  } catch (error) {
    console.error('Failed to fetch stock data:', error)
    useToast().add({
      title: 'Error',
      description: `Failed to load data for ${symbol}`,
      color: 'error'
    })
    selectedStock.value = null
  } finally {
    loading.value = false
  }
}

const refreshStock = async () => {
  if (selectedSymbol.value) {
    // Force fetch the selected stock from Ovtlyr (refresh=true)
    await fetchStockData(selectedSymbol.value, true)
  }
}

// Watchers
watch(selectedSymbol, (newSymbol) => {
  if (newSymbol) {
    fetchStockData(newSymbol)
  } else {
    selectedStock.value = null
  }
})

// Lifecycle
onMounted(() => {
  fetchStockSymbols()
  fetchStrategies()
})

// Watch for symbol changes and clear signals
watch(selectedSymbol, () => {
  signalsData.value = null
})
</script>
