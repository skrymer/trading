<script setup lang="ts">
import { h, resolveComponent, type Ref } from 'vue'
import type { Portfolio, Position, PortfolioStats, CreateFromBrokerResult, PortfolioSyncResult, PositionUnrealizedPnl, EquityCurveData, ForexSummary, ForexLot, ForexDisposal, CashTransaction, CashTransactionSummary } from '~/types'
import { usePositionFormatters } from '~/composables/usePositionFormatters'

const { formatOptionDetails, formatCurrency, formatDate } = usePositionFormatters()

const portfolio = ref<Portfolio | null>(null)
const portfolios = ref<Portfolio[]>([])
const stats = ref<PortfolioStats | null>(null)
const openPositions = ref<Position[]>([])
const closedPositions = ref<Position[]>([])
const sortedOpenPositions = computed(() => [...openPositions.value].sort((a, b) => b.openedDate.localeCompare(a.openedDate)))
const sortedClosedPositions = computed(() => [...closedPositions.value].sort((a, b) => (b.closedDate ?? '').localeCompare(a.closedDate ?? '')))
const equityCurveData = ref<EquityCurveData | null>(null)
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const isLoadingPortfolios = ref(true)
const showUnrealizedPnl = ref(false)
const unrealizedPnlData = ref<Map<number, PositionUnrealizedPnl>>(new Map())
const isLoadingUnrealizedPnl = ref(false)
const showBaseCurrency = ref(false)
const forexSummary = ref<ForexSummary | null>(null)
const forexLots = ref<ForexLot[]>([])
const forexDisposals = ref<ForexDisposal[]>([])
const cashTransactions = ref<CashTransaction[]>([])
const cashTransactionSummary = ref<CashTransactionSummary | null>(null)

// Currency display helpers
const hasBaseCurrency = computed(() => {
  return portfolio.value?.baseCurrency && portfolio.value.baseCurrency !== portfolio.value.currency
})

// Convert a value from trade currency to base currency using the current FX rate
const toDisplayCurrency = (value: number) => {
  if (showBaseCurrency.value && stats.value?.currentFxRate) {
    return value * stats.value.currentFxRate
  }
  return value
}

const displayCurrencyLabel = computed(() => {
  if (showBaseCurrency.value && portfolio.value?.baseCurrency) {
    return portfolio.value.baseCurrency
  }
  return portfolio.value?.currency || 'USD'
})

// Modal states
const isCreatePortfolioModalOpen = ref(false)
const isPositionDetailsModalOpen = ref(false)
const isAddExecutionModalOpen = ref(false)
const isClosePositionModalOpen = ref(false)
const isEditMetadataModalOpen = ref(false)
const isDeletePositionModalOpen = ref(false)
const isDeletePortfolioModalOpen = ref(false)
const isCreateFromBrokerModalOpen = ref(false)
const isSyncPortfolioModalOpen = ref(false)

const selectedPosition = ref<Position | null>(null)
const toast = useToast()
const activeTab = ref('open')
const showStrategyBreakdown = ref(false)

// Row selection for batch operations
const selectedOpenIds = ref<Set<number>>(new Set())
const selectedClosedIds = ref<Set<number>>(new Set())
const isBatchEditStrategyModalOpen = ref(false)

