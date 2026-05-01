<script setup lang="ts">
import { z } from 'zod'
import type { BacktestRequest, StrategyConfig, AvailableConditions, PositionSizingConfig, RankerMetadata, SizerConfig } from '~/types'
import { AssetTypeOptions, SectorSymbol, SectorSymbolDescriptions } from '~/types/enums'

const SectorOptions = Object.values(SectorSymbol).map(s => ({
  label: `${s} (${SectorSymbolDescriptions[s]})`,
  value: s as string
}))

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'run-backtest': [config: BacktestRequest]
}>()

// Form ref
const form = ref<{ submit: () => void } | null>(null)

// Sidebar navigation
type SectionId = 'universe' | 'strategies' | 'position-limits' | 'trade-timing' | 'position-sizing' | 'advanced'
const currentSection = ref<SectionId>('universe')

// Form state
const state = reactive<{
  stockSelection: 'all' | 'specific'
  specificStocks: string[]
  assetTypes: string[]
  excludeSectors: string[]
  entryStrategy: StrategyConfig
  exitStrategy: StrategyConfig
  startDate: string
  endDate: string
  positionLimitEnabled: boolean
  maxPositions: number
  ranker: string
  cooldownDays: number
  entryDelayDays: number
  refresh: boolean
  useUnderlyingAssets: boolean
  detectedMappings: Array<{ symbol: string, underlying: string, canRemove: boolean }>
  customOverrides: Array<{ symbol: string, underlying: string }>
  positionSizingEnabled: boolean
  positionSizingStartingCapital: number
  positionSizingLeverageRatio: number | null
  positionSizingSizer: SizerConfig
}>({
  stockSelection: 'all',
  specificStocks: [],
  assetTypes: [],
  excludeSectors: [],
  entryStrategy: { type: 'predefined', name: 'PlanAlpha' },
  exitStrategy: { type: 'predefined', name: 'PlanMoney' },
  startDate: '',
  endDate: '',
  positionLimitEnabled: true,
  maxPositions: 10,
  ranker: 'strategy-default',
  cooldownDays: 0,
  entryDelayDays: 0,
  refresh: false,
  useUnderlyingAssets: true,
  detectedMappings: [],
  customOverrides: [],
  positionSizingEnabled: false,
  positionSizingStartingCapital: 100000,
  positionSizingLeverageRatio: null,
  positionSizingSizer: { type: 'atrRisk', riskPercentage: 1.25, nAtr: 2.0 }
})

const SIZER_DEFAULTS: Record<SizerConfig['type'], SizerConfig> = {
  atrRisk: { type: 'atrRisk', riskPercentage: 1.25, nAtr: 2.0 },
  percentEquity: { type: 'percentEquity', percent: 12.5 },
  kelly: { type: 'kelly', winRate: 0.52, winLossRatio: 1.5, fractionMultiplier: 0.25 },
  volTarget: { type: 'volTarget', targetVolPct: 0.5, kAtr: 1.0 }
}

function changeSizerType(type: SizerConfig['type']) {
  // Spread-copy so subsequent v-model edits don't mutate SIZER_DEFAULTS.
  state.positionSizingSizer = { ...SIZER_DEFAULTS[type] }
}

