<script setup lang="ts">
import { h } from 'vue'
import type { Row } from '@tanstack/vue-table'
import type { TableColumn } from '@nuxt/ui'
import type { ScanRequest, ScanResponse, ScanResult, ScannerTrade, ExitCheckResponse, ExitCheckResult, EntryValidationResponse, EntryValidationResult, PositionSizingSettings, OptionContractResponse, DrawdownStatsResponse, ClosedTradeStatsResponse, EquityCurvePoint } from '~/types'

// State
const pageLoading = ref(true)
const loadingMessage = ref('Loading trades...')
const scanResponse = ref<ScanResponse | null>(null)
const trades = ref<ScannerTrade[]>([])
const sortedTrades = computed(() => [...trades.value].sort((a, b) => b.entryDate.localeCompare(a.entryDate)))
const sortedClosedTrades = computed(() => [...closedTrades.value].sort((a, b) => (b.exitDate ?? '').localeCompare(a.exitDate ?? '')))
const exitResults = ref<Map<number, ExitCheckResult>>(new Map())
const scanStatus = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const exitCheckStatus = ref<'idle' | 'pending'>('idle')
const validationStatus = ref<'idle' | 'pending'>('idle')
const validationResults = ref<Map<string, EntryValidationResult>>(new Map())
const activeTab = ref('trades')
const closedTradeStats = ref<ClosedTradeStatsResponse | null>(null)

// Modal states
const isScanConfigModalOpen = ref(false)
const isDeleteTradeModalOpen = ref(false)
const isRollTradeModalOpen = ref(false)
const isTradeDetailsModalOpen = ref(false)
const isCloseTradeModalOpen = ref(false)
const isResetPeakModalOpen = ref(false)
const isResetAllTradesModalOpen = ref(false)
const isBatchAddModalOpen = ref(false)
const selectedTrade = ref<ScannerTrade | null>(null)

// Last scan config (for pre-filling add trade)
const lastEntryStrategy = ref('')
const lastExitStrategy = ref('')

// Drawdown state
const closedTrades = ref<ScannerTrade[]>([])
const drawdownStats = ref<DrawdownStatsResponse | null>(null)
const closeTradePrice = ref(0)
const closeTradeDate = ref(new Date().toISOString().split('T')[0])

// Position sizing
const positionSizingSettings = ref<PositionSizingSettings>({
  enabled: true,
  portfolioValue: 100000,
  riskPercentage: 1.5,
  nAtr: 2.0,
  instrumentMode: 'STOCK',
  maxPositions: 15,
  drawdownScalingEnabled: true,
  drawdownThresholds: [
    { drawdownPercent: 5.0, riskMultiplier: 0.67 },
    { drawdownPercent: 10.0, riskMultiplier: 0.33 }
  ],
  peakEquity: null
})
const selectedSymbols = ref<Set<string>>(new Set())

// Option contracts
const optionContracts = ref<Map<string, OptionContractResponse>>(new Map())
const optionFetchStatus = ref<'idle' | 'pending'>('idle')

const toast = useToast()

const isOptionsMode = computed(() => positionSizingSettings.value.instrumentMode === 'OPTION')

// Drawdown computed properties
const totalUnrealizedPnlDollars = computed(() => {
  let total = 0
  for (const [, result] of exitResults.value) {
    total += result.unrealizedPnlDollars ?? 0
  }
  return total
})

const todayPnlDollars = computed(() => {
  let total = 0
  for (const [, result] of exitResults.value) {
    total += result.dailyPnlDollars ?? 0
  }
  return total
})

const exitTriggeredSymbols = computed(() => {
  return trades.value
    .filter(t => exitResults.value.get(t.id)?.exitTriggered)
    .map(t => t.symbol)
})

const currentEquity = computed(() => {
  const pv = positionSizingSettings.value.portfolioValue
  const realized = drawdownStats.value?.totalRealizedPnl ?? 0
  return pv + realized + totalUnrealizedPnlDollars.value
})

const currentDrawdownPct = computed(() => {
  const peak = positionSizingSettings.value.peakEquity ?? positionSizingSettings.value.portfolioValue
  if (peak <= 0) return 0
  return Math.max(0, ((peak - currentEquity.value) / peak) * 100)
})

const sortedThresholds = computed(() => {
  const thresholds = positionSizingSettings.value.drawdownThresholds ?? []
  return [...thresholds].sort((a, b) => a.drawdownPercent - b.drawdownPercent)
})

const activeDrawdownThreshold = computed(() => {
  if (!positionSizingSettings.value.drawdownScalingEnabled) return null
  return sortedThresholds.value.findLast(t => currentDrawdownPct.value >= t.drawdownPercent) ?? null
})

const effectiveRiskPercentage = computed(() => {
  const base = positionSizingSettings.value.riskPercentage
  if (!activeDrawdownThreshold.value) return base
  return base * activeDrawdownThreshold.value.riskMultiplier
})

function calculateQuantity(atr: number, symbol?: string): number {
  if (atr <= 0) return 0
  const { nAtr } = positionSizingSettings.value
  const equity = currentEquity.value
  if (equity <= 0) return 0
  const riskDollars = equity * (effectiveRiskPercentage.value / 100)

  if (isOptionsMode.value && symbol) {
    const contract = optionContracts.value.get(symbol)
    const delta = contract?.delta ?? 0.80
    return Math.floor(riskDollars / (nAtr * atr * delta * 100))
  }
  return Math.floor(riskDollars / (nAtr * atr))
}

function calculateRiskDollars(atr: number, symbol?: string): number {
  if (atr <= 0) return 0
  const { nAtr } = positionSizingSettings.value
  const qty = calculateQuantity(atr, symbol)
  if (qty <= 0) return 0

  if (isOptionsMode.value && symbol) {
    const contract = optionContracts.value.get(symbol)
    const delta = contract?.delta ?? 0.80
    return qty * nAtr * atr * delta * 100
  }
  return qty * nAtr * atr
}

const existingCapitalDeployed = computed(() =>
  trades.value.reduce((sum, t) => {
    if (t.instrumentType === 'OPTION') {
      return sum + t.quantity * t.entryPrice * t.multiplier
    }
    return sum + t.quantity * t.entryPrice
  }, 0)
)

const totalPortfolioRisk = computed(() => {
  // Approximation: uses current equity and risk settings for all trades.
  // Actual risk per trade may differ since trades were entered at different equity levels.
  const equity = currentEquity.value
  if (equity <= 0) return 0
  const riskPerTrade = equity * (effectiveRiskPercentage.value / 100)
  return riskPerTrade * trades.value.length
})

const portfolioRiskPct = computed(() => {
  const equity = currentEquity.value
  if (equity <= 0) return 0
  return (totalPortfolioRisk.value / equity) * 100
})

