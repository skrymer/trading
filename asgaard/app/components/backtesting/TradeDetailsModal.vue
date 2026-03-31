<script setup lang="ts">
import { format } from 'date-fns'
import { h, resolveComponent } from 'vue'
import type { Trade, PositionSizedTrade } from '~/types'
import type { TableColumn } from '@nuxt/ui'

const props = defineProps<{
  open: boolean
  date: string
  trades: Trade[]
  sizedTrades?: Map<string, PositionSizedTrade>
  openPositions?: PositionSizedTrade[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const expanded = ref<Record<string, boolean>>({})

watch(() => props.open, (isOpen) => {
  if (!isOpen) {
    expanded.value = {}
  }
})

const UIcon = resolveComponent('UIcon')

const hasPositionSizing = computed(() => props.sizedTrades && props.sizedTrades.size > 0)

// Helper to find position-sized trade matching a raw trade
function findSizedTrade(trade: Trade): PositionSizedTrade | undefined {
  if (!props.sizedTrades) return undefined
  const entryDate = trade.entryQuote?.date
  if (!entryDate) return undefined
  return props.sizedTrades.get(`${trade.stockSymbol}:${entryDate}`)
}

// Summary statistics - use position-sized data when available
const totalProfit = computed(() => {
  if (hasPositionSizing.value) {
    return props.trades.reduce((sum, t) => {
      const sized = findSizedTrade(t)
      return sum + (sized?.portfolioReturnPct ?? t.profitPercentage)
    }, 0)
  }
  return props.trades.reduce((sum, t) => sum + t.profitPercentage, 0)
})

const totalProfitDollar = computed(() => {
  if (hasPositionSizing.value) {
    return props.trades.reduce((sum, t) => {
      const sized = findSizedTrade(t)
      return sum + (sized?.dollarProfit ?? t.profit)
    }, 0)
  }
  return props.trades.reduce((sum, t) => sum + t.profit, 0)
})

const numberOfTrades = computed(() => props.trades.length)

const winningTrades = computed(() => {
  if (hasPositionSizing.value) {
    return props.trades.filter((t) => {
      const sized = findSizedTrade(t)
      return (sized?.dollarProfit ?? t.profitPercentage) > 0
    })
  }
  return props.trades.filter(t => t.profitPercentage > 0)
})

const losingTrades = computed(() => {
  if (hasPositionSizing.value) {
    return props.trades.filter((t) => {
      const sized = findSizedTrade(t)
      return (sized?.dollarProfit ?? t.profitPercentage) < 0
    })
  }
  return props.trades.filter(t => t.profitPercentage < 0)
})

const winRate = computed(() => {
  if (numberOfTrades.value === 0) return 0
  return (winningTrades.value.length / numberOfTrades.value) * 100
})

const bestTrade = computed(() => {
  if (props.trades.length === 0) return null
  return props.trades.reduce((best, trade) => {
    const bestPct = findSizedTrade(best)?.portfolioReturnPct ?? best.profitPercentage
    const tradePct = findSizedTrade(trade)?.portfolioReturnPct ?? trade.profitPercentage
    return tradePct > bestPct ? trade : best
  })
})

const worstTrade = computed(() => {
  if (props.trades.length <= 1) return null
  return props.trades.reduce((worst, trade) => {
    const worstPct = findSizedTrade(worst)?.portfolioReturnPct ?? worst.profitPercentage
    const tradePct = findSizedTrade(trade)?.portfolioReturnPct ?? trade.profitPercentage
    return tradePct < worstPct ? trade : worst
  })
})

function getTradeProfit(trade: Trade) {
  const sized = findSizedTrade(trade)
  return {
    pct: sized?.portfolioReturnPct ?? trade.profitPercentage,
    dollar: sized?.dollarProfit ?? trade.profit,
    shares: sized?.shares
  }
}

// Table configuration
const columns = computed<TableColumn<any>[]>(() => {
  const cols: TableColumn<any>[] = [
    {
      id: 'expand',
      header: '',
      cell: ({ row }: { row: any }) => h('button', {
        onClick: () => row.toggleExpanded(),
        class: [
          'inline-flex items-center justify-center',
          'w-8 h-8',
          'rounded hover:bg-muted/50',
          'transition-colors',
          'text-gray-700 dark:text-gray-200'
        ]
      }, [
        h(UIcon, {
          name: 'i-lucide-chevron-down',
          class: [
            'w-4 h-4',
            'transition-transform',
            row.getIsExpanded() ? 'rotate-180' : ''
          ]
        })
      ])
    },
    { accessorKey: 'stockSymbol', header: 'Symbol' },
    { accessorKey: 'profitPercentage', header: 'Profit %' },
    { accessorKey: 'profit', header: 'Profit $' }
  ]

  if (hasPositionSizing.value) {
    cols.push({ accessorKey: 'shares', header: 'Shares' })
  }

  cols.push(
    { accessorKey: 'tradingDays', header: 'Days' },
    { accessorKey: 'entryPrice', header: 'Entry' },
    { accessorKey: 'exitPrice', header: 'Exit' },
    { accessorKey: 'exitDate', header: 'Exit Date' },
    { accessorKey: 'exitReason', header: 'Exit Reason' }
  )

  return cols
})

// Open positions columns
const openPositionColumns: TableColumn<any>[] = [
  { accessorKey: 'symbol', header: 'Symbol' },
  { accessorKey: 'shares', header: 'Shares' },
  { accessorKey: 'entryPrice', header: 'Entry' },
  { accessorKey: 'entryDate', header: 'Entry Date' },
  { accessorKey: 'dollarProfit', header: 'P&L' }
]

const openPositionData = computed(() => {
  if (!props.openPositions?.length) return []
  return props.openPositions
    .filter(pos => pos.shares > 0)
    .map(pos => ({
      symbol: pos.symbol,
      shares: pos.shares,
      entryPrice: pos.entryPrice.toFixed(2),
      entryDate: format(new Date(pos.entryDate), 'MMM dd, yyyy'),
      dollarProfit: pos.dollarProfit,
      portfolioReturnPct: pos.portfolioReturnPct
    }))
})

const tableData = computed(() => {
  return props.trades.map((trade) => {
    const exitQuote = trade.quotes?.[trade.quotes.length - 1]
    const exitDate = exitQuote?.date ? format(new Date(exitQuote.date), 'MMM dd, yyyy') : 'N/A'
    const p = getTradeProfit(trade)

    return {
      _trade: trade,
      stockSymbol: trade.stockSymbol,
      sector: trade.sector,
      entryPrice: trade.entryQuote?.closePrice?.toFixed(2) || 'N/A',
      exitPrice: exitQuote?.closePrice?.toFixed(2) || 'N/A',
      exitDate,
      tradingDays: trade.tradingDays || 'N/A',
      profitPercentage: Number(p.pct.toFixed(2)),
      profit: Number(p.dollar.toFixed(2)),
      shares: p.shares ?? null,
      exitReason: trade.exitReason || 'N/A'
    }
  })
})
</script>

<template>
  <UModal
    :open="open"
    :title="`Trades for ${date}`"
    :ui="{ content: 'sm:max-w-6xl' }"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div v-if="trades.length > 0" class="space-y-6">
        <!-- Summary Cards -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <UCard :ui="{ body: '!p-4' }">
            <div class="space-y-1">
              <p class="text-xs text-muted uppercase">
                Total Profit
              </p>
              <p
                :class="[
                  'text-2xl font-bold',
                  totalProfit >= 0 ? 'text-success' : 'text-error'
                ]"
              >
                {{ totalProfit >= 0 ? '+' : '' }}{{ totalProfit.toFixed(2) }}%
              </p>
              <p class="text-xs text-muted">
                {{ totalProfitDollar >= 0 ? '+' : '' }}${{ totalProfitDollar.toFixed(2) }}
              </p>
            </div>
          </UCard>

          <UCard :ui="{ body: '!p-4' }">
            <div class="space-y-1">
              <p class="text-xs text-muted uppercase">
                Win Rate
              </p>
              <p class="text-2xl font-bold">
                {{ winRate.toFixed(0) }}%
              </p>
              <p class="text-xs text-muted">
                {{ winningTrades.length }}W / {{ losingTrades.length }}L
              </p>
            </div>
          </UCard>

          <UCard :ui="{ body: '!p-4' }">
            <div class="space-y-1">
              <p class="text-xs text-muted uppercase">
                Best Trade
              </p>
              <p class="text-2xl font-bold text-success">
                {{ bestTrade ? '+' + getTradeProfit(bestTrade).pct.toFixed(2) + '%' : 'N/A' }}
              </p>
              <p class="text-xs text-muted">
                {{ bestTrade?.stockSymbol || 'N/A' }}
              </p>
            </div>
          </UCard>

          <UCard :ui="{ body: '!p-4' }">
            <div class="space-y-1">
              <p class="text-xs text-muted uppercase">
                Worst Trade
              </p>
              <p class="text-2xl font-bold text-error">
                {{ worstTrade ? getTradeProfit(worstTrade).pct.toFixed(2) + '%' : 'N/A' }}
              </p>
              <p class="text-xs text-muted">
                {{ worstTrade?.stockSymbol || 'N/A' }}
              </p>
            </div>
          </UCard>
        </div>

        <!-- Trades Table -->
        <div>
          <h3 class="text-sm font-semibold mb-3">
            Trade Details ({{ tableData.length }} trades)
          </h3>
          <UTable
            v-model:expanded="expanded"
            :columns="columns"
            :data="tableData"
          >
            <template #stockSymbol-data="{ row }">
              <div class="flex items-center gap-2">
                <UBadge
                  :color="row.original.profitPercentage >= 0 ? 'success' : 'error'"
                  variant="subtle"
                >
                  {{ row.original.stockSymbol }}
                </UBadge>
                <UTooltip
                  v-if="row.original._trade.underlyingSymbol && row.original._trade.underlyingSymbol !== row.original.stockSymbol"
                  :text="`Strategy signals from ${row.original._trade.underlyingSymbol}`"
                >
                  <UBadge color="info" size="xs" variant="subtle">
                    {{ row.original._trade.underlyingSymbol }}
                  </UBadge>
                </UTooltip>
              </div>
            </template>

            <template #profitPercentage-data="{ row }">
              <span
                :class="[
                  'font-semibold',
                  row.original.profitPercentage >= 0 ? 'text-success' : 'text-error'
                ]"
              >
                {{ row.original.profitPercentage >= 0 ? '+' : '' }}{{ Number(row.original.profitPercentage).toFixed(2) }}%
              </span>
            </template>

            <template #profit-data="{ row }">
              <span
                :class="[
                  'font-semibold',
                  row.original.profit >= 0 ? 'text-success' : 'text-error'
                ]"
              >
                {{ row.original.profit >= 0 ? '+' : '' }}${{ Number(row.original.profit).toFixed(2) }}
              </span>
            </template>

            <template #entryPrice-data="{ row }">
              <span>${{ row.original.entryPrice }}</span>
            </template>

            <template #exitPrice-data="{ row }">
              <span>${{ row.original.exitPrice }}</span>
            </template>

            <!-- Expanded row content -->
            <template #expanded="{ row }">
              <div class="p-4 bg-muted/30">
                <BacktestingTradeChart
                  :trade="row.original._trade"
                  :override-profit-pct="row.original.profitPercentage"
                />
              </div>
            </template>
          </UTable>
        </div>

        <!-- Open Positions -->
        <div v-if="openPositionData.length > 0">
          <h3 class="text-sm font-semibold mb-3">
            Open Positions ({{ openPositionData.length }})
          </h3>
          <UTable
            :columns="openPositionColumns"
            :data="openPositionData"
          >
            <template #symbol-data="{ row }">
              <UBadge color="warning" variant="subtle">
                {{ row.original.symbol }}
              </UBadge>
            </template>

            <template #entryPrice-data="{ row }">
              <span>${{ row.original.entryPrice }}</span>
            </template>

            <template #dollarProfit-data="{ row }">
              <span
                :class="[
                  'font-semibold',
                  row.original.dollarProfit >= 0 ? 'text-success' : 'text-error'
                ]"
              >
                {{ row.original.dollarProfit >= 0 ? '+' : '' }}${{ Number(row.original.dollarProfit).toFixed(2) }}
                <span class="text-xs text-muted ml-1">
                  ({{ row.original.portfolioReturnPct >= 0 ? '+' : '' }}{{ Number(row.original.portfolioReturnPct).toFixed(2) }}%)
                </span>
              </span>
            </template>
          </UTable>
        </div>
      </div>
    </template>
  </UModal>
</template>
