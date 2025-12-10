<script setup lang="ts">
import { format } from 'date-fns'
import { h, resolveComponent } from 'vue'
import type { Trade } from '~/types'
import type { TableColumn } from '@nuxt/ui'

const props = defineProps<{
  open: boolean
  date: string
  trades: Trade[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

// Expanded rows state
const expanded = ref<Record<string, boolean>>({})

// Reset expanded state when modal closes
watch(() => props.open, (isOpen) => {
  if (!isOpen) {
    expanded.value = {}
  }
})

const UIcon = resolveComponent('UIcon')

// Summary statistics
const totalProfit = computed(() => {
  return props.trades.reduce((sum, t) => sum + t.profitPercentage, 0)
})

const totalProfitDollar = computed(() => {
  return props.trades.reduce((sum, t) => sum + t.profit, 0)
})

const numberOfTrades = computed(() => props.trades.length)

const winningTrades = computed(() => {
  return props.trades.filter(t => t.profitPercentage > 0)
})

const losingTrades = computed(() => {
  return props.trades.filter(t => t.profitPercentage < 0)
})

const winRate = computed(() => {
  if (numberOfTrades.value === 0) return 0
  return (winningTrades.value.length / numberOfTrades.value) * 100
})

const bestTrade = computed(() => {
  if (props.trades.length === 0) return null
  return props.trades.reduce((best, trade) =>
    trade.profitPercentage > best.profitPercentage ? trade : best
  )
})

const worstTrade = computed(() => {
  if (props.trades.length <= 1) return null
  return props.trades.reduce((worst, trade) =>
    trade.profitPercentage < worst.profitPercentage ? trade : worst
  )
})

// Table configuration
const columns: TableColumn<any>[] = [
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
  { accessorKey: 'profit', header: 'Profit $' },
  { accessorKey: 'tradingDays', header: 'Days' },
  { accessorKey: 'entryPrice', header: 'Entry' },
  { accessorKey: 'exitPrice', header: 'Exit' },
  { accessorKey: 'exitDate', header: 'Exit Date' },
  { accessorKey: 'exitReason', header: 'Exit Reason' }
]

const tableData = computed(() => {
  return props.trades.map((trade) => {
    const exitQuote = trade.quotes?.[trade.quotes.length - 1]
    const exitDate = exitQuote?.date ? format(new Date(exitQuote.date), 'MMM dd, yyyy') : 'N/A'

    return {
      _trade: trade, // Keep reference for expanded chart
      stockSymbol: trade.stockSymbol,
      sector: trade.sector,
      entryPrice: trade.entryQuote?.closePrice?.toFixed(2) || 'N/A',
      exitPrice: exitQuote?.closePrice?.toFixed(2) || 'N/A',
      exitDate: exitDate,
      tradingDays: trade.tradingDays || 'N/A',
      profitPercentage: Number(trade.profitPercentage.toFixed(2)),
      profit: Number(trade.profit.toFixed(2)),
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
          <!-- Total Profit Card -->
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

          <!-- Win Rate Card -->
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

          <!-- Best Trade Card -->
          <UCard :ui="{ body: '!p-4' }">
            <div class="space-y-1">
              <p class="text-xs text-muted uppercase">
                Best Trade
              </p>
              <p class="text-2xl font-bold text-success">
                {{ bestTrade ? '+' + bestTrade.profitPercentage.toFixed(2) + '%' : 'N/A' }}
              </p>
              <p class="text-xs text-muted">
                {{ bestTrade?.stockSymbol || 'N/A' }}
              </p>
            </div>
          </UCard>

          <!-- Worst Trade Card -->
          <UCard :ui="{ body: '!p-4' }">
            <div class="space-y-1">
              <p class="text-xs text-muted uppercase">
                Worst Trade
              </p>
              <p class="text-2xl font-bold text-error">
                {{ worstTrade ? worstTrade.profitPercentage.toFixed(2) + '%' : 'N/A' }}
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
                <BacktestingTradeChart :trade="row.original._trade" />
              </div>
            </template>
          </UTable>
        </div>
      </div>
    </template>
  </UModal>
</template>