const maxPositions = computed(() => positionSizingSettings.value.maxPositions ?? 15)
const availableSlots = computed(() => maxPositions.value)
const activeTradeSymbols = computed(() => new Set(trades.value.map(t => t.symbol)))

const filteredResults = computed(() => {
  if (!scanResponse.value) return []
  return scanResponse.value.results.filter(r => !activeTradeSymbols.value.has(r.symbol))
})

const preferredResults = computed(() => filteredResults.value.slice(0, availableSlots.value))

const remainingResults = computed(() => filteredResults.value.slice(availableSlots.value))

const selectedResults = computed(() => {
  if (!scanResponse.value) return []
  return scanResponse.value.results.filter(r => selectedSymbols.value.has(r.symbol))
})

const selectedCapital = computed(() =>
  selectedResults.value.reduce((sum, r) => {
    const qty = calculateQuantity(r.atr, r.symbol)
    if (isOptionsMode.value) {
      const contract = optionContracts.value.get(r.symbol)
      const optionPrice = contract?.price ?? r.closePrice
      return sum + qty * optionPrice * 100
    }
    return sum + qty * r.closePrice
  }, 0)
)

const portfolioUtilization = computed(() => {
  const equity = currentEquity.value
  if (equity <= 0) return 0
  return ((existingCapitalDeployed.value + selectedCapital.value) / equity) * 100
})

const equityCurveData = computed<EquityCurvePoint[]>(() => {
  const closed = closedTrades.value.filter(t => t.exitDate && t.realizedPnl != null)
  if (closed.length === 0) return []

  const sorted = [...closed].sort((a, b) => a.exitDate!.localeCompare(b.exitDate!))
  const startingCapital = positionSizingSettings.value.portfolioValue
  if (startingCapital <= 0) return []

  let equity = startingCapital
  return sorted.map((trade) => {
    const profitPct = (trade.realizedPnl! / equity) * 100
    equity += trade.realizedPnl!
    return { date: trade.exitDate!, profitPercentage: profitPct }
  })
})

const tabItems = [
  {
    label: 'Active Trades',
    icon: 'i-lucide-list',
    value: 'trades',
    slot: 'trades'
  },
  {
    label: 'Closed Trades',
    icon: 'i-lucide-archive',
    value: 'closed',
    slot: 'closed'
  },
  {
    label: 'Candidates',
    icon: 'i-lucide-crosshair',
    value: 'results',
    slot: 'results'
  }
]

const latestDataDate = ref<string | null>(null)
const dataIsStale = computed(() => {
  if (!latestDataDate.value) return false
  const now = new Date()
  const dataDate = new Date(latestDataDate.value + 'T00:00:00')
  const diffDays = Math.floor((now.getTime() - dataDate.getTime()) / (1000 * 60 * 60 * 24))
  return diffDays > 2
})

const lastExitCheckAt = ref<number>(0)
const EXIT_CHECK_COOLDOWN_MS = 5 * 60 * 1000

onMounted(async () => {
  void loadDataFreshness()
  try {
    loadingMessage.value = 'Loading trades and settings...'
    await Promise.all([loadTrades(), loadPositionSizingSettings(), loadDrawdownStats(), loadClosedTrades(), loadClosedTradeStats()])
    if (trades.value.length > 0) {
      const now = Date.now()
      if (now - lastExitCheckAt.value >= EXIT_CHECK_COOLDOWN_MS) {
        lastExitCheckAt.value = now
        loadingMessage.value = `Checking status for ${trades.value.length} trade${trades.value.length > 1 ? 's' : ''}...`
        await checkExits()
      }
    }
  } finally {
    pageLoading.value = false
  }
})

async function loadDataFreshness() {
  try {
    const data = await $fetch<{ latestDataDate: string | null }>('/udgaard/api/data-management/latest-date')
    latestDataDate.value = data.latestDataDate
  } catch (error) {
    console.error('Failed to load data freshness:', error)
  }
}

async function loadPositionSizingSettings() {
  try {
    const data = await $fetch<PositionSizingSettings>('/udgaard/api/settings/position-sizing')
    positionSizingSettings.value = data
  } catch (error) {
    console.error('Failed to load position sizing settings:', error)
  }
}

async function savePositionSizingSettings() {
  try {
    await $fetch('/udgaard/api/settings/position-sizing', {
      method: 'POST',
      body: positionSizingSettings.value
    })
  } catch (error) {
    console.error('Failed to save position sizing settings:', error)
  }
}

async function loadTrades() {
  try {
    trades.value = await $fetch<ScannerTrade[]>('/udgaard/api/scanner/trades')
  } catch (error) {
    console.error('Error loading scanner trades:', error)
  }
}

async function loadClosedTrades() {
  try {
    closedTrades.value = await $fetch<ScannerTrade[]>('/udgaard/api/scanner/trades/closed')
  } catch (error) {
    console.error('Error loading closed trades:', error)
  }
}

async function loadClosedTradeStats() {
  try {
    closedTradeStats.value = await $fetch<ClosedTradeStatsResponse>('/udgaard/api/scanner/trades/closed/stats')
  } catch (error) {
    console.error('Error loading closed trade stats:', error)
  }
}

async function loadDrawdownStats() {
  try {
    const stats = await $fetch<DrawdownStatsResponse>('/udgaard/api/scanner/drawdown-stats')
    drawdownStats.value = stats
    positionSizingSettings.value.peakEquity = stats.peakEquity
  } catch (error) {
    console.error('Error loading drawdown stats:', error)
  }
}

