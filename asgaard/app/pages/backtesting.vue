<script setup lang="ts">
import { format, sub } from 'date-fns'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Range, Trade, BacktestRequest, MonteCarloResult, BacktestReport } from '~/types'
import { MonteCarloTechnique, MonteCarloTechniqueDescriptions } from '~/types/enums'

const allTrades = ref<Trade[]>([])
const backtestReport = ref<BacktestReport | null>(null)
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const isConfigModalOpen = ref(false)

// Monte Carlo state
const monteCarloResult = ref<MonteCarloResult | null>(null)
const monteCarloStatus = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const showMonteCarloResults = ref(false)
const selectedTechnique = ref<MonteCarloTechnique>(MonteCarloTechnique.TRADE_SHUFFLING)

// Tab state
const activeTab = ref('overview')

// Tab items configuration
const tabItems = [
  {
    label: 'Overview',
    icon: 'i-lucide-layout-dashboard',
    value: 'overview',
    slot: 'overview'
  },
  {
    label: 'Equity Curve',
    icon: 'i-lucide-trending-up',
    value: 'equity-curve',
    slot: 'equity-curve'
  },
  {
    label: 'Performance',
    icon: 'i-lucide-bar-chart-3',
    value: 'performance',
    slot: 'performance'
  },
  {
    label: 'Diagnostics',
    icon: 'i-lucide-activity',
    value: 'diagnostics',
    slot: 'diagnostics'
  },
  {
    label: 'Monte Carlo',
    icon: 'i-lucide-chart-scatter',
    value: 'monte-carlo',
    slot: 'monte-carlo'
  }
]

function openConfigModal() {
  isConfigModalOpen.value = true
}

const toast = useToast()

async function runBacktest(config: BacktestRequest) {
  status.value = 'pending'
  allTrades.value = []
  backtestReport.value = null
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
    // Increased timeout for large backtests (30 minutes)
    const report = await $fetch<BacktestReport>('/udgaard/api/backtest', {
      method: 'POST',
      body: config,
      timeout: 1800000 // 30 minutes in milliseconds
    })
    backtestReport.value = report
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
      timeout: 1800000 // 30 minutes in milliseconds
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

// Use grouped trades from backend report
const tradesGroupedByDate = computed(() => {
  if (!backtestReport.value?.tradesGroupedByDate) return []

  return backtestReport.value.tradesGroupedByDate.map(entry => ({
    date: entry.date,
    count: entry.trades.length,
    totalProfit: entry.profitPercentage,
    trades: entry.trades
  }))
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

      <!-- Tabbed Results -->
      <div v-else>
        <UTabs v-model="activeTab" :items="tabItems" variant="link" class="w-full">
          <!-- Overview Tab -->
          <template #overview>
            <div class="grid gap-4 mt-4">
              <BacktestingCards :report="backtestReport" :loading="false" />
              <ChartsBarChart
                v-if="allTrades && allTrades.length > 0"
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
            </div>
          </template>

          <!-- Equity Curve Tab -->
          <template #equity-curve>
            <div class="grid gap-4 mt-4">
              <BacktestingEquityCurve
                v-if="allTrades && allTrades.length > 0"
                :trades="allTrades"
                :loading="false"
              />
            </div>
          </template>

          <!-- Performance Tab -->
          <template #performance>
            <div class="grid gap-4 mt-4">
              <BacktestingTimeBasedStats
                :report="backtestReport"
                :loading="false"
              />
              <BacktestingSectorAnalysis
                v-if="backtestReport"
                :report="backtestReport"
                :loading="false"
              />
              <!-- TODO: Add ExitReasonAnalysis component here in Phase 4 -->
            </div>
          </template>

          <!-- Diagnostics Tab -->
          <template #diagnostics>
            <div class="grid gap-4 mt-4">
              <UCard>
                <div class="text-center py-8">
                  <UIcon name="i-lucide-activity" class="w-12 h-12 text-muted mx-auto mb-2" />
                  <p class="text-muted">
                    Advanced diagnostics coming soon
                  </p>
                  <p class="text-sm text-muted mt-2">
                    ATR drawdowns, market conditions, and excursion analysis
                  </p>
                </div>
              </UCard>
              <!-- TODO: Add ATRDrawdownStats component here in Phase 5 -->
              <!-- TODO: Add MarketConditions component here in Phase 6 -->
              <!-- TODO: Add ExcursionAnalysis component here in Phase 7 -->
            </div>
          </template>

          <!-- Monte Carlo Tab -->
          <template #monte-carlo>
            <div class="grid gap-4 mt-4">
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
              <div v-if="showMonteCarloResults">
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
            </div>
          </template>
        </UTabs>

        <!-- No results message -->
        <UCard v-if="status === 'success' && (!allTrades || allTrades.length === 0)" class="mt-4">
          <div class="text-center py-8">
            <p class="text-muted">
              No trades found for the selected criteria
            </p>
          </div>
        </UCard>

        <!-- Error state -->
        <UCard v-if="status === 'error'" class="mt-4">
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