// Validation schema
const schema = z.object({
  stockSelection: z.enum(['all', 'specific']),
  specificStocks: z.array(z.string()),
  entryStrategy: z.object({
    type: z.enum(['predefined', 'custom']),
    name: z.string().optional(),
    conditions: z.array(z.any()).optional()
  }).passthrough(),
  exitStrategy: z.object({
    type: z.enum(['predefined', 'custom']),
    name: z.string().optional(),
    conditions: z.array(z.any()).optional()
  }).passthrough(),
  positionLimitEnabled: z.boolean(),
  maxPositions: z.number().optional(),
  positionSizingEnabled: z.boolean(),
  positionSizingStartingCapital: z.number().optional(),
  positionSizingLeverageRatio: z.number().nullable().optional(),
  positionSizingSizer: z.object({ type: z.string() }).passthrough()
}).passthrough().superRefine((data, ctx) => {
  // Specific stocks required when in specific mode
  if (data.stockSelection === 'specific' && data.specificStocks.length === 0) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Select at least one stock',
      path: ['specificStocks']
    })
  }

  // Entry strategy validation
  if (data.entryStrategy.type === 'predefined' && !data.entryStrategy.name) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Select an entry strategy',
      path: ['entryStrategy']
    })
  }
  if (data.entryStrategy.type === 'custom' && (!data.entryStrategy.conditions || data.entryStrategy.conditions.length === 0)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Add at least one entry condition',
      path: ['entryStrategy']
    })
  }

  // Exit strategy validation
  if (data.exitStrategy.type === 'predefined' && !data.exitStrategy.name) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Select an exit strategy',
      path: ['exitStrategy']
    })
  }
  if (data.exitStrategy.type === 'custom' && (!data.exitStrategy.conditions || data.exitStrategy.conditions.length === 0)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Add at least one exit condition',
      path: ['exitStrategy']
    })
  }

  // Position limit validation
  if (data.positionLimitEnabled) {
    if (!data.maxPositions || data.maxPositions < 1) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must be at least 1',
        path: ['maxPositions']
      })
    }
  }

  // Position sizing validation
  if (data.positionSizingEnabled) {
    const cap = data.positionSizingStartingCapital
    if (!cap || cap < 1000 || cap > 1_000_000_000) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must be between $1,000 and $1B',
        path: ['positionSizingStartingCapital']
      })
    }
    if (data.positionSizingLeverageRatio != null && (data.positionSizingLeverageRatio < 0.1 || data.positionSizingLeverageRatio > 20)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Must be between 0.1 and 20',
        path: ['positionSizingLeverageRatio']
      })
    }

    const sizer = data.positionSizingSizer as SizerConfig
    const fail = (message: string, field: string) => ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message,
      path: ['positionSizingSizer', field]
    })
    if (sizer.type === 'atrRisk') {
      if (!sizer.riskPercentage || sizer.riskPercentage < 0.1 || sizer.riskPercentage > 10) fail('Must be between 0.1% and 10%', 'riskPercentage')
      if (!sizer.nAtr || sizer.nAtr < 0.5 || sizer.nAtr > 10) fail('Must be between 0.5 and 10', 'nAtr')
    } else if (sizer.type === 'percentEquity') {
      if (!sizer.percent || sizer.percent < 0.5 || sizer.percent > 100) fail('Must be between 0.5% and 100%', 'percent')
    } else if (sizer.type === 'kelly') {
      if (sizer.winRate == null || sizer.winRate <= 0 || sizer.winRate >= 1) fail('Must be between 0 and 1', 'winRate')
      if (!sizer.winLossRatio || sizer.winLossRatio <= 0 || sizer.winLossRatio > 100) fail('Must be between 0 and 100', 'winLossRatio')
      const m = sizer.fractionMultiplier
      if (m == null || m <= 0 || m > 1) fail('Must be between 0 and 1', 'fractionMultiplier')
    } else if (sizer.type === 'volTarget') {
      if (!sizer.targetVolPct || sizer.targetVolPct < 0.05 || sizer.targetVolPct > 100) fail('Must be between 0.05% and 100%', 'targetVolPct')
      if (!sizer.kAtr || sizer.kAtr <= 0 || sizer.kAtr > 10) fail('Must be between 0 and 10', 'kAtr')
    }
  }
})

// Track validation errors per section for sidebar indicators
const validationErrors = ref<Record<string, string[]>>({})

// Map field paths to sidebar sections
const fieldToSection: Record<string, SectionId> = {
  specificStocks: 'universe',
  stockSelection: 'universe',
  entryStrategy: 'strategies',
  exitStrategy: 'strategies',
  maxPositions: 'position-limits',
  positionSizingStartingCapital: 'position-sizing',
  positionSizingLeverageRatio: 'position-sizing',
  positionSizingSizer: 'position-sizing'
}

function sectionHasErrors(sectionId: SectionId): boolean {
  return Object.entries(validationErrors.value).some(
    ([field, errors]) => fieldToSection[field] === sectionId && errors.length > 0
  )
}

