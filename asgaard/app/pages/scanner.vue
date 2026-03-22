<script setup lang="ts">
import { h } from 'vue'
import type { Row } from '@tanstack/vue-table'
import type { TableColumn } from '@nuxt/ui'
import type { ScanRequest, ScanResponse, ScanResult, ScannerTrade, ExitCheckResponse, ExitCheckResult, PositionSizingSettings, AddScannerTradeRequest, OptionContractResponse, DrawdownStatsResponse } from '~/types'

// State
const scanResponse = ref<ScanResponse | null>(null)
const trades = ref<ScannerTrade[]>([])
const exitResults = ref<Map<number, ExitCheckResult>>(new Map())
const scanStatus = ref<'idle' | 'pending' | 'success' | 'error'>('idle')
const exitCheckStatus = ref<'idle' | 'pending'>('idle')
const activeTab = ref('results')

// Modal states
const isScanConfigModalOpen = ref(false)
const isAddTradeModalOpen = ref(false)
const isDeleteTradeModalOpen = ref(false)
const isRollTradeModalOpen = ref(false)
const isTradeDetailsModalOpen = ref(false)
const isCloseTradeModalOpen = ref(false)
const isResetPeakModalOpen = ref(false)
const selectedScanResult = ref<ScanResult | null>(null)
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
const addingSelectedTrades = ref(false)

// Option contracts
const optionContracts = ref<Map<string, OptionContractResponse>>(new Map())
const optionFetchStatus = ref<'idle' | 'pending'>('idle')

const toast = useToast()
const NuxtLink = resolveComponent('NuxtLink')

const isOptionsMode = computed(() => positionSizingSettings.value.instrumentMode === 'OPTION')

