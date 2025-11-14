<script setup lang="ts">
import { format } from 'date-fns'
import { h, resolveComponent } from 'vue'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Portfolio, PortfolioTrade, PortfolioStats, PortfolioTradeResponse } from '~/types'
import type { TableColumn } from '@nuxt/ui'

const portfolio = ref<Portfolio | null>(null)
const portfolios = ref<Portfolio[]>([])
const stats = ref<PortfolioStats | null>(null)
const openTrades = ref<PortfolioTradeResponse[]>([])
const closedTrades = ref<PortfolioTradeResponse[]>([])
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const isLoadingPortfolios = ref(true)
const isCreatePortfolioModalOpen = ref(false)
const isOpenTradeModalOpen = ref(false)
const isCloseTradeModalOpen = ref(false)
const isDeletePortfolioModalOpen = ref(false)
const isOpeningTrade = ref(false)
const isClosingTrade = ref(false)
const isRefreshingStocks = ref(false)
const selectedTrade = ref<PortfolioTrade | null>(null)

const toast = useToast()

// Load all portfolios on mount
onMounted(async () => {
  await loadPortfolios()
})

// Load all portfolios
async function loadPortfolios() {
  try {
    const allPortfolios = await $fetch<Portfolio[]>('/udgaard/api/portfolio')
    portfolios.value = allPortfolios

    // Auto-select the first portfolio if available
    if (allPortfolios.length > 0 && !portfolio.value) {
      portfolio.value = allPortfolios[0]
      await loadPortfolioData()
    }
  } catch (error) {
    console.error('Error loading portfolios:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load portfolios',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  } finally {
    isLoadingPortfolios.value = false
  }
}

// Switch to a different portfolio
async function selectPortfolio(selectedPortfolio: Portfolio) {
  portfolio.value = selectedPortfolio
  await loadPortfolioData()
}

// Create portfolio
async function createPortfolio(data: { name: string; initialBalance: number; currency: string }) {
  try {
    const newPortfolio = await $fetch<Portfolio>('/udgaard/api/portfolio', {
      method: 'POST',
      body: data
    })
    portfolio.value = newPortfolio
    isCreatePortfolioModalOpen.value = false

    // Reload portfolios list
    await loadPortfolios()
    await loadPortfolioData()

    toast.add({
      title: 'Portfolio Created',
      description: `${data.name} created successfully`,
      icon: 'i-lucide-check-circle',
      color: 'green'
    })
  } catch (error) {
    console.error('Error creating portfolio:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to create portfolio',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  }
}

// Load portfolio data
async function loadPortfolioData() {
  if (!portfolio.value?.id) return

  status.value = 'pending'
  try {
    const [statsData, openTradesData, closedTradesData] = await Promise.all([
      $fetch<PortfolioStats>(`/udgaard/api/portfolio/${portfolio.value.id}/stats`),
      $fetch<PortfolioTradeResponse[]>(`/udgaard/api/portfolio/${portfolio.value.id}/trades?status=OPEN`),
      $fetch<PortfolioTradeResponse[]>(`/udgaard/api/portfolio/${portfolio.value.id}/trades?status=CLOSED`)
    ])

    stats.value = statsData
    openTrades.value = openTradesData
    closedTrades.value = closedTradesData
    status.value = 'success'
  } catch (error) {
    console.error('Error loading portfolio data:', error)
    status.value = 'error'
    toast.add({
      title: 'Error',
      description: 'Failed to load portfolio data',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  }
}

// Open trade
async function openTrade(data: {
  symbol: string
  entryPrice: number
  entryDate: string
  quantity: number
  entryStrategy: string
  exitStrategy: string
  currency: string
}) {
  if (!portfolio.value?.id) return

  isOpeningTrade.value = true
  try {
    await $fetch(`/udgaard/api/portfolio/${portfolio.value.id}/trades`, {
      method: 'POST',
      body: data
    })

    isOpenTradeModalOpen.value = false
    await loadPortfolioData()

    toast.add({
      title: 'Trade Opened',
      description: `${data.symbol} position opened`,
      icon: 'i-lucide-check-circle',
      color: 'green'
    })
  } catch (error) {
    console.error('Error opening trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to open trade',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  } finally {
    isOpeningTrade.value = false
  }
}

// Close trade
async function closeTrade(tradeId: string, exitPrice: number, exitDate: string) {
  if (!portfolio.value?.id) return

  isClosingTrade.value = true
  try {
    await $fetch(`/udgaard/api/portfolio/${portfolio.value.id}/trades/${tradeId}/close`, {
      method: 'PUT',
      body: { exitPrice, exitDate }
    })

    isCloseTradeModalOpen.value = false
    selectedTrade.value = null
    await loadPortfolioData()

    toast.add({
      title: 'Trade Closed',
      description: 'Position closed successfully',
      icon: 'i-lucide-check-circle',
      color: 'green'
    })
  } catch (error) {
    console.error('Error closing trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to close trade',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  } finally {
    isClosingTrade.value = false
  }
}

// Delete portfolio
async function deletePortfolio() {
  if (!portfolio.value?.id) return

  try {
    await $fetch(`/udgaard/api/portfolio/${portfolio.value.id}`, {
      method: 'DELETE'
    })

    isDeletePortfolioModalOpen.value = false

    toast.add({
      title: 'Portfolio Deleted',
      description: `${portfolio.value.name} has been deleted`,
      icon: 'i-lucide-check-circle',
      color: 'green'
    })

    // Reset portfolio and reload
    portfolio.value = null
    stats.value = null
    openTrades.value = []
    closedTrades.value = []
    await loadPortfolios()
  } catch (error) {
    console.error('Error deleting portfolio:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to delete portfolio',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  }
}

// Refresh stocks for open trades
async function refreshOpenTradeStocks() {
  if (!portfolio.value || openTrades.value.length === 0) {
    toast.add({
      title: 'No Open Trades',
      description: 'There are no open trades to refresh',
      icon: 'i-lucide-info',
      color: 'blue'
    })
    return
  }

  const symbols = [...new Set(openTrades.value.map(tradeResponse => tradeResponse.trade.symbol))]

  toast.add({
    title: 'Refreshing Stocks',
    description: `Updating ${symbols.length} stock(s)...`,
    icon: 'i-lucide-refresh-cw',
    color: 'blue'
  })

  isRefreshingStocks.value = true
  try {
    await $fetch('/udgaard/api/stocks/refresh', {
      method: 'POST',
      body: symbols
    })

    toast.add({
      title: 'Success',
      description: `Refreshed ${symbols.length} stock(s)`,
      icon: 'i-lucide-check-circle',
      color: 'green'
    })
  } catch (error) {
    console.error('Error refreshing stocks:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to refresh stocks',
      icon: 'i-lucide-alert-circle',
      color: 'red'
    })
  } finally {
    isRefreshingStocks.value = false
  }
}

// Menu items
const items = computed(() => {
  const menuItems: DropdownMenuItem[][] = []

  if (!portfolio.value) {
    menuItems.push([{
      label: 'Create Portfolio',
      icon: 'i-lucide-plus',
      onSelect: () => { isCreatePortfolioModalOpen.value = true }
    }])
  } else {
    menuItems.push([{
      label: 'Open Trade',
      icon: 'i-lucide-trending-up',
      onSelect: () => { isOpenTradeModalOpen.value = true }
    }])
    menuItems.push([{
      label: 'Delete Portfolio',
      icon: 'i-lucide-trash-2',
      onSelect: () => { isDeletePortfolioModalOpen.value = true }
    }])
  }

  return menuItems
})

// Format currency
function formatCurrency(value: number, currency: string = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency
  }).format(value)
}

// Format percentage
function formatPercentage(value: number) {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

// Open close trade modal
function openCloseTradeModal(trade: PortfolioTrade) {
  selectedTrade.value = trade
  isCloseTradeModalOpen.value = true
}

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UIcon = resolveComponent('UIcon')

// Expanded rows state for open trades table
const expandedRows = ref<Record<string, boolean>>({})

// Table columns for open trades
const openTradesColumns: TableColumn[] = [
  {
    id: 'expand',
    header: '',
    cell: ({ row }) => h('button', {
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
  {
    id: 'symbol',
    header: 'Symbol',
    cell: ({ row }) => h('div', { class: 'flex items-center gap-2' }, [
      h('div', {}, [
        h('p', { class: 'font-medium' }, row.original.symbol),
        row.original.optionsInfo ? h('p', { class: 'text-xs text-muted' }, row.original.optionsInfo) : null
      ]),
      row.original.hasExitSignal ? h(UBadge, {
        variant: 'subtle',
        color: 'warning',
        label: 'Exit Signal',
        class: 'cursor-help',
        title: row.original.exitSignalReason || 'Exit signal detected'
      }) : null
    ])
  },
  {
    id: 'type',
    header: 'Type',
    cell: ({ row }) => h(UBadge, {
      variant: 'subtle',
      color: row.original.instrumentType === 'OPTION' ? 'info' : row.original.instrumentType === 'LEVERAGED_ETF' ? 'warning' : 'neutral',
      label: row.original.instrumentType === 'OPTION' ? 'Option' : row.original.instrumentType === 'LEVERAGED_ETF' ? 'ETF' : 'Stock'
    })
  },
  {
    id: 'entry',
    header: 'Entry',
    cell: ({ row }) => h('div', {}, [
      h('p', { class: 'font-medium' }, row.original.entry.price),
      h('p', { class: 'text-xs text-muted' }, row.original.entry.date)
    ])
  },
  {
    id: 'quantity',
    header: 'Qty',
    cell: ({ row }) => h(UBadge, {
      variant: 'subtle',
      color: 'neutral'
    }, () => row.original.quantity)
  },
  { accessorKey: 'value', header: 'Value' },
  {
    id: 'strategies',
    header: 'Strategies',
    cell: ({ row }) => h('div', { class: 'text-xs' }, [
      h('p', { class: 'text-muted' }, row.original.strategies.entry),
      h('p', { class: 'text-muted' }, `→ ${row.original.strategies.exit}`)
    ])
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h('div', { class: 'flex justify-end' }, [
      h(UButton, {
        icon: 'i-lucide-x-circle',
        size: 'sm',
        color: 'red',
        variant: 'ghost',
        square: true,
        onClick: () => openCloseTradeModal(row.original.trade)
      })
    ])
  }
]

// Table data for open trades
const openTradesTableData = computed(() => {
  return openTrades.value.map(tradeResponse => {
    const trade = tradeResponse.trade
    let optionsInfo = ''
    let positionValue = 0

    if (trade.instrumentType === 'OPTION') {
      const strikeDisplay = trade.strikePrice ? `$${trade.strikePrice.toFixed(2)}` : ''
      const expiryDisplay = trade.expirationDate ? format(new Date(trade.expirationDate), 'MMM dd') : ''
      optionsInfo = `${trade.optionType || ''} ${strikeDisplay} exp ${expiryDisplay}`
      positionValue = trade.entryPrice * (trade.contracts || trade.quantity) * (trade.multiplier || 100)
    } else {
      positionValue = trade.entryPrice * trade.quantity
    }

    return {
      symbol: trade.symbol,
      instrumentType: trade.instrumentType,
      optionsInfo: optionsInfo || undefined,
      status: trade.status,
      hasExitSignal: tradeResponse.hasExitSignal,
      exitSignalReason: tradeResponse.exitSignalReason,
      entry: {
        price: formatCurrency(trade.entryPrice, trade.currency),
        date: format(new Date(trade.entryDate), 'MMM dd, yyyy')
      },
      quantity: trade.instrumentType === 'OPTION' ? `${trade.contracts || trade.quantity}c` : trade.quantity,
      value: formatCurrency(positionValue, trade.currency),
      strategies: {
        entry: trade.entryStrategy,
        exit: trade.exitStrategy
      },
      trade: trade
    }
  })
})
</script>

<template>
  <UDashboardPanel id="portfolio">
    <template #header>
      <UDashboardNavbar title="Portfolio Manager" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <USelect
            v-if="portfolios.length > 0"
            :model-value="portfolio?.id"
            :items="portfolios.map(p => ({ label: p.name, value: p.id }))"
            value-key="value"
            class="w-64"
            placeholder="Select Portfolio"
            @update:model-value="(id) => selectPortfolio(portfolios.find(p => p.id === id)!)"
          />
          <UDropdownMenu :items="items">
            <UButton icon="i-lucide-plus" size="md" class="rounded-full" />
          </UDropdownMenu>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Loading State -->
      <div v-if="isLoadingPortfolios" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-loader-2" class="w-16 h-16 text-primary mb-4 animate-spin" />
        <h3 class="text-lg font-semibold mb-2">Loading Portfolios</h3>
        <p class="text-muted text-center">
          Please wait while we fetch your portfolios...
        </p>
      </div>

      <!-- No Portfolio State -->
      <div v-else-if="!portfolio" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-briefcase" class="w-16 h-16 text-muted mb-4" />
        <h3 class="text-lg font-semibold mb-2">No Portfolio</h3>
        <p class="text-muted text-center mb-4">
          Create a portfolio to start tracking your trades
        </p>
        <UButton
          label="Create Portfolio"
          icon="i-lucide-plus"
          @click="isCreatePortfolioModalOpen = true"
        />
      </div>

      <!-- Portfolio Content -->
      <div v-else class="grid gap-4">
        <!-- Portfolio Header Card -->
        <UCard>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <h2 class="text-2xl font-bold">{{ portfolio.name }}</h2>
                <p class="text-muted text-sm">{{ portfolio.currency }}</p>
              </div>
              <div class="text-right">
                <p class="text-sm text-muted">Current Balance</p>
                <p class="text-2xl font-bold">
                  {{ formatCurrency(portfolio.currentBalance, portfolio.currency) }}
                </p>
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4 pt-4 border-t">
              <div>
                <p class="text-sm text-muted">Initial Balance</p>
                <p class="font-semibold">
                  {{ formatCurrency(portfolio.initialBalance, portfolio.currency) }}
                </p>
              </div>
              <div>
                <p class="text-sm text-muted">Total Profit</p>
                <p class="font-semibold" :class="portfolio.currentBalance >= portfolio.initialBalance ? 'text-green-600' : 'text-red-600'">
                  {{ formatCurrency(portfolio.currentBalance - portfolio.initialBalance, portfolio.currency) }}
                  ({{ formatPercentage(((portfolio.currentBalance - portfolio.initialBalance) / portfolio.initialBalance) * 100) }})
                </p>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Statistics Cards -->
        <div v-if="stats" class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <UCard>
            <div>
              <p class="text-sm text-muted">Total Trades</p>
              <p class="text-2xl font-bold">{{ stats.totalTrades }}</p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">Win Rate</p>
              <p class="text-2xl font-bold">{{ stats.winRate.toFixed(1) }}%</p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">YTD Return</p>
              <p class="text-2xl font-bold" :class="stats.ytdReturn >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatPercentage(stats.ytdReturn) }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">Proven Edge</p>
              <p class="text-2xl font-bold" :class="stats.provenEdge >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatPercentage(stats.provenEdge) }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">Avg Win</p>
              <p class="text-2xl font-bold text-green-600">{{ formatPercentage(stats.avgWin) }}</p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">Avg Loss</p>
              <p class="text-2xl font-bold text-red-600">{{ formatPercentage(stats.avgLoss) }}</p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">Open Trades</p>
              <p class="text-2xl font-bold">{{ stats.openTrades }}</p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">Closed Trades</p>
              <p class="text-2xl font-bold">{{ stats.closedTrades }}</p>
            </div>
          </UCard>
        </div>

        <!-- Open Trades -->
        <UCard>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="text-lg font-semibold">Open Trades</h3>
              <div class="flex gap-2">
                <UButton
                  icon="i-lucide-refresh-cw"
                  size="sm"
                  color="gray"
                  variant="ghost"
                  :loading="isRefreshingStocks"
                  @click="refreshOpenTradeStocks"
                />
                <UButton
                  label="Open Trade"
                  icon="i-lucide-plus"
                  size="sm"
                  @click="isOpenTradeModalOpen = true"
                />
              </div>
            </div>
          </template>

          <div v-if="openTrades.length === 0" class="text-center py-8 text-muted">
            No open trades
          </div>

          <UTable
            v-else
            v-model:expanded="expandedRows"
            :columns="openTradesColumns"
            :data="openTradesTableData"
          >
            <template #symbol-data="{ row }">
              <div class="flex items-center gap-2">
                <div class="flex flex-col">
                  <span class="font-semibold">{{ row.original.symbol }}</span>
                  <span
                    v-if="row.original.trade.underlyingSymbol && row.original.trade.underlyingSymbol !== row.original.symbol"
                    class="text-xs text-muted"
                  >
                    Signals from {{ row.original.trade.underlyingSymbol }}
                  </span>
                </div>
                <UBadge color="blue" size="xs">{{ row.original.status }}</UBadge>
              </div>
            </template>

            <template #value-data="{ row }">
              <span class="font-semibold">{{ row.original.value }}</span>
            </template>

            <template #expanded="{ row }">
              <div class="p-4 bg-muted/30">
                <PortfolioOpenTradeChart :trade="row.original.trade" />
              </div>
            </template>
          </UTable>
        </UCard>

        <!-- Equity Curve -->
        <PortfolioEquityCurve
          v-if="portfolio.id && closedTrades.length > 0"
          :key="`equity-${closedTrades.length}`"
          :portfolio-id="portfolio.id"
          :loading="status === 'pending'"
        />

        <!-- Recent Closed Trades -->
        <UCard>
          <template #header>
            <h3 class="text-lg font-semibold">Recent Closed Trades</h3>
          </template>

          <div v-if="closedTrades.length === 0" class="text-center py-8 text-muted">
            No closed trades
          </div>

          <div v-else class="space-y-2">
            <div
              v-for="tradeResponse in closedTrades.slice(0, 10)"
              :key="tradeResponse.trade.id"
              class="flex items-center justify-between p-4 border rounded-lg"
            >
              <div class="flex-1">
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ tradeResponse.trade.symbol }}</p>
                  <UBadge color="gray" size="xs">{{ tradeResponse.trade.status }}</UBadge>
                </div>
                <p class="text-sm text-muted">
                  {{ format(new Date(tradeResponse.trade.entryDate), 'MMM dd') }} →
                  {{ tradeResponse.trade.exitDate ? format(new Date(tradeResponse.trade.exitDate), 'MMM dd, yyyy') : 'N/A' }}
                </p>
                <p class="text-xs text-muted">
                  {{ formatCurrency(tradeResponse.trade.entryPrice, tradeResponse.trade.currency) }} →
                  {{ tradeResponse.trade.exitPrice ? formatCurrency(tradeResponse.trade.exitPrice, tradeResponse.trade.currency) : 'N/A' }}
                </p>
              </div>
              <div class="text-right">
                <p class="font-semibold" :class="(tradeResponse.trade.profitPercentage || 0) >= 0 ? 'text-green-600' : 'text-red-600'">
                  {{ tradeResponse.trade.profitPercentage ? formatPercentage(tradeResponse.trade.profitPercentage) : 'N/A' }}
                </p>
                <p class="text-sm text-muted">
                  {{ tradeResponse.trade.profit ? formatCurrency(tradeResponse.trade.profit, tradeResponse.trade.currency) : 'N/A' }}
                </p>
              </div>
            </div>
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Create Portfolio Modal -->
  <PortfolioCreateModal
    v-model:open="isCreatePortfolioModalOpen"
    @create="createPortfolio"
  />

  <!-- Open Trade Modal -->
  <PortfolioOpenTradeModal
    v-if="portfolio"
    v-model:open="isOpenTradeModalOpen"
    :currency="portfolio.currency"
    :loading="isOpeningTrade"
    @open-trade="openTrade"
  />

  <!-- Close Trade Modal -->
  <PortfolioCloseTradeModal
    v-if="selectedTrade"
    v-model:open="isCloseTradeModalOpen"
    :trade="selectedTrade"
    :loading="isClosingTrade"
    @close-trade="closeTrade"
  />

  <!-- Delete Portfolio Modal -->
  <PortfolioDeleteModal
    v-if="portfolio && isDeletePortfolioModalOpen"
    v-model:open="isDeletePortfolioModalOpen"
    :portfolio="portfolio"
    @delete="deletePortfolio"
  />
</template>
