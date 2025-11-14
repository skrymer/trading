<script setup lang="ts">
import { format } from 'date-fns'
import type { MarketBreadth, Stock } from '~/types'
import { MarketSymbol, MarketSymbolDescriptions } from '~/types/enums'

// Fetch FULLSTOCK data for overall market metrics
const { data: fullstockData, pending, error } = await useFetch<MarketBreadth>('/udgaard/api/market-breadth', {
  params: { marketSymbol: MarketSymbol.FULLSTOCK }
})

// Fetch SPY stock data for heatmap comparison
const { data: spyData } = await useFetch<Stock>('/udgaard/api/stocks/SPY')

// Calculate current FULLSTOCK metrics
const fullstockMetrics = computed(() => {
  if (!fullstockData.value || !fullstockData.value.quotes || fullstockData.value.quotes.length === 0) {
    return null
  }

  const latestQuote = fullstockData.value.quotes[fullstockData.value.quotes.length - 1]
  if (!latestQuote) return null

  const previousQuote = fullstockData.value.quotes.length > 1
    ? fullstockData.value.quotes[fullstockData.value.quotes.length - 2]
    : null

  // Calculate bullish percentage from the number of stocks in uptrend
  const totalStocks = (latestQuote.numberOfStocksInUptrend ?? 0) +
                     (latestQuote.numberOfStocksInNeutral ?? 0) +
                     (latestQuote.numberOfStocksInDowntrend ?? 0)
  const bullishPercent = totalStocks > 0
    ? ((latestQuote.numberOfStocksInUptrend ?? 0) / totalStocks) * 100
    : 0

  const previousTotalStocks = previousQuote
    ? (previousQuote.numberOfStocksInUptrend ?? 0) +
      (previousQuote.numberOfStocksInNeutral ?? 0) +
      (previousQuote.numberOfStocksInDowntrend ?? 0)
    : totalStocks
  const previousBullishPercent = previousQuote && previousTotalStocks > 0
    ? ((previousQuote.numberOfStocksInUptrend ?? 0) / previousTotalStocks) * 100
    : bullishPercent

  const change = bullishPercent - previousBullishPercent

  // Get SPY heatmap for comparison
  const spyLatestQuote = spyData.value?.quotes && spyData.value.quotes.length > 0
    ? spyData.value.quotes[spyData.value.quotes.length - 1]
    : null
  const spyHeatmap = spyLatestQuote?.heatmap ?? 0

  return {
    symbol: fullstockData.value.symbol ?? '',
    name: fullstockData.value.name ?? '',
    bullishPercent,
    change,
    inUptrend: fullstockData.value.inUptrend ?? false,
    heatmap: spyHeatmap,
    previousHeatmap: fullstockData.value.previousHeatmap ?? 0,
    stocksInUptrend: latestQuote.numberOfStocksInUptrend ?? 0,
    stocksInDowntrend: latestQuote.numberOfStocksInDowntrend ?? 0,
    totalStocks: (latestQuote.numberOfStocksInUptrend ?? 0) + (latestQuote.numberOfStocksInDowntrend ?? 0),
    donchianUpper: latestQuote.donchianUpperBand ?? 0,
    donchianLower: latestQuote.donchianLowerBand ?? 0,
    ema10: latestQuote.ema_10 ?? 0
  }
})

// Fetch all sector symbols (excluding FULLSTOCK and UNK)
const sectorSymbols = Object.values(MarketSymbol).filter(
  symbol => symbol !== MarketSymbol.FULLSTOCK && symbol !== MarketSymbol.UNK
)

// Fetch all sectors for comparison
const sectorsData = ref<MarketBreadth[]>([])
const sectorsLoading = ref(true)

onMounted(async () => {
  try {
    const sectorPromises = sectorSymbols.map(symbol =>
      $fetch<MarketBreadth>('/udgaard/api/market-breadth', {
        params: { marketSymbol: symbol }
      })
    )
    sectorsData.value = await Promise.all(sectorPromises)
  } catch (error) {
    console.error('Failed to load sectors:', error)
  } finally {
    sectorsLoading.value = false
  }
})

