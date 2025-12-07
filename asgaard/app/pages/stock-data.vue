<template>
  <div class="container mx-auto p-6 flex flex-col min-h-[calc(100vh-4rem)]">
    <UCard class="flex flex-col flex-1">
      <template #header>
        <div class="flex flex-col gap-4">
          <!-- Top Row: Title and Symbol Search -->
          <div class="flex items-center justify-between gap-4">
            <h1 class="text-2xl font-bold">
              Stock Data Viewer
            </h1>
            <div class="flex items-center gap-3 flex-1 max-w-md">
              <UInputMenu
                v-model="selectedSymbol"
                :items="stockSymbols"
                :placeholder="loadingSymbols ? 'Loading symbols...' : 'Type to search stocks...'"
                :disabled="loadingSymbols"
                :loading="loadingSymbols"
                icon="i-lucide-search"
                class="flex-1"
              />
              <UButton
                v-if="selectedStock"
                icon="i-heroicons-arrow-path"
                :loading="loading"
                @click="refreshStock"
              >
                Refresh
              </UButton>
            </div>
          </div>

          <!-- Strategy Selection Row -->
          <div v-if="selectedStock" class="flex items-center gap-4 p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
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
        </div>
      </template>

      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-12">
        <UIcon name="i-heroicons-arrow-path" class="animate-spin text-4xl" />
      </div>

      <!-- Stock Data Display -->
      <div v-else-if="selectedStock" class="flex flex-col flex-1 min-h-0">
        <StockPriceChart
          v-if="selectedStock.quotes && selectedStock.quotes.length > 0"
          :quotes="selectedStock.quotes"
          :order-blocks="selectedStock.orderBlocks || []"
          :symbol="selectedStock.symbol"
          :signals="signalsData"
          :entry-strategy="selectedEntryStrategy"
        />
      </div>

      <!-- No Selection State -->
      <div v-else class="text-center py-12 text-gray-500">
        Select a stock symbol to view data
      </div>
    </UCard>
  </div>
</template>

<script setup lang="ts">
import type { Stock, StockQuote, OrderBlock } from '~/types'

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
