<script setup lang="ts">
import type { BacktestReport, TimeBasedStats, PeriodStats } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

// Period selection
const selectedPeriod = ref<'year' | 'quarter' | 'month'>('year')

const periodItems = [
  { value: 'year', label: 'By Year' },
  { value: 'quarter', label: 'By Quarter' },
  { value: 'month', label: 'By Month' }
]

// Define row type
interface PeriodRow extends PeriodStats {
  period: string
}

// Get stats for selected period
const periodStats = computed<PeriodRow[]>(() => {
  if (!props.report?.timeBasedStats) {
    return []
  }

  const stats = props.report.timeBasedStats

  let data: Record<string, PeriodStats> = {}

  switch (selectedPeriod.value) {
    case 'year':
      data = stats.byYear
      break
    case 'quarter':
      data = stats.byQuarter
      break
    case 'month':
      data = stats.byMonth
      break
  }

  // Convert to array and sort by period (descending)
  return Object.entries(data)
    .map(([period, stats]) => ({
      period,
      ...stats
    }))
    .sort((a, b) => b.period.localeCompare(a.period))
})

// Table columns
const columns = [
  { accessorKey: 'period', header: 'Period' },
  { accessorKey: 'trades', header: 'Trades' },
  { accessorKey: 'winRate', header: 'Win Rate' },
  { accessorKey: 'avgProfit', header: 'Avg Profit' },
  { accessorKey: 'avgHoldingDays', header: 'Avg Days' }
]

// Table data
const tableData = computed(() => {
  return periodStats.value.map(stat => ({
    period: stat.period,
    trades: stat.trades,
    winRate: formatWinRate(stat.winRate),
    avgProfit: formatProfit(stat.avgProfit),
    avgHoldingDays: formatDays(stat.avgHoldingDays),
    raw: stat
  }))
})

// Chart data for win rate trend
const winRateChartSeries = computed(() => {
  if (!periodStats.value.length) return []

  return [{
    name: 'Win Rate %',
    data: periodStats.value.map(s => s.winRate).reverse()
  }]
})

const winRateChartCategories = computed(() => {
  return periodStats.value.map(s => s.period).reverse()
})

// Chart data for profit by period
const profitChartSeries = computed(() => {
  if (!periodStats.value.length) {
    return []
  }

  return [{
    name: 'Avg Profit %',
    data: periodStats.value.map(s => s.avgProfit).reverse()
  }]
})

const profitChartCategories = computed(() => {
  return periodStats.value.map(s => s.period).reverse()
})

// Color-code profit bars
const profitBarColors = computed(() => {
  return periodStats.value.map(s => s.avgProfit >= 0 ? '#10b981' : '#ef4444').reverse()
})

// Format helpers
const formatWinRate = (value: number) => `${value.toFixed(1)}%`  // Backend already returns as percentage (0-100)
const formatProfit = (value: number) => `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
const formatDays = (value: number) => value.toFixed(1)
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-calendar" class="w-5 h-5" />
          <h3 class="text-lg font-semibold">
            Time-Based Performance
          </h3>
        </div>
        <USelect
          v-model="selectedPeriod"
          :items="periodItems"
          value-key="value"
          class="w-40"
        />
      </div>
    </template>

    <div v-if="loading" class="space-y-4">
      <USkeleton class="h-64 w-full" />
      <USkeleton class="h-64 w-full" />
    </div>

    <div v-else-if="!report?.timeBasedStats" class="text-center py-8">
      <UIcon name="i-lucide-calendar-x" class="w-12 h-12 text-muted mx-auto mb-2" />
      <p class="text-muted">
        No time-based statistics available
      </p>
      <p class="text-sm text-muted mt-2">
        This backtest was run before time-based stats were added.
      </p>
      <p class="text-sm text-primary mt-1 font-medium">
        Run a new backtest to see performance over time.
      </p>
    </div>

    <div v-else-if="periodStats.length === 0" class="text-center py-8">
      <p class="text-muted">
        No data for selected period
      </p>
    </div>

    <div v-else class="space-y-6">
      <!-- Performance Table -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Performance Summary
        </h4>
        <UTable
          :columns="columns"
          :data="tableData"
        >
          <template #period-data="{ row }">
            <span class="font-medium">{{ row.original.period }}</span>
          </template>
          <template #trades-data="{ row }">
            <span>{{ row.original.trades }}</span>
          </template>
          <template #winRate-data="{ row }">
            <span
              :class="{
                'text-success': row.original.raw.winRate >= 0.6,
                'text-warning': row.original.raw.winRate >= 0.4 && row.original.raw.winRate < 0.6,
                'text-error': row.original.raw.winRate < 0.4
              }"
            >
              {{ row.original.winRate }}
            </span>
          </template>
          <template #avgProfit-data="{ row }">
            <span
              :class="{
                'text-success': row.original.raw.avgProfit > 0,
                'text-error': row.original.raw.avgProfit < 0,
                'text-muted': row.original.raw.avgProfit === 0
              }"
            >
              {{ row.original.avgProfit }}
            </span>
          </template>
          <template #avgHoldingDays-data="{ row }">
            <span class="text-muted">{{ row.original.avgHoldingDays }}</span>
          </template>
        </UTable>
      </div>

      <!-- Win Rate Trend Chart -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Win Rate Trend
        </h4>
        <ChartsLineChart
          :series="winRateChartSeries"
          :categories="winRateChartCategories"
          :height="250"
          y-axis-label="Win Rate %"
          :show-legend="false"
        />
      </div>

      <!-- Profit by Period Chart -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Average Profit by {{ selectedPeriod === 'year' ? 'Year' : selectedPeriod === 'quarter' ? 'Quarter' : 'Month' }}
        </h4>
        <div v-if="profitChartSeries.length === 0 || !profitChartSeries[0] || profitChartSeries[0].data.length === 0" class="text-center py-8">
          <p class="text-muted text-sm">No data available for chart</p>
        </div>
        <ChartsBarChart
          v-else
          :series="profitChartSeries"
          :categories="profitChartCategories"
          :bar-colors="profitBarColors"
          :distributed="true"
          y-axis-label="Avg Profit %"
          :height="250"
          :show-legend="false"
          :show-data-labels="false"
        />
      </div>
    </div>
  </UCard>
</template>