function toggleSelect(id: number, selectionSet: Ref<Set<number>>) {
  const next = new Set(selectionSet.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectionSet.value = next
}

function toggleSelectAll(positions: Position[], selectionSet: Ref<Set<number>>) {
  if (selectionSet.value.size === positions.length) {
    selectionSet.value = new Set()
  } else {
    selectionSet.value = new Set(positions.map(p => p.id))
  }
}

// Clear selection when switching tabs
watch(activeTab, () => {
  selectedOpenIds.value = new Set()
  selectedClosedIds.value = new Set()
})

const selectedPositionsForBatch = computed(() => {
  if (activeTab.value === 'open') {
    return openPositions.value.filter(p => selectedOpenIds.value.has(p.id))
  }
  return closedPositions.value.filter(p => selectedClosedIds.value.has(p.id))
})

// Tab items configuration
const tabItems = computed(() => {
  const items = [
    {
      label: 'Open Positions',
      icon: 'i-lucide-folder-open',
      value: 'open',
      slot: 'open'
    },
    {
      label: 'Closed Positions',
      icon: 'i-lucide-folder-closed',
      value: 'closed',
      slot: 'closed'
    },
    {
      label: 'Equity Curve',
      icon: 'i-lucide-trending-up',
      value: 'equity-curve',
      slot: 'equity-curve'
    }
  ]

  if (cashTransactionSummary.value && (cashTransactionSummary.value.totalDeposits > 0 || cashTransactionSummary.value.totalWithdrawals > 0)) {
    items.push({
      label: 'Cash Flow',
      icon: 'i-lucide-wallet',
      value: 'cash-flow',
      slot: 'cash-flow'
    })
  }

  if (hasBaseCurrency.value) {
    items.push({
      label: `Forex (${portfolio.value?.baseCurrency})`,
      icon: 'i-lucide-arrow-left-right',
      value: 'forex',
      slot: 'forex'
    })
  }

  return items
})

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
  selectedOpenIds.value = new Set()
  selectedClosedIds.value = new Set()
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
    const [portfolioData, statsData, openPositionsData, closedPositionsData, equityCurve] = await Promise.all([
      $fetch<Portfolio>(`/udgaard/api/portfolio/${portfolio.value.id}`),
      $fetch<PortfolioStats>(`/udgaard/api/positions/${portfolio.value.id}/stats`),
      $fetch<Position[]>(`/udgaard/api/positions/${portfolio.value.id}?status=OPEN`),
      $fetch<Position[]>(`/udgaard/api/positions/${portfolio.value.id}?status=CLOSED`),
      $fetch<EquityCurveData>(`/udgaard/api/positions/${portfolio.value.id}/equity-curve`)
    ])

    portfolio.value = portfolioData
    stats.value = statsData
    openPositions.value = openPositionsData
    closedPositions.value = closedPositionsData
    equityCurveData.value = equityCurve
    status.value = 'success'

    // Load cash transaction data
    try {
      const [txs, txSummary] = await Promise.all([
        $fetch<CashTransaction[]>(`/udgaard/api/portfolio/${portfolioData.id}/cash-transactions`),
        $fetch<CashTransactionSummary>(`/udgaard/api/portfolio/${portfolioData.id}/cash-transactions/summary`)
      ])
      cashTransactions.value = txs
      cashTransactionSummary.value = txSummary
    } catch {
      cashTransactions.value = []
      cashTransactionSummary.value = null
    }

    // Load forex data if portfolio has a different base currency
    if (portfolioData.baseCurrency && portfolioData.baseCurrency !== portfolioData.currency) {
      try {
        const [summary, lots, disposals] = await Promise.all([
          $fetch<ForexSummary>(`/udgaard/api/portfolio/${portfolioData.id}/forex/summary`),
          $fetch<ForexLot[]>(`/udgaard/api/portfolio/${portfolioData.id}/forex/lots`),
          $fetch<ForexDisposal[]>(`/udgaard/api/portfolio/${portfolioData.id}/forex/disposals`)
        ])
        forexSummary.value = summary
        forexLots.value = lots
        forexDisposals.value = disposals
      } catch {
        forexSummary.value = null
        forexLots.value = []
        forexDisposals.value = []
      }
    }
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

// Load unrealized P&L for open positions
async function loadUnrealizedPnl() {
  if (!portfolio.value?.id) return

  isLoadingUnrealizedPnl.value = true
  try {
    const data = await $fetch<PositionUnrealizedPnl[]>(`/udgaard/api/positions/${portfolio.value.id}/unrealized-pnl`)

    // Convert array to map for easy lookup
    const pnlMap = new Map<number, PositionUnrealizedPnl>()
    data.forEach((pnl) => {
      pnlMap.set(pnl.positionId, pnl)
    })
    unrealizedPnlData.value = pnlMap
    showUnrealizedPnl.value = true

    toast.add({
      title: 'Success',
      description: `Loaded unrealized P&L for ${data.length} positions`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (error) {
    console.error('Error loading unrealized P&L:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load unrealized P&L',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    isLoadingUnrealizedPnl.value = false
  }
}

// Toggle unrealized P&L display
function toggleUnrealizedPnl() {
  if (showUnrealizedPnl.value) {
    // Hide unrealized P&L
    showUnrealizedPnl.value = false
    unrealizedPnlData.value.clear()
  } else {
    // Fetch and show unrealized P&L
    loadUnrealizedPnl()
  }
}

// Calculate total unrealized P&L
const totalUnrealizedPnl = computed(() => {
  if (!showUnrealizedPnl.value || unrealizedPnlData.value.size === 0) return 0

  let total = 0
  unrealizedPnlData.value.forEach((pnl) => {
    if (pnl.unrealizedPnl !== null) {
      total += pnl.unrealizedPnl
    }
  })
  return total
})

// Calculate projected balance (current + unrealized P&L)
const projectedBalance = computed(() => {
  if (!portfolio.value) return 0
  return portfolio.value.currentBalance + totalUnrealizedPnl.value
})

// Calculate projected total return including unrealized P&L
const projectedTotalReturn = computed(() => {
  if (!portfolio.value || portfolio.value.initialBalance === 0) return 0
  return ((projectedBalance.value - portfolio.value.initialBalance) / portfolio.value.initialBalance) * 100
})

// Equity curve with projected balance point when unrealized P&L is shown
const displayEquityCurve = computed(() => {
  if (!equityCurveData.value || !portfolio.value) return null

  const baseDataPoints = [...equityCurveData.value.dataPoints]

  // If showing unrealized P&L and we have data points, add a projected point
  if (showUnrealizedPnl.value && baseDataPoints.length > 0 && totalUnrealizedPnl.value !== 0) {
    const lastPoint = baseDataPoints[baseDataPoints.length - 1]
    const todayDate = new Date().toISOString().substring(0, 10)
    const projectedPoint = {
      date: todayDate,
      balance: projectedBalance.value,
      returnPercentage: projectedTotalReturn.value
    }

    // If the last point is today, replace it; otherwise add new point
    if (lastPoint && lastPoint.date === todayDate) {
      baseDataPoints[baseDataPoints.length - 1] = projectedPoint
    } else {
      baseDataPoints.push(projectedPoint)
    }
  }

  return { dataPoints: baseDataPoints }
})

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
      description: `${portfolio.value.name} deleted successfully`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    // Reset and reload
    portfolio.value = null
    stats.value = null
    openPositions.value = []
    closedPositions.value = []
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

// Delete position
async function deletePosition(positionId: number) {
  try {
    await $fetch(`/udgaard/api/positions/${portfolio.value?.id}/${positionId}`, {
      method: 'DELETE'
    })

    isDeletePositionModalOpen.value = false
    selectedPosition.value = null

    toast.add({
      title: 'Position Deleted',
      description: 'Position deleted successfully',
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    await loadPortfolioData()
  } catch (error) {
    console.error('Error deleting position:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to delete position',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

// Broker integration - handle results from modals
async function createFromBroker(result: CreateFromBrokerResult) {
  portfolio.value = result.portfolio

  await loadPortfolios()
  await loadPortfolioData()

  toast.add({
    title: 'Import Successful',
    description: `Imported ${result.tradesImported} positions, ${result.rollsDetected} rolls detected`,
    icon: 'i-lucide-check-circle',
    color: 'success'
  })
}

async function syncPortfolio(result: PortfolioSyncResult) {
  await loadPortfolioData()

  toast.add({
    title: 'Sync Successful',
    description: `Added ${result.tradesAdded} new positions`,
    icon: 'i-lucide-check-circle',
    color: 'success'
  })
}

// Position actions
function viewPositionDetails(position: Position) {
  selectedPosition.value = position
  isPositionDetailsModalOpen.value = true
}

function openAddExecutionModal(position: Position) {
  selectedPosition.value = position
  isAddExecutionModalOpen.value = true
}

function openClosePositionModal(position: Position) {
  selectedPosition.value = position
  isClosePositionModalOpen.value = true
}

function openEditMetadataModal(position: Position) {
  selectedPosition.value = position
  isEditMetadataModalOpen.value = true
}

function openDeletePositionModal(position: Position) {
  selectedPosition.value = position
  isDeletePositionModalOpen.value = true
}

async function handleDeletePosition() {
  if (!selectedPosition.value) return
  await deletePosition(selectedPosition.value.id)
}

async function handleBatchEditSuccess() {
  selectedOpenIds.value = new Set()
  selectedClosedIds.value = new Set()
  await loadPortfolioData()
}

// Computed stats display
// When unrealized P&L is shown, include it in total profit
const displayStats = computed(() => {
  if (!stats.value) return null

  const projectedProfit = stats.value.totalProfit + totalUnrealizedPnl.value

  return {
    totalPositions: stats.value.totalTrades,
    openPositions: stats.value.openTrades,
    closedPositions: stats.value.closedTrades,
    ytdReturn: stats.value.ytdReturn,
    annualizedReturn: stats.value.annualizedReturn,
    winRate: stats.value.winRate,
    provenEdge: stats.value.provenEdge,
    profitFactor: stats.value.profitFactor,
    avgWin: stats.value.avgWin,
    avgLoss: stats.value.avgLoss,
    totalProfit: stats.value.totalProfit,
    projectedProfit
  }
})

// Strategy breakdown stats
const UNASSIGNED_STRATEGIES = new Set(['', 'Broker Import'])

interface StrategyStats {
  strategy: string
  trades: number
  wins: number
  losses: number
  winRate: number
  avgWin: number
  avgLoss: number
  profitFactor: number | null
  totalPnl: number
}

const strategyBreakdown = computed<StrategyStats[]>(() => {
  if (closedPositions.value.length === 0) return []

  const grouped = new Map<string, Position[]>()
  for (const pos of closedPositions.value) {
    const key = (!pos.entryStrategy || UNASSIGNED_STRATEGIES.has(pos.entryStrategy))
      ? '(Unassigned)'
      : pos.entryStrategy
    const list = grouped.get(key) || []
    list.push(pos)
    grouped.set(key, list)
  }

  const results: StrategyStats[] = []
  for (const [strategy, positions] of grouped) {
    const wins = positions.filter(p => (p.realizedPnl ?? 0) > 0)
    const losses = positions.filter(p => (p.realizedPnl ?? 0) < 0)
    const totalPnl = positions.reduce((sum, p) => sum + (p.realizedPnl ?? 0), 0)

    const avgWinPnl = wins.length > 0
      ? wins.reduce((sum, p) => sum + (p.realizedPnl ?? 0), 0) / wins.length
      : 0
    const avgLossPnl = losses.length > 0
      ? losses.reduce((sum, p) => sum + (p.realizedPnl ?? 0), 0) / losses.length
      : 0

    const grossProfit = wins.reduce((sum, p) => sum + (p.realizedPnl ?? 0), 0)
    const grossLoss = Math.abs(losses.reduce((sum, p) => sum + (p.realizedPnl ?? 0), 0))

    results.push({
      strategy,
      trades: positions.length,
      wins: wins.length,
      losses: losses.length,
      winRate: positions.length > 0 ? (wins.length / positions.length) * 100 : 0,
      avgWin: avgWinPnl,
      avgLoss: avgLossPnl,
      profitFactor: grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? null : 0),
      totalPnl
    })
  }

  return results.sort((a, b) => b.trades - a.trades)
})

const strategyBreakdownColumns = computed(() => {
  const UBadge = resolveComponent('UBadge')

  return [
    {
      accessorKey: 'strategy',
      header: 'Strategy',
      cell: ({ row }: { row: any }) => {
        const s = row.original as StrategyStats
        const children: any[] = []
        if (s.strategy === '(Unassigned)') {
          children.push(h('span', { class: 'italic text-gray-400' }, s.strategy))
        } else {
          children.push(h('span', {}, s.strategy))
        }
        if (s.trades < 5) {
          children.push(h(UBadge as any, { variant: 'subtle', color: 'warning', size: 'xs', class: 'ml-2' }, () => '< 5 trades'))
        }
        return h('div', { class: 'flex items-center' }, children)
      }
    },
    { accessorKey: 'trades', header: 'Trades' },
    {
      accessorKey: 'winRate',
      header: 'Win Rate',
      cell: ({ row }: { row: any }) => {
        const val = row.original.winRate
        return h('span', { class: val >= 50 ? 'text-green-600' : 'text-red-600' }, formatPercent(val))
      }
    },
    {
      accessorKey: 'avgWin',
      header: 'Avg Win',
      cell: ({ row }: { row: any }) => h('span', { class: 'text-green-600' }, formatCurrency(row.original.avgWin))
    },
    {
      accessorKey: 'avgLoss',
      header: 'Avg Loss',
      cell: ({ row }: { row: any }) => h('span', { class: 'text-red-600' }, formatCurrency(row.original.avgLoss))
    },
    {
      accessorKey: 'profitFactor',
      header: 'Profit Factor',
      cell: ({ row }: { row: any }) => formatProfitFactor(row.original.profitFactor)
    },
    {
      accessorKey: 'totalPnl',
      header: 'Total P&L',
      cell: ({ row }: { row: any }) => {
        const val = row.original.totalPnl
        return h('span', { class: val >= 0 ? 'text-green-600' : 'text-red-600' }, formatCurrency(val))
      }
    }
  ]
})

// Format percentage
const formatPercent = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value / 100)
}

// Format profit factor
const formatProfitFactor = (value: number | null | undefined) => {
  if (value === null || value === undefined) return '∞'
  return value.toFixed(2)
}

// Equity curve chart series
const equityCurveSeries = computed(() => {
  if (!displayEquityCurve.value) return []

  return [{
    name: 'Balance',
    data: displayEquityCurve.value.dataPoints.map(p => p.balance)
  }]
})

// Equity curve chart options
const equityCurveChartOptions = computed(() => {
  if (!displayEquityCurve.value || !portfolio.value) return null

  const dataPoints = displayEquityCurve.value.dataPoints
  const colorMode = useColorMode()
  const isDark = colorMode.value === 'dark'

  return {
    chart: {
      type: 'area',
      height: 350,
      toolbar: {
        show: false
      },
      zoom: {
        enabled: false
      },
      background: 'transparent',
      foreColor: isDark ? '#d1d5db' : '#6b7280'
    },
    dataLabels: {
      enabled: false
    },
    stroke: {
      curve: 'smooth',
      width: 2
    },
    xaxis: {
      type: 'datetime',
      categories: dataPoints.map(p => p.date),
      labels: {
        format: 'MMM yyyy',
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        }
      }
    },
    yaxis: {
      labels: {
        formatter: (value: number) => formatCurrency(value),
        style: {
          colors: isDark ? '#9ca3af' : '#6b7280'
        }
      }
    },
    tooltip: {
      theme: isDark ? 'dark' : 'light',
      x: {
        format: 'dd MMM yyyy'
      },
      y: {
        formatter: (value: number) => formatCurrency(value)
      }
    },
    fill: {
      type: 'gradient',
      gradient: {
        shadeIntensity: 1,
        opacityFrom: 0.7,
        opacityTo: 0.3,
        stops: [0, 90, 100]
      }
    },
    colors: ['#3b82f6'],
    grid: {
      borderColor: isDark ? '#374151' : '#e5e7eb'
    }
  }
})

// Table columns
const openPositionsColumns = computed(() => {
  const UButton = resolveComponent('UButton')

  const columns: any[] = [
    {
      id: 'select',
      header: () => h('input', {
        type: 'checkbox',
        checked: selectedOpenIds.value.size === openPositions.value.length && openPositions.value.length > 0,
        indeterminate: selectedOpenIds.value.size > 0 && selectedOpenIds.value.size < openPositions.value.length,
        class: 'rounded',
        onChange: () => toggleSelectAll(openPositions.value, selectedOpenIds)
      }),
      cell: ({ row }: { row: any }) => h('input', {
        type: 'checkbox',
        checked: selectedOpenIds.value.has(row.original.id),
        class: 'rounded',
        onChange: () => toggleSelect(row.original.id, selectedOpenIds)
      })
    },
    {
      accessorKey: 'symbol',
      header: 'Symbol',
      cell: ({ row }: { row: any }) => {
        const position = row.original
        const details = formatOptionDetails(position)

        return h('div', {}, [
          h(resolveComponent('SymbolLink'), { symbol: position.symbol }),
          details ? h('div', { class: 'text-xs text-gray-500' }, details) : null
        ].filter(Boolean))
      }
    },
    { accessorKey: 'instrumentType', header: 'Type' },
    {
      accessorKey: 'entryStrategy',
      header: 'Strategy',
      cell: ({ row }: { row: any }) => row.original.entryStrategy || '—'
    },
    { accessorKey: 'currentQuantity', header: 'Quantity' },
    {
      accessorKey: 'averageEntryPrice',
      header: 'Avg Entry',
      cell: ({ row }: { row: any }) => formatCurrency(row.original.averageEntryPrice)
    },
    {
      accessorKey: 'totalCost',
      header: 'Total Cost',
      cell: ({ row }: { row: any }) => formatCurrency(row.original.totalCost)
    }
  ]

  // Add current price and unrealized P&L columns if showing
  if (showUnrealizedPnl.value) {
    columns.push({
      accessorKey: 'currentPrice',
      header: 'Current Price',
      cell: ({ row }: { row: any }) => {
        const pnl = unrealizedPnlData.value.get(row.original.id)
        if (!pnl || pnl.currentPrice === null) {
          return h('span', { class: 'text-gray-400' }, '-')
        }
        return formatCurrency(pnl.currentPrice)
      }
    })

    columns.push({
      accessorKey: 'unrealizedPnl',
      header: 'Unrealized P&L',
      cell: ({ row }: { row: any }) => {
        const pnl = unrealizedPnlData.value.get(row.original.id)
        if (!pnl || pnl.unrealizedPnl === null) {
          return h('span', { class: 'text-gray-400' }, '-')
        }
        return h('span', {
          class: pnl.unrealizedPnl >= 0 ? 'text-green-600' : 'text-red-600'
        }, formatCurrency(pnl.unrealizedPnl))
      }
    })
  }

  columns.push(
    {
      accessorKey: 'openedDate',
      header: 'Opened',
      cell: ({ row }: { row: any }) => formatDate(row.original.openedDate)
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }: { row: any }) => h(UButton as any, {
        icon: 'i-lucide-eye',
        variant: 'ghost',
        size: 'sm',
        onClick: () => viewPositionDetails(row.original)
      })
    }
  )

  return columns
})

const closedPositionsColumns = computed(() => {
  const UButton = resolveComponent('UButton')

  return [
    {
      id: 'select',
      header: () => h('input', {
        type: 'checkbox',
        checked: selectedClosedIds.value.size === closedPositions.value.length && closedPositions.value.length > 0,
        indeterminate: selectedClosedIds.value.size > 0 && selectedClosedIds.value.size < closedPositions.value.length,
        class: 'rounded',
        onChange: () => toggleSelectAll(closedPositions.value, selectedClosedIds)
      }),
      cell: ({ row }: { row: any }) => h('input', {
        type: 'checkbox',
        checked: selectedClosedIds.value.has(row.original.id),
        class: 'rounded',
        onChange: () => toggleSelect(row.original.id, selectedClosedIds)
      })
    },
    {
      accessorKey: 'symbol',
      header: 'Symbol',
      cell: ({ row }: { row: any }) => {
        const position = row.original
        const details = formatOptionDetails(position)

        return h('div', {}, [
          h(resolveComponent('SymbolLink'), { symbol: position.symbol }),
          details ? h('div', { class: 'text-xs text-gray-500' }, details) : null
        ].filter(Boolean))
      }
    },
    { accessorKey: 'instrumentType', header: 'Type' },
    {
      accessorKey: 'entryStrategy',
      header: 'Strategy',
      cell: ({ row }: { row: any }) => row.original.entryStrategy || '—'
    },
    {
      accessorKey: 'averageEntryPrice',
      header: 'Entry',
      cell: ({ row }: { row: any }) => formatCurrency(row.original.averageEntryPrice)
    },
    {
      accessorKey: 'realizedPnl',
      header: 'Realized P&L',
      cell: ({ row }: { row: any }) => {
        const value = row.original.realizedPnl || 0
        return h('span', {
          class: value >= 0 ? 'text-green-600' : 'text-red-600'
        }, formatCurrency(value))
      }
    },
    {
      accessorKey: 'openedDate',
      header: 'Opened',
      cell: ({ row }: { row: any }) => formatDate(row.original.openedDate)
    },
    {
      accessorKey: 'closedDate',
      header: 'Closed',
      cell: ({ row }: { row: any }) => row.original.closedDate ? formatDate(row.original.closedDate) : '-'
    },
    {
      id: 'actions',
      header: 'Actions',
      cell: ({ row }: { row: any }) => h(UButton as any, {
        icon: 'i-lucide-eye',
        label: 'View Details',
        variant: 'ghost',
        size: 'sm',
        onClick: () => viewPositionDetails(row.original)
      })
    }
  ]
})

// Forex lot table columns
const forexLotColumns = [
  {
    accessorKey: 'acquisitionDate',
    header: 'Date',
    cell: ({ row }: { row: any }) => formatDate(row.original.acquisitionDate)
  },
  {
    accessorKey: 'sourceDescription',
    header: 'Source'
  },
  {
    accessorKey: 'quantity',
    header: 'USD Acquired',
    cell: ({ row }: { row: any }) => formatCurrency(row.original.quantity)
  },
  {
    accessorKey: 'remainingQuantity',
    header: 'Remaining',
    cell: ({ row }: { row: any }) => formatCurrency(row.original.remainingQuantity)
  },
  {
    accessorKey: 'costRate',
    header: 'FX Rate',
    cell: ({ row }: { row: any }) => row.original.costRate.toFixed(4)
  },
  {
    accessorKey: 'costBasis',
    header: `Cost (${portfolio.value?.baseCurrency || 'AUD'})`,
    cell: ({ row }: { row: any }) => formatCurrency(row.original.costBasis)
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }: { row: any }) => {
      const status = row.original.status
      return h('span', {
        class: status === 'OPEN' ? 'text-blue-600' : 'text-gray-400'
      }, status)
    }
  }
]

// Cash transaction table columns
const cashTransactionColumns = [
  {
    accessorKey: 'transactionDate',
    header: 'Date',
    cell: ({ row }: { row: any }) => formatDate(row.original.transactionDate)
  },
  {
    accessorKey: 'type',
    header: 'Type',
    cell: ({ row }: { row: any }) => {
      const type = row.original.type
      return h('span', {
        class: type === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'
      }, type)
    }
  },
  {
    accessorKey: 'amount',
    header: 'Amount',
    cell: ({ row }: { row: any }) => {
      const type = row.original.type
      const prefix = type === 'DEPOSIT' ? '+' : '-'
      return h('span', {
        class: type === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'
      }, `${prefix}${formatCurrency(row.original.amount)}`)
    }
  },
  {
    accessorKey: 'currency',
    header: 'Currency'
  },
  {
    accessorKey: 'fxRateToBase',
    header: 'FX Rate',
    cell: ({ row }: { row: any }) => row.original.fxRateToBase ? row.original.fxRateToBase.toFixed(4) : '-'
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }: { row: any }) => row.original.description || '-'
  }
]

