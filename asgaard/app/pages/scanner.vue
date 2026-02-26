<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { ScanRequest, ScanResponse, ScanResult, ScannerTrade, ExitCheckResponse, ExitCheckResult } from '~/types'

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

const toast = useToast()

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

// Load trades on mount
onMounted(async () => {
  await loadTrades()
})

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
        />

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
                    ({{ (scanResponse.executionTimeMs / 1000).toFixed(1) }}s)
                  </div>
                </div>
                <ScannerScanResultsTable
                  :results="scanResponse.results"
                  @add-trade="openAddTrade"
                />
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
    :open="isScanConfigModalOpen"
    @update:open="isScanConfigModalOpen = $event"
    @run-scan="runScan"
  />

  <ScannerAddTradeModal
    v-model="isAddTradeModalOpen"
    :scan-result="selectedScanResult"
    :entry-strategy-name="lastEntryStrategy"
    :exit-strategy-name="lastExitStrategy"
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