// Calculate which sectors have higher/lower fear and greed than overall market (using SPY)
const sectorComparison = computed(() => {
  if (!spyData.value || sectorsData.value.length === 0) return []

  // Get SPY's heatmap for comparison
  const spyLatestQuote = spyData.value.quotes && spyData.value.quotes.length > 0
    ? spyData.value.quotes[spyData.value.quotes.length - 1]
    : null
  const overallHeatmap = spyLatestQuote?.heatmap ?? 0

  return sectorsData.value
    .map(sector => {
      // Use the latest quote for heatmap, second-to-last for bullish % if latest doesn't have it
      const latestQuote = sector.quotes && sector.quotes.length > 0
        ? sector.quotes[sector.quotes.length - 1]
        : null

      if (!latestQuote) return null

      // Calculate bullish percentage from the number of stocks in uptrend
      const totalStocks = (latestQuote.numberOfStocksInUptrend ?? 0) +
                         (latestQuote.numberOfStocksInNeutral ?? 0) +
                         (latestQuote.numberOfStocksInDowntrend ?? 0)
      const bullishPercent = totalStocks > 0
        ? ((latestQuote.numberOfStocksInUptrend ?? 0) / totalStocks) * 100
        : 0
      const sectorHeatmap = latestQuote.heatmap ?? 0
      const heatmapDiff = sectorHeatmap - overallHeatmap

      return {
        symbol: sector.symbol ?? '',
        name: sector.name ?? '',
        bullishPercent,
        heatmap: sectorHeatmap,
        inUptrend: sector.inUptrend ?? false,
        heatmapDiff,
        sentiment: heatmapDiff > 5 ? 'greedy' : heatmapDiff < -5 ? 'fearful' : 'neutral',
        stocksInUptrend: latestQuote.numberOfStocksInUptrend ?? 0,
        totalStocks: (latestQuote.numberOfStocksInUptrend ?? 0) + (latestQuote.numberOfStocksInDowntrend ?? 0)
      }
    })
    .filter(s => s !== null)
    .sort((a, b) => b!.heatmap - a!.heatmap)
})
</script>