// Drawdown computed properties
const totalUnrealizedPnlDollars = computed(() => {
  let total = 0
  for (const [, result] of exitResults.value) {
    total += result.unrealizedPnlDollars ?? 0
  }
  return total
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

const activeDrawdownThreshold = computed(() => {
  if (!positionSizingSettings.value.drawdownScalingEnabled) return null
  const thresholds = positionSizingSettings.value.drawdownThresholds ?? []
  const sorted = [...thresholds].sort((a, b) => b.drawdownPercent - a.drawdownPercent)
  return sorted.find(t => currentDrawdownPct.value >= t.drawdownPercent) ?? null
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
const availableSlots = computed(() => Math.max(0, maxPositions.value - trades.value.length))
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

const tabItems = [
  {
    label: 'Scan Results',
    icon: 'i-lucide-search',
    value: 'results',
    slot: 'results'
  },
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
  }
]

const lastExitCheckAt = ref<number>(0)
const EXIT_CHECK_COOLDOWN_MS = 5 * 60 * 1000

onMounted(async () => {
  await Promise.all([loadTrades(), loadPositionSizingSettings(), loadDrawdownStats(), loadClosedTrades()])
  if (trades.value.length > 0) {
    activeTab.value = 'trades'
    const now = Date.now()
    if (now - lastExitCheckAt.value >= EXIT_CHECK_COOLDOWN_MS) {
      lastExitCheckAt.value = now
      checkExits()
    }
  }
})

async function loadPositionSizingSettings() {
  try {
    const data = await $fetch<PositionSizingSettings>('/udgaard/api/settings/position-sizing')
    positionSizingSettings.value = data
  } catch (error) {
    console.error('Failed to load position sizing settings:', error)
  }
}

let saveTimeout: ReturnType<typeof setTimeout> | null = null
function debouncedSavePositionSizing() {
  if (saveTimeout) clearTimeout(saveTimeout)
  saveTimeout = setTimeout(async () => {
    try {
      await $fetch('/udgaard/api/settings/position-sizing', {
        method: 'POST',
        body: positionSizingSettings.value
      })
    } catch (error) {
      console.error('Failed to save position sizing settings:', error)
    }
  }, 500)
}

watch(positionSizingSettings, () => {
  debouncedSavePositionSizing()
}, { deep: true })

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

function openAddTrade(result: ScanResult) {
  selectedScanResult.value = result
  isAddTradeModalOpen.value = true
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
    await Promise.all([loadTrades(), loadClosedTrades(), loadDrawdownStats()])
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

async function onTradeAdded() {
  await loadTrades()
  activeTab.value = 'trades'
}

async function onTradeRolled() {
  await loadTrades()
}

async function addSelectedTrades() {
  if (selectedResults.value.length === 0 || !scanResponse.value) return
  addingSelectedTrades.value = true
  let added = 0
  try {
    for (const result of selectedResults.value) {
      const contract = optionContracts.value.get(result.symbol)
      const qty = calculateQuantity(result.atr, result.symbol) || (isOptionsMode.value ? 1 : 100)
      const body: AddScannerTradeRequest = {
        symbol: result.symbol,
        sectorSymbol: result.sectorSymbol ?? undefined,
        instrumentType: isOptionsMode.value ? 'OPTION' : 'STOCK',
        entryPrice: isOptionsMode.value && contract ? contract.price : result.closePrice,
        entryDate: result.date,
        quantity: qty,
        entryStrategyName: lastEntryStrategy.value,
        exitStrategyName: lastExitStrategy.value
      }
      if (isOptionsMode.value && contract) {
        body.optionType = 'CALL'
        body.strikePrice = contract.strike
        body.expirationDate = contract.expiration
        body.multiplier = 100
      }
      await $fetch('/udgaard/api/scanner/trades', { method: 'POST', body })
      added++
    }
    selectedSymbols.value = new Set()
    await loadTrades()
    activeTab.value = 'trades'
    toast.add({
      title: 'Trades Added',
      description: `Added ${added} trades to scanner`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (error: any) {
    if (added > 0) {
      await loadTrades()
    }
    toast.add({
      title: 'Error',
      description: error.data?.message || `Failed after adding ${added} trades`,
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    addingSelectedTrades.value = false
  }
}

function daysHeld(entryDate: string): number {
  const entry = new Date(entryDate)
  const now = new Date()
  return Math.floor((now.getTime() - entry.getTime()) / (1000 * 60 * 60 * 24))
}

// Active trades table columns
const tradeColumns: TableColumn<ScannerTrade>[] = [
  {
    id: 'symbol',
    header: 'Symbol',
    cell: ({ row }) => h('div', { class: 'flex items-center gap-1.5' }, [
      h(NuxtLink, {
        to: `/stock-data/${row.original.symbol.toLowerCase()}`,
        target: '_blank',
        class: 'font-semibold text-primary hover:underline'
      }, () => row.original.symbol),
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
    cell: ({ row }) => `$${row.original.entryPrice.toFixed(2)}`
  },
  {
    id: 'currentPrice',
    header: 'Current Price',
    cell: ({ row }) => {
      const result = exitResults.value.get(row.original.id)
      if (!result) return '-'
      return `$${result.currentPrice.toFixed(2)}`
    }
  },
  {
    id: 'optionPrice',
    header: 'Premium',
    cell: ({ row }) => {
      const t = row.original
      if (t.instrumentType !== 'OPTION') return '-'
      return t.optionPrice ? `$${t.optionPrice.toFixed(2)}` : '-'
    }
  },
  {
    id: 'optionDetails',
    header: 'Option',
    cell: ({ row }) => {
      const t = row.original
      if (t.instrumentType !== 'OPTION') return '-'
      const delta = t.delta ? ` Δ${t.delta.toFixed(2)}` : ''
      return `${t.optionType} $${t.strikePrice?.toFixed(0)} ${t.expirationDate}${delta}`
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
    cell: ({ row }) => h(NuxtLink, {
      to: `/stock-data/${row.original.symbol.toLowerCase()}`,
      target: '_blank',
      class: 'font-semibold text-primary hover:underline'
    }, () => row.original.symbol)
  },
  {
    id: 'instrumentType',
    header: 'Type',
    cell: ({ row }) => row.original.instrumentType
  },
  {
    id: 'entryPrice',
    header: 'Entry',
    cell: ({ row }) => `$${row.original.entryPrice.toFixed(2)}`
  },
  {
    id: 'exitPrice',
    header: 'Exit',
    cell: ({ row }) => row.original.exitPrice ? `$${row.original.exitPrice.toFixed(2)}` : '-'
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
      const sign = pnl >= 0 ? '+' : ''
      return h('span', { class: color }, `${sign}$${pnl.toFixed(2)}`)
    }
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
  <UDashboardPanel id="scanner">
    <template #header>
      <UDashboardNavbar title="Scanner">
        <template #right>
          <UButton
            label="New Scan"
            icon="i-lucide-scan-search"
            @click="isScanConfigModalOpen = true"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-4 space-y-4">
        <!-- Stats Cards -->
        <ScannerStatsCards
          :trades="trades"
          :exit-results="exitResults"
          :capital-deployed="positionSizingSettings.enabled ? existingCapitalDeployed : undefined"
          :portfolio-value="positionSizingSettings.enabled ? positionSizingSettings.portfolioValue : undefined"
          :drawdown-pct="currentDrawdownPct"
          :effective-risk="effectiveRiskPercentage"
          :base-risk="positionSizingSettings.riskPercentage"
          :drawdown-scaling-active="positionSizingSettings.drawdownScalingEnabled"
          :total-risk="positionSizingSettings.enabled ? totalPortfolioRisk : undefined"
          :risk-pct="positionSizingSettings.enabled ? portfolioRiskPct : undefined"
        />

        <!-- Position Sizing Config -->
        <div v-if="positionSizingSettings.enabled" class="p-4 bg-muted/50 rounded-lg border border-default space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-calculator" class="w-4 h-4 text-muted" />
              <span class="text-sm font-semibold">Position Sizing</span>
            </div>
            <USwitch v-model="positionSizingSettings.enabled" size="sm" />
          </div>
          <div class="grid grid-cols-4 gap-4">
            <UFormField label="Instrument">
              <div class="flex w-full">
                <UButton
                  label="Stock"
                  size="sm"
                  :variant="positionSizingSettings.instrumentMode === 'STOCK' ? 'solid' : 'outline'"
                  class="flex-1 rounded-r-none"
                  @click="positionSizingSettings.instrumentMode = 'STOCK'"
                />
                <UButton
                  label="Options"
                  size="sm"
                  :variant="positionSizingSettings.instrumentMode === 'OPTION' ? 'solid' : 'outline'"
                  class="flex-1 rounded-l-none"
                  @click="positionSizingSettings.instrumentMode = 'OPTION'"
                />
              </div>
            </UFormField>
            <UFormField label="Portfolio Value ($)">
              <UInput
                v-model.number="positionSizingSettings.portfolioValue"
                type="number"
                :min="1000"
                :step="1000"
              />
            </UFormField>
            <UFormField label="Risk Per Trade (%)">
              <UInput
                v-model.number="positionSizingSettings.riskPercentage"
                type="number"
                :min="0.1"
                :max="10"
                :step="0.1"
              />
            </UFormField>
            <UFormField>
              <template #label>
                <span class="flex items-center gap-1">
                  ATR Multiplier
                  <UTooltip :text="isOptionsMode ? 'Contracts = Risk$ / (nATR × ATR × delta × 100)' : 'Shares = Risk$ / (nATR × ATR). Lower = more shares, tighter stop.'">
                    <UIcon name="i-lucide-info" class="size-3.5 text-muted" />
                  </UTooltip>
                </span>
              </template>
              <UInput
                v-model.number="positionSizingSettings.nAtr"
                type="number"
                :min="0.5"
                :max="10"
                :step="0.5"
              />
            </UFormField>
          </div>
          <!-- Portfolio Utilization -->
          <div class="space-y-1">
            <div class="flex items-center justify-between text-xs text-muted">
              <span>
                Existing: ${{ existingCapitalDeployed.toLocaleString('en-US', { maximumFractionDigits: 0 }) }}
                <template v-if="selectedCapital > 0">
                  + Selected: ${{ selectedCapital.toLocaleString('en-US', { maximumFractionDigits: 0 }) }}
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
          <div class="flex items-center justify-between pt-2 border-t border-default">
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-shield-alert" class="w-4 h-4 text-muted" />
              <span class="text-sm font-semibold">Drawdown Scaling</span>
              <UTooltip text="Automatically reduces risk per trade when portfolio is in drawdown">
                <UIcon name="i-lucide-info" class="size-3.5 text-muted" />
              </UTooltip>
            </div>
            <USwitch v-model="positionSizingSettings.drawdownScalingEnabled" size="sm" />
          </div>

          <template v-if="positionSizingSettings.drawdownScalingEnabled">
            <!-- Drawdown Alert -->
            <UAlert
              v-if="activeDrawdownThreshold"
              :color="currentDrawdownPct >= 10 ? 'error' : 'warning'"
              :title="`Drawdown: ${currentDrawdownPct.toFixed(1)}%`"
              :description="`Risk scaled to ${effectiveRiskPercentage.toFixed(2)}% (${activeDrawdownThreshold.riskMultiplier}x of ${positionSizingSettings.riskPercentage}%)`"
              icon="i-lucide-trending-down"
            />

            <!-- Drawdown Info Row -->
            <div class="grid grid-cols-5 gap-4 text-sm items-center">
              <div>
                <span class="text-muted">Equity:</span>
                <span class="ml-1 font-medium">${{ currentEquity.toLocaleString('en-US', { maximumFractionDigits: 0 }) }}</span>
              </div>
              <div>
                <span class="text-muted">Peak:</span>
                <span class="ml-1 font-medium">${{ (positionSizingSettings.peakEquity ?? positionSizingSettings.portfolioValue).toLocaleString('en-US', { maximumFractionDigits: 0 }) }}</span>
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
          </template>
        </div>
        <div v-else class="flex items-center justify-between p-3 bg-muted/50 rounded-lg border border-default">
          <div class="flex items-center gap-2">
            <UIcon name="i-lucide-calculator" class="w-4 h-4 text-muted" />
            <span class="text-sm text-muted">Position Sizing</span>
          </div>
          <USwitch v-model="positionSizingSettings.enabled" size="sm" />
        </div>

        <!-- Tabs -->
        <UTabs v-model="activeTab" :items="tabItems">
          <!-- Scan Results Tab -->
          <template #results>
            <div class="pt-4">
              <div v-if="scanStatus === 'idle'" class="text-center py-12">
                <UIcon name="i-lucide-scan-search" class="w-12 h-12 text-muted mb-3" />
                <p class="text-muted">
                  Run a scan to find entry signals
                </p>
                <UButton
                  label="Configure Scan"
                  variant="soft"
                  class="mt-3"
                  @click="isScanConfigModalOpen = true"
                />
              </div>

              <div v-else-if="scanStatus === 'pending'" class="text-center py-12">
                <UIcon name="i-lucide-loader-2" class="w-8 h-8 text-primary animate-spin mb-3" />
                <p class="text-muted">
                  Scanning stocks...
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
                  <UButton
                    v-if="positionSizingSettings.enabled && selectedSymbols.size > 0"
                    :label="`Add ${selectedSymbols.size} Trade${selectedSymbols.size > 1 ? 's' : ''}`"
                    icon="i-lucide-plus"
                    size="sm"
                    :loading="addingSelectedTrades"
                    @click="addSelectedTrades"
                  />
                </div>

                <div v-if="scanResponse.results.length === 0 && scanResponse.nearMissCandidates.length === 0" class="text-center py-12">
                  <UIcon name="i-lucide-search-x" class="w-12 h-12 text-muted mb-3" />
                  <p class="text-muted">
                    No matches found
                  </p>
                  <UButton
                    label="New Scan"
                    icon="i-lucide-scan-search"
                    variant="soft"
                    class="mt-3"
                    @click="isScanConfigModalOpen = true"
                  />
                </div>

                <div v-if="preferredResults.length > 0" class="space-y-2">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-semibold">Preferred Trades</span>
                    <UBadge variant="subtle" size="sm">
                      {{ preferredResults.length }} of {{ availableSlots }} slots
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
                    @add-trade="openAddTrade"
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
                    @add-trade="openAddTrade"
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
              <div class="flex justify-end mb-3">
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
                :data="trades"
                :columns="tradeColumns"
                :ui="tradesTableUi"
              >
                <template #empty-state>
                  <div class="flex flex-col items-center justify-center py-8">
                    <UIcon name="i-lucide-inbox" class="w-10 h-10 text-muted mb-2" />
                    <p class="text-muted text-sm">
                      No active scanner trades. Run a scan and add trades to track.
                    </p>
                  </div>
                </template>
              </UTable>
            </div>
          </template>

          <!-- Closed Trades Tab -->
          <template #closed>
            <div class="pt-4">
              <div v-if="drawdownStats && drawdownStats.closedTradeCount > 0" class="flex items-center gap-4 mb-3 text-sm text-muted">
                <span>
                  Total P&L:
                  <span
                    class="font-semibold"
                    :class="drawdownStats.totalRealizedPnl >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                  >
                    {{ drawdownStats.totalRealizedPnl >= 0 ? '+' : '' }}${{ drawdownStats.totalRealizedPnl.toFixed(2) }}
                  </span>
                </span>
                <span>
                  Win Rate:
                  <span class="font-semibold">{{ (drawdownStats.winRate * 100).toFixed(0) }}%</span>
                </span>
                <span>
                  Trades: <span class="font-semibold">{{ drawdownStats.closedTradeCount }}</span>
                </span>
              </div>
              <UTable
                :data="closedTrades"
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

  <ScannerAddTradeModal
    v-model="isAddTradeModalOpen"
    :scan-result="selectedScanResult"
    :entry-strategy-name="lastEntryStrategy"
    :exit-strategy-name="lastExitStrategy"
    :calculated-quantity="positionSizingSettings.enabled && selectedScanResult ? calculateQuantity(selectedScanResult.atr, selectedScanResult.symbol) : undefined"
    :option-contract="selectedScanResult ? optionContracts.get(selectedScanResult.symbol) : undefined"
    @success="onTradeAdded"
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
        <span class="font-semibold">${{ currentEquity.toLocaleString('en-US', { maximumFractionDigits: 0 }) }}</span>,
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
</template>
