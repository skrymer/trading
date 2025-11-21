<script setup lang="ts">
import type { ConditionMetadata, ConditionConfig, CustomStrategyConfig } from '~/types'

const props = defineProps<{
  availableConditions: ConditionMetadata[]
  strategyType: 'entry' | 'exit'
  modelValue: CustomStrategyConfig
}>()

const emit = defineEmits<{
  'update:modelValue': [config: CustomStrategyConfig]
}>()

// Local state
const operator = ref<'AND' | 'OR' | 'NOT'>(props.modelValue.operator || (props.strategyType === 'entry' ? 'AND' : 'OR'))
const description = ref(props.modelValue.description || '')
const conditions = ref<ConditionConfig[]>(props.modelValue.conditions || [])
const selectedConditionType = ref<string>('')

// Group conditions by category
const conditionsByCategory = computed(() => {
  const groups = new Map<string, ConditionMetadata[]>()

  props.availableConditions.forEach((condition) => {
    if (!groups.has(condition.category)) {
      groups.set(condition.category, [])
    }
    groups.get(condition.category)!.push(condition)
  })

  return Array.from(groups.entries()).map(([category, items]) => ({
    category,
    conditions: items
  }))
})

// Available condition options for dropdown
const conditionOptions = computed(() => {
  return props.availableConditions.map(c => ({
    label: c.displayName,
    value: c.type,
    description: c.description,
    category: c.category
  }))
})

// Operator options
const operatorOptions = props.strategyType === 'entry'
  ? [
      { label: 'AND (All conditions must be met)', value: 'AND' },
      { label: 'OR (Any condition must be met)', value: 'OR' }
    ]
  : [
      { label: 'OR (Exit on any condition)', value: 'OR' },
      { label: 'AND (Exit when all conditions met)', value: 'AND' }
    ]

function addCondition() {
  if (!selectedConditionType.value) return

  const conditionMeta = props.availableConditions.find(c => c.type === selectedConditionType.value)
  if (!conditionMeta) return

  // Create default condition config
  const defaultParams: Record<string, any> = {}
  conditionMeta.parameters.forEach((param) => {
    defaultParams[param.name] = param.defaultValue
  })

  conditions.value.push({
    type: selectedConditionType.value,
    parameters: defaultParams
  })

  selectedConditionType.value = ''
  emitUpdate()
}

function removeCondition(index: number) {
  conditions.value.splice(index, 1)
  emitUpdate()
}

function updateCondition(index: number, config: ConditionConfig) {
  conditions.value[index] = config
  emitUpdate()
}

function emitUpdate() {
  emit('update:modelValue', {
    type: 'custom',
    operator: operator.value,
    description: description.value || undefined,
    conditions: [...conditions.value]
  })
}

// Watch for changes
watch([operator, description], () => {
  emitUpdate()
})

// Get condition metadata for a condition config
function getConditionMetadata(config: ConditionConfig): ConditionMetadata | undefined {
  return props.availableConditions.find(c => c.type === config.type)
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold">
        {{ strategyType === 'entry' ? 'Entry' : 'Exit' }} Conditions
      </h3>
      <UBadge color="primary" size="xs">
        {{ conditions.length }} condition{{ conditions.length !== 1 ? 's' : '' }}
      </UBadge>
    </div>

    <!-- Description (optional) -->
    <UFormField label="Description (Optional)" name="description">
      <UInput
        v-model="description"
        placeholder="e.g., Conservative ETF Entry"
        size="sm"
      />
    </UFormField>

    <!-- Operator Selection -->
    <UFormField label="Logic Operator" name="operator">
      <USelect
        v-model="operator"
        :items="operatorOptions"
        value-key="value"
        size="sm"
      />
    </UFormField>

    <!-- Add Condition -->
    <div class="space-y-2">
      <label class="text-xs font-medium">Add Condition</label>
      <div class="flex gap-2">
        <UInputMenu
          v-model="selectedConditionType"
          :items="conditionOptions"
          value-key="value"
          placeholder="Search conditions..."
          icon="i-lucide-search"
          class="flex-1"
          size="sm"
        >
          <template #item="{ item }">
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-medium">{{ item.label }}</span>
              <span class="text-xs text-muted line-clamp-1">{{ item.description }}</span>
            </div>
          </template>
        </UInputMenu>
        <UButton
          icon="i-lucide-plus"
          :disabled="!selectedConditionType"
          size="sm"
          @click="addCondition"
        >
          Add
        </UButton>
      </div>
    </div>

    <!-- Conditions List -->
    <div v-if="conditions.length > 0" class="space-y-2">
      <label class="text-xs font-medium">Conditions ({{ operator }})</label>
      <div class="space-y-2">
        <StrategyConditionCard
          v-for="(config, index) in conditions"
          :key="index"
          :condition="getConditionMetadata(config)!"
          :config="config"
          :removable="true"
          @update:config="updateCondition(index, $event)"
          @remove="removeCondition(index)"
        />
      </div>
    </div>

    <!-- Empty State -->
    <UAlert
      v-else
      color="primary"
      variant="subtle"
      icon="i-lucide-info"
      title="No conditions added"
      description="Add at least one condition to create a custom strategy"
    />

    <!-- Info -->
    <UAlert
      v-if="strategyType === 'entry'"
      color="neutral"
      variant="subtle"
      icon="i-lucide-lightbulb"
      title="Entry Strategy Tip"
      description="Use AND for conservative entries (all conditions must be met). Use OR for aggressive entries (any condition triggers entry)."
    />
    <UAlert
      v-else
      color="neutral"
      variant="subtle"
      icon="i-lucide-lightbulb"
      title="Exit Strategy Tip"
      description="OR is typical for exits (exit on any stop/target). AND requires all exit conditions to be met simultaneously."
    />
  </div>
</template>
