<script setup lang="ts">
import type { BacktestReport, ExitReasonAnalysis, ExitStats } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

// Transform exit reason data
const exitReasonStats = computed(() => {
  if (!props.report?.exitReasonAnalysis?.byReason) return []

  return Object.entries(props.report.exitReasonAnalysis.byReason)
    .map(([reason, stats]) => ({
      reason,
      ...stats
    }))
    .sort((a, b) => b.count - a.count) // Sort by count descending
})

// Table columns
const columns = [
  { accessorKey: 'reason', header: 'Exit Reason' },
  { accessorKey: 'count', header: 'Count' },
  { accessorKey: 'percentage', header: '%' },
  { accessorKey: 'winRate', header: 'Win Rate' },
  { accessorKey: 'avgProfit', header: 'Avg Profit' },
  { accessorKey: 'avgHoldingDays', header: 'Avg Days' }
]

// Calculate total trades for percentages
const totalTrades = computed(() => {
  return exitReasonStats.value.reduce((sum, stat) => sum + stat.count, 0)
})

// Table data
const tableData = computed(() => {
  return exitReasonStats.value.map(stat => {
    const percentage = totalTrades.value > 0 ? (stat.count / totalTrades.value) * 100 : 0

    return {
      reason: stat.reason,
      count: stat.count,
      percentage: percentage.toFixed(1) + '%',
      winRate: (stat.winRate * 100).toFixed(1) + '%',
      avgProfit: (stat.avgProfit >= 0 ? '+' : '') + stat.avgProfit.toFixed(2) + '%',
      avgHoldingDays: stat.avgHoldingDays.toFixed(1),
      raw: {
        ...stat,
        percentage
      }
    }
  })
})

// Pie chart data
const pieChartSeries = computed(() => {
  return exitReasonStats.value.map(s => s.count)
})

const pieChartLabels = computed(() => {
  return exitReasonStats.value.map(s => s.reason)
})

// Insights
const mostCommonExit = computed(() => {
  if (exitReasonStats.value.length === 0) return null
  return exitReasonStats.value[0] // Already sorted by count
})

const mostProfitableExit = computed(() => {
  if (exitReasonStats.value.length === 0) return null
  return [...exitReasonStats.value].sort((a, b) => b.avgProfit - a.avgProfit)[0]
})

const leastProfitableExit = computed(() => {
  if (exitReasonStats.value.length === 0) return null
  return [...exitReasonStats.value].sort((a, b) => a.avgProfit - b.avgProfit)[0]
})

