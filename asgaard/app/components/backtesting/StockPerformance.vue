<script setup lang="ts">
import type { BacktestReport } from '~/types'

const props = defineProps<{
  report: BacktestReport | null
  loading?: boolean
}>()

const topStocks = computed(() => {
  if (!props.report?.stockPerformance) return []

  // Return top 20 stocks by edge
  return props.report.stockPerformance.slice(0, 20)
})

const columns = [
  { accessorKey: 'symbol', header: 'Symbol' },
  { accessorKey: 'trades', header: 'Trades' },
  { accessorKey: 'winRate', header: 'Win Rate' },
  { accessorKey: 'edge', header: 'Edge %' },
  { accessorKey: 'profitFactor', header: 'PF' },
  { accessorKey: 'maxDrawdown', header: 'Max DD %' },
  { accessorKey: 'totalProfit', header: 'Total Profit %' },
  { accessorKey: 'avgProfit', header: 'Avg Profit %' },
  { accessorKey: 'avgHoldingDays', header: 'Avg Days' }
]

const tableData = computed(() => {
  return topStocks.value.map(stock => ({
    symbol: stock.symbol,
    trades: stock.trades,
    winRate: stock.winRate.toFixed(1) + '%',
    edge: stock.edge.toFixed(2) + '%',
    profitFactor: stock.profitFactor !== null ? stock.profitFactor.toFixed(2) : 'N/A',
    maxDrawdown: stock.maxDrawdown.toFixed(2) + '%',
    totalProfit: stock.totalProfitPercentage.toFixed(2) + '%',
    avgProfit: stock.avgProfit.toFixed(2) + '%',
    avgHoldingDays: stock.avgHoldingDays.toFixed(1),
    raw: stock
  }))
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">
          Top 20 Stock Performance
        </h3>
        <p class="text-sm text-muted">
          Ranked by Edge
        </p>
      </div>
    </template>

    <div v-if="loading">
      <USkeleton class="h-64 w-full" />
    </div>

    <div v-else-if="!report || !report.stockPerformance || report.stockPerformance.length === 0" class="text-center py-8">
      <p class="text-muted">
        No stock performance data available
      </p>
    </div>

    <UTable
      v-else
      :columns="columns"
      :data="tableData"
    >
      <template #symbol-data="{ row }">
        <span class="font-bold text-primary">{{ row.original.symbol }}</span>
      </template>

      <template #trades-data="{ row }">
        <UBadge variant="subtle" color="neutral">
          {{ row.original.trades }}
        </UBadge>
      </template>

      <template #winRate-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.winRate >= 50 ? 'text-success' : 'text-muted'
          ]"
        >
          {{ row.original.winRate }}
        </span>
      </template>

      <template #edge-data="{ row }">
        <span
          :class="[
            'font-bold',
            row.original.raw.edge >= 0 ? 'text-success' : 'text-error'
          ]"
        >
          {{ row.original.raw.edge >= 0 ? '+' : '' }}{{ row.original.edge }}
        </span>
      </template>

      <template #profitFactor-data="{ row }">
        <span
          v-if="row.original.raw.profitFactor !== null"
          :class="[
            'font-bold',
            row.original.raw.profitFactor >= 1.0 ? 'text-success' : 'text-error'
          ]"
        >
          {{ row.original.profitFactor }}
        </span>
        <span v-else class="text-muted italic">
          N/A
        </span>
      </template>

      <template #maxDrawdown-data="{ row }">
        <span
          :class="[
            'font-bold',
            row.original.raw.maxDrawdown <= 10 ? 'text-success' : row.original.raw.maxDrawdown <= 20 ? 'text-warning' : 'text-error'
          ]"
        >
          {{ row.original.maxDrawdown }}
        </span>
      </template>

      <template #totalProfit-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.totalProfitPercentage >= 0 ? 'text-success' : 'text-error'
          ]"
        >
          {{ row.original.raw.totalProfitPercentage >= 0 ? '+' : '' }}{{ row.original.totalProfit }}
        </span>
      </template>

      <template #avgProfit-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.avgProfit >= 0 ? 'text-success' : 'text-error'
          ]"
        >
          {{ row.original.raw.avgProfit >= 0 ? '+' : '' }}{{ row.original.avgProfit }}
        </span>
      </template>

      <template #avgHoldingDays-data="{ row }">
        <span class="text-muted">
          {{ row.original.avgHoldingDays }}
        </span>
      </template>
    </UTable>
  </UCard>
</template>