// Forex disposal table columns
const forexDisposalColumns = [
  {
    accessorKey: 'disposalDate',
    header: 'Date',
    cell: ({ row }: { row: any }) => formatDate(row.original.disposalDate)
  },
  {
    accessorKey: 'quantity',
    header: 'USD Disposed',
    cell: ({ row }: { row: any }) => formatCurrency(row.original.quantity)
  },
  {
    accessorKey: 'costRate',
    header: 'Acquired Rate',
    cell: ({ row }: { row: any }) => row.original.costRate.toFixed(4)
  },
  {
    accessorKey: 'disposalRate',
    header: 'Disposal Rate',
    cell: ({ row }: { row: any }) => row.original.disposalRate.toFixed(4)
  },
  {
    accessorKey: 'costBasisAud',
    header: `Cost (${portfolio.value?.baseCurrency || 'AUD'})`,
    cell: ({ row }: { row: any }) => formatCurrency(row.original.costBasisAud)
  },
  {
    accessorKey: 'proceedsAud',
    header: `Proceeds (${portfolio.value?.baseCurrency || 'AUD'})`,
    cell: ({ row }: { row: any }) => formatCurrency(row.original.proceedsAud)
  },
  {
    accessorKey: 'realizedFxPnl',
    header: 'FX P&L',
    cell: ({ row }: { row: any }) => {
      const value = row.original.realizedFxPnl
      return h('span', {
        class: value >= 0 ? 'text-green-600' : 'text-red-600'
      }, `${value >= 0 ? '+' : ''}${formatCurrency(value)}`)
    }
  }
]
</script>

