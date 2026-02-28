<template>
  <UDashboardPanel id="stock-data">
    <template #header>
      <UDashboardNavbar title="Stock Data Viewer" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <SymbolSearch
            v-model="selectedSymbol"
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

          <UButton
            icon="i-heroicons-funnel"
            color="success"
            variant="soft"
            :loading="loadingConditions"
            @click="showConditionModal = true"
          >
            Show Conditions
          </UButton>
        </div>

        <!-- Condition Signals Info Bar -->
        <div v-if="conditionSignalsData" class="flex items-center gap-4 p-3 bg-green-50 dark:bg-green-900/10 rounded-lg">
          <UIcon name="i-heroicons-funnel" class="text-green-600 dark:text-green-400 flex-shrink-0" />
          <div class="text-sm flex-1">
            <span class="font-medium">{{ conditionSignalsData.matchingQuotes }}</span> of
            <span class="font-medium">{{ conditionSignalsData.totalQuotes }}</span> quotes matched
            ({{ conditionSignalsData.conditionDescriptions.join(` ${conditionSignalsData.operator} `) }})
          </div>
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-heroicons-x-mark"
            @click="conditionSignalsData = null"
          >
            Clear
          </UButton>
        </div>

        <StockPriceChart
          v-if="selectedStock.quotes && selectedStock.quotes.length > 0"
          :quotes="selectedStock.quotes"
          :order-blocks="selectedStock.orderBlocks || []"
          :symbol="selectedStock.symbol"
          :signals="signalsData"
          :entry-strategy="selectedEntryStrategy"
          :condition-signals="conditionSignalsData"
        />

        <!-- Strategy Signals Table -->
        <ChartsStrategySignalsTable
          :signals="signalsData"
          :entry-strategy="selectedEntryStrategy"
          :exit-strategy="selectedExitStrategy"
        />

        <!-- Condition Signals Table -->
        <ConditionSignalsTable
          v-if="conditionSignalsData"
          :condition-signals="conditionSignalsData"
        />

        <!-- Time Range Selector -->
        <div v-if="selectedStock" class="flex items-center justify-end">
          <div class="flex items-center gap-2">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
              Time Range:
            </label>
            <USelectMenu
              v-model="heatmapMonths"
              :items="[
                { label: '1 Week', value: 0.25 },
                { label: '1 Month', value: 1 },
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

        <!-- Breadth Analysis Charts -->
        <div v-if="marketBreadthSeries.length > 0 || sectorBreadthSeries.length > 0" class="space-y-4">
          <h3 class="text-lg font-semibold">
            Breadth Analysis
          </h3>

          <!-- Market Breadth Chart -->
          <div v-if="marketBreadthSeries.length > 0">
            <h4 class="text-md font-medium mb-2">
              Market Breadth
            </h4>
            <ChartsLineChart
              :series="marketBreadthSeries"
              :categories="marketBreadthCategories"
              :line-colors="['#eab308', '#10b981', '#ef4444']"
              :height="400"
              :percent-mode="true"
            />
          </div>

          <!-- Sector Breadth Chart -->
          <div v-if="sectorBreadthSeries.length > 0 && selectedStock?.sectorSymbol">
            <h4 class="text-md font-medium mb-2">
              {{ selectedStock.sectorSymbol }} Sector Breadth
            </h4>
            <ChartsLineChart
              :series="sectorBreadthSeries"
              :categories="sectorBreadthCategories"
              :line-colors="['#eab308', '#10b981', '#ef4444']"
              :height="400"
              :percent-mode="true"
            />
          </div>
        </div>
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
      <!-- Condition Config Modal -->
      <ConditionConfigModal
        v-model:open="showConditionModal"
        @evaluate="evaluateConditions"
      />
    </template>
  </UDashboardPanel>
</template>

<script setup lang="ts">
import type { Stock, MarketBreadthDaily, SectorBreadthDaily, ConditionConfig, StockConditionSignals } from '~/types'

// Page meta
definePageMeta({
  layout: 'default'
})

// State
const selectedSymbol = ref<string>('')
const selectedStock = ref<Stock | null>(null)
const loading = ref(false)
const loadingStrategies = ref(false)
const loadingSignals = ref(false)
const entryStrategies = ref<string[]>([])
const exitStrategies = ref<string[]>([])
const selectedEntryStrategy = ref<string>('')
const selectedExitStrategy = ref<string>('')
const cooldownDays = ref<number>(0)
const signalsData = ref<any>(null)
const showConditionModal = ref(false)
const conditionSignalsData = ref<StockConditionSignals | null>(null)
const loadingConditions = ref(false)
const heatmapMonths = ref<{ label: string, value: number }>({ label: '3 Months', value: 3 })
const marketBreadthData = ref<MarketBreadthDaily[]>([])
const sectorBreadthData = ref<SectorBreadthDaily[]>([])