<template>
  <UDashboardPanel id="market-breadth">
    <template #header>
      <UDashboardNavbar title="Market Breadth">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Loading State -->
      <div v-if="pending" class="space-y-4">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <UCard v-for="i in 4" :key="i">
            <USkeleton class="h-6 w-24 mb-2" />
            <USkeleton class="h-8 w-32" />
          </UCard>
        </div>
      </div>

      <!-- Error State -->
      <UCard v-else-if="error">
        <div class="text-center py-8">
          <UIcon name="i-lucide-alert-circle" class="w-12 h-12 text-error mx-auto mb-2" />
          <p class="text-error">Failed to load market breadth data</p>
        </div>
      </UCard>

      <!-- Main Content -->
      <div v-else-if="fullstockMetrics" class="space-y-6">
        <!-- FULLSTOCK Summary Cards -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <!-- Bullish Percentage -->
          <UCard>
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-muted">Overall Bullish %</p>
                <div class="flex items-baseline gap-2 mt-1">
                  <p class="text-2xl font-bold">{{ fullstockMetrics.bullishPercent.toFixed(1) }}%</p>
                  <span
                    class="text-sm font-medium"
                    :class="fullstockMetrics.change >= 0 ? 'text-green-600' : 'text-red-600'"
                  >
                    {{ fullstockMetrics.change >= 0 ? '+' : '' }}{{ fullstockMetrics.change.toFixed(1) }}%
                  </span>
                </div>
              </div>
              <UIcon
                :name="fullstockMetrics.bullishPercent > 50 ? 'i-lucide-trending-up' : 'i-lucide-trending-down'"
                :class="fullstockMetrics.bullishPercent > 50 ? 'text-green-500' : 'text-red-500'"
                class="w-8 h-8"
              />
            </div>
          </UCard>

          <!-- Market Trend -->
          <UCard>
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-muted">Market Trend</p>
                <div class="flex items-center gap-2 mt-1">
                  <UBadge
                    :color="fullstockMetrics.inUptrend ? 'success' : 'error'"
                    variant="subtle"
                    size="lg"
                  >
                    {{ fullstockMetrics.inUptrend ? 'Uptrend' : 'Downtrend' }}
                  </UBadge>
                </div>
              </div>
              <UIcon
                :name="fullstockMetrics.inUptrend ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down'"
                :class="fullstockMetrics.inUptrend ? 'text-green-500' : 'text-red-500'"
                class="w-8 h-8"
              />
            </div>
          </UCard>

          <!-- Heatmap (Fear & Greed) -->
          <UCard>
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-muted">Overall Heatmap</p>
                <div class="flex items-baseline gap-2 mt-1">
                  <p class="text-2xl font-bold">{{ fullstockMetrics.heatmap.toFixed(1) }}</p>
                  <span class="text-sm text-muted">
                    {{ fullstockMetrics.heatmap > 70 ? 'Greed' : fullstockMetrics.heatmap < 30 ? 'Fear' : 'Neutral' }}
                  </span>
                </div>
              </div>
              <div
                class="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold"
                :style="{
                  backgroundColor: `hsl(${fullstockMetrics.heatmap * 1.2}, 70%, 50%)`
                }"
              >
                {{ Math.round(fullstockMetrics.heatmap) }}
              </div>
            </div>
          </UCard>

          <!-- Stock Count -->
          <UCard>
            <div>
              <p class="text-sm text-muted">Total Stocks</p>
              <div class="mt-1">
                <p class="text-2xl font-bold">{{ fullstockMetrics.totalStocks }}</p>
                <div class="flex items-center gap-2 mt-1 text-sm">
                  <span class="text-green-600">↑ {{ fullstockMetrics.stocksInUptrend }}</span>
                  <span class="text-muted">•</span>
                  <span class="text-red-600">↓ {{ fullstockMetrics.stocksInDowntrend }}</span>
                </div>
              </div>
            </div>
          </UCard>
        </div>

        <!-- Sector Breakdown (Full Width) -->
        <UCard>
          <template #header>
            <h3 class="text-lg font-semibold">Sector Breakdown</h3>
            <p class="text-xs text-muted mt-1">vs. Overall Market Heatmap ({{ fullstockMetrics.heatmap.toFixed(1) }})</p>
          </template>

          <div v-if="sectorsLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            <UCard v-for="i in 12" :key="i">
              <USkeleton class="h-6 w-16 mb-2" />
              <USkeleton class="h-4 w-24" />
            </UCard>
          </div>

          <div v-else-if="sectorComparison.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            <UCard
              v-for="sector in sectorComparison"
              :key="sector.symbol"
              :ui="{ body: 'space-y-3' }"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="font-semibold text-base">{{ sector.symbol }}</span>
                  <UIcon
                    v-if="sector.sentiment === 'greedy'"
                    name="i-lucide-flame"
                    class="w-4 h-4 text-orange-500"
                    title="More greedy than market"
                  />
                  <UIcon
                    v-else-if="sector.sentiment === 'fearful'"
                    name="i-lucide-snowflake"
                    class="w-4 h-4 text-blue-500"
                    title="More fearful than market"
                  />
                </div>
                <UBadge
                  :color="sector.inUptrend ? 'success' : 'error'"
                  variant="subtle"
                  size="xs"
                >
                  {{ sector.inUptrend ? '▲' : '▼' }}
                </UBadge>
              </div>

              <div class="text-sm text-muted">{{ sector.name }}</div>

              <div class="flex items-center gap-2 text-sm">
                <span class="font-medium">{{ sector.bullishPercent.toFixed(1) }}%</span>
                <span class="text-muted">Bullish</span>
              </div>

              <div class="flex items-center gap-2">
                <div class="flex-1">
                  <div class="flex items-center justify-between text-xs mb-1">
                    <span class="text-muted">Heatmap</span>
                    <span class="font-medium">{{ sector.heatmap.toFixed(1) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <div class="flex-1 bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                      <div
                        class="h-2 rounded-full transition-all"
                        :class="sector.heatmapDiff > 0 ? 'bg-orange-500' : 'bg-blue-500'"
                        :style="{ width: `${Math.min(Math.abs(sector.heatmapDiff), 100)}%` }"
                      />
                    </div>
                    <span
                      class="text-xs font-medium min-w-[3rem] text-right"
                      :class="sector.heatmapDiff > 0 ? 'text-orange-600' : 'text-blue-600'"
                    >
                      {{ sector.heatmapDiff > 0 ? '+' : '' }}{{ sector.heatmapDiff.toFixed(1) }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="flex items-center gap-2 text-xs text-muted pt-2 border-t border-gray-200 dark:border-gray-700">
                <span class="text-green-600">↑ {{ sector.stocksInUptrend }}</span>
                <span>•</span>
                <span>{{ sector.totalStocks }} stocks</span>
              </div>
            </UCard>
          </div>

          <div v-else class="text-center py-8 text-sm text-muted">
            No sector data available
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>
</template>
