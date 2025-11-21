<script setup lang="ts">
import type { Trade } from '~/types'
import { getSectorName } from '~/types/enums'

const props = defineProps<{
  trades: Trade[] | null
  loading?: boolean
}>()

interface SectorMetrics {
  sector: string
  sectorName: string
  totalTrades: number
  winningTrades: number
  losingTrades: number
  winRate: number
  totalProfit: number
  averageProfit: number
  edge: number
}

const sectorMetrics = computed<SectorMetrics[]>(() => {
  if (!props.trades || props.trades.length === 0) return []

  const sectorMap = new Map<string, Trade[]>()

  // Group trades by sector
  props.trades.forEach((trade) => {
    if (!sectorMap.has(trade.sector)) {
      sectorMap.set(trade.sector, [])
    }
    sectorMap.get(trade.sector)!.push(trade)
  })

  // Calculate metrics for each sector
  const metrics = Array.from(sectorMap.entries()).map(([sector, trades]) => {
    const winningTrades = trades.filter(t => t.profitPercentage > 0)
    const losingTrades = trades.filter(t => t.profitPercentage <= 0)
    const totalProfit = trades.reduce((sum, t) => sum + t.profitPercentage, 0)
    const averageProfit = totalProfit / trades.length
    const winRate = (winningTrades.length / trades.length) * 100

    // Edge calculation: average profit * win rate
    const edge = averageProfit * (winRate / 100)

    return {
      sector,
      sectorName: getSectorName(sector),
      totalTrades: trades.length,
      winningTrades: winningTrades.length,
      losingTrades: losingTrades.length,
      winRate,
      totalProfit,
      averageProfit,
      edge
    }
  })

  // Sort by edge (highest first)
  return metrics.sort((a, b) => b.edge - a.edge)
})

const columns = [
  { accessorKey: 'sector', header: 'Sector' },
  { accessorKey: 'totalTrades', header: 'Trades' },
  { accessorKey: 'winRate', header: 'Win Rate' },
  { accessorKey: 'totalProfit', header: 'Total Profit %' },
  { accessorKey: 'averageProfit', header: 'Avg Profit %' },
  { accessorKey: 'edge', header: 'Edge' }
]

const tableData = computed(() => {
  return sectorMetrics.value.map(metric => ({
    sector: metric.sectorName,
    totalTrades: metric.totalTrades,
    winRate: metric.winRate.toFixed(1) + '%',
    totalProfit: metric.totalProfit.toFixed(2),
    averageProfit: metric.averageProfit.toFixed(2),
    edge: metric.edge.toFixed(2),
    raw: metric
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

    <div v-else-if="!trades || trades.length === 0" class="text-center py-8">
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
            row.original.raw.winRate >= 50 ? 'text-success' : 'text-muted'
          ]"
        >
          {{ row.original.winRate }}
        </span>
      </template>

      <template #totalProfit-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.totalProfit >= 0 ? 'text-success' : 'text-error'
          ]"
        >
          {{ row.original.raw.totalProfit >= 0 ? '+' : '' }}{{ row.original.totalProfit }}%
        </span>
      </template>

      <template #averageProfit-data="{ row }">
        <span
          :class="[
            'font-semibold',
            row.original.raw.averageProfit >= 0 ? 'text-success' : 'text-error'
          ]"
        >
          {{ row.original.raw.averageProfit >= 0 ? '+' : '' }}{{ row.original.averageProfit }}%
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
    </UTable>
  </UCard>
</template>
