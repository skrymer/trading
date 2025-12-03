<script setup lang="ts">
import { format } from 'date-fns'
import { h, resolveComponent } from 'vue'
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Portfolio, PortfolioTrade, PortfolioStats, Stock } from '~/types'

const portfolio = ref<Portfolio | null>(null)
const portfolios = ref<Portfolio[]>([])
const stats = ref<PortfolioStats | null>(null)
const openTrades = ref<PortfolioTrade[]>([])
const closedTrades = ref<PortfolioTrade[]>([])
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const isLoadingPortfolios = ref(true)
const isCreatePortfolioModalOpen = ref(false)
const isOpenTradeModalOpen = ref(false)
const isEditTradeModalOpen = ref(false)
const isCloseTradeModalOpen = ref(false)
const isDeleteTradeModalOpen = ref(false)
const isDeletePortfolioModalOpen = ref(false)
const isRollTradeModalOpen = ref(false)
const isRollChainModalOpen = ref(false)
const isOpeningTrade = ref(false)
const isEditingTrade = ref(false)
const isClosingTrade = ref(false)
const isDeletingTrade = ref(false)
const isRefreshingStocks = ref(false)
const selectedTrade = ref<PortfolioTrade | null>(null)
const groupRolledTrades = ref(false)

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
      portfolio.value = allPortfolios[0] ?? null
      await loadPortfolioData()
    }
  } catch (error) {
    console.error('Error loading portfolios:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load portfolios',
      icon: 'i-lucide-alert-circle',
      color: 'error'
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
async function createPortfolio(data: { name: string, initialBalance: number, currency: string }) {
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
      color: 'success'
    })
  } catch (error) {
    console.error('Error creating portfolio:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to create portfolio',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

// Load portfolio data
async function loadPortfolioData() {
  if (!portfolio.value?.id) return

  status.value = 'pending'
  try {
    const [statsData, openTradesData, closedTradesData] = await Promise.all([
      $fetch<PortfolioStats>(`/udgaard/api/portfolio/${portfolio.value.id}/stats?groupRolledTrades=${groupRolledTrades.value}`),
      $fetch<PortfolioTrade[]>(`/udgaard/api/portfolio/${portfolio.value.id}/trades?status=OPEN`),
      $fetch<PortfolioTrade[]>(`/udgaard/api/portfolio/${portfolio.value.id}/trades?status=CLOSED`)
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
      color: 'error'
    })
  }
}

// Watch for groupRolledTrades changes and reload stats
watch(groupRolledTrades, () => {
  loadPortfolioData()
})

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
      color: 'success'
    })
  } catch (error) {
    console.error('Error opening trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to open trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    isOpeningTrade.value = false
  }
}

