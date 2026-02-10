<script setup lang="ts">
import type { BacktestRequest, StrategyConfig, AvailableConditions } from '~/types'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'run-backtest': [config: BacktestRequest]
}>()

// Form ref
const form = ref<{ submit: () => void } | null>(null)

// Form state
const state = reactive<{
  stockSelection: 'all' | 'specific'
  specificStocks: string[]
  entryStrategy: StrategyConfig
  exitStrategy: StrategyConfig
  startDate: string
  endDate: string
  positionLimitEnabled: boolean
  maxPositions: number
  ranker: string
  cooldownDays: number
  refresh: boolean
  useUnderlyingAssets: boolean
  detectedMappings: Array<{ symbol: string, underlying: string, canRemove: boolean }>
  customOverrides: Array<{ symbol: string, underlying: string }>
}>({
  stockSelection: 'all',
  specificStocks: [],
  entryStrategy: { type: 'predefined', name: 'PlanAlpha' },
  exitStrategy: { type: 'predefined', name: 'PlanMoney' },
  startDate: '',
  endDate: '',
  positionLimitEnabled: true,
  maxPositions: 10,
  ranker: 'Adaptive',
  cooldownDays: 0,
  refresh: false,
  useUnderlyingAssets: true,
  detectedMappings: [],
  customOverrides: []
})

// Fetch available strategies from backend
const { data: availableStrategies } = useFetch<{
  entryStrategies: string[]
  exitStrategies: string[]
}>('/udgaard/api/backtest/strategies')

// Fetch available rankers from backend
const { data: availableRankers } = useFetch<string[]>('/udgaard/api/backtest/rankers')

// Fetch available conditions for custom strategies
const { data: availableConditions } = useFetch<AvailableConditions>('/udgaard/api/backtest/conditions')

// Ranker options computed from fetched data
const rankerOptions = computed(() => {
  if (!availableRankers.value) return []
  return availableRankers.value.map(r => ({
    label: r,
    value: r
  }))
})

// Asset mapper - matches backend AssetMapper.kt
const ASSET_MAPPER: Record<string, string> = {
  // Nasdaq QQQ
  TQQQ: 'QQQ',
  SQQQ: 'QQQ',
  QLD: 'QQQ',
  QID: 'QQQ',
  // S&P 500 SPY
  UPRO: 'SPY',
  SPXL: 'SPY',
  SPXU: 'SPY',
  SSO: 'SPY',
  SDS: 'SPY',
  // Semiconductors
  SOXL: 'SOXX',
  SOXS: 'SOXX',
  // Russell 2000
  TNA: 'IWM',
  TZA: 'IWM',
  UWM: 'IWM',
  TWM: 'IWM',
  // Dow Jones
  UDOW: 'DIA',
  SDOW: 'DIA',
  // Financials
  FAS: 'XLF',
  FAZ: 'XLF',
  // Energy
  ERX: 'XLE',
  ERY: 'XLE',
  // Technology
  TECL: 'XLK',
  TECS: 'XLK',
  // Biotech
  LABU: 'XBI',
  LABD: 'XBI',
  // Gold
  NUGT: 'GDX',
  DUST: 'GDX',
  // Oil
  GUSH: 'XOP',
  DRIP: 'XOP',
  // Emerging Markets
  EDC: 'EEM',
  EDZ: 'EEM'
}

// Auto-populate detected mappings based on selected stocks
watch(() => state.specificStocks, (newStocks) => {
  if (!state.useUnderlyingAssets) return

  const detected: Array<{ symbol: string, underlying: string, canRemove: boolean }> = []

  newStocks.forEach((symbol) => {
    const underlying = ASSET_MAPPER[symbol.toUpperCase()]
    if (underlying && !state.detectedMappings.find(m => m.symbol === symbol)) {
      detected.push({ symbol, underlying, canRemove: true })
    }
  })

  // Keep existing detected mappings for stocks still selected
  const existingStillSelected = state.detectedMappings.filter(m =>
    newStocks.includes(m.symbol)
  )

  state.detectedMappings = [...existingStillSelected, ...detected]
})

function addCustomOverride() {
  state.customOverrides.push({ symbol: '', underlying: '' })
}

function removeCustomOverride(index: number) {
  state.customOverrides.splice(index, 1)
}

function removeDetectedMapping(symbol: string) {
  const index = state.detectedMappings.findIndex(m => m.symbol === symbol)
  if (index !== -1) {
    state.detectedMappings.splice(index, 1)
  }
}

