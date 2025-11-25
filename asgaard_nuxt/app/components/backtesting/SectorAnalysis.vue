<script setup lang="ts">
import type { BacktestReport } from '~/types'
import { getSectorName } from '~/types/enums'

const props = defineProps<{
  report: BacktestReport | null
  loading?: boolean
}>()

const sectorStats = computed(() => {
  if (!props.report?.sectorStats) return []

  return props.report.sectorStats.map(stat => ({
    ...stat,
    sectorName: getSectorName(stat.sector)
  }))
})

const columns = [
  { accessorKey: 'sector', header: 'Sector' },
  { accessorKey: 'totalTrades', header: 'Trades' },
  { accessorKey: 'winRate', header: 'Win Rate' },
  { accessorKey: 'edge', header: 'Edge %' },
  { accessorKey: 'totalProfit', header: 'Total Profit %' },
  { accessorKey: 'maxDrawdown', header: 'Max Drawdown %' },
  { accessorKey: 'avgWin', header: 'Avg Win %' },
  { accessorKey: 'avgLoss', header: 'Avg Loss %' }
]

const tableData = computed(() => {
  return sectorStats.value.map(stat => ({
    sector: stat.sectorName,
    totalTrades: stat.totalTrades,
    winRate: (stat.winRate * 100).toFixed(1) + '%',
    edge: stat.edge.toFixed(2) + '%',
    totalProfit: stat.totalProfitPercentage.toFixed(2) + '%',
    maxDrawdown: stat.maxDrawdown.toFixed(2) + '%',
    avgWin: stat.averageWinPercent.toFixed(2) + '%',
    avgLoss: stat.averageLossPercent.toFixed(2) + '%',
    raw: stat
  }))
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">
          Sector Performance Analysis
        </h3>
        <p class="text-sm text-muted">
          Ranked by Edge
        </p>
      </div>
    </template>

    <div v-if="loading">
      <USkeleton class="h-64 w-full" />
    </div>

    <div v-else-if="!report || !report.sectorStats || report.sectorStats.length === 0" class="text-center py-8">
      <p class="text-muted">
        No sector data available
      </p>
    </div>

    <UTable
      v-else
      :columns="columns"
      :data="tableData"
    >
      <template #sector-data="{ row }">
        <span class="font-medium">{{ row.original.sector }}</span>
      </template>

      <template #totalTrades-data="{ row }">
        <UBadge variant="subtle" color="neutral">
          {{ row.original.totalTrades }}
        </UBadge>
      </template>

      <template #winRate-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.winRate >= 0.5 ? 'text-success' : 'text-muted'
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

      <template #maxDrawdown-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.maxDrawdown <= 10 ? 'text-success' : row.original.raw.maxDrawdown <= 20 ? 'text-warning' : 'text-error'
          ]"
        >
          {{ row.original.maxDrawdown }}
        </span>
      </template>

      <template #avgWin-data="{ row }">
        <span class="font-semibold text-success">
          +{{ row.original.avgWin }}
        </span>
      </template>

      <template #avgLoss-data="{ row }">
        <span class="font-semibold text-error">
          -{{ row.original.avgLoss }}
        </span>
      </template>
    </UTable>
  </UCard>
</template>