const bestWinRateExit = computed(() => {
  if (exitReasonStats.value.length === 0) return null
  return [...exitReasonStats.value].sort((a, b) => b.winRate - a.winRate)[0]
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-log-out" class="w-5 h-5" />
        <h3 class="text-lg font-semibold">
          Exit Reason Analysis
        </h3>
      </div>
    </template>

    <div v-if="loading" class="space-y-4">
      <USkeleton class="h-64 w-full" />
      <USkeleton class="h-64 w-full" />
    </div>

    <div v-else-if="!report?.exitReasonAnalysis" class="text-center py-8">
      <UIcon name="i-lucide-help-circle" class="w-12 h-12 text-muted mx-auto mb-2" />
      <p class="text-muted">
        No exit reason analysis available
      </p>
      <p class="text-sm text-muted mt-2">
        Run a new backtest to see exit effectiveness
      </p>
    </div>

    <div v-else-if="exitReasonStats.length === 0" class="text-center py-8">
      <p class="text-muted">
        No exit data available
      </p>
    </div>

    <div v-else class="space-y-6">
      <!-- Insights Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <UCard v-if="mostCommonExit" :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Most Common Exit</div>
          <div class="font-semibold text-lg">{{ mostCommonExit.reason }}</div>
          <div class="text-sm text-muted mt-1">
            {{ mostCommonExit.count }} trades ({{ ((mostCommonExit.count / totalTrades) * 100).toFixed(1) }}%)
          </div>
        </UCard>

        <UCard v-if="mostProfitableExit" :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Most Profitable Exit</div>
          <div class="font-semibold text-lg text-success">{{ mostProfitableExit.reason }}</div>
          <div class="text-sm text-muted mt-1">
            Avg: +{{ mostProfitableExit.avgProfit.toFixed(2) }}%
          </div>
        </UCard>

        <UCard v-if="bestWinRateExit" :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Best Win Rate</div>
          <div class="font-semibold text-lg">{{ bestWinRateExit.reason }}</div>
          <div class="text-sm text-muted mt-1">
            {{ (bestWinRateExit.winRate * 100).toFixed(1) }}% win rate
          </div>
        </UCard>

        <UCard v-if="leastProfitableExit" :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Needs Improvement</div>
          <div class="font-semibold text-lg text-error">{{ leastProfitableExit.reason }}</div>
          <div class="text-sm text-muted mt-1">
            Avg: {{ leastProfitableExit.avgProfit.toFixed(2) }}%
          </div>
        </UCard>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Exit Distribution Pie Chart -->
        <div>
          <h4 class="text-sm font-medium mb-3">
            Exit Reason Distribution
          </h4>
          <ChartsDonutChart
            :series="pieChartSeries"
            :labels="pieChartLabels"
            :height="300"
          />
        </div>

        <!-- Exit Reason Stats Table -->
        <div>
          <h4 class="text-sm font-medium mb-3">
            Exit Effectiveness
          </h4>
          <UTable
            :columns="columns"
            :data="tableData"
          >
            <template #reason-data="{ row }">
              <span class="font-medium">{{ row.original.reason }}</span>
            </template>

            <template #count-data="{ row }">
              <UBadge variant="subtle" color="neutral">
                {{ row.original.count }}
              </UBadge>
            </template>

            <template #percentage-data="{ row }">
              <span class="text-muted">{{ row.original.percentage }}</span>
            </template>

            <template #winRate-data="{ row }">
              <span
                :class="{
                  'text-success font-semibold': row.original.raw.winRate >= 0.6,
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
                  'text-success font-semibold': row.original.raw.avgProfit > 0,
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
      </div>

      <!-- Actionable Insights -->
      <UCard :ui="{ body: 'p-4' }" class="bg-primary/5">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-lightbulb" class="w-5 h-5 text-primary mt-0.5" />
          <div class="flex-1">
            <h4 class="font-semibold mb-2">Insights & Recommendations</h4>
            <ul class="space-y-2 text-sm">
              <li v-if="mostCommonExit">
                <span class="text-muted">•</span>
                <strong class="ml-2">{{ mostCommonExit.reason }}</strong> triggers most often
                ({{ ((mostCommonExit.count / totalTrades) * 100).toFixed(1) }}% of exits)
                with {{ (mostCommonExit.winRate * 100).toFixed(1) }}% win rate.
              </li>
              <li v-if="mostProfitableExit && mostProfitableExit.reason !== mostCommonExit?.reason">
                <span class="text-muted">•</span>
                <strong class="ml-2">{{ mostProfitableExit.reason }}</strong> is most profitable
                (avg +{{ mostProfitableExit.avgProfit.toFixed(2) }}%) but only triggers
                {{ mostProfitableExit.count }} times.
              </li>
              <li v-if="leastProfitableExit && leastProfitableExit.avgProfit < 0">
                <span class="text-muted">•</span>
                <strong class="ml-2 text-error">{{ leastProfitableExit.reason }}</strong>
                may need adjustment - averaging {{ leastProfitableExit.avgProfit.toFixed(2) }}% per trade.
              </li>
              <li v-if="bestWinRateExit && bestWinRateExit.winRate > 0.7">
                <span class="text-muted">•</span>
                <strong class="ml-2 text-success">{{ bestWinRateExit.reason }}</strong>
                has excellent {{ (bestWinRateExit.winRate * 100).toFixed(1) }}% win rate -
                consider making it trigger more often.
              </li>
            </ul>
          </div>
        </div>
      </UCard>
    </div>
  </UCard>
</template>