function onSubmit() {
  // Build customUnderlyingMap from detected + custom overrides
  const customUnderlyingMap: Record<string, string> = {}

  if (state.useUnderlyingAssets) {
    // Add detected mappings
    state.detectedMappings.forEach((m) => {
      if (m.symbol && m.underlying) {
        customUnderlyingMap[m.symbol.toUpperCase()] = m.underlying.toUpperCase()
      }
    })

    // Add custom overrides (takes priority)
    state.customOverrides.forEach((o) => {
      if (o.symbol && o.underlying) {
        customUnderlyingMap[o.symbol.toUpperCase()] = o.underlying.toUpperCase()
      }
    })
  }

  const config: BacktestRequest = {
    entryStrategy: state.entryStrategy,
    exitStrategy: state.exitStrategy,
    stockSymbols: state.stockSelection === 'all' ? undefined : state.specificStocks,
    startDate: state.startDate || undefined,
    endDate: state.endDate || undefined,
    maxPositions: state.positionLimitEnabled ? state.maxPositions : undefined,
    ranker: state.positionLimitEnabled ? state.ranker : undefined,
    cooldownDays: state.cooldownDays > 0 ? state.cooldownDays : undefined,
    refresh: state.refresh,
    useUnderlyingAssets: state.useUnderlyingAssets,
    customUnderlyingMap: Object.keys(customUnderlyingMap).length > 0 ? customUnderlyingMap : undefined
  }

  emit('run-backtest', config)
  emit('update:open', false)
}

function cancel() {
  emit('update:open', false)
}
</script>

