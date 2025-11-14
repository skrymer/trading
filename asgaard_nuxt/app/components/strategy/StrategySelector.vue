<script setup lang="ts">
import type { StrategyConfig, ConditionMetadata, CustomStrategyConfig } from '~/types'

const props = defineProps<{
  availablePredefined: string[]
  availableConditions: ConditionMetadata[]
  strategyType: 'entry' | 'exit'
  modelValue: StrategyConfig
}>()

const emit = defineEmits<{
  'update:modelValue': [config: StrategyConfig]
}>()

// Strategy mode: predefined or custom
const mode = ref<'predefined' | 'custom'>(props.modelValue.type)

// Predefined strategy selection
const predefinedStrategy = ref<string>(
  props.modelValue.type === 'predefined' ? props.modelValue.name : (props.availablePredefined[0] || '')
)

// Custom strategy config
const customStrategy = ref<CustomStrategyConfig>(
  props.modelValue.type === 'custom'
    ? props.modelValue
    : {
        type: 'custom',
        operator: props.strategyType === 'entry' ? 'AND' : 'OR',
        conditions: []
      }
)

// Format strategy name for display
function formatStrategyName(name: string): string {
  return name.replace(/([A-Z])/g, ' $1').trim()
}

// Predefined options
const predefinedOptions = computed(() => {
  return props.availablePredefined.map(name => ({
    label: formatStrategyName(name),
    value: name
  }))
})

// Emit updates
function emitUpdate() {
  if (mode.value === 'predefined') {
    emit('update:modelValue', {
      type: 'predefined',
      name: predefinedStrategy.value
    })
  } else {
    emit('update:modelValue', customStrategy.value)
  }
}

// Watch for changes
watch(mode, () => emitUpdate())
watch(predefinedStrategy, () => {
  if (mode.value === 'predefined') {
    emitUpdate()
  }
})
watch(customStrategy, () => {
  if (mode.value === 'custom') {
    emitUpdate()
  }
}, { deep: true })
</script>

<template>
  <div class="space-y-4">
    <!-- Strategy Type Selection -->
    <UFormField :label="`${strategyType === 'entry' ? 'Entry' : 'Exit'} Strategy Type`" name="strategyMode">
      <URadioGroup
        v-model="mode"
        :items="[
          { value: 'predefined', label: 'Use Predefined Strategy' },
          { value: 'custom', label: 'Build Custom Strategy' }
        ]"
      />
    </UFormField>

    <!-- Predefined Strategy Selection -->
    <div v-if="mode === 'predefined'">
      <UFormField :label="`Select ${strategyType === 'entry' ? 'Entry' : 'Exit'} Strategy`" name="predefinedStrategy">
        <USelect
          v-model="predefinedStrategy"
          :items="predefinedOptions"
          value-key="value"
          placeholder="Select strategy"
          class="w-full"
        />
      </UFormField>
    </div>

    <!-- Custom Strategy Builder -->
    <div v-else>
      <StrategyBuilder
        v-model="customStrategy"
        :available-conditions="availableConditions"
        :strategy-type="strategyType"
      />
    </div>
  </div>
</template>