<template>
  <UDashboardPanel id="portfolio" grow>
    <template #header>
      <UDashboardNavbar title="Portfolio Manager">
        <template #left>
          <USelect
            v-if="portfolios.length > 0"
            :model-value="portfolio?.id"
            :items="portfolios.map((p) => ({ label: p.name, value: p.id }))"
            value-key="value"
            placeholder="Select Portfolio"
            class="w-64"
            @update:model-value="(id) => { if (id) selectPortfolio(portfolios.find((p) => p.id === id)!) }"
          />
        </template>
        <template #right>
          <div class="flex gap-2">
            <UButton
              label="Create Portfolio"
              icon="i-lucide-plus"
              @click="isCreatePortfolioModalOpen = true"
            />
            <UDropdownMenu
              v-if="portfolio"
              :items="[
                { label: 'Sync Portfolio', icon: 'i-lucide-refresh-cw', onSelect: (): void => { isSyncPortfolioModalOpen = true }, disabled: portfolio.broker !== 'IBKR' },
                { label: 'Import from Broker', icon: 'i-lucide-download', onSelect: (): void => { isCreateFromBrokerModalOpen = true } },
                { type: 'separator' },
                { label: 'Delete Portfolio', icon: 'i-lucide-trash', onSelect: (): void => { isDeletePortfolioModalOpen = true } }
              ]"
            >
              <UButton icon="i-lucide-more-vertical" variant="ghost" />
            </UDropdownMenu>
          </div>
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
        <p class="text-gray-500 dark:text-gray-400 text-center">
          Please wait while we fetch your portfolios...
        </p>
      </div>

      <!-- No Portfolio State -->
      <div v-else-if="!portfolio" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-briefcase" class="w-16 h-16 text-gray-500 dark:text-gray-400 mb-4" />
        <h3 class="text-lg font-semibold mb-2">
          No Portfolio
        </h3>
        <p class="text-gray-500 dark:text-gray-400 text-center mb-4">
          Create a portfolio to start tracking your trades
        </p>
        <div class="flex gap-2">
          <UButton
            label="Create Portfolio"
            icon="i-lucide-plus"
            @click="isCreatePortfolioModalOpen = true"
          />
          <UButton
            label="Import from Broker"
            icon="i-lucide-download"
            variant="outline"
            @click="isCreateFromBrokerModalOpen = true"
          />
        </div>
      </div>

      <!-- Toggle Controls -->
      <div v-if="portfolio && displayStats" class="px-4 pt-4 flex justify-end gap-2">
        <UButton
          v-if="hasBaseCurrency"
          :label="showBaseCurrency ? portfolio!.baseCurrency! : portfolio!.currency"
          icon="i-lucide-arrow-left-right"
          size="sm"
          :variant="showBaseCurrency ? 'solid' : 'outline'"
          @click="showBaseCurrency = !showBaseCurrency"
        />
        <UButton
          v-if="displayStats.openPositions > 0"
          :label="showUnrealizedPnl ? 'Hide Unrealized P&L' : 'Show Unrealized P&L'"
          :icon="showUnrealizedPnl ? 'i-lucide-eye-off' : 'i-lucide-trending-up'"
          :loading="isLoadingUnrealizedPnl"
          size="sm"
          variant="outline"
          @click="toggleUnrealizedPnl"
        />
        <UButton
          v-if="displayStats.closedPositions > 0"
          label="Strategy Breakdown"
          icon="i-lucide-bar-chart-3"
          size="sm"
          :variant="showStrategyBreakdown ? 'solid' : 'outline'"
          @click="showStrategyBreakdown = !showStrategyBreakdown"
        />
      </div>

      <!-- Stats Cards -->
      <div v-if="portfolio && displayStats" class="p-4 grid grid-cols-1 md:grid-cols-3 gap-4">
        <UCard>
          <template #header>
            <h3 class="text-base font-semibold">
              Portfolio Overview
            </h3>
          </template>
          <div class="space-y-2">
            <div class="flex justify-between">
              <span>Starting Balance ({{ displayCurrencyLabel }}):</span>
              <span class="font-semibold">{{ formatCurrency(toDisplayCurrency(portfolio.initialBalance)) }}</span>
            </div>
            <div class="flex justify-between">
              <span>Current Balance ({{ displayCurrencyLabel }}):</span>
              <span class="font-semibold text-primary">{{ formatCurrency(toDisplayCurrency(portfolio.currentBalance)) }}</span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between">
              <span>Unrealized P&L:</span>
              <span class="font-semibold" :class="totalUnrealizedPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ totalUnrealizedPnl >= 0 ? '+' : '' }}{{ formatCurrency(toDisplayCurrency(totalUnrealizedPnl)) }}
              </span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between border-t pt-2">
              <span class="font-medium">Projected Balance:</span>
              <span class="font-semibold text-blue-600">{{ formatCurrency(toDisplayCurrency(projectedBalance)) }}</span>
            </div>
            <div class="flex justify-between" :class="{ 'border-t pt-2': showUnrealizedPnl }">
              <span>Total Positions:</span>
              <span class="font-semibold">{{ displayStats.totalPositions }}</span>
            </div>
            <div class="flex justify-between">
              <span>Open:</span>
              <span class="font-semibold text-blue-600">{{ displayStats.openPositions }}</span>
            </div>
            <div class="flex justify-between">
              <span>Closed:</span>
              <span class="font-semibold text-gray-600">{{ displayStats.closedPositions }}</span>
            </div>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <h3 class="text-base font-semibold">
              Performance
            </h3>
          </template>
          <div class="space-y-2">
            <div class="flex justify-between">
              <span>Win Rate:</span>
              <span class="font-semibold">{{ formatPercent(displayStats.winRate) }}</span>
            </div>
            <div class="flex justify-between">
              <span>Proven Edge:</span>
              <span class="font-semibold" :class="displayStats.provenEdge >= 0 ? 'text-green-600' : 'text-red-600'">{{ formatPercent(displayStats.provenEdge) }}</span>
            </div>
            <div class="flex justify-between">
              <span>Profit Factor:</span>
              <span class="font-semibold">{{ formatProfitFactor(displayStats.profitFactor) }}</span>
            </div>
            <div class="flex justify-between">
              <span>{{ showUnrealizedPnl ? 'Realized' : 'Total' }} Return:</span>
              <span class="font-semibold">{{ formatPercent(displayStats.ytdReturn) }}</span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between border-t pt-2">
              <span class="font-medium">Projected Return:</span>
              <span class="font-semibold text-blue-600">{{ formatPercent(projectedTotalReturn) }}</span>
            </div>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <h3 class="text-base font-semibold">
              P&L
            </h3>
          </template>
          <div class="space-y-2">
            <!-- Raw trade P&L (no commissions, no forex) -->
            <div class="flex justify-between">
              <span>Trade P&L ({{ displayCurrencyLabel }}):</span>
              <span class="font-semibold" :class="displayStats.totalProfit >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatCurrency(toDisplayCurrency(displayStats.totalProfit)) }}
              </span>
            </div>
            <!-- Commissions -->
            <div v-if="stats?.totalCommissions" class="flex justify-between">
              <span>Commissions:</span>
              <span class="font-semibold text-red-600">
                {{ formatCurrency(toDisplayCurrency(stats.totalCommissions)) }}
              </span>
            </div>
            <!-- P&L after commissions -->
            <div v-if="stats?.totalCommissions" class="flex justify-between border-t pt-2">
              <span class="font-medium">After Commissions:</span>
              <span class="font-semibold" :class="(displayStats.totalProfit + (stats?.totalCommissions || 0)) >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatCurrency(toDisplayCurrency(displayStats.totalProfit + (stats?.totalCommissions || 0))) }}
              </span>
            </div>
            <!-- FX P&L on initial balance -->
            <div v-if="stats?.totalRealizedFxPnl != null" class="flex justify-between">
              <span>FX P&L:</span>
              <span class="font-semibold" :class="stats.totalRealizedFxPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ stats.totalRealizedFxPnl >= 0 ? '+' : '' }}{{ formatCurrency(toDisplayCurrency(stats.totalRealizedFxPnl)) }}
              </span>
            </div>
            <!-- Actual P&L (everything included) -->
            <div v-if="stats?.totalCommissions || stats?.totalRealizedFxPnl != null" class="flex justify-between border-t pt-2">
              <span class="font-bold">Net P&L ({{ displayCurrencyLabel }}):</span>
              <span class="font-bold" :class="(displayStats.totalProfit + (stats?.totalCommissions || 0) + (stats?.totalRealizedFxPnl || 0)) >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatCurrency(toDisplayCurrency(displayStats.totalProfit + (stats?.totalCommissions || 0) + (stats?.totalRealizedFxPnl || 0))) }}
              </span>
            </div>
            <!-- Unrealized -->
            <div v-if="showUnrealizedPnl" class="flex justify-between border-t pt-2">
              <span>Unrealized P&L:</span>
              <span class="font-semibold" :class="totalUnrealizedPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ totalUnrealizedPnl >= 0 ? '+' : '' }}{{ formatCurrency(toDisplayCurrency(totalUnrealizedPnl)) }}
              </span>
            </div>
            <!-- Avg Win/Loss -->
            <div class="flex justify-between" :class="{ 'border-t pt-2': !showUnrealizedPnl }">
              <span>Avg Win:</span>
              <span class="font-semibold text-green-600">{{ formatCurrency(displayStats.avgWin) }}</span>
            </div>
            <div class="flex justify-between">
              <span>Avg Loss:</span>
              <span class="font-semibold text-red-600">{{ formatCurrency(displayStats.avgLoss) }}</span>
            </div>
          </div>
        </UCard>
      </div>

      <!-- Strategy Breakdown -->
      <div v-show="showStrategyBreakdown && strategyBreakdown.length > 0" class="px-4">
        <UCard>
          <template #header>
            <h3 class="text-base font-semibold">
              Performance by Entry Strategy
            </h3>
          </template>
          <UTable :data="strategyBreakdown" :columns="strategyBreakdownColumns" />
        </UCard>
      </div>

      <!-- Tabs for Positions and Equity Curve -->
      <div v-if="portfolio" class="p-4">
        <UTabs
          v-model="activeTab"
          :items="tabItems"
          variant="link"
          class="w-full"
        >
          <!-- Open Positions Tab -->
          <template #open>
            <UCard>
              <template #header>
                <h3 class="text-lg font-semibold">
                  Open Positions
                </h3>
              </template>

              <div v-if="selectedOpenIds.size > 0" class="flex items-center justify-between px-4 py-2 bg-primary-50 dark:bg-primary-950 border-b border-primary-200 dark:border-primary-800">
                <span class="text-sm font-medium">{{ selectedOpenIds.size }} position{{ selectedOpenIds.size === 1 ? '' : 's' }} selected</span>
                <div class="flex gap-2">
                  <UButton
                    label="Edit Strategy"
                    icon="i-lucide-pencil"
                    size="sm"
                    variant="soft"
                    @click="isBatchEditStrategyModalOpen = true"
                  />
                  <UButton
                    icon="i-lucide-x"
                    size="sm"
                    variant="ghost"
                    @click="selectedOpenIds = new Set()"
                  />
                </div>
              </div>

              <div v-if="openPositions.length === 0" class="text-center py-8 text-gray-500">
                No open positions
              </div>
              <UTable v-else :data="sortedOpenPositions" :columns="openPositionsColumns" />
            </UCard>
          </template>

          <!-- Closed Positions Tab -->
          <template #closed>
            <UCard>
              <template #header>
                <h3 class="text-lg font-semibold">
                  Closed Positions
                </h3>
              </template>

              <div v-if="selectedClosedIds.size > 0" class="flex items-center justify-between px-4 py-2 bg-primary-50 dark:bg-primary-950 border-b border-primary-200 dark:border-primary-800">
                <span class="text-sm font-medium">{{ selectedClosedIds.size }} position{{ selectedClosedIds.size === 1 ? '' : 's' }} selected</span>
                <div class="flex gap-2">
                  <UButton
                    label="Edit Strategy"
                    icon="i-lucide-pencil"
                    size="sm"
                    variant="soft"
                    @click="isBatchEditStrategyModalOpen = true"
                  />
                  <UButton
                    icon="i-lucide-x"
                    size="sm"
                    variant="ghost"
                    @click="selectedClosedIds = new Set()"
                  />
                </div>
              </div>

              <div v-if="closedPositions.length === 0" class="text-center py-8 text-gray-500">
                No closed positions
              </div>
              <UTable v-else :data="sortedClosedPositions" :columns="closedPositionsColumns" />
            </UCard>
          </template>

          <!-- Equity Curve Tab -->
          <template #equity-curve>
            <UCard v-if="displayEquityCurve && displayEquityCurve.dataPoints.length > 0">
              <template #header>
                <h3 class="text-lg font-semibold">
                  Equity Curve
                  <span v-if="showUnrealizedPnl" class="text-xs font-normal text-blue-600 ml-2">(including unrealized P&L)</span>
                </h3>
              </template>
              <ClientOnly>
                <apexchart
                  v-if="equityCurveChartOptions"
                  type="area"
                  height="350"
                  :options="equityCurveChartOptions"
                  :series="equityCurveSeries"
                />
              </ClientOnly>
            </UCard>

            <!-- Empty State -->
            <UCard v-else>
              <div class="flex flex-col items-center justify-center py-12">
                <UIcon name="i-lucide-trending-up" class="w-16 h-16 text-gray-400 mb-4" />
                <h3 class="text-lg font-semibold mb-2">
                  No Equity Data
                </h3>
                <p class="text-gray-500">
                  Close some positions to see your equity curve
                </p>
              </div>
            </UCard>
          </template>

          <!-- Cash Flow Tab -->
          <template #cash-flow>
            <div v-if="cashTransactionSummary" class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Total Deposits
                  </div>
                  <div class="text-2xl font-bold text-green-600">
                    +{{ formatCurrency(cashTransactionSummary.totalDeposits) }}
                  </div>
                </div>
              </UCard>
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Total Withdrawals
                  </div>
                  <div class="text-2xl font-bold text-red-600">
                    -{{ formatCurrency(cashTransactionSummary.totalWithdrawals) }}
                  </div>
                </div>
              </UCard>
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Net Cash Flow
                  </div>
                  <div class="text-2xl font-bold" :class="cashTransactionSummary.netCashFlow >= 0 ? 'text-green-600' : 'text-red-600'">
                    {{ cashTransactionSummary.netCashFlow >= 0 ? '+' : '' }}{{ formatCurrency(cashTransactionSummary.netCashFlow) }}
                  </div>
                </div>
              </UCard>
            </div>

            <UCard>
              <template #header>
                <h3 class="text-lg font-semibold">
                  Transactions
                </h3>
              </template>
              <div v-if="cashTransactions.length === 0" class="text-center py-8 text-gray-500">
                No cash transactions recorded
              </div>
              <UTable v-else :data="cashTransactions" :columns="cashTransactionColumns" />
            </UCard>
          </template>

          <!-- Forex Tab -->
          <template #forex>
            <!-- Forex Summary Cards -->
            <div v-if="forexSummary" class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Realized FX P&L
                  </div>
                  <div class="text-2xl font-bold" :class="forexSummary.totalRealizedFxPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                    {{ forexSummary.totalRealizedFxPnl >= 0 ? '+' : '' }}{{ formatCurrency(forexSummary.totalRealizedFxPnl) }}
                  </div>
                  <div class="text-xs text-gray-400">
                    {{ portfolio?.baseCurrency }}
                  </div>
                </div>
              </UCard>
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Open USD Balance
                  </div>
                  <div class="text-2xl font-bold text-blue-600">
                    {{ formatCurrency(forexSummary.openUsdBalance) }}
                  </div>
                  <div class="text-xs text-gray-400">
                    across {{ forexSummary.openLotsCount }} lots
                  </div>
                </div>
              </UCard>
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Open Lots
                  </div>
                  <div class="text-2xl font-bold">
                    {{ forexSummary.openLotsCount }}
                  </div>
                </div>
              </UCard>
              <UCard>
                <div class="text-center">
                  <div class="text-sm text-gray-500">
                    Disposals
                  </div>
                  <div class="text-2xl font-bold">
                    {{ forexSummary.disposalsCount }}
                  </div>
                </div>
              </UCard>
            </div>

            <!-- Forex Lots Table -->
            <UCard class="mb-4">
              <template #header>
                <h3 class="text-lg font-semibold">
                  USD Acquisition Lots (FIFO)
                </h3>
              </template>
              <div v-if="forexLots.length === 0" class="text-center py-8 text-gray-500">
                No forex lots recorded
              </div>
              <UTable v-else :data="forexLots" :columns="forexLotColumns" />
            </UCard>

            <!-- Forex Disposals Table -->
            <UCard>
              <template #header>
                <h3 class="text-lg font-semibold">
                  USD Disposals (Realized FX P&L)
                </h3>
              </template>
              <div v-if="forexDisposals.length === 0" class="text-center py-8 text-gray-500">
                No forex disposals recorded
              </div>
              <UTable v-else :data="forexDisposals" :columns="forexDisposalColumns" />
            </UCard>
          </template>
        </UTabs>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Modals -->
  <PortfolioCreateModal
    :open="isCreatePortfolioModalOpen"
    @update:open="(value) => isCreatePortfolioModalOpen = value"
    @create="createPortfolio"
  />

  <PortfolioDeleteModal
    v-if="portfolio"
    :open="isDeletePortfolioModalOpen"
    :portfolio="portfolio"
    @update:open="(value) => isDeletePortfolioModalOpen = value"
    @delete="deletePortfolio"
  />

  <PortfolioCreateFromBrokerModal
    :open="isCreateFromBrokerModalOpen"
    @update:open="(value) => isCreateFromBrokerModalOpen = value"
    @created="(result) => { isCreateFromBrokerModalOpen = false; createFromBroker(result); }"
  />

  <PortfolioSyncPortfolioModal
    v-if="portfolio"
    :open="isSyncPortfolioModalOpen"
    :portfolio="portfolio"
    @update:open="(value) => isSyncPortfolioModalOpen = value"
    @synced="(result) => { isSyncPortfolioModalOpen = false; syncPortfolio(result); }"
  />

  <PortfolioPositionDetailsModal
    v-model="isPositionDetailsModalOpen"
    :position="selectedPosition || null"
    @add-execution="openAddExecutionModal"
    @edit-metadata="openEditMetadataModal"
    @close-position="openClosePositionModal"
    @delete="openDeletePositionModal"
  />

  <PortfolioAddExecutionModal
    v-model="isAddExecutionModalOpen"
    :position="selectedPosition || null"
    @success="loadPortfolioData"
  />

  <PortfolioClosePositionModal
    v-model="isClosePositionModalOpen"
    :position="selectedPosition || null"
    @success="loadPortfolioData"
  />

  <PortfolioEditPositionMetadataModal
    v-model="isEditMetadataModalOpen"
    :position="selectedPosition || null"
    @success="loadPortfolioData"
  />

  <PortfolioDeletePositionModal
    v-model="isDeletePositionModalOpen"
    :position="selectedPosition || null"
    @delete="handleDeletePosition"
  />

  <PortfolioBatchEditStrategyModal
    v-model="isBatchEditStrategyModalOpen"
    :positions="selectedPositionsForBatch"
    @success="handleBatchEditSuccess"
  />
</template>