<template>
  <UModal
    :open="open"
    title="Backtest Configuration"
    fullscreen
    :ui="{ content: 'overflow-y-auto' }"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <UForm ref="form" :state="state" @submit="onSubmit">
        <div class="space-y-6">
          <!-- Stock Selection and Date Range -->
          <UCard>
            <template #header>
              <h3 class="text-base font-semibold">
                Stock Selection & Date Range
              </h3>
            </template>
            <div class="space-y-4">
              <div class="grid grid-cols-3 gap-4">
                <UFormField label="Stock Selection" name="stockSelection">
                  <URadioGroup
                    v-model="state.stockSelection"
                    :items="[
                      { value: 'all', label: 'All Stocks' },
                      { value: 'specific', label: 'Specific Stocks' }
                    ]"
                  />
                </UFormField>

                <UFormField name="startDate">
                  <template #label>
                    <span>Start Date <span class="text-muted">Optional</span></span>
                  </template>
                  <UInput
                    v-model="state.startDate"
                    type="date"
                    placeholder="YYYY-MM-DD"
                  />
                </UFormField>

                <UFormField name="endDate">
                  <template #label>
                    <span>End Date <span class="text-muted">Optional</span></span>
                  </template>
                  <UInput
                    v-model="state.endDate"
                    type="date"
                    placeholder="YYYY-MM-DD"
                  />
                </UFormField>
              </div>

              <SymbolSearch
                v-if="state.stockSelection === 'specific'"
                v-model="state.specificStocks"
                placeholder="Type to search stock symbols..."
                multiple
              />
            </div>
          </UCard>

          <!-- Strategies Section -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <!-- Entry Strategy -->
            <UCard :ui="{ body: 'space-y-3' }">
              <template #header>
                <h3 class="text-base font-semibold">
                  Entry Strategy
                </h3>
              </template>
              <StrategySelector
                v-if="availableStrategies && availableConditions"
                v-model="state.entryStrategy"
                :available-predefined="availableStrategies.entryStrategies"
                :available-conditions="availableConditions.entryConditions"
                strategy-type="entry"
              />
              <div v-else class="flex items-center justify-center py-8">
                <UIcon name="i-lucide-loader-2" class="w-6 h-6 animate-spin text-muted" />
              </div>
            </UCard>

            <!-- Exit Strategy -->
            <UCard :ui="{ body: 'space-y-3' }">
              <template #header>
                <h3 class="text-base font-semibold">
                  Exit Strategy
                </h3>
              </template>
              <StrategySelector
                v-if="availableStrategies && availableConditions"
                v-model="state.exitStrategy"
                :available-predefined="availableStrategies.exitStrategies"
                :available-conditions="availableConditions.exitConditions"
                strategy-type="exit"
              />
              <div v-else class="flex items-center justify-center py-8">
                <UIcon name="i-lucide-loader-2" class="w-6 h-6 animate-spin text-muted" />
              </div>
            </UCard>
          </div>

          <!-- Position Limiting and Cooldown Section -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <!-- Position Limiting Section -->
            <UCard>
              <template #header>
                <div class="flex items-center justify-between">
                  <h3 class="text-base font-semibold">
                    Position Limiting
                  </h3>
                  <USwitch v-model="state.positionLimitEnabled" />
                </div>
              </template>
              <div v-if="state.positionLimitEnabled" class="grid grid-cols-2 gap-4">
                <UFormField label="Max Positions" name="maxPositions" help="Maximum positions per day">
                  <UInput
                    v-model.number="state.maxPositions"
                    type="number"
                    min="1"
                    max="100"
                    placeholder="10"
                  />
                </UFormField>

                <UFormField label="Stock Ranker" name="ranker" help="Method to rank stocks on same entry date">
                  <USelect
                    v-model="state.ranker"
                    :items="rankerOptions"
                    value-key="value"
                    placeholder="Select ranker"
                  />
                </UFormField>
              </div>
              <div v-else class="text-sm text-muted">
                Unlimited positions mode: All stocks matching the entry strategy will be entered
              </div>
            </UCard>

            <!-- Cooldown Period Section -->
            <UCard>
              <template #header>
                <h3 class="text-base font-semibold">
                  Cooldown Period
                </h3>
              </template>
              <div class="flex items-center gap-4">
                <label class="text-sm font-medium whitespace-nowrap">
                  Cooldown Days:
                </label>
                <UInput
                  v-model.number="state.cooldownDays"
                  type="number"
                  min="0"
                  max="365"
                  placeholder="0"
                  class="w-32"
                />
              </div>
              <div v-if="state.cooldownDays > 0" class="text-sm text-muted mt-4">
                After <strong>any exit</strong> (win or loss), all new entries are blocked for {{ state.cooldownDays }} trading {{ state.cooldownDays === 1 ? 'day' : 'days' }}.
              </div>
              <div v-else class="text-sm text-muted mt-4">
                No cooldown: New positions can be entered immediately after exits if entry conditions are met
              </div>
            </UCard>
          </div>

          <!-- Underlying Assets Configuration -->
          <UCard>
            <template #header>
              <div class="flex items-center justify-between">
                <div>
                  <h3 class="text-base font-semibold">
                    Underlying Asset Signals
                  </h3>
                  <p class="text-xs text-muted mt-1">
                    Use underlying assets for strategy evaluation
                  </p>
                </div>
                <USwitch v-model="state.useUnderlyingAssets" />
              </div>
            </template>

            <div v-if="state.useUnderlyingAssets" class="space-y-4">
              <p class="text-sm text-muted">
                Leveraged ETFs will use their underlying assets for entry/exit signals, while P&L is calculated from the actual traded symbol.
              </p>

              <!-- Auto-detected Mappings -->
              <div v-if="state.detectedMappings.length > 0" class="space-y-2">
                <div class="flex items-center justify-between">
                  <h4 class="text-sm font-medium">
                    Auto-Detected Mappings
                  </h4>
                  <span class="text-xs text-muted">{{ state.detectedMappings.length }} detected</span>
                </div>
                <div class="space-y-2">
                  <div
                    v-for="mapping in state.detectedMappings"
                    :key="mapping.symbol"
                    class="flex items-center gap-2 p-2 rounded-lg bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800"
                  >
                    <span class="text-sm font-medium">{{ mapping.symbol }}</span>
                    <UIcon name="i-lucide-arrow-right" class="w-3 h-3 text-muted" />
                    <span class="text-sm font-medium text-blue-600 dark:text-blue-400">{{ mapping.underlying }}</span>
                    <span class="text-xs text-muted flex-1">(signals)</span>
                    <UButton
                      icon="i-lucide-x"
                      size="xs"
                      variant="ghost"
                      color="neutral"
                      @click="removeDetectedMapping(mapping.symbol)"
                    />
                  </div>
                </div>
              </div>

              <!-- Custom Overrides -->
              <div class="space-y-2">
                <div class="flex items-center justify-between">
                  <h4 class="text-sm font-medium">
                    Custom Overrides
                  </h4>
                  <UButton
                    icon="i-lucide-plus"
                    label="Add Override"
                    size="xs"
                    variant="outline"
                    @click="addCustomOverride"
                  />
                </div>
                <div v-if="state.customOverrides.length > 0" class="space-y-2">
                  <div
                    v-for="(override, idx) in state.customOverrides"
                    :key="idx"
                    class="flex items-center gap-2"
                  >
                    <UInput
                      v-model="override.symbol"
                      placeholder="Symbol (e.g., AAPL)"
                      size="sm"
                      class="flex-1"
                    />
                    <UIcon name="i-lucide-arrow-right" class="w-3 h-3 text-muted" />
                    <UInput
                      v-model="override.underlying"
                      placeholder="Underlying (e.g., SPY)"
                      size="sm"
                      class="flex-1"
                    />
                    <UButton
                      icon="i-lucide-x"
                      size="xs"
                      variant="ghost"
                      color="neutral"
                      @click="removeCustomOverride(idx)"
                    />
                  </div>
                </div>
                <p v-else class="text-xs text-muted italic">
                  No custom overrides. Click "Add Override" to create one.
                </p>
              </div>
            </div>

            <div v-else class="text-sm text-muted">
              Disabled: Strategies will be evaluated on the traded symbols directly.
            </div>
          </UCard>

          <!-- Refresh Data -->
          <UFormField name="refresh">
            <UCheckbox v-model="state.refresh" label="Refresh data from source" />
          </UFormField>

          <!-- Info Alert -->
          <UAlert
            v-if="state.positionLimitEnabled"
            color="primary"
            variant="subtle"
            title="Position-Limited Backtest"
            description="When multiple stocks trigger on the same day, only the top-ranked stocks (by selected ranker) will be taken."
          />
          <UAlert
            v-else
            color="warning"
            variant="subtle"
            title="Unlimited Positions Mode"
            description="All stocks matching the entry strategy will be entered. This may result in unrealistic backtest results with many concurrent positions."
          />
        </div>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="outline"
          label="Cancel"
          @click="cancel"
        />
        <UButton
          label="Run Backtest"
          :disabled="state.stockSelection === 'specific' && state.specificStocks.length === 0"
          @click="form?.submit()"
        />
      </div>
    </template>
  </UModal>
</template>