// Edit trade
async function editTrade(data: {
  tradeId: string
  symbol: string
  entryPrice: number
  entryDate: string
  quantity: number
  entryStrategy: string
  exitStrategy: string
  underlyingSymbol?: string
  instrumentType: string
  optionType?: string
  strikePrice?: number
  expirationDate?: string
  contracts?: number
  multiplier?: number
  entryIntrinsicValue?: number
  entryExtrinsicValue?: number
}) {
  if (!portfolio.value?.id) return

  isEditingTrade.value = true
  try {
    await $fetch(`/udgaard/api/portfolio/${portfolio.value.id}/trades/${data.tradeId}`, {
      method: 'PUT',
      body: {
        symbol: data.symbol,
        entryPrice: data.entryPrice,
        entryDate: data.entryDate,
        quantity: data.quantity,
        entryStrategy: data.entryStrategy,
        exitStrategy: data.exitStrategy,
        underlyingSymbol: data.underlyingSymbol,
        instrumentType: data.instrumentType,
        optionType: data.optionType,
        strikePrice: data.strikePrice,
        expirationDate: data.expirationDate,
        contracts: data.contracts,
        multiplier: data.multiplier,
        entryIntrinsicValue: data.entryIntrinsicValue,
        entryExtrinsicValue: data.entryExtrinsicValue
      }
    })

    isEditTradeModalOpen.value = false
    selectedTrade.value = null
    await loadPortfolioData()

    toast.add({
      title: 'Trade Updated',
      description: `${data.symbol} position updated successfully`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (error) {
    console.error('Error updating trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to update trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    isEditingTrade.value = false
  }
}

// Delete trade
async function deleteTrade(tradeId: string) {
  if (!portfolio.value?.id) return

  isDeletingTrade.value = true
  try {
    await $fetch(`/udgaard/api/portfolio/${portfolio.value.id}/trades/${tradeId}`, {
      method: 'DELETE'
    })

    isDeleteTradeModalOpen.value = false
    selectedTrade.value = null
    await loadPortfolioData()

    toast.add({
      title: 'Trade Deleted',
      description: 'Position deleted successfully',
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (error) {
    console.error('Error deleting trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to delete trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    isDeletingTrade.value = false
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
      color: 'success'
    })
  } catch (error) {
    console.error('Error closing trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to close trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
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
      color: 'success'
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
      color: 'error'
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
      color: 'info'
    })
    return
  }

  const symbols = [...new Set(openTrades.value.map(trade => trade.symbol))]

  toast.add({
    title: 'Refreshing Stocks',
    description: `Updating ${symbols.length} stock(s)...`,
    icon: 'i-lucide-refresh-cw',
    color: 'info'
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
      color: 'success'
    })
  } catch (error) {
    console.error('Error refreshing stocks:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to refresh stocks',
      icon: 'i-lucide-alert-circle',
      color: 'error'
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
    // Trade actions
    menuItems.push([{
      label: 'Open Trade',
      icon: 'i-lucide-trending-up',
      onSelect: () => { isOpenTradeModalOpen.value = true }
    }])

    // Portfolio management actions
    menuItems.push([
      {
        label: 'Create Portfolio',
        icon: 'i-lucide-plus',
        onSelect: () => { isCreatePortfolioModalOpen.value = true }
      },
      {
        label: 'Delete Portfolio',
        icon: 'i-lucide-trash-2',
        onSelect: () => { isDeletePortfolioModalOpen.value = true }
      }
    ])
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

// Fetch current prices for open trade symbols
const stockPrices = ref<Record<string, number>>({})
const fetchingPrices = ref(false)

async function fetchCurrentPrices() {
  if (!openTrades.value.length) return

  fetchingPrices.value = true
  try {
    const symbols = [...new Set(openTrades.value.map(trade => trade.underlyingSymbol || trade.symbol))]

    const pricePromises = symbols.map(async (symbol) => {
      try {
        const stock = await $fetch<Stock>(`/udgaard/api/stocks/${symbol}`)
        const latestQuote = stock.quotes && stock.quotes.length > 0
          ? stock.quotes[stock.quotes.length - 1]
          : null
        return { symbol, price: latestQuote?.closePrice || 0 }
      } catch (error) {
        console.error(`Failed to fetch price for ${symbol}:`, error)
        return { symbol, price: 0 }
      }
    })

    const prices = await Promise.all(pricePromises)
    stockPrices.value = prices.reduce((acc, { symbol, price }) => {
      acc[symbol] = price
      return acc
    }, {} as Record<string, number>)
  } catch (error) {
    console.error('Failed to fetch current prices:', error)
  } finally {
    fetchingPrices.value = false
  }
}

// Watch for open trades changes and fetch prices
watch(() => openTrades.value, () => {
  if (openTrades.value.length > 0) {
    fetchCurrentPrices()
  }
}, { immediate: true })

// Calculate what stats would be if open trades were closed today
const projectedStats = computed(() => {
  if (!stats.value || !openTrades.value.length || !portfolio.value) {
    return null
  }

  // Simulate closing all open trades at current prices
  const simulatedClosedTrades = openTrades.value.map(trade => {
    const symbol = trade.underlyingSymbol || trade.symbol
    const currentPrice = stockPrices.value[symbol] || 0

    if (currentPrice === 0) {
      return null // Skip if price not available
    }

    let profit = 0
    let profitPercentage = 0

    if (trade.instrumentType === 'OPTION') {
      const entryValue = trade.entryPrice * (trade.contracts || trade.quantity) * (trade.multiplier || 100)
      const exitValue = currentPrice * (trade.contracts || trade.quantity) * (trade.multiplier || 100)
      profit = exitValue - entryValue
      profitPercentage = (profit / entryValue) * 100
    } else {
      const entryValue = trade.entryPrice * trade.quantity
      const exitValue = currentPrice * trade.quantity
      profit = exitValue - entryValue
      profitPercentage = (profit / entryValue) * 100
    }

    return {
      profit,
      profitPercentage
    }
  }).filter(t => t !== null)

  // Combine actual closed trades with simulated ones
  const allTrades = [
    ...closedTrades.value.map(t => ({
      profit: t.profit || 0,
      profitPercentage: t.profitPercentage || 0
    })),
    ...simulatedClosedTrades
  ]

  if (allTrades.length === 0) {
    return null
  }

  // Calculate stats
  const wins = allTrades.filter(t => t.profit > 0)
  const losses = allTrades.filter(t => t.profit < 0)

  const numberOfWins = wins.length
  const numberOfLosses = losses.length
  const totalTrades = allTrades.length
  const winRate = (numberOfWins / totalTrades) * 100

  const avgWin = wins.length > 0
    ? wins.reduce((sum, t) => sum + t.profitPercentage, 0) / wins.length
    : 0

  const avgLoss = losses.length > 0
    ? losses.reduce((sum, t) => sum + t.profitPercentage, 0) / losses.length
    : 0

  const lossRate = 100 - winRate
  const provenEdge = (winRate / 100 * avgWin) - (lossRate / 100 * Math.abs(avgLoss))

  const totalProfit = allTrades.reduce((sum, t) => sum + t.profit, 0)
  const totalProfitPercentage = (totalProfit / portfolio.value.initialBalance) * 100

  const largestWin = wins.length > 0 ? Math.max(...wins.map(t => t.profitPercentage)) : 0
  const largestLoss = losses.length > 0 ? Math.min(...losses.map(t => t.profitPercentage)) : 0

  return {
    totalTrades,
    openTrades: openTrades.value.length,
    closedTrades: closedTrades.value.length,
    numberOfWins,
    numberOfLosses,
    winRate,
    avgWin,
    avgLoss,
    provenEdge,
    totalProfit,
    totalProfitPercentage,
    largestWin,
    largestLoss,
    ytdReturn: stats.value.ytdReturn, // Keep original for now
    annualizedReturn: stats.value.annualizedReturn // Keep original for now
  }
})

// Toggle for showing projected stats
const showOpenTradesStats = ref(false)

// Display stats based on toggle
const displayStats = computed(() => {
  if (showOpenTradesStats.value && projectedStats.value) {
    return projectedStats.value
  }
  return stats.value
})

// Calculate projected balance if open trades were closed today
const projectedBalance = computed(() => {
  if (!portfolio.value || !projectedStats.value || !stats.value) {
    return null
  }

  // Calculate unrealized P/L from open trades (difference between projected and actual total profit)
  const unrealizedPL = projectedStats.value.totalProfit - stats.value.totalProfit

  // Add unrealized P/L to current balance
  return portfolio.value.currentBalance + unrealizedPL
})

// Open edit trade modal
function openEditTradeModal(trade: PortfolioTrade) {
  selectedTrade.value = trade
  isEditTradeModalOpen.value = true
}

// Open delete trade modal
function openDeleteTradeModal(trade: PortfolioTrade) {
  selectedTrade.value = trade
  isDeleteTradeModalOpen.value = true
}

// Open close trade modal
function openCloseTradeModal(trade: PortfolioTrade) {
  selectedTrade.value = trade
  isCloseTradeModalOpen.value = true
}

// Open roll trade modal
function openRollTradeModal(trade: PortfolioTrade) {
  selectedTrade.value = trade
  isRollTradeModalOpen.value = true
}

// Open roll chain modal
function openRollChainModal(trade: PortfolioTrade) {
  selectedTrade.value = trade
  isRollChainModalOpen.value = true
}

// Handle trade rolled
async function handleTradeRolled() {
  isRollTradeModalOpen.value = false
  selectedTrade.value = null
  await loadPortfolioData()

  toast.add({
    title: 'Position Rolled',
    description: 'Position rolled successfully',
    icon: 'i-lucide-check-circle',
    color: 'success'
  })
}

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UIcon = resolveComponent('UIcon')

// Type for open trades table data
interface OpenTradeTableRow {
  symbol: string
  instrumentType: string
  optionsInfo?: string
  status: string
  entry: { price: string, date: string }
  quantity: string | number
  value: string
  strategies: { entry: string, exit: string }
  trade: PortfolioTrade
}

// Expanded rows state for open trades table
const expandedRows = ref<Record<string, boolean>>({})

// Table columns for open trades
const openTradesColumns: TableColumn<OpenTradeTableRow>[] = [
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
  {
    id: 'symbol',
    header: 'Symbol',
    cell: ({ row }: { row: any }) => {
      const elements = [
        h('div', {}, [
          h('p', { class: 'font-medium' }, row.original.symbol),
          row.original.optionsInfo ? h('p', { class: 'text-xs text-muted' }, row.original.optionsInfo) : null
        ])
      ]

      // Add roll badge if this trade is part of a roll chain
      if (row.original.trade.rollNumber && row.original.trade.rollNumber > 0) {
        elements.push(h(UBadge, {
          color: 'info',
          size: 'xs',
          label: `Roll #${row.original.trade.rollNumber}`
        }))
      }

      return h('div', { class: 'flex items-center gap-2' }, elements)
    }
  },
  {
    id: 'type',
    header: 'Type',
    cell: ({ row }: { row: any }) => h(UBadge, {
      variant: 'subtle',
      color: row.original.instrumentType === 'OPTION' ? 'info' : row.original.instrumentType === 'LEVERAGED_ETF' ? 'warning' : 'neutral',
      label: row.original.instrumentType === 'OPTION' ? 'Option' : row.original.instrumentType === 'LEVERAGED_ETF' ? 'ETF' : 'Stock'
    })
  },
  {
    id: 'entry',
    header: 'Entry',
    cell: ({ row }: { row: any }) => h('div', {}, [
      h('p', { class: 'font-medium' }, row.original.entry.price),
      h('p', { class: 'text-xs text-muted' }, row.original.entry.date)
    ])
  },
  {
    id: 'quantity',
    header: 'Qty',
    cell: ({ row }: { row: any }) => h(UBadge, {
      variant: 'subtle',
      color: 'neutral'
    }, () => row.original.quantity)
  },
  { accessorKey: 'value', header: 'Value' },
  {
    id: 'strategies',
    header: 'Strategies',
    cell: ({ row }: { row: any }) => h('div', { class: 'text-xs' }, [
      h('p', { class: 'text-muted' }, row.original.strategies.entry),
      h('p', { class: 'text-muted' }, `→ ${row.original.strategies.exit}`)
    ])
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }: { row: any }) => {
      const buttons = []
      const trade = row.original.trade

      // Roll chain button (for any trade that's part of a roll chain)
      if (trade.parentTradeId || trade.rolledToTradeId) {
        buttons.push(h(UButton, {
          icon: 'i-lucide-link',
          size: 'sm',
          color: 'info',
          variant: 'ghost',
          square: true,
          onClick: () => openRollChainModal(trade)
        }))
      }

      // Roll button (only for open option trades)
      if (trade.instrumentType === 'OPTION') {
        buttons.push(h(UButton, {
          icon: 'i-lucide-repeat',
          size: 'sm',
          color: 'warning',
          variant: 'ghost',
          square: true,
          onClick: () => openRollTradeModal(trade)
        }))
      }

      // Edit button
      buttons.push(h(UButton, {
        icon: 'i-lucide-pencil',
        size: 'sm',
        color: 'neutral',
        variant: 'ghost',
        square: true,
        onClick: () => openEditTradeModal(trade)
      }))

      // Delete button
      buttons.push(h(UButton, {
        icon: 'i-lucide-trash-2',
        size: 'sm',
        color: 'error',
        variant: 'ghost',
        square: true,
        onClick: () => openDeleteTradeModal(trade)
      }))

      // Close button
      buttons.push(h(UButton, {
        icon: 'i-lucide-x-circle',
        size: 'sm',
        color: 'success',
        variant: 'ghost',
        square: true,
        onClick: () => openCloseTradeModal(trade)
      }))

      return h('div', { class: 'flex justify-end gap-1' }, buttons)
    }
  }
]

// Table data for open trades
const openTradesTableData = computed(() => {
  return openTrades.value.map((trade) => {
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
            :items="portfolios.map((p: Portfolio) => ({ label: p.name, value: p.id }))"
            value-key="value"
            class="w-64"
            placeholder="Select Portfolio"
            @update:model-value="(id: number) => selectPortfolio(portfolios.find((p: Portfolio) => Number(p.id) === id)!)"
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
        <h3 class="text-lg font-semibold mb-2">
          Loading Portfolios
        </h3>
        <p class="text-muted text-center">
          Please wait while we fetch your portfolios...
        </p>
      </div>

      <!-- No Portfolio State -->
      <div v-else-if="!portfolio" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-briefcase" class="w-16 h-16 text-muted mb-4" />
        <h3 class="text-lg font-semibold mb-2">
          No Portfolio
        </h3>
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
        <!-- Projected Metrics and Group Rolled Trades Toggles -->
        <div class="flex items-center justify-end gap-6">
          <div v-if="openTrades.length > 0 && projectedStats" class="flex items-center gap-2">
            <label class="text-sm text-muted cursor-pointer" for="show-open-trades">
              Show Projected Metrics
            </label>
            <input
              id="show-open-trades"
              v-model="showOpenTradesStats"
              type="checkbox"
              class="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600 cursor-pointer"
            >
          </div>
          <div class="flex items-center gap-2">
            <label class="text-sm text-muted cursor-pointer" for="group-rolled-trades">
              Group Rolled Trades
            </label>
            <input
              id="group-rolled-trades"
              v-model="groupRolledTrades"
              type="checkbox"
              class="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600 cursor-pointer"
            >
          </div>
        </div>

        <!-- Info Note -->
        <div v-if="showOpenTradesStats && projectedStats" class="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
          <div class="flex items-start gap-2">
            <UIcon name="i-lucide-info" class="w-4 h-4 text-blue-600 mt-0.5 flex-shrink-0" />
            <p class="text-sm text-blue-900 dark:text-blue-100">
              <strong>Projected Metrics:</strong> All metrics below (including balance and profit) show what your portfolio performance would be if all <strong>{{ openTrades.length }} open position{{ openTrades.length !== 1 ? 's' : '' }}</strong> were closed at today's market prices. The projected balance includes unrealized P/L.
            </p>
          </div>
        </div>

        <!-- Portfolio Header Card -->
        <UCard>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <h2 class="text-2xl font-bold">
                  {{ portfolio.name }}
                </h2>
                <p class="text-muted text-sm">
                  {{ portfolio.currency }}
                </p>
              </div>
              <div class="text-right">
                <p class="text-sm text-muted">
                  {{ showOpenTradesStats && projectedBalance ? 'Projected Balance' : 'Current Balance' }}
                </p>
                <p class="text-2xl font-bold">
                  {{ formatCurrency(showOpenTradesStats && projectedBalance ? projectedBalance : portfolio.currentBalance, portfolio.currency) }}
                </p>
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4 pt-4 border-t">
              <div>
                <p class="text-sm text-muted">
                  Initial Balance
                </p>
                <p class="font-semibold">
                  {{ formatCurrency(portfolio.initialBalance, portfolio.currency) }}
                </p>
              </div>
              <div>
                <p class="text-sm text-muted">
                  {{ showOpenTradesStats && projectedBalance ? 'Projected Profit' : 'Total Profit' }}
                </p>
                <p class="font-semibold" :class="(showOpenTradesStats && projectedBalance ? projectedBalance : portfolio.currentBalance) >= portfolio.initialBalance ? 'text-green-600' : 'text-red-600'">
                  {{ formatCurrency((showOpenTradesStats && projectedBalance ? projectedBalance : portfolio.currentBalance) - portfolio.initialBalance, portfolio.currency) }}
                  ({{ formatPercentage((((showOpenTradesStats && projectedBalance ? projectedBalance : portfolio.currentBalance) - portfolio.initialBalance) / portfolio.initialBalance) * 100) }})
                </p>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Statistics Cards -->
        <div v-if="stats">
          <h3 class="text-sm font-semibold text-muted mb-3">
            Portfolio Statistics
          </h3>

          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <UCard>
            <div>
              <p class="text-sm text-muted">
                Total Trades
              </p>
              <p class="text-2xl font-bold">
                {{ displayStats?.totalTrades }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                Win Rate
              </p>
              <p class="text-2xl font-bold">
                {{ displayStats?.winRate.toFixed(1) }}%
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                YTD Return
              </p>
              <p class="text-2xl font-bold" :class="(displayStats?.ytdReturn ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatPercentage(displayStats?.ytdReturn ?? 0) }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                Proven Edge
              </p>
              <p class="text-2xl font-bold" :class="(displayStats?.provenEdge ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatPercentage(displayStats?.provenEdge ?? 0) }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                Avg Win
              </p>
              <p class="text-2xl font-bold text-green-600">
                {{ formatPercentage(displayStats?.avgWin ?? 0) }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                Avg Loss
              </p>
              <p class="text-2xl font-bold text-red-600">
                {{ formatPercentage(displayStats?.avgLoss ?? 0) }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                Open Trades
              </p>
              <p class="text-2xl font-bold">
                {{ displayStats?.openTrades }}
              </p>
            </div>
          </UCard>

          <UCard>
            <div>
              <p class="text-sm text-muted">
                Closed Trades
              </p>
              <p class="text-2xl font-bold">
                {{ displayStats?.closedTrades }}
              </p>
            </div>
          </UCard>
          </div>
        </div>

        <!-- Open Trades -->
        <UCard>
          <template #header>
            <div class="flex items-center justify-between">
              <h3 class="text-lg font-semibold">
                Open Trades
              </h3>
              <div class="flex gap-2">
                <UButton
                  icon="i-lucide-refresh-cw"
                  size="sm"
                  color="neutral"
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
                <UBadge color="blue" size="xs">
                  {{ row.original.status }}
                </UBadge>
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
            <h3 class="text-lg font-semibold">
              Recent Closed Trades
            </h3>
          </template>

          <div v-if="closedTrades.length === 0" class="text-center py-8 text-muted">
            No closed trades
          </div>

          <div v-else class="space-y-2">
            <div
              v-for="trade in closedTrades.slice(0, 10)"
              :key="trade.id"
              class="flex items-center justify-between p-4 border rounded-lg"
            >
              <div class="flex-1">
                <div class="flex items-center gap-2">
                  <p class="font-semibold">
                    {{ trade.symbol }}
                  </p>
                  <UBadge color="neutral" size="xs">
                    {{ trade.status }}
                  </UBadge>
                </div>
                <p class="text-sm text-muted">
                  {{ format(new Date(trade.entryDate), 'MMM dd') }} →
                  {{ trade.exitDate ? format(new Date(trade.exitDate), 'MMM dd, yyyy') : 'N/A' }}
                </p>
                <p class="text-xs text-muted">
                  {{ formatCurrency(trade.entryPrice, trade.currency) }} →
                  {{ trade.exitPrice ? formatCurrency(trade.exitPrice, trade.currency) : 'N/A' }}
                </p>
              </div>
              <div class="text-right">
                <p class="font-semibold" :class="(trade.profitPercentage || 0) >= 0 ? 'text-green-600' : 'text-red-600'">
                  {{ trade.profitPercentage ? formatPercentage(trade.profitPercentage) : 'N/A' }}
                </p>
                <p class="text-sm text-muted">
                  {{ trade.profit ? formatCurrency(trade.profit, trade.currency) : 'N/A' }}
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
    :current-balance="portfolio.currentBalance"
    :portfolio-created-date="portfolio.createdDate"
    :loading="isOpeningTrade"
    @open-trade="openTrade"
  />

  <!-- Edit Trade Modal -->
  <PortfolioEditTradeModal
    v-if="selectedTrade && portfolio"
    v-model:open="isEditTradeModalOpen"
    :trade="selectedTrade"
    :currency="portfolio.currency"
    :loading="isEditingTrade"
    @update-trade="editTrade"
  />

  <!-- Delete Trade Modal -->
  <PortfolioDeleteTradeModal
    v-if="selectedTrade"
    v-model:open="isDeleteTradeModalOpen"
    :trade="selectedTrade"
    :loading="isDeletingTrade"
    @delete-trade="deleteTrade"
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

  <!-- Roll Trade Modal -->
  <PortfolioRollTradeModal
    v-if="selectedTrade && portfolio"
    v-model:open="isRollTradeModalOpen"
    :trade="selectedTrade"
    :portfolio-id="Number(portfolio.id)"
    @rolled="handleTradeRolled"
  />

  <!-- Roll Chain Modal -->
  <PortfolioRollChainModal
    v-if="selectedTrade && portfolio"
    v-model:open="isRollChainModalOpen"
    :trade="selectedTrade"
    :portfolio-id="Number(portfolio.id)"
  />
</template>