function onValidationError(event: { errors: Array<{ name?: string, message: string }> }) {
  const errors = event.errors || []
  const errorMap: Record<string, string[]> = {}
  errors.forEach((err) => {
    const field = err.name
    if (field) {
      if (!errorMap[field]) errorMap[field] = []
      errorMap[field].push(err.message)
    }
  })
  validationErrors.value = errorMap

  // Navigate to first section with errors
  const firstErrorField = errors[0]?.name
  if (firstErrorField && fieldToSection[firstErrorField]) {
    currentSection.value = fieldToSection[firstErrorField]
  }
}

// Fetch available strategies from backend
const { data: availableStrategies } = useFetch<{
  entryStrategies: string[]
  exitStrategies: string[]
}>('/udgaard/api/backtest/strategies')

// Fetch available rankers from backend
const { data: availableRankers } = useFetch<RankerMetadata[]>('/udgaard/api/backtest/rankers')

// Fetch available conditions for custom strategies
const { data: availableConditions } = useFetch<AvailableConditions>('/udgaard/api/backtest/conditions')

// Ranker options computed from fetched data
const rankerOptions = computed(() => {
  const options = [{ label: 'Strategy Default', value: 'strategy-default' }]
  if (availableRankers.value) {
    options.push(...availableRankers.value.map(r => ({ label: r.displayName, value: r.type })))
  }
  return options
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
  validationErrors.value = {}

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

  const positionSizing: PositionSizingConfig | undefined = state.positionSizingEnabled
    ? {
        startingCapital: state.positionSizingStartingCapital,
        sizer: state.positionSizingSizer,
        ...(state.positionSizingLeverageRatio != null ? { leverageRatio: state.positionSizingLeverageRatio } : {})
      }
    : undefined

  const config: BacktestRequest = {
    entryStrategy: state.entryStrategy,
    exitStrategy: state.exitStrategy,
    stockSymbols: state.stockSelection === 'all' ? undefined : state.specificStocks,
    assetTypes: state.stockSelection === 'all' && state.assetTypes.length > 0 ? state.assetTypes : undefined,
    excludeSectors: state.excludeSectors.length > 0 ? state.excludeSectors : undefined,
    startDate: state.startDate || undefined,
    endDate: state.endDate || undefined,
    maxPositions: state.positionLimitEnabled ? state.maxPositions : undefined,
    ranker: state.positionLimitEnabled && state.ranker && state.ranker !== 'strategy-default' ? state.ranker : undefined,
    cooldownDays: state.cooldownDays > 0 ? state.cooldownDays : undefined,
    entryDelayDays: state.entryDelayDays > 0 ? state.entryDelayDays : undefined,
    refresh: state.refresh,
    useUnderlyingAssets: state.useUnderlyingAssets,
    customUnderlyingMap: Object.keys(customUnderlyingMap).length > 0 ? customUnderlyingMap : undefined,
    positionSizing
  }

  emit('run-backtest', config)
  emit('update:open', false)
}

function cancel() {
  emit('update:open', false)
}

// Sidebar section definitions with computed subtitles
const sections = computed(() => [
  {
    id: 'universe' as SectionId,
    label: 'Universe',
    icon: 'i-lucide-database',
    subtitle: state.stockSelection === 'specific'
      ? state.specificStocks.length > 0
        ? `${state.specificStocks.length} stock${state.specificStocks.length === 1 ? '' : 's'}`
        : 'No stocks selected'
      : state.assetTypes.length > 0
        ? state.assetTypes.join(', ')
        : 'All Stocks'
  },
  {
    id: 'strategies' as SectionId,
    label: 'Strategies',
    icon: 'i-lucide-git-branch',
    subtitle: (() => {
      const entry = state.entryStrategy.type === 'predefined' ? state.entryStrategy.name : 'Custom'
      const exit = state.exitStrategy.type === 'predefined' ? state.exitStrategy.name : 'Custom'
      return `${entry} / ${exit}`
    })()
  },
  {
    id: 'position-limits' as SectionId,
    label: 'Position Limits',
    icon: 'i-lucide-layers',
    subtitle: state.positionLimitEnabled
      ? `Max ${state.maxPositions}, ${state.ranker === 'strategy-default' ? 'Default ranker' : state.ranker}`
      : 'Disabled'
  },
  {
    id: 'trade-timing' as SectionId,
    label: 'Trade Timing',
    icon: 'i-lucide-clock',
    subtitle: (() => {
      const parts: string[] = []
      if (state.cooldownDays > 0) parts.push(`${state.cooldownDays}d cooldown`)
      if (state.entryDelayDays > 0) parts.push(`${state.entryDelayDays}d delay`)
      return parts.length > 0 ? parts.join(', ') : 'No timing constraints'
    })()
  },
  {
    id: 'position-sizing' as SectionId,
    label: 'Position Sizing',
    icon: 'i-lucide-trending-up',
    subtitle: state.positionSizingEnabled
      ? (() => {
          const cap = `$${state.positionSizingStartingCapital.toLocaleString()}`
          const s = state.positionSizingSizer
          const desc = s.type === 'atrRisk'
            ? `${s.riskPercentage}% risk`
            : s.type === 'percentEquity'
              ? `${s.percent}% equity`
              : s.type === 'kelly'
                ? `Kelly W=${s.winRate}, R=${s.winLossRatio}`
                : `${s.targetVolPct}% vol target`
          return `${cap}, ${desc}`
        })()
      : 'Disabled'
  },
  {
    id: 'advanced' as SectionId,
    label: 'Advanced',
    icon: 'i-lucide-settings-2',
    subtitle: (() => {
      const parts: string[] = []
      if (state.useUnderlyingAssets) parts.push('Underlying assets')
      if (state.refresh) parts.push('Refresh data')
      return parts.length > 0 ? parts.join(', ') : 'Default settings'
    })()
  }
])
</script>

<template>
  <UModal
    :open="open"
    title="Backtest Configuration"
    :ui="{ content: 'max-w-5xl h-[85vh] flex flex-col' }"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <UForm
        ref="form"
        :state="state"
        :schema="schema"
        class="flex flex-col flex-1 min-h-0"
        @submit="onSubmit"
        @error="onValidationError"
      >
        <div class="flex flex-1 min-h-0">
          <!-- Sidebar -->
          <div class="w-48 shrink-0 border-r border-default flex flex-col gap-1 py-2 px-2">
            <button
              v-for="section in sections"
              :key="section.id"
              type="button"
              class="w-full text-left rounded-lg px-3 py-2.5 transition-colors"
              :class="currentSection === section.id
                ? 'bg-primary/10 text-primary'
                : 'text-default hover:bg-elevated'"
              @click="currentSection = section.id"
            >
              <div class="flex items-center gap-2 mb-0.5">
                <UIcon
                  :name="section.icon"
                  class="w-4 h-4 shrink-0"
                  :class="currentSection === section.id ? 'text-primary' : 'text-muted'"
                />
                <span class="text-sm font-medium">{{ section.label }}</span>
                <span
                  v-if="sectionHasErrors(section.id)"
                  class="w-2 h-2 rounded-full bg-red-500 shrink-0"
                />
              </div>
              <p class="text-xs text-muted leading-tight pl-6 truncate">
                {{ section.subtitle }}
              </p>
            </button>
          </div>

          <!-- Content panel -->
          <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
            <!-- Universe: Stock Selection & Date Range -->
            <div v-show="currentSection === 'universe'">
              <h3 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">
                Stock Selection & Date Range
              </h3>
              <div class="space-y-4">
                <div class="grid grid-cols-4 gap-4">
                  <UFormField label="Stock Selection" name="stockSelection">
                    <URadioGroup
                      v-model="state.stockSelection"
                      :items="[
                        { value: 'all', label: 'All Stocks' },
                        { value: 'specific', label: 'Specific Stocks' }
                      ]"
                    />
                  </UFormField>

                  <UFormField
                    v-if="state.stockSelection === 'all'"
                    label="Asset Types"
                    name="assetTypes"
                    help="Filter by asset type (empty = all)"
                  >
                    <USelectMenu
                      v-model="state.assetTypes"
                      :items="AssetTypeOptions"
                      value-key="value"
                      multiple
                      placeholder="All asset types"
                      :search-input="false"
                    />
                  </UFormField>
                  <div v-else />

                  <UFormField
                    v-if="state.stockSelection === 'all'"
                    label="Exclude Sectors"
                    name="excludeSectors"
                    help="Exclude stocks in these sectors"
                    class="col-span-2"
                  >
                    <USelectMenu
                      v-model="state.excludeSectors"
                      :items="SectorOptions"
                      value-key="value"
                      multiple
                      placeholder="No exclusions"
                      :search-input="false"
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

                <UFormField
                  v-if="state.stockSelection === 'specific'"
                  name="specificStocks"
                >
                  <SymbolSearch
                    v-model="state.specificStocks"
                    placeholder="Type to search stock symbols..."
                    multiple
                  />
                </UFormField>
              </div>
            </div>

            <!-- Strategies: Entry & Exit -->
            <div v-show="currentSection === 'strategies'">
              <h3 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">
                Entry & Exit Strategy
              </h3>
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <!-- Entry Strategy -->
                <UFormField name="entryStrategy">
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
                </UFormField>

                <!-- Exit Strategy -->
                <UFormField name="exitStrategy">
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
                </UFormField>
              </div>
            </div>

            <!-- Position Limits -->
            <div v-show="currentSection === 'position-limits'">
              <h3 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">
                Position Limiting
              </h3>
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
            </div>

            <!-- Trade Timing -->
            <div v-show="currentSection === 'trade-timing'">
              <h3 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">
                Cooldown & Entry Delay
              </h3>
              <UCard>
                <template #header>
                  <h3 class="text-base font-semibold">
                    Trade Timing
                  </h3>
                </template>
                <div class="grid grid-cols-2 gap-6">
                  <div>
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
                    <div v-if="state.cooldownDays > 0" class="text-sm text-muted mt-2">
                      After <strong>any exit</strong> (win or loss), all new entries are blocked for {{ state.cooldownDays }} trading {{ state.cooldownDays === 1 ? 'day' : 'days' }}.
                    </div>
                    <div v-else class="text-sm text-muted mt-2">
                      No cooldown: Immediate re-entry after exits
                    </div>
                  </div>

                  <div>
                    <div class="flex items-center gap-4">
                      <label class="text-sm font-medium whitespace-nowrap">
                        Entry Delay Days:
                      </label>
                      <UInput
                        v-model.number="state.entryDelayDays"
                        type="number"
                        min="0"
                        max="5"
                        placeholder="0"
                        class="w-32"
                      />
                    </div>
                    <div v-if="state.entryDelayDays > 0" class="text-sm text-muted mt-2">
                      Signal fires on Day 0, entry at Day {{ state.entryDelayDays }}'s close price
                    </div>
                    <div v-else class="text-sm text-muted mt-2">
                      No delay: Enter at signal day's close price
                    </div>
                  </div>
                </div>
              </UCard>
            </div>

            <!-- Position Sizing -->
            <div v-show="currentSection === 'position-sizing'">
              <h3 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">
                ATR-Based Sizing
              </h3>
              <UCard>
                <template #header>
                  <div class="flex items-center justify-between">
                    <div>
                      <h3 class="text-base font-semibold">
                        Position Sizing
                      </h3>
                      <p class="text-xs text-muted mt-1">
                        Pluggable position sizing for realistic portfolio simulation
                      </p>
                    </div>
                    <USwitch v-model="state.positionSizingEnabled" />
                  </div>
                </template>

                <div v-if="state.positionSizingEnabled" class="space-y-4">
                  <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Starting Capital ($)" name="positionSizingStartingCapital">
                      <UInput
                        v-model.number="state.positionSizingStartingCapital"
                        type="number"
                        min="1000"
                        step="1000"
                        placeholder="100000"
                      />
                    </UFormField>

                    <UFormField label="Leverage Ratio" name="positionSizingLeverageRatio" help="Max notional as multiple of portfolio (empty = no cap)">
                      <UInput
                        v-model.number="state.positionSizingLeverageRatio"
                        type="number"
                        min="0.1"
                        max="20"
                        step="0.1"
                        placeholder="No cap"
                      />
                    </UFormField>
                  </div>

                  <UFormField label="Sizer" name="positionSizingSizer" help="Determines how per-position share count is computed">
                    <USelect
                      :model-value="state.positionSizingSizer.type"
                      :items="[
                        { label: 'ATR Risk (risk % per trade with ATR stop)', value: 'atrRisk' },
                        { label: 'Percent Equity (fixed % of equity per position)', value: 'percentEquity' },
                        { label: 'Kelly (size from win rate + win/loss ratio)', value: 'kelly' },
                        { label: 'Vol Target (equal-vol contribution)', value: 'volTarget' }
                      ]"
                      @update:model-value="changeSizerType($event as SizerConfig['type'])"
                    />
                  </UFormField>

                  <div v-if="state.positionSizingSizer.type === 'atrRisk'" class="grid grid-cols-2 gap-4">
                    <UFormField label="Risk Per Trade (%)" help="% of portfolio risked per trade">
                      <UInput
                        v-model.number="state.positionSizingSizer.riskPercentage"
                        type="number"
                        min="0.1"
                        max="10"
                        step="0.05"
                        placeholder="1.25"
                      />
                    </UFormField>
                    <UFormField label="ATR Multiplier" help="Stop distance in ATR units">
                      <UInput
                        v-model.number="state.positionSizingSizer.nAtr"
                        type="number"
                        min="0.5"
                        max="10"
                        step="0.1"
                        placeholder="2.0"
                      />
                    </UFormField>
                  </div>

                  <div v-else-if="state.positionSizingSizer.type === 'percentEquity'" class="grid grid-cols-2 gap-4">
                    <UFormField label="Percent of Equity (%)" help="% of equity allocated per position">
                      <UInput
                        v-model.number="state.positionSizingSizer.percent"
                        type="number"
                        min="0.5"
                        max="100"
                        step="0.5"
                        placeholder="12.5"
                      />
                    </UFormField>
                  </div>

                  <div v-else-if="state.positionSizingSizer.type === 'kelly'" class="grid grid-cols-3 gap-4">
                    <UFormField label="Win Rate (0-1)" help="Historical win rate">
                      <UInput
                        v-model.number="state.positionSizingSizer.winRate"
                        type="number"
                        min="0"
                        max="1"
                        step="0.01"
                        placeholder="0.52"
                      />
                    </UFormField>
                    <UFormField label="Win/Loss Ratio" help="avgWin / avgLoss">
                      <UInput
                        v-model.number="state.positionSizingSizer.winLossRatio"
                        type="number"
                        min="0.1"
                        step="0.1"
                        placeholder="1.5"
                      />
                    </UFormField>
                    <UFormField label="Fraction Multiplier" help="0.25 = quarter-Kelly (recommended)">
                      <UInput
                        v-model.number="state.positionSizingSizer.fractionMultiplier"
                        type="number"
                        min="0.01"
                        max="1"
                        step="0.05"
                        placeholder="0.25"
                      />
                    </UFormField>
                  </div>

                  <div v-else-if="state.positionSizingSizer.type === 'volTarget'" class="grid grid-cols-2 gap-4">
                    <UFormField label="Target Vol (%)" help="Target per-position vol contribution">
                      <UInput
                        v-model.number="state.positionSizingSizer.targetVolPct"
                        type="number"
                        min="0.05"
                        max="100"
                        step="0.05"
                        placeholder="0.5"
                      />
                    </UFormField>
                    <UFormField label="ATR → stdev factor (k)" help="k × ATR proxy for daily stdev">
                      <UInput
                        v-model.number="state.positionSizingSizer.kAtr"
                        type="number"
                        min="0.1"
                        step="0.1"
                        placeholder="1.0"
                      />
                    </UFormField>
                  </div>
                </div>
                <div v-else class="text-sm text-muted">
                  Disabled: Backtest will report per-trade percentage returns without position sizing
                </div>
              </UCard>
            </div>

            <!-- Advanced -->
            <div v-show="currentSection === 'advanced'">
              <h3 class="text-sm font-semibold text-muted uppercase tracking-wide mb-4">
                Underlying Assets & Options
              </h3>
              <div class="space-y-4">
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
            </div>
          </div>
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
          @click="form?.submit()"
        />
      </div>
    </template>
  </UModal>
</template>
