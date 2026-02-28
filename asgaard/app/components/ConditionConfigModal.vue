<script setup lang="ts">
import type { ConditionConfig, ConditionMetadata, CustomStrategyConfig } from '~/types'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'evaluate': [conditions: ConditionConfig[], operator: 'AND' | 'OR']
}>()

const { data: availableConditions } = useFetch<{ entryConditions: ConditionMetadata[], exitConditions: ConditionMetadata[] }>('/udgaard/api/backtest/conditions')

const customConfig = ref<CustomStrategyConfig>({
  type: 'custom',
  operator: 'AND',
  conditions: []
})

function handleEvaluate() {
  if (customConfig.value.conditions.length === 0) return
  emit('evaluate', customConfig.value.conditions, customConfig.value.operator as 'AND' | 'OR')
  emit('update:open', false)
}
</script>

<template>
  <UModal :open="open" title="Configure Conditions" @update:open="emit('update:open', $event)">
    <template #body>
      <StrategyBuilder
        v-if="availableConditions"
        v-model="customConfig"
        :available-conditions="availableConditions.entryConditions"
        strategy-type="entry"
      />
      <div v-else class="flex justify-center py-8">
        <UIcon name="i-heroicons-arrow-path" class="animate-spin text-2xl" />
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="soft" @click="emit('update:open', false)">
          Cancel
        </UButton>
        <UButton
          icon="i-heroicons-play"
          :disabled="customConfig.conditions.length === 0"
          @click="handleEvaluate"
        >
          Evaluate Conditions
        </UButton>
      </div>
    </template>
  </UModal>
</template>
