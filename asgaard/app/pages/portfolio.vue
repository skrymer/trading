<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { Portfolio, Position, PortfolioStats, CreateFromBrokerResult, PortfolioSyncResult, PositionUnrealizedPnl, EquityCurveData } from '~/types'

const { formatPositionName, formatOptionDetails, formatCurrency, formatDate } = usePositionFormatters()

const portfolio = ref<Portfolio | null>(null)
const portfolios = ref<Portfolio[]>([])
const stats = ref<PortfolioStats | null>(null)
const openPositions = ref<Position[]>([])
const closedPositions = ref<Position[]>([])
const equityCurveData = ref<EquityCurveData | null>(null)
const status = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const isLoadingPortfolios = ref(true)
const showUnrealizedPnl = ref(false)
const unrealizedPnlData = ref<Map<number, PositionUnrealizedPnl>>(new Map())
const isLoadingUnrealizedPnl = ref(false)

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

// Tab items configuration
const tabItems = [
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
    const todayDate = new Date().toISOString().split('T')[0]
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
    avgWin: stats.value.avgWin,
    avgLoss: stats.value.avgLoss,
    totalProfit: stats.value.totalProfit,
    projectedProfit
  }
})

// Format percentage
const formatPercent = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value / 100)
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
      accessorKey: 'symbol',
      header: 'Symbol',
      cell: ({ row }: { row: any }) => {
        const position = row.original
        const name = formatPositionName(position)
        const details = formatOptionDetails(position)

        return h('div', {}, [
          h('div', { class: 'font-medium' }, name),
          details ? h('div', { class: 'text-xs text-gray-500' }, details) : null
        ].filter(Boolean))
      }
    },
    { accessorKey: 'instrumentType', header: 'Type' },
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
      accessorKey: 'symbol',
      header: 'Symbol',
      cell: ({ row }: { row: any }) => {
        const position = row.original
        const name = formatPositionName(position)
        const details = formatOptionDetails(position)

        return h('div', {}, [
          h('div', { class: 'font-medium' }, name),
          details ? h('div', { class: 'text-xs text-gray-500' }, details) : null
        ].filter(Boolean))
      }
    },
    { accessorKey: 'instrumentType', header: 'Type' },
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
                { label: 'Sync Portfolio', icon: 'i-lucide-refresh-cw', click: (): void => { isSyncPortfolioModalOpen = true }, disabled: portfolio.broker !== 'IBKR' },
                { label: 'Import from Broker', icon: 'i-lucide-download', click: (): void => { isCreateFromBrokerModalOpen = true } },
                { type: 'separator' },
                { label: 'Delete Portfolio', icon: 'i-lucide-trash', click: (): void => { isDeletePortfolioModalOpen = true } }
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

      <!-- Unrealized P&L Toggle -->
      <div v-if="portfolio && displayStats && displayStats.openPositions > 0" class="px-4 pt-4 flex justify-end">
        <UButton
          :label="showUnrealizedPnl ? 'Hide Unrealized P&L' : 'Show Unrealized P&L'"
          :icon="showUnrealizedPnl ? 'i-lucide-eye-off' : 'i-lucide-trending-up'"
          :loading="isLoadingUnrealizedPnl"
          size="sm"
          variant="outline"
          @click="toggleUnrealizedPnl"
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
              <span>Starting Balance:</span>
              <span class="font-semibold">{{ formatCurrency(portfolio.initialBalance) }}</span>
            </div>
            <div class="flex justify-between">
              <span>Current Balance:</span>
              <span class="font-semibold text-primary">{{ formatCurrency(portfolio.currentBalance) }}</span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between">
              <span>Unrealized P&L:</span>
              <span class="font-semibold" :class="totalUnrealizedPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ totalUnrealizedPnl >= 0 ? '+' : '' }}{{ formatCurrency(totalUnrealizedPnl) }}
              </span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between border-t pt-2">
              <span class="font-medium">Projected Balance:</span>
              <span class="font-semibold text-blue-600">{{ formatCurrency(projectedBalance) }}</span>
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
              <span class="font-semibold text-green-600">{{ formatPercent(displayStats.provenEdge) }}</span>
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
            <div class="flex justify-between">
              <span>{{ showUnrealizedPnl ? 'Realized' : 'Total' }} Profit:</span>
              <span class="font-semibold" :class="displayStats.totalProfit >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatCurrency(displayStats.totalProfit) }}
              </span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between">
              <span>Unrealized P&L:</span>
              <span class="font-semibold" :class="totalUnrealizedPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ totalUnrealizedPnl >= 0 ? '+' : '' }}{{ formatCurrency(totalUnrealizedPnl) }}
              </span>
            </div>
            <div v-if="showUnrealizedPnl" class="flex justify-between border-t pt-2">
              <span class="font-medium">Projected Profit:</span>
              <span class="font-semibold text-blue-600" :class="displayStats.projectedProfit >= 0 ? 'text-blue-600' : 'text-orange-600'">
                {{ formatCurrency(displayStats.projectedProfit) }}
              </span>
            </div>
            <div class="flex justify-between" :class="{ 'border-t pt-2': showUnrealizedPnl }">
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

              <div v-if="openPositions.length === 0" class="text-center py-8 text-gray-500">
                No open positions
              </div>
              <UTable v-else :data="openPositions" :columns="openPositionsColumns" />
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

              <div v-if="closedPositions.length === 0" class="text-center py-8 text-gray-500">
                No closed positions
              </div>
              <UTable v-else :data="closedPositions" :columns="closedPositionsColumns" />
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
    :position="selectedPosition"
    @add-execution="openAddExecutionModal"
    @edit-metadata="openEditMetadataModal"
    @close-position="openClosePositionModal"
    @delete="openDeletePositionModal"
  />

  <PortfolioAddExecutionModal
    v-model="isAddExecutionModalOpen"
    :position="selectedPosition"
    @success="loadPortfolioData"
  />

  <PortfolioClosePositionModal
    v-model="isClosePositionModalOpen"
    :position="selectedPosition"
    @success="loadPortfolioData"
  />

  <PortfolioEditPositionMetadataModal
    v-model="isEditMetadataModalOpen"
    :position="selectedPosition"
    @success="loadPortfolioData"
  />

  <PortfolioDeletePositionModal
    v-model="isDeletePositionModalOpen"
    :position="selectedPosition"
    @delete="handleDeletePosition"
  />
</template>