// Breadth chart computed properties
const cutoffDate = computed(() => {
  const today = new Date()
  const cutoff = new Date()
  const months = heatmapMonths.value.value
  const days = Math.round(months * 30)
  cutoff.setDate(today.getDate() - days)
  return cutoff
})

const filteredMarketBreadth = computed(() => {
  return marketBreadthData.value.filter((d) => {
    const quoteDate = new Date(d.quoteDate)
    return quoteDate >= cutoffDate.value
  })
})

const filteredSectorBreadth = computed(() => {
  return sectorBreadthData.value.filter((d) => {
    const quoteDate = new Date(d.quoteDate)
    return quoteDate >= cutoffDate.value
  })
})

const marketBreadthSeries = computed(() => {
  if (filteredMarketBreadth.value.length === 0) return []
  return [
    { name: 'Uptrend %', data: filteredMarketBreadth.value.map(d => d.breadthPercent) },
    { name: 'EMA 10', data: filteredMarketBreadth.value.map(d => d.ema10) },
    { name: 'EMA 20', data: filteredMarketBreadth.value.map(d => d.ema20) }
  ]
})

const marketBreadthCategories = computed(() => {
  return filteredMarketBreadth.value.map((d) => {
    const date = new Date(d.quoteDate)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  })
})

const sectorBreadthSeries = computed(() => {
  if (filteredSectorBreadth.value.length === 0) return []
  return [
    { name: 'Uptrend %', data: filteredSectorBreadth.value.map(d => d.bullPercentage) },
    { name: 'EMA 10', data: filteredSectorBreadth.value.map(d => d.ema10) },
    { name: 'EMA 20', data: filteredSectorBreadth.value.map(d => d.ema20) }
  ]
})

const sectorBreadthCategories = computed(() => {
  return filteredSectorBreadth.value.map((d) => {
    const date = new Date(d.quoteDate)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  })
})

// Methods
const fetchBreadthData = async () => {
  try {
    marketBreadthData.value = await $fetch<MarketBreadthDaily[]>('/udgaard/api/breadth/market-daily')
  } catch (error) {
    console.error('Failed to fetch market breadth:', error)
    marketBreadthData.value = []
  }

  if (selectedStock.value?.sectorSymbol) {
    try {
      sectorBreadthData.value = await $fetch<SectorBreadthDaily[]>(
        `/udgaard/api/breadth/sector-daily/${selectedStock.value.sectorSymbol}`
      )
    } catch (error) {
      console.error('Failed to fetch sector breadth:', error)
      sectorBreadthData.value = []
    }
  } else {
    sectorBreadthData.value = []
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
  conditionSignalsData.value = null
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

const evaluateConditions = async (conditions: ConditionConfig[], operator: 'AND' | 'OR') => {
  if (!selectedSymbol.value || conditions.length === 0) return
  loadingConditions.value = true
  try {
    const data = await $fetch<StockConditionSignals>(
      `/udgaard/api/stocks/${selectedSymbol.value}/condition-signals`,
      {
        method: 'POST',
        body: { conditions, operator }
      }
    )
    conditionSignalsData.value = data
    useToast().add({
      title: 'Conditions Evaluated',
      description: `${data.matchingQuotes} of ${data.totalQuotes} quotes matched`,
      color: 'success'
    })
  } catch (error) {
    console.error('Failed to evaluate conditions:', error)
    useToast().add({
      title: 'Error',
      description: 'Failed to evaluate conditions',
      color: 'error'
    })
    conditionSignalsData.value = null
  } finally {
    loadingConditions.value = false
  }
}

const fetchStockData = async (symbol: string, refresh = false) => {
  loading.value = true
  try {
    const params = new URLSearchParams()
    if (refresh) {
      params.append('refresh', 'true')
    }
    const url = `/udgaard/api/stocks/${symbol}${params.toString() ? `?${params.toString()}` : ''}`
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
    await fetchStockData(selectedSymbol.value, true)
  }
}

// Watchers
watch(selectedSymbol, async (newSymbol) => {
  if (newSymbol) {
    await fetchStockData(newSymbol)
    fetchBreadthData()
  } else {
    selectedStock.value = null
    marketBreadthData.value = []
    sectorBreadthData.value = []
  }
})

// Lifecycle
onMounted(() => {
  fetchStrategies()
})

// Watch for symbol changes and clear signals
watch(selectedSymbol, () => {
  signalsData.value = null
  conditionSignalsData.value = null
})
</script>
