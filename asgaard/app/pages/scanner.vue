<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { ScanRequest, ScanResponse, ScanResult, ScannerTrade, ExitCheckResponse, ExitCheckResult, PositionSizingSettings, AddScannerTradeRequest, OptionContractResponse } from '~/types'

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
const selectedScanResult = ref<ScanResult | null>(null)
const selectedTrade = ref<ScannerTrade | null>(null)

// Last scan config (for pre-filling add trade)
const lastEntryStrategy = ref('')
const lastExitStrategy = ref('')

// Position sizing
const positionSizingSettings = ref<PositionSizingSettings>({
  enabled: true,
  portfolioValue: 100000,
  riskPercentage: 1.5,
  nAtr: 2.0,
  instrumentMode: 'STOCK',
  maxPositions: 15
})
const selectedSymbols = ref<Set<string>>(new Set())
const addingSelectedTrades = ref(false)

// Option contracts
const optionContracts = ref<Map<string, OptionContractResponse>>(new Map())
const optionFetchStatus = ref<'idle' | 'pending'>('idle')

const toast = useToast()

const isOptionsMode = computed(() => positionSizingSettings.value.instrumentMode === 'OPTION')

function calculateQuantity(atr: number, symbol?: string): number {
  if (atr <= 0) return 0
  const { portfolioValue, riskPercentage, nAtr } = positionSizingSettings.value
  if (portfolioValue <= 0) return 0
  const riskDollars = portfolioValue * (riskPercentage / 100)

  if (isOptionsMode.value && symbol) {
    const contract = optionContracts.value.get(symbol)
    const delta = contract?.delta ?? 0.80
    return Math.floor(riskDollars / (nAtr * atr * delta * 100))
  }
  return Math.floor(riskDollars / (nAtr * atr))
}

const existingCapitalDeployed = computed(() =>
  trades.value.reduce((sum, t) => {
    if (t.instrumentType === 'OPTION') {
      return sum + t.quantity * t.entryPrice * t.multiplier
    }
    return sum + t.quantity * t.entryPrice
  }, 0)
)

const maxPositions = computed(() => positionSizingSettings.value.maxPositions ?? 15)
const availableSlots = computed(() => Math.max(0, maxPositions.value - trades.value.length))

const preferredResults = computed(() => {
  if (!scanResponse.value) return []
  return scanResponse.value.results.slice(0, availableSlots.value)
})

const remainingResults = computed(() => {
  if (!scanResponse.value) return []
  return scanResponse.value.results.slice(availableSlots.value)
})

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
  const pv = positionSizingSettings.value.portfolioValue
  if (pv <= 0) return 0
  return ((existingCapitalDeployed.value + selectedCapital.value) / pv) * 100
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
  }
]

// Load trades and position sizing settings on mount
onMounted(async () => {
  await Promise.all([loadTrades(), loadPositionSizingSettings()])
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

async function runScan(config: ScanRequest) {
  isScanConfigModalOpen.value = false
  scanStatus.value = 'pending'
  lastEntryStrategy.value = config.entryStrategyName
  lastExitStrategy.value = config.exitStrategyName

  try {
    scanResponse.value = await $fetch<ScanResponse>('/udgaard/api/scanner/scan', {
      method: 'POST',
      body: config
    })
    scanStatus.value = 'success'
    activeTab.value = 'results'

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

    toast.add({
      title: 'Exit Check Complete',
      description: `${response.checksPerformed} trades checked, ${response.exitsTriggered} exits triggered`,
      icon: 'i-lucide-check-circle',
      color: response.exitsTriggered > 0 ? 'warning' : 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to check exits',
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
    cell: ({ row }) => h('button', {
      class: 'font-semibold hover:underline text-primary',
      onClick: () => openTradeDetails(row.original)
    }, row.original.symbol)
  },
  {
    id: 'type',
    header: 'Type',
    cell: ({ row }) => {
      const t = row.original
      if (t.instrumentType === 'OPTION') {
        return `${t.optionType} $${t.strikePrice?.toFixed(0)} ${t.expirationDate}`
      }
      return t.instrumentType
    }
  },
  {
    id: 'entryPrice',
    header: 'Entry',
    cell: ({ row }) => `$${row.original.entryPrice.toFixed(2)}`
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
    cell: ({ row }) => row.original.entryStrategyName
  },
  {
    id: 'exitAlert',
    header: 'Exit',
    cell: ({ row }) => {
      const result = exitResults.value.get(row.original.id)
      if (!result) return '-'
      if (result.exitTriggered) {
        return h(resolveComponent('UBadge'), { color: 'error', variant: 'solid', size: 'sm' }, () => result.exitReason ?? 'EXIT')
      }
      return h(resolveComponent('UBadge'), { color: 'success', variant: 'subtle', size: 'sm' }, () => `${result.unrealizedPnlPercent >= 0 ? '+' : ''}${result.unrealizedPnlPercent.toFixed(1)}%`)
    }
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => {
      const buttons = [
        h(resolveComponent('UButton'), {
          icon: 'i-lucide-trash-2',
          size: 'xs',
          variant: 'ghost',
          color: 'error',
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
</script>

<template>
  <UDashboardPanel id="scanner">
    <template #header>
      <UDashboardNavbar title="Scanner">
        <template #actions>
          <UButton
            label="Check Exits"
            icon="i-lucide-shield-alert"
            variant="soft"
            :loading="exitCheckStatus === 'pending'"
            :disabled="trades.length === 0"
            @click="checkExits"
          />
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
              <UButtonGroup size="sm" class="w-full">
                <UButton
                  label="Stock"
                  :variant="positionSizingSettings.instrumentMode === 'STOCK' ? 'solid' : 'outline'"
                  class="flex-1"
                  @click="positionSizingSettings.instrumentMode = 'STOCK'"
                />
                <UButton
                  label="Options"
                  :variant="positionSizingSettings.instrumentMode === 'OPTION' ? 'solid' : 'outline'"
                  class="flex-1"
                  @click="positionSizingSettings.instrumentMode = 'OPTION'"
                />
              </UButtonGroup>
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
            <UProgress
              :value="Math.min(portfolioUtilization, 100)"
              :color="portfolioUtilization > 100 ? 'error' : portfolioUtilization > 80 ? 'warning' : 'primary'"
              size="xs"
            />
          </div>
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
              <UTable :data="trades" :columns="tradeColumns">
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
</template>
