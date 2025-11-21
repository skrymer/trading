<script setup lang="ts">
import { format, sub } from 'date-fns'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Range, Trade, BacktestRequest, MonteCarloResult, BacktestReport } from '~/types'
import { MonteCarloTechnique, MonteCarloTechniqueDescriptions } from '~/types/enums'

const allTrades = ref<Trade[]>([])
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const isConfigModalOpen = ref(false)

// Monte Carlo state
const monteCarloResult = ref<MonteCarloResult | null>(null)
const monteCarloStatus = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const showMonteCarloResults = ref(false)
const selectedTechnique = ref<MonteCarloTechnique>(MonteCarloTechnique.TRADE_SHUFFLING)

function openConfigModal() {
  isConfigModalOpen.value = true
}

const toast = useToast()

async function runBacktest(config: BacktestRequest) {
  status.value = 'pending'
  allTrades.value = []
  monteCarloResult.value = null
  monteCarloStatus.value = 'idle'
  showMonteCarloResults.value = false

  // Estimate backtest size
  const stockCount = config.stockSymbols?.length || 'all'
  toast.add({
    title: 'Backtest Started',
    description: `Running backtest on ${stockCount} stocks. This may take several minutes...`,
    icon: 'i-lucide-play-circle',
    color: 'primary'
  })

  const startTime = Date.now()

  try {
    // Use POST endpoint for dynamic strategy support
    // Increased timeout for large backtests (10 minutes)
    const report = await $fetch<{ trades: Trade[] }>('/udgaard/api/backtest', {
      method: 'POST',
      body: config,
      timeout: 600000 // 10 minutes in milliseconds
    })
    allTrades.value = report.trades
    status.value = 'success'
  } catch (error) {
    console.error('Error fetching trades:', error)
    status.value = 'error'

    toast.add({
      title: 'Backtest Failed',
      description: error instanceof Error ? error.message : 'Failed to complete backtest. Please try again.',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

async function runMonteCarloSimulation() {
  if (!allTrades.value || allTrades.value.length === 0) {
    toast.add({
      title: 'No Backtest Data',
      description: 'Please run a backtest first before running Monte Carlo simulation',
      icon: 'i-lucide-alert-circle',
      color: 'warning'
    })
    return
  }

  monteCarloStatus.value = 'pending'
  showMonteCarloResults.value = true

  toast.add({
    title: 'Monte Carlo Simulation Started',
    description: `Running ${MonteCarloTechniqueDescriptions[selectedTechnique.value].name} with 10,000 iterations...`,
    icon: 'i-lucide-chart-scatter',
    color: 'primary'
  })

  const startTime = Date.now()

  try {
    // Send only trades - backend will construct BacktestReport
    const result = await $fetch<MonteCarloResult>('/udgaard/api/monte-carlo/simulate', {
      method: 'POST',
      body: {
        trades: allTrades.value,
        technique: selectedTechnique.value,
        iterations: 10000,
        includeAllEquityCurves: false
      },
      timeout: 300000 // 5 minutes
    })

    monteCarloResult.value = result
    monteCarloStatus.value = 'success'
  } catch (error) {
    console.error('Error running Monte Carlo simulation:', error)
    monteCarloStatus.value = 'error'

    toast.add({
      title: 'Monte Carlo Simulation Failed',
      description: error instanceof Error ? error.message : 'Failed to complete simulation. Please try again.',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

const items = [[{
  label: 'New backtest',
  icon: 'i-lucide-play',
  onSelect: openConfigModal
}]] satisfies DropdownMenuItem[][]

// Modal state
const isModalOpen = ref(false)
const selectedDateTrades = ref<{ date: string, trades: Trade[] } | null>(null)

function handleBarClick(dataPointIndex: number) {
  const dateGroup = tradesGroupedByDate.value[dataPointIndex]
  if (dateGroup) {
    selectedDateTrades.value = {
      date: format(new Date(dateGroup.date), 'MMM dd, yyyy'),
      trades: dateGroup.trades
    }
    isModalOpen.value = true
  }
}

// Group trades by startDate
const tradesGroupedByDate = computed(() => {
  if (!allTrades.value) return []

  const grouped = new Map<string, Trade[]>()

  allTrades.value.forEach((trade) => {
    const dateKey = format(new Date(trade.startDate), 'yyyy-MM-dd')
    if (!grouped.has(dateKey)) {
      grouped.set(dateKey, [])
    }
    grouped.get(dateKey)!.push(trade)
  })

  return Array.from(grouped.entries())
    .sort(([dateA], [dateB]) => dateA.localeCompare(dateB))
    .map(([date, trades]) => {
      const totalProfit = trades.reduce((sum, trade) => sum + trade.profitPercentage, 0)
      return {
        date,
        count: trades.length,
        totalProfit,
        trades
      }
    })
})

// Prepare data for bar chart
const chartSeries = computed(() => {
  return [{
    name: 'Total Profit %',
    data: tradesGroupedByDate.value.map(item => item.totalProfit)
  }]
})

const chartCategories = computed(() => {
  return tradesGroupedByDate.value.map(item =>
    format(new Date(item.date), 'MMM dd, yyyy')
  )
})

// Generate colors based on profit (red for negative, green for positive)
const chartColors = computed(() => {
  return tradesGroupedByDate.value.map(item =>
    item.totalProfit < 0 ? '#ef4444' : '#10b981'
  )
})
</script>

<template>
  <UDashboardPanel id="backtesting">
    <template #header>
      <UDashboardNavbar title="Backtesting" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <UDropdownMenu :items="items">
            <UButton icon="i-lucide-plus" size="md" class="rounded-full" />
          </UDropdownMenu>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Empty State -->
      <div v-if="status === 'idle'" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-bar-chart-3" class="w-16 h-16 text-muted mb-4" />
        <h3 class="text-lg font-semibold mb-2">
          No Backtest Results
        </h3>
        <p class="text-muted text-center mb-4">
          Click "New Backtest" to run a backtest and see the results
        </p>
        <UButton
          label="New Backtest"
          icon="i-lucide-play"
          @click="openConfigModal"
        />
      </div>

      <!-- Enhanced Loading State -->
      <div v-else-if="status === 'pending'" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-loader-2" class="w-16 h-16 text-primary mb-4 animate-spin" />
        <h3 class="text-lg font-semibold mb-2">
          Running Backtest...
        </h3>
        <p class="text-muted text-center mb-2">
          This may take several minutes depending on the number of stocks and date range
        </p>
        <UCard class="mt-4 max-w-md">
          <div class="space-y-2 text-sm text-muted">
            <p class="flex items-center gap-2">
              <UIcon name="i-lucide-clock" class="w-4 h-4" />
              Processing historical data and executing strategy
            </p>
            <p class="flex items-center gap-2">
              <UIcon name="i-lucide-database" class="w-4 h-4" />
              Fetching stock quotes and market data
            </p>
            <p class="flex items-center gap-2">
              <UIcon name="i-lucide-trending-up" class="w-4 h-4" />
              Calculating trade entries and exits
            </p>
          </div>
        </UCard>
        <p class="text-xs text-muted mt-4">
          Please do not close this page
        </p>
      </div>

      <!-- Loading & Results -->
      <div v-else class="grid gap-4 lg:grid-cols-1">
        <!-- Cards with loading state -->
        <BacktestingCards :trades="allTrades" :loading="false" />

        <!-- Chart loading skeleton - not shown since we're in v-else after 'pending' check -->
        <UCard v-if="false">
          <template #header>
            <USkeleton class="h-6 w-48" />
          </template>
          <USkeleton class="h-96 w-full" />
        </UCard>

        <!-- Chart loaded content -->
        <ChartsBarChart
          v-else-if="allTrades && allTrades.length > 0"
          :series="chartSeries"
          :categories="chartCategories"
          :bar-colors="chartColors"
          :distributed="true"
          title="Trades Profit by Start Date"
          y-axis-label="Total Profit %"
          :height="400"
          :show-data-labels="false"
          :show-legend="false"
          @bar-click="handleBarClick"
        />

        <!-- Equity Curve -->
        <BacktestingEquityCurve
          v-if="allTrades && allTrades.length > 0"
          :trades="allTrades"
          :loading="false"
        />

        <!-- Sector Analysis -->
        <BacktestingSectorAnalysis
          v-if="allTrades && allTrades.length > 0"
          :trades="allTrades"
          :loading="false"
        />

        <!-- Monte Carlo Section -->
        <UCard v-if="status === 'success' && allTrades && allTrades.length > 0">
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-chart-scatter" class="w-5 h-5" />
              <h3 class="text-lg font-semibold">
                Monte Carlo Simulation
              </h3>
            </div>
          </template>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium mb-2">Simulation Technique</label>
              <USelect
                v-model="selectedTechnique"
                :items="[
                  { value: MonteCarloTechnique.TRADE_SHUFFLING, label: MonteCarloTechniqueDescriptions[MonteCarloTechnique.TRADE_SHUFFLING].name },
                  { value: MonteCarloTechnique.BOOTSTRAP_RESAMPLING, label: MonteCarloTechniqueDescriptions[MonteCarloTechnique.BOOTSTRAP_RESAMPLING].name }
                ]"
                value-key="value"
              />
              <p class="text-sm text-muted mt-2">
                {{ MonteCarloTechniqueDescriptions[selectedTechnique].description }}
              </p>
            </div>

            <div class="flex justify-center">
              <UButton
                label="Run Simulation"
                icon="i-lucide-play"
                size="lg"
                :loading="monteCarloStatus === 'pending'"
                @click="runMonteCarloSimulation()"
              />
            </div>
          </div>
        </UCard>

        <!-- Monte Carlo Results -->
        <div v-if="showMonteCarloResults" class="mt-8">
          <UCard>
            <template #header>
              <div class="flex items-center justify-between">
                <h2 class="text-xl font-semibold">
                  Monte Carlo Simulation Results
                </h2>
                <UButton
                  v-if="monteCarloStatus === 'success'"
                  icon="i-lucide-x"
                  size="sm"
                  variant="ghost"
                  @click="showMonteCarloResults = false"
                />
              </div>
            </template>
            <BacktestingMonteCarloResults
              :result="monteCarloResult"
              :loading="monteCarloStatus === 'pending'"
            />
          </UCard>
        </div>

        <!-- No results -->
        <UCard v-else-if="status === 'success' && (!allTrades || allTrades.length === 0)">
          <div class="text-center py-8">
            <p class="text-muted">
              No trades found for the selected criteria
            </p>
          </div>
        </UCard>

        <!-- Error state -->
        <UCard v-else-if="status === 'error'">
          <div class="text-center py-8">
            <UIcon name="i-lucide-alert-circle" class="w-12 h-12 text-error mx-auto mb-2" />
            <p class="text-error">
              Failed to load backtest results
            </p>
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Config Modal -->
  <BacktestingConfigModal
    v-model:open="isConfigModalOpen"
    @run-backtest="runBacktest"
  />

  <!-- Trade Details Modal -->
  <BacktestingTradeDetailsModal
    v-model:open="isModalOpen"
    :date="selectedDateTrades?.date || ''"
    :trades="selectedDateTrades?.trades || []"
  />
</template>