async function runScan(config: ScanRequest) {
  isScanConfigModalOpen.value = false
  scanStatus.value = 'pending'
  activeTab.value = 'results'
  lastEntryStrategy.value = config.entryStrategyName
  lastExitStrategy.value = config.exitStrategyName
  validationResults.value = new Map()

  try {
    scanResponse.value = await $fetch<ScanResponse>('/udgaard/api/scanner/scan', {
      method: 'POST',
      body: config
    })
    scanStatus.value = 'success'

    // Fetch option contracts for preferred results in options mode
    if (isOptionsMode.value && scanResponse.value.results.length > 0) {
      await fetchOptionContracts(preferredResults.value)
    } else {
      optionContracts.value = new Map()
    }

    toast.add({
      title: 'Scan Complete',
      description: `Found ${scanResponse.value.results.length} matches from ${scanResponse.value.totalStocksScanned} stocks in ${(scanResponse.value.executionTimeMs / 1000).toFixed(1)}s`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (error: any) {
    scanStatus.value = 'error'
    toast.add({
      title: 'Scan Failed',
      description: error.data?.message || 'Failed to run scan',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

async function fetchOptionContracts(results: ScanResult[]) {
  if (results.length === 0) return
  optionFetchStatus.value = 'pending'
  try {
    const symbols = results.slice(0, 20).map(r => r.symbol)
    const stockPrices: Record<string, number> = {}
    for (const r of results.slice(0, 20)) {
      stockPrices[r.symbol] = r.closePrice
    }
    const date = results[0]?.date
    const data = await $fetch<Record<string, OptionContractResponse>>('/udgaard/api/scanner/option-contracts', {
      method: 'POST',
      body: { symbols, stockPrices, date }
    })
    optionContracts.value = new Map(Object.entries(data))
  } catch (error) {
    console.error('Failed to fetch option contracts:', error)
    optionContracts.value = new Map()
  } finally {
    optionFetchStatus.value = 'idle'
  }
}

function downloadWatchlist() {
  const symbols = trades.value.map(t => t.symbol).join('\n')
  const blob = new Blob([symbols + '\n'], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'tradingview_watchlist.txt'
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function checkExits() {
  exitCheckStatus.value = 'pending'
  try {
    const response = await $fetch<ExitCheckResponse>('/udgaard/api/scanner/check-exits', {
      method: 'POST'
    })

    const newMap = new Map<number, ExitCheckResult>()
    for (const result of response.results) {
      newMap.set(result.tradeId, result)
    }
    exitResults.value = newMap
    updatePeakEquity()

    toast.add({
      title: 'Status Check Complete',
      description: `${response.checksPerformed} trades checked, ${response.exitsTriggered} exits triggered`,
      icon: 'i-lucide-check-circle',
      color: response.exitsTriggered > 0 ? 'warning' : 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to check status',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    exitCheckStatus.value = 'idle'
  }
}

async function validateEntries() {
  if (!scanResponse.value) return
  validationStatus.value = 'pending'
  try {
    const symbols = preferredResults.value.map(r => r.symbol)
    const response = await $fetch<EntryValidationResponse>('/udgaard/api/scanner/validate-entries', {
      method: 'POST',
      body: {
        symbols,
        entryStrategyName: scanResponse.value.entryStrategyName,
        exitStrategyName: scanResponse.value.exitStrategyName
      }
    })

    const newMap = new Map<string, EntryValidationResult>()
    for (const result of response.results) {
      newMap.set(result.symbol, result)
    }
    validationResults.value = newMap

    toast.add({
      title: 'Validation Complete',
      description: `${response.validCount} valid, ${response.invalidCount} invalid entry, ${response.doaCount} DOA`,
      icon: 'i-lucide-check-circle',
      color: response.invalidCount > 0 || response.doaCount > 0 ? 'warning' : 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to validate entries',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    validationStatus.value = 'idle'
  }
}

function openDeleteTrade(trade: ScannerTrade) {
  selectedTrade.value = trade
  isDeleteTradeModalOpen.value = true
}

function openRollTrade(trade: ScannerTrade) {
  selectedTrade.value = trade
  isRollTradeModalOpen.value = true
}

function openTradeDetails(trade: ScannerTrade) {
  selectedTrade.value = trade
  isTradeDetailsModalOpen.value = true
}

function openCloseTrade(trade: ScannerTrade) {
  selectedTrade.value = trade
  const exitResult = exitResults.value.get(trade.id)
  closeTradePrice.value = exitResult?.currentPrice ?? trade.entryPrice
  closeTradeDate.value = new Date().toISOString().split('T')[0]
  isCloseTradeModalOpen.value = true
}

const closeTradeValid = computed(() => {
  if (!selectedTrade.value) return false
  if (closeTradePrice.value <= 0) return false
  if (!closeTradeDate.value || closeTradeDate.value < selectedTrade.value.entryDate) return false
  return true
})

async function closeTrade() {
  if (!selectedTrade.value || !closeTradeValid.value) return
  try {
    await $fetch(`/udgaard/api/scanner/trades/${selectedTrade.value.id}/close`, {
      method: 'PUT',
      body: {
        exitPrice: closeTradePrice.value,
        exitDate: closeTradeDate.value
      }
    })
    isCloseTradeModalOpen.value = false
    await Promise.all([loadTrades(), loadClosedTrades(), loadClosedTradeStats(), loadDrawdownStats()])
    updatePeakEquity()
    toast.add({
      title: 'Trade Closed',
      description: `Closed ${selectedTrade.value.symbol}`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (_error: any) {
    toast.add({
      title: 'Error',
      description: 'Failed to close trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

function updatePeakEquity() {
  const current = currentEquity.value
  const peak = positionSizingSettings.value.peakEquity ?? positionSizingSettings.value.portfolioValue
  if (current > peak) {
    positionSizingSettings.value.peakEquity = current
  }
}

function resetPeakEquity() {
  positionSizingSettings.value.peakEquity = currentEquity.value
}

async function deleteTrade() {
  if (!selectedTrade.value) return
  try {
    await $fetch(`/udgaard/api/scanner/trades/${selectedTrade.value.id}`, {
      method: 'DELETE'
    })
    isDeleteTradeModalOpen.value = false
    await loadTrades()
    toast.add({
      title: 'Trade Removed',
      description: `Removed ${selectedTrade.value.symbol} from scanner`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (_error: any) {
    toast.add({
      title: 'Error',
      description: 'Failed to remove trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

async function resetAllTrades() {
  try {
    const result = await $fetch<{ deleted: number }>('/udgaard/api/scanner/trades/reset', { method: 'POST' })
    isResetAllTradesModalOpen.value = false
    positionSizingSettings.value.peakEquity = null
    await Promise.all([loadTrades(), loadClosedTrades(), loadClosedTradeStats(), loadDrawdownStats()])
    exitResults.value = new Map()
    toast.add({
      title: 'Scanner Reset',
      description: `Deleted ${result.deleted} trades`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (_error: any) {
    toast.add({
      title: 'Error',
      description: 'Failed to reset trades',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

async function deleteClosedTrade(trade: ScannerTrade) {
  try {
    await $fetch(`/udgaard/api/scanner/trades/${trade.id}`, { method: 'DELETE' })
    await Promise.all([loadClosedTrades(), loadClosedTradeStats(), loadDrawdownStats()])
    toast.add({
      title: 'Trade Deleted',
      description: `Deleted closed trade ${trade.symbol}`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (_error: any) {
    toast.add({
      title: 'Error',
      description: 'Failed to delete closed trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

async function onTradeRolled() {
  await loadTrades()
}

function openBatchAddModal() {
  if (selectedResults.value.length === 0) return
  isBatchAddModalOpen.value = true
}

async function onBatchTradesAdded() {
  selectedSymbols.value = new Set()
  await loadTrades()
  activeTab.value = 'trades'
}

function onRemoveSymbol(symbol: string) {
  const next = new Set(selectedSymbols.value)
  next.delete(symbol)
  selectedSymbols.value = next
}

function daysHeld(entryDate: string): number {
  const parts = entryDate.split('-').map(Number)
  const year = parts[0] ?? 0
  const month = parts[1] ?? 1
  const day = parts[2] ?? 1
  const entry = new Date(year, month - 1, day)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.floor((today.getTime() - entry.getTime()) / (1000 * 60 * 60 * 24))
}

// Active trades table columns
const tradeColumns: TableColumn<ScannerTrade>[] = [
  {
    id: 'symbol',
    header: 'Symbol',
    cell: ({ row }) => h('div', { class: 'flex items-center gap-1.5' }, [
      h(resolveComponent('SymbolLink'), { symbol: row.original.symbol }),
      h(resolveComponent('UButton'), {
        icon: 'i-lucide-info',
        size: 'xs',
        variant: 'ghost',
        color: 'neutral',
        onClick: () => openTradeDetails(row.original)
      })
    ])
  },
  {
    id: 'entryPrice',
    header: 'Entry Price',
    cell: ({ row }) => formatUsd(row.original.entryPrice)
  },
  {
    id: 'currentPrice',
    header: 'Current Price',
    cell: ({ row }) => {
      const result = exitResults.value.get(row.original.id)
      if (!result) return '-'
      return formatUsd(result.currentPrice)
    }
  },
  {
    id: 'value',
    header: 'Value',
    cell: ({ row }) => {
      const result = exitResults.value.get(row.original.id)
      if (!result) return '-'
      const trade = row.original
      const multiplier = trade.instrumentType === 'OPTION' ? (trade.multiplier || 100) : 1
      const value = result.currentPrice * trade.quantity * multiplier
      return formatUsd(value, false)
    }
  },
  {
    id: 'quantity',
    header: 'Qty',
    cell: ({ row }) => row.original.quantity.toLocaleString()
  },
  {
    id: 'optionPrice',
    header: 'Premium',
    cell: ({ row }) => {
      const t = row.original
      if (t.instrumentType !== 'OPTION') return '-'
      return t.optionPrice ? formatUsd(t.optionPrice) : '-'
    }
  },
  {
    id: 'optionDetails',
    header: 'Option',
    cell: ({ row }) => {
      const t = row.original
      if (t.instrumentType !== 'OPTION') return '-'
      const delta = t.delta ? ` Δ${t.delta.toFixed(2)}` : ''
      return `${t.optionType} ${formatUsd(t.strikePrice ?? 0, false)} ${t.expirationDate}${delta}`
    }
  },
  {
    id: 'entryDate',
    header: 'Date',
    cell: ({ row }) => row.original.entryDate
  },
  {
    id: 'daysHeld',
    header: 'Days',
    cell: ({ row }) => daysHeld(row.original.entryDate).toString()
  },
  {
    id: 'strategy',
    header: 'Strategy',
    cell: ({ row }) => `${row.original.entryStrategyName} / ${row.original.exitStrategyName}`
  },
  {
    id: 'exitAlert',
    header: 'Status',
    cell: ({ row }) => {
      const result = exitResults.value.get(row.original.id)
      if (!result) return '-'

      const UIcon = resolveComponent('UIcon')
      const UBadge = resolveComponent('UBadge')

      if (result.exitTriggered) {
        return h(UBadge, { color: 'error', variant: 'solid', size: 'sm', class: 'gap-1' }, () => [
          h(UIcon, { name: 'i-lucide-x-circle', class: 'size-3.5 shrink-0' }),
          result.exitReason ?? 'EXIT'
        ])
      }

      const pnl = result.unrealizedPnlPercent
      const hasSignificantPnl = Math.abs(pnl) >= 0.05
      const pnlColor = pnl > 0 ? 'success' : pnl < -0.05 ? 'error' : 'neutral'
      const pnlLabel = hasSignificantPnl
        ? `${pnl >= 0 ? '+' : ''}${pnl.toFixed(1)}%`
        : null

      return h(UBadge, { color: pnlColor, variant: 'subtle', size: 'sm', class: 'gap-1' }, () => [
        h(UIcon, { name: 'i-lucide-check-circle', class: 'size-3.5 shrink-0' }),
        ...(pnlLabel ? [pnlLabel] : [])
      ])
    }
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => {
      const buttons = [
        h(resolveComponent('UButton'), {
          icon: 'i-lucide-circle-x',
          size: 'xs',
          variant: 'ghost',
          color: 'warning',
          title: 'Close trade',
          onClick: () => openCloseTrade(row.original)
        }),
        h(resolveComponent('UButton'), {
          icon: 'i-lucide-trash-2',
          size: 'xs',
          variant: 'ghost',
          color: 'error',
          title: 'Delete trade',
          onClick: () => openDeleteTrade(row.original)
        })
      ]
      if (row.original.instrumentType === 'OPTION') {
        buttons.unshift(
          h(resolveComponent('UButton'), {
            icon: 'i-lucide-refresh-cw',
            size: 'xs',
            variant: 'ghost',
            onClick: () => openRollTrade(row.original)
          })
        )
      }
      return h('div', { class: 'flex gap-1' }, buttons)
    }
  }
]

// Closed trades table columns
const closedTradeColumns: TableColumn<ScannerTrade>[] = [
  {
    id: 'symbol',
    header: 'Symbol',
    cell: ({ row }) => h(resolveComponent('SymbolLink'), { symbol: row.original.symbol })
  },
  {
    id: 'instrumentType',
    header: 'Type',
    cell: ({ row }) => row.original.instrumentType
  },
  {
    id: 'entryPrice',
    header: 'Entry',
    cell: ({ row }) => formatUsd(row.original.entryPrice)
  },
  {
    id: 'exitPrice',
    header: 'Exit',
    cell: ({ row }) => row.original.exitPrice ? formatUsd(row.original.exitPrice) : '-'
  },
  {
    id: 'entryDate',
    header: 'Opened',
    cell: ({ row }) => row.original.entryDate
  },
  {
    id: 'exitDate',
    header: 'Closed',
    cell: ({ row }) => row.original.exitDate ?? '-'
  },
  {
    id: 'quantity',
    header: 'Qty',
    cell: ({ row }) => row.original.quantity.toString()
  },
  {
    id: 'realizedPnl',
    header: 'P&L',
    cell: ({ row }) => {
      const pnl = row.original.realizedPnl
      if (pnl == null) return '-'
      const color = pnl >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
      return h('span', { class: color }, formatSignedUsd(pnl))
    }
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h(resolveComponent('UButton'), {
      icon: 'i-lucide-trash-2',
      size: 'xs',
      variant: 'ghost',
      color: 'error',
      onClick: () => deleteClosedTrade(row.original)
    })
  }
]

const tradesTableUi = computed(() => ({
  // NuxtUI v4 UTable accepts (row: Row<T>) => string for tr at runtime, but Volar
  // resolves the ui prop as the static theme slot type (ClassNameValue). Cast to any.
  tr: ((row: Row<ScannerTrade>) =>
    exitResults.value.get(row.original.id)?.exitTriggered
      ? 'bg-red-500/8 dark:bg-red-500/10 border-l-2 border-l-red-500'
      : '') as any
}))
</script>

<template>
  <UDashboardPanel id="mission-control">
    <template #header>
      <UDashboardNavbar title="Mission Control">
        <template #right>
          <UTooltip v-if="dataIsStale" :text="`Stock data last updated: ${latestDataDate}`">
            <UBadge color="warning" variant="subtle" class="mr-4 cursor-help">
              <UIcon name="i-lucide-alert-triangle" class="w-3.5 h-3.5 mr-1" />
              Data stale ({{ latestDataDate }})
            </UBadge>
          </UTooltip>
          <div v-if="exitResults.size > 0" class="flex items-center gap-1.5 mr-4">
            <span class="text-xs text-muted">Today</span>
            <span
              class="text-sm font-semibold tabular-nums"
              :class="todayPnlDollars >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
            >
              {{ formatSignedUsd(todayPnlDollars, false) }}
            </span>
          </div>
          <UButton
            label="Reset"
            icon="i-lucide-trash-2"
            color="error"
            variant="ghost"
            @click="isResetAllTradesModalOpen = true"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div v-if="pageLoading" class="flex flex-col items-center justify-center py-24">
        <UIcon name="i-lucide-loader-2" class="w-12 h-12 text-primary animate-spin mb-4" />
        <p class="text-muted">
          {{ loadingMessage }}
        </p>
      </div>
      <div v-else class="p-4 space-y-4">
        <!-- Stats Cards -->
        <ScannerStatsCards
          :trades="trades"
          :exit-results="exitResults"
          :capital-deployed="positionSizingSettings.enabled ? existingCapitalDeployed : undefined"
          :portfolio-value="positionSizingSettings.enabled ? currentEquity : undefined"
          :drawdown-pct="currentDrawdownPct"
          :effective-risk="effectiveRiskPercentage"
          :base-risk="positionSizingSettings.riskPercentage"
          :drawdown-scaling-active="positionSizingSettings.drawdownScalingEnabled"
          :total-risk="positionSizingSettings.enabled ? totalPortfolioRisk : undefined"
          :risk-pct="positionSizingSettings.enabled ? portfolioRiskPct : undefined"
          :max-heat-pct="positionSizingSettings.enabled ? maxPositions * effectiveRiskPercentage : undefined"
        />

        <!-- Drawdown Alert (always visible when active) -->
        <UAlert
          v-if="positionSizingSettings.enabled && positionSizingSettings.drawdownScalingEnabled && activeDrawdownThreshold"
          :color="currentDrawdownPct >= 10 ? 'error' : 'warning'"
          :title="`Drawdown: ${currentDrawdownPct.toFixed(1)}%`"
          :description="`Risk scaled to ${effectiveRiskPercentage.toFixed(2)}% (${activeDrawdownThreshold.riskMultiplier}x of ${positionSizingSettings.riskPercentage}%)`"
          icon="i-lucide-trending-down"
        />

        <!-- Position Sizing (read-only) -->
        <UCollapsible v-if="positionSizingSettings.enabled">
          <UButton
            variant="ghost"
            color="neutral"
            size="sm"
            icon="i-lucide-calculator"
            class="w-full justify-start"
          >
            <span class="text-sm">
              {{ positionSizingSettings.instrumentMode === 'OPTION' ? 'Options' : 'Stock' }}
              &middot; {{ formatUsd(positionSizingSettings.portfolioValue, false) }}
              &middot; {{ effectiveRiskPercentage.toFixed(2) }}% risk
              &middot; {{ positionSizingSettings.nAtr }}x ATR
              &middot; {{ portfolioUtilization.toFixed(0) }}% utilized
            </span>
          </UButton>
          <template #content>
            <div class="p-4 bg-muted/50 rounded-lg border border-default space-y-3 mt-1">
              <div class="flex items-center justify-between">
                <span class="text-sm font-semibold">Position Sizing</span>
                <NuxtLink to="/settings">
                  <UButton
                    label="Settings"
                    icon="i-lucide-settings"
                    size="xs"
                    variant="soft"
                    color="neutral"
                  />
                </NuxtLink>
              </div>
              <div class="grid grid-cols-4 gap-4 text-sm">
                <div>
                  <span class="text-muted">Instrument</span>
                  <div class="flex mt-1">
                    <UButton
                      label="Stock"
                      size="xs"
                      :variant="positionSizingSettings.instrumentMode === 'STOCK' ? 'solid' : 'outline'"
                      class="flex-1 rounded-r-none"
                      @click="positionSizingSettings.instrumentMode = 'STOCK'; savePositionSizingSettings()"
                    />
                    <UButton
                      label="Options"
                      size="xs"
                      :variant="positionSizingSettings.instrumentMode === 'OPTION' ? 'solid' : 'outline'"
                      class="flex-1 rounded-l-none"
                      @click="positionSizingSettings.instrumentMode = 'OPTION'; savePositionSizingSettings()"
                    />
                  </div>
                </div>
                <div>
                  <span class="text-muted">Starting Capital</span>
                  <p class="font-medium">
                    {{ formatUsd(positionSizingSettings.portfolioValue, false) }}
                  </p>
                </div>
                <div>
                  <span class="text-muted">Risk Per Trade</span>
                  <p class="font-medium">
                    {{ positionSizingSettings.riskPercentage }}%
                  </p>
                </div>
                <div>
                  <span class="text-muted">ATR Multiplier</span>
                  <p class="font-medium">
                    {{ positionSizingSettings.nAtr }}x
                  </p>
                </div>
              </div>
              <!-- Portfolio Utilization -->
              <div class="space-y-1">
                <div class="flex items-center justify-between text-xs text-muted">
                  <span>
                    Existing: {{ formatUsd(existingCapitalDeployed, false) }}
                    <template v-if="selectedCapital > 0">
                      + Selected: {{ formatUsd(selectedCapital, false) }}
                    </template>
                  </span>
                  <span :class="portfolioUtilization > 100 ? 'text-red-500 font-semibold' : ''">
                    {{ portfolioUtilization.toFixed(1) }}% utilized
                  </span>
                </div>
                <div class="h-1.5 w-full rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all"
                    :class="portfolioUtilization > 100 ? 'bg-red-500' : portfolioUtilization > 80 ? 'bg-yellow-500' : 'bg-primary'"
                    :style="{ width: `${Math.min(portfolioUtilization, 100)}%` }"
                  />
                </div>
              </div>

              <!-- Drawdown Scaling -->
              <template v-if="positionSizingSettings.drawdownScalingEnabled">
                <div class="flex items-center gap-2 pt-2 border-t border-default">
                  <UIcon name="i-lucide-shield-alert" class="w-4 h-4 text-muted" />
                  <span class="text-sm font-semibold">Drawdown Scaling</span>
                </div>

                <!-- Drawdown Info Row -->
                <div class="grid grid-cols-5 gap-4 text-sm items-center">
                  <div>
                    <span class="text-muted">Equity:</span>
                    <span class="ml-1 font-medium">{{ formatUsd(currentEquity, false) }}</span>
                  </div>
                  <div>
                    <span class="text-muted">Peak:</span>
                    <span class="ml-1 font-medium">{{ formatUsd(positionSizingSettings.peakEquity ?? positionSizingSettings.portfolioValue, false) }}</span>
                  </div>
                  <div>
                    <span class="text-muted">Drawdown:</span>
                    <span
                      class="ml-1 font-medium"
                      :class="currentDrawdownPct >= 10 ? 'text-red-600 dark:text-red-400' : currentDrawdownPct >= 5 ? 'text-yellow-600 dark:text-yellow-400' : ''"
                    >
                      {{ currentDrawdownPct.toFixed(1) }}%
                    </span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-muted">Risk:</span>
                    <span class="font-medium">
                      <template v-if="activeDrawdownThreshold">
                        <span class="line-through text-muted">{{ positionSizingSettings.riskPercentage }}%</span>
                        <span class="ml-1 text-warning">{{ effectiveRiskPercentage.toFixed(2) }}%</span>
                      </template>
                      <template v-else>
                        {{ positionSizingSettings.riskPercentage }}%
                      </template>
                    </span>
                  </div>
                  <div class="flex justify-end">
                    <UButton
                      label="Reset Peak"
                      size="xs"
                      variant="soft"
                      color="warning"
                      icon="i-lucide-rotate-ccw"
                      @click="isResetPeakModalOpen = true"
                    />
                  </div>
                </div>

                <!-- Drawdown Thresholds Display -->
                <div v-if="positionSizingSettings.drawdownThresholds?.length" class="flex flex-wrap gap-2">
                  <div
                    v-for="t in sortedThresholds"
                    :key="t.drawdownPercent"
                    class="flex items-center gap-1.5 px-2 py-1 rounded-md text-xs border"
                    :class="currentDrawdownPct >= t.drawdownPercent
                      ? 'border-red-500/50 bg-red-500/10 text-red-600 dark:text-red-400'
                      : 'border-default bg-muted/30 text-muted'"
                  >
                    <UIcon
                      :name="currentDrawdownPct >= t.drawdownPercent ? 'i-lucide-alert-triangle' : 'i-lucide-shield'"
                      class="size-3"
                    />
                    <span class="font-medium">{{ t.drawdownPercent }}%</span>
                    <span>&rarr;</span>
                    <span v-if="t.riskMultiplier === 0" class="font-semibold">HALT</span>
                    <span v-else>{{ (positionSizingSettings.riskPercentage * t.riskMultiplier).toFixed(2) }}% risk</span>
                  </div>
                </div>
              </template>
            </div>
          </template>
        </UCollapsible>

        <!-- Equity Curve -->
        <UCollapsible v-if="equityCurveData.length > 0">
          <UButton
            variant="ghost"
            color="neutral"
            size="sm"
            icon="i-lucide-trending-up"
            class="w-full justify-start"
          >
            <span class="text-sm">
              Equity Curve &middot; {{ equityCurveData.length }} closed trades
            </span>
          </UButton>
          <template #content>
            <div class="mt-2">
              <EquityCurve
                :equity-curve-data="equityCurveData"
                :initial-capital="positionSizingSettings.portfolioValue"
                :loading="false"
              />
            </div>
          </template>
        </UCollapsible>

        <!-- Tabs -->
        <UTabs v-model="activeTab" :items="tabItems">
          <!-- Scan Results Tab -->
          <template #results>
            <div class="pt-4">
              <div v-if="scanStatus === 'idle'" class="text-center py-12">
                <UIcon name="i-lucide-crosshair" class="w-12 h-12 text-muted mb-3" />
                <p class="text-muted">
                  Scan the market to find new trade candidates
                </p>
                <UButton
                  label="Find Candidates"
                  variant="soft"
                  class="mt-3"
                  @click="isScanConfigModalOpen = true"
                />
              </div>

              <div v-else-if="scanStatus === 'pending'" class="text-center py-12">
                <UIcon name="i-lucide-loader-2" class="w-8 h-8 text-primary animate-spin mb-3" />
                <p class="text-muted">
                  Finding candidates...
                </p>
              </div>

              <div v-else-if="scanResponse">
                <div class="flex items-center justify-between mb-3">
                  <div class="text-sm text-muted">
                    {{ scanResponse.results.length }} matches from {{ scanResponse.totalStocksScanned }} stocks
                    <template v-if="scanResponse.nearMissCandidates.length > 0">
                      &middot; {{ scanResponse.nearMissCandidates.length }} near-misses
                    </template>
                    &mdash; {{ scanResponse.entryStrategyName }} / {{ scanResponse.exitStrategyName }}
                    <template v-if="scanResponse.rankerName">
                      &middot; Ranked by {{ scanResponse.rankerName }}
                    </template>
                    ({{ (scanResponse.executionTimeMs / 1000).toFixed(1) }}s)
                    <template v-if="isOptionsMode && optionFetchStatus === 'pending'">
                      &middot; Loading option prices...
                    </template>
                  </div>
                  <div class="flex items-center gap-2">
                    <UButton
                      label="Find Candidates"
                      icon="i-lucide-crosshair"
                      size="sm"
                      @click="isScanConfigModalOpen = true"
                    />
                    <UButton
                      label="Validate"
                      icon="i-lucide-shield-check"
                      variant="soft"
                      size="sm"
                      :loading="validationStatus === 'pending'"
                      :disabled="preferredResults.length === 0"
                      @click="validateEntries"
                    />
                    <template v-if="positionSizingSettings.enabled && selectedSymbols.size > 0">
                      <span
                        class="text-xs tabular-nums"
                        :class="portfolioUtilization > 100 ? 'text-red-500 font-semibold' : portfolioUtilization > 80 ? 'text-yellow-500' : 'text-muted'"
                      >
                        {{ portfolioUtilization.toFixed(0) }}% utilized
                      </span>
                      <UTooltip
                        :text="portfolioUtilization > 100 ? 'Not enough equity to fund selected trades' : ''"
                        :disabled="portfolioUtilization <= 100"
                      >
                        <UButton
                          :label="`Add ${selectedSymbols.size} Trade${selectedSymbols.size > 1 ? 's' : ''}`"
                          icon="i-lucide-plus"
                          size="sm"
                          :disabled="portfolioUtilization > 100"
                          @click="openBatchAddModal"
                        />
                      </UTooltip>
                    </template>
                  </div>
                </div>

                <div v-if="scanResponse.results.length === 0 && scanResponse.nearMissCandidates.length === 0" class="text-center py-12">
                  <UIcon name="i-lucide-search-x" class="w-12 h-12 text-muted mb-3" />
                  <p class="text-muted">
                    No matches found
                  </p>
                  <UButton
                    label="Find Candidates"
                    icon="i-lucide-crosshair"
                    variant="soft"
                    class="mt-3"
                    @click="isScanConfigModalOpen = true"
                  />
                </div>

                <div v-if="preferredResults.length > 0" class="space-y-2">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-semibold">Preferred Trades</span>
                    <UBadge variant="subtle" size="sm">
                      {{ preferredResults.length }} of {{ maxPositions }} max
                    </UBadge>
                  </div>
                  <ScannerScanResultsTable
                    v-model="selectedSymbols"
                    :results="preferredResults"
                    :position-sizing-enabled="positionSizingSettings.enabled"
                    :calculate-quantity="calculateQuantity"
                    :calculate-risk-dollars="calculateRiskDollars"
                    :instrument-mode="positionSizingSettings.instrumentMode ?? 'STOCK'"
                    :option-contracts="optionContracts"
                    :validation-results="validationResults"
                  />
                </div>

                <div v-if="remainingResults.length > 0" class="space-y-2 mt-6">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-semibold text-muted">Remaining</span>
                    <UBadge variant="subtle" color="neutral" size="sm">
                      {{ remainingResults.length }}
                    </UBadge>
                  </div>
                  <ScannerScanResultsTable
                    v-model="selectedSymbols"
                    :results="remainingResults"
                    :position-sizing-enabled="positionSizingSettings.enabled"
                    :calculate-quantity="calculateQuantity"
                    :calculate-risk-dollars="calculateRiskDollars"
                    :instrument-mode="positionSizingSettings.instrumentMode ?? 'STOCK'"
                  />
                </div>

                <ScannerNearMissAnalysis
                  v-if="scanResponse.nearMissCandidates.length > 0 || scanResponse.conditionFailureSummary.length > 0"
                  :near-miss-candidates="scanResponse.nearMissCandidates"
                  :condition-failure-summary="scanResponse.conditionFailureSummary"
                  :total-stocks-scanned="scanResponse.totalStocksScanned"
                  :match-count="scanResponse.results.length"
                  class="mt-4"
                />
              </div>
            </div>
          </template>

          <!-- Active Trades Tab -->
          <template #trades>
            <div class="pt-4">
              <UAlert
                v-if="exitTriggeredSymbols.length > 0"
                color="error"
                :title="`${exitTriggeredSymbols.length} exit signal${exitTriggeredSymbols.length > 1 ? 's' : ''} triggered`"
                :description="`${exitTriggeredSymbols.join(', ')} — review and close`"
                icon="i-lucide-alert-triangle"
                class="mb-3"
              />
              <div class="flex justify-end gap-2 mb-3">
                <UButton
                  label="Export Watchlist"
                  icon="i-lucide-download"
                  variant="soft"
                  size="sm"
                  :disabled="trades.length === 0"
                  @click="downloadWatchlist"
                />
                <UButton
                  label="Check Status"
                  icon="i-lucide-shield-alert"
                  variant="soft"
                  size="sm"
                  :loading="exitCheckStatus === 'pending'"
                  :disabled="trades.length === 0"
                  @click="checkExits"
                />
              </div>
              <UTable
                :data="sortedTrades"
                :columns="tradeColumns"
                :ui="tradesTableUi"
              >
                <template #empty-state>
                  <div class="flex flex-col items-center justify-center py-8">
                    <UIcon name="i-lucide-inbox" class="w-10 h-10 text-muted mb-2" />
                    <p class="text-muted text-sm">
                      No active trades. Find candidates and add trades to track.
                    </p>
                  </div>
                </template>
              </UTable>
            </div>
          </template>

          <!-- Closed Trades Tab -->
          <template #closed>
            <div class="pt-4 space-y-4">
              <!-- Overall Stats Cards -->
              <div v-if="closedTradeStats?.overall" class="text-sm text-muted">
                {{ closedTradeStats!.byStrategy.length === 1 ? closedTradeStats!.byStrategy[0]?.strategy : 'All Strategies' }}
                &middot; {{ closedTradeStats!.overall!.trades }} closed trades
              </div>
              <div v-if="closedTradeStats?.overall" class="grid grid-cols-2 lg:grid-cols-6 gap-3">
                <div class="p-3 bg-muted/50 rounded-lg border border-default">
                  <div class="text-xs text-muted">
                    Total P&L
                  </div>
                  <div
                    class="text-xl font-bold mt-1"
                    :class="closedTradeStats.overall.totalPnl >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                  >
                    {{ formatSignedUsd(closedTradeStats.overall.totalPnl, false) }}
                  </div>
                </div>
                <div class="p-3 bg-muted/50 rounded-lg border border-default">
                  <div class="text-xs text-muted">
                    Edge
                  </div>
                  <div
                    class="text-xl font-bold mt-1"
                    :class="closedTradeStats.overall.edge >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                  >
                    {{ closedTradeStats.overall.edge >= 0 ? '+' : '' }}{{ closedTradeStats.overall.edge.toFixed(2) }}%
                  </div>
                </div>
                <div class="p-3 bg-muted/50 rounded-lg border border-default">
                  <div class="text-xs text-muted">
                    Win Rate
                  </div>
                  <div class="text-xl font-bold mt-1">
                    {{ closedTradeStats.overall.winRate.toFixed(0) }}%
                    <span class="text-sm font-normal text-muted">
                      ({{ closedTradeStats.overall.wins }}W / {{ closedTradeStats.overall.losses }}L)
                    </span>
                  </div>
                </div>
                <div class="p-3 bg-muted/50 rounded-lg border border-default">
                  <div class="text-xs text-muted">
                    Profit Factor
                  </div>
                  <div class="text-xl font-bold mt-1">
                    {{ closedTradeStats.overall.profitFactor === null ? '∞' : closedTradeStats.overall.profitFactor.toFixed(2) }}
                  </div>
                </div>
                <div class="p-3 bg-muted/50 rounded-lg border border-default">
                  <div class="text-xs text-muted">
                    Avg Win / Loss
                  </div>
                  <div class="mt-1">
                    <span class="text-base font-bold text-green-600 dark:text-green-400">
                      {{ formatSignedUsd(closedTradeStats.overall.avgWinDollars, false) }}
                    </span>
                    <span class="text-muted mx-0.5">/</span>
                    <span class="text-base font-bold text-red-600 dark:text-red-400">
                      -{{ formatUsd(closedTradeStats.overall.avgLossDollars, false) }}
                    </span>
                  </div>
                </div>
                <div class="p-3 bg-muted/50 rounded-lg border border-default">
                  <div class="text-xs text-muted">
                    Trades
                  </div>
                  <div class="text-xl font-bold mt-1">
                    {{ closedTradeStats.overall.trades }}
                  </div>
                </div>
              </div>

              <!-- Strategy Breakdown Table -->
              <div v-if="closedTradeStats && closedTradeStats.byStrategy.length > 1">
                <h3 class="text-sm font-semibold mb-2">
                  Performance by Entry Strategy
                </h3>
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-default text-muted">
                        <th class="text-left py-2 pr-4">
                          Strategy
                        </th>
                        <th class="text-right py-2 px-2">
                          Trades
                        </th>
                        <th class="text-right py-2 px-2">
                          Win Rate
                        </th>
                        <th class="text-right py-2 px-2">
                          Edge
                        </th>
                        <th class="text-right py-2 px-2">
                          PF
                        </th>
                        <th class="text-right py-2 px-2">
                          Avg Win
                        </th>
                        <th class="text-right py-2 px-2">
                          Avg Loss
                        </th>
                        <th class="text-right py-2 pl-2">
                          Total P&L
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="s in closedTradeStats.byStrategy" :key="s.strategy" class="border-b border-default/50">
                        <td class="py-2 pr-4">
                          {{ s.strategy }}
                          <UBadge
                            v-if="s.trades < 5"
                            variant="subtle"
                            color="warning"
                            size="xs"
                            class="ml-1"
                          >
                            &lt; 5
                          </UBadge>
                        </td>
                        <td class="text-right py-2 px-2">
                          {{ s.trades }}
                        </td>
                        <td class="text-right py-2 px-2" :class="s.winRate >= 50 ? 'text-green-600' : 'text-red-600'">
                          {{ s.winRate.toFixed(0) }}%
                        </td>
                        <td class="text-right py-2 px-2" :class="s.edge >= 0 ? 'text-green-600' : 'text-red-600'">
                          {{ s.edge >= 0 ? '+' : '' }}{{ s.edge.toFixed(2) }}%
                        </td>
                        <td class="text-right py-2 px-2">
                          {{ s.profitFactor === null ? '∞' : s.profitFactor.toFixed(2) }}
                        </td>
                        <td class="text-right py-2 px-2 text-green-600">
                          {{ formatSignedUsd(s.avgWinDollars, false) }}
                        </td>
                        <td class="text-right py-2 px-2 text-red-600">
                          -{{ formatUsd(s.avgLossDollars, false) }}
                        </td>
                        <td class="text-right py-2 pl-2" :class="s.totalPnl >= 0 ? 'text-green-600' : 'text-red-600'">
                          {{ formatSignedUsd(s.totalPnl, false) }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Closed Trades Table -->
              <UTable
                :data="sortedClosedTrades"
                :columns="closedTradeColumns"
              >
                <template #empty-state>
                  <div class="flex flex-col items-center justify-center py-8">
                    <UIcon name="i-lucide-archive" class="w-10 h-10 text-muted mb-2" />
                    <p class="text-muted text-sm">
                      No closed trades yet. Close trades to track realized P&L.
                    </p>
                  </div>
                </template>
              </UTable>
            </div>
          </template>
        </UTabs>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Modals -->
  <ScannerScanConfigModal
    v-model:open="isScanConfigModalOpen"
    @run-scan="runScan"
  />

  <ScannerBatchAddTradesModal
    v-model="isBatchAddModalOpen"
    :results="selectedResults"
    :entry-strategy-name="lastEntryStrategy"
    :exit-strategy-name="lastExitStrategy"
    :instrument-mode="positionSizingSettings.instrumentMode ?? 'STOCK'"
    :calculate-quantity="calculateQuantity"
    :option-contracts="optionContracts"
    :selected-capital="selectedCapital"
    :portfolio-utilization="portfolioUtilization"
    @success="onBatchTradesAdded"
    @remove-symbol="onRemoveSymbol"
  />

  <ScannerDeleteTradeModal
    :open="isDeleteTradeModalOpen"
    :trade="selectedTrade"
    @update:open="isDeleteTradeModalOpen = $event"
    @delete="deleteTrade"
  />

  <ScannerRollTradeModal
    v-model="isRollTradeModalOpen"
    :trade="selectedTrade"
    @success="onTradeRolled"
  />

  <ScannerTradeDetailsModal
    :open="isTradeDetailsModalOpen"
    :trade="selectedTrade"
    @update:open="isTradeDetailsModalOpen = $event"
  />

  <!-- Close Trade Modal -->
  <UModal
    :open="isCloseTradeModalOpen"
    title="Close Trade"
    @update:open="isCloseTradeModalOpen = $event"
  >
    <template #body>
      <div v-if="selectedTrade" class="space-y-4">
        <p class="text-sm">
          Close <span class="font-semibold">{{ selectedTrade.symbol }}</span> and record realized P&L.
        </p>
        <UFormField label="Exit Price ($)">
          <UInput
            v-model.number="closeTradePrice"
            type="number"
            :min="0"
            :step="0.01"
          />
        </UFormField>
        <UFormField label="Exit Date">
          <UInput
            v-model="closeTradeDate"
            type="date"
            :min="selectedTrade?.entryDate"
          />
        </UFormField>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="isCloseTradeModalOpen = false"
        />
        <UButton
          label="Close Trade"
          color="warning"
          icon="i-lucide-circle-x"
          :disabled="!closeTradeValid"
          @click="closeTrade"
        />
      </div>
    </template>
  </UModal>

  <!-- Reset Peak Confirmation Modal -->
  <UModal
    :open="isResetPeakModalOpen"
    title="Reset Peak Equity"
    @update:open="isResetPeakModalOpen = $event"
  >
    <template #body>
      <p class="text-sm">
        This will reset the peak equity to the current equity of
        <span class="font-semibold">{{ formatUsd(currentEquity, false) }}</span>,
        setting drawdown to 0%. This cannot be undone.
      </p>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="isResetPeakModalOpen = false"
        />
        <UButton
          label="Reset Peak"
          color="warning"
          icon="i-lucide-rotate-ccw"
          @click="resetPeakEquity(); isResetPeakModalOpen = false"
        />
      </div>
    </template>
  </UModal>

  <!-- Reset All Trades Confirmation Modal -->
  <UModal
    :open="isResetAllTradesModalOpen"
    title="Reset All Scanner Trades"
    @update:open="isResetAllTradesModalOpen = $event"
  >
    <template #body>
      <p class="text-sm">
        This will permanently delete <span class="font-semibold">all</span> scanner trades
        ({{ trades.length }} open, {{ closedTrades.length }} closed). This cannot be undone.
      </p>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="isResetAllTradesModalOpen = false"
        />
        <UButton
          label="Delete All Trades"
          color="error"
          icon="i-lucide-trash-2"
          @click="resetAllTrades"
        />
      </div>
    </template>
  </UModal>
</template>
