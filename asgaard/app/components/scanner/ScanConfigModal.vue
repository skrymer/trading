<script setup lang="ts">
import type { ScanRequest } from '~/types'
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
  'run-scan': [config: ScanRequest]
}>()

const loading = ref(false)

const state = reactive({
  entryStrategyName: '',
  exitStrategyName: '',
  stockSelection: 'all' as 'all' | 'specific',
  specificStocks: '',
  assetTypes: [] as string[],
  excludeSectors: [] as string[]
})

// Fetch available strategies from backend
const { data: availableStrategies } = useFetch<{
  entryStrategies: string[]
  exitStrategies: string[]
}>('/udgaard/api/backtest/strategies')

const entryStrategyOptions = computed(() =>
  (availableStrategies.value?.entryStrategies ?? []).map(s => ({ label: s, value: s }))
)

const exitStrategyOptions = computed(() =>
  (availableStrategies.value?.exitStrategies ?? []).map(s => ({ label: s, value: s }))
)

// Auto-select first strategies when loaded
watch(availableStrategies, (strategies) => {
  if (strategies && !state.entryStrategyName && strategies.entryStrategies.length > 0) {
    state.entryStrategyName = strategies.entryStrategies[0] ?? ''
  }
  if (strategies && !state.exitStrategyName && strategies.exitStrategies.length > 0) {
    state.exitStrategyName = strategies.exitStrategies[0] ?? ''
  }
}, { immediate: true })

function handleSubmit() {
  const config: ScanRequest = {
    entryStrategyName: state.entryStrategyName,
    exitStrategyName: state.exitStrategyName
  }

  if (state.stockSelection === 'specific' && state.specificStocks.trim()) {
    config.stockSymbols = state.specificStocks.split(',').map(s => s.trim().toUpperCase()).filter(Boolean)
  }
  if (state.assetTypes.length > 0) {
    config.assetTypes = state.assetTypes
  }
  if (state.excludeSectors.length > 0) {
    config.excludeSectors = state.excludeSectors
  }

  emit('run-scan', config)
}
</script>

<template>
  <UModal
    :open="open"
    title="Configure Scan"
    size="lg"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-4">
        <!-- Entry Strategy -->
        <UFormField label="Entry Strategy" required>
          <USelect
            v-model="state.entryStrategyName"
            :items="entryStrategyOptions"
            placeholder="Select entry strategy"
            value-key="value"
          />
        </UFormField>

        <!-- Exit Strategy -->
        <UFormField label="Exit Strategy" required>
          <USelect
            v-model="state.exitStrategyName"
            :items="exitStrategyOptions"
            placeholder="Select exit strategy"
            value-key="value"
          />
        </UFormField>

        <!-- Stock Selection -->
        <UFormField label="Stocks">
          <div class="space-y-2">
            <URadioGroup
              v-model="state.stockSelection"
              :items="[
                { label: 'All stocks', value: 'all' },
                { label: 'Specific symbols', value: 'specific' }
              ]"
            />
            <UInput
              v-if="state.stockSelection === 'specific'"
              v-model="state.specificStocks"
              placeholder="AAPL, MSFT, GOOGL..."
            />
          </div>
        </UFormField>

        <!-- Asset Types -->
        <UFormField label="Asset Types (optional)">
          <USelectMenu
            v-model="state.assetTypes"
            :items="AssetTypeOptions"
            multiple
            placeholder="All types"
            value-key="value"
          />
        </UFormField>

        <!-- Exclude Sectors -->
        <UFormField label="Exclude Sectors">
          <USelectMenu
            v-model="state.excludeSectors"
            :items="SectorOptions"
            multiple
            placeholder="None"
            value-key="value"
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
          @click="emit('update:open', false)"
        />
        <UButton
          label="Run Scan"
          icon="i-lucide-scan-search"
          :disabled="!state.entryStrategyName || !state.exitStrategyName"
          :loading="loading"
          @click="handleSubmit"
        />
      </div>
    </template>
  </UModal>
</template>
