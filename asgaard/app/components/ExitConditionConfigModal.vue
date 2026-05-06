<script setup lang="ts">
import type { ConditionConfig, ConditionMetadata, CustomStrategyConfig } from '~/types'

const props = defineProps<{
  open: boolean
  // ISO date strings (YYYY-MM-DD) that exist in this stock's quote history.
  // Required so the modal can disable Evaluate when the picked entry date isn't a trading day —
  // most exit conditions need an entry quote to evaluate, and silently dropping them to false is
  // worse UX than refusing the request.
  availableQuoteDates: string[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'evaluate': [conditions: ConditionConfig[], operator: 'AND' | 'OR', entryDate: string]
}>()

const { data: availableConditions } = useFetch<{ entryConditions: ConditionMetadata[], exitConditions: ConditionMetadata[] }>('/udgaard/api/backtest/conditions')

const customConfig = ref<CustomStrategyConfig>({
  type: 'custom',
  operator: 'OR',
  conditions: []
})

const entryDate = ref<string>('')

const dateSet = computed(() => new Set(props.availableQuoteDates))
const entryDateValid = computed(() => entryDate.value !== '' && dateSet.value.has(entryDate.value))
const evaluateDisabled = computed(() => customConfig.value.conditions.length === 0 || !entryDateValid.value)

// Default the picker to the most recent available quote date when the modal opens.
watch(() => props.open, (isOpen) => {
  if (!isOpen || entryDate.value !== '') return
  const latest = props.availableQuoteDates[props.availableQuoteDates.length - 1]
  if (latest !== undefined) {
    entryDate.value = latest
  }
})

function handleEvaluate() {
  if (evaluateDisabled.value) return
  emit('evaluate', customConfig.value.conditions, customConfig.value.operator as 'AND' | 'OR', entryDate.value)
  emit('update:open', false)
}
</script>

<template>
  <UModal :open="open" title="Configure Exit Conditions" @update:open="emit('update:open', $event)">
    <template #body>
      <div class="flex flex-col gap-4">
        <UFormField
          label="Hypothetical Entry Date"
          :error="entryDate !== '' && !entryDateValid ? 'Pick a trading day from this stock\'s history' : undefined"
          required
        >
          <UInput v-model="entryDate" type="date" />
        </UFormField>

        <StrategyBuilder
          v-if="availableConditions"
          v-model="customConfig"
          :available-conditions="availableConditions.exitConditions"
          strategy-type="exit"
        />
        <div v-else class="flex justify-center py-8">
          <UIcon name="i-heroicons-arrow-path" class="animate-spin text-2xl" />
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="soft" @click="emit('update:open', false)">
          Cancel
        </UButton>
        <UButton
          icon="i-heroicons-play"
          :disabled="evaluateDisabled"
          @click="handleEvaluate"
        >
          Evaluate Exit Conditions
        </UButton>
      </div>
    </template>
  </UModal>
</template>
