<template>
  <div class="container mx-auto p-6">
    <UCard>
      <template #header>
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
      </template>

      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-12">
        <UIcon name="i-heroicons-arrow-path" class="animate-spin text-4xl" />
      </div>

      <!-- Stock Data Display -->
      <div v-else-if="selectedStock">
        <StockPriceChart
          v-if="selectedStock.quotes && selectedStock.quotes.length > 0"
          :quotes="selectedStock.quotes"
          :order-blocks="selectedStock.orderBlocks || []"
          :symbol="selectedStock.symbol"
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
const stockSymbols = ref<string[]>([])

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
})
</script>
