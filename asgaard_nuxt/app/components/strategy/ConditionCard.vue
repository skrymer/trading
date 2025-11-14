<script setup lang="ts">
import type { ConditionMetadata, ConditionConfig } from '~/types'

const props = defineProps<{
  condition: ConditionMetadata
  config: ConditionConfig
  removable?: boolean
}>()

const emit = defineEmits<{
  'update:config': [config: ConditionConfig]
  'remove': []
}>()

// Initialize parameters with default values
const parameters = reactive<Record<string, any>>({})

// Set default values for all parameters
onMounted(() => {
  props.condition.parameters.forEach(param => {
    if (props.config.parameters?.[param.name] !== undefined) {
      parameters[param.name] = props.config.parameters[param.name]
    } else {
      parameters[param.name] = param.defaultValue
    }
  })
  emitUpdate()
})

function emitUpdate() {
  emit('update:config', {
    type: props.condition.type,
    parameters: { ...parameters }
  })
}

function updateParameter(name: string, value: any) {
  parameters[name] = value
  emitUpdate()
}

// Get category color
function getCategoryColor(category: string): string {
  const colors: Record<string, string> = {
    'Stock': 'blue',
    'SPY': 'green',
    'Market': 'purple',
    'Sector': 'orange',
    'OrderBlock': 'pink',
    'RiskManagement': 'yellow',
    'Signal': 'red',
    'Trend': 'indigo',
    'ProfitTaking': 'teal',
    'StopLoss': 'rose'
  }
  return colors[category] || 'gray'
}
</script>

<template>
  <UCard :ui="{ body: 'space-y-3' }">
    <div class="flex items-start justify-between">
      <div class="flex-1">
        <div class="flex items-center gap-2 mb-1">
          <h4 class="font-medium text-sm">{{ condition.displayName }}</h4>
          <UBadge :color="getCategoryColor(condition.category)" size="xs">
            {{ condition.category }}
          </UBadge>
        </div>
        <p class="text-xs text-muted">{{ condition.description }}</p>
      </div>
      <UButton
        v-if="removable"
        icon="i-lucide-x"
        color="neutral"
        variant="ghost"
        size="xs"
        @click="emit('remove')"
      />
    </div>

    <!-- Parameters -->
    <div v-if="condition.parameters.length > 0" class="space-y-2 pt-2 border-t">
      <div v-for="param in condition.parameters" :key="param.name" class="grid grid-cols-2 gap-2 items-center">
        <label class="text-xs font-medium">{{ param.displayName }}</label>

        <!-- Number input -->
        <UInput
          v-if="param.type === 'number' && !param.options"
          :model-value="parameters[param.name]"
          @update:model-value="updateParameter(param.name, $event)"
          type="number"
          :min="param.min"
          :max="param.max"
          size="xs"
        />

        <!-- Select for number with options -->
        <USelect
          v-else-if="param.type === 'number' && param.options"
          :model-value="String(parameters[param.name])"
          @update:model-value="updateParameter(param.name, Number($event))"
          :items="param.options.map(o => ({ label: o, value: o }))"
          value-key="value"
          size="xs"
        />

        <!-- Boolean checkbox -->
        <UCheckbox
          v-else-if="param.type === 'boolean'"
          :model-value="parameters[param.name]"
          @update:model-value="updateParameter(param.name, $event)"
        />

        <!-- String input -->
        <UInput
          v-else
          :model-value="parameters[param.name]"
          @update:model-value="updateParameter(param.name, $event)"
          size="xs"
        />
      </div>
    </div>
  </UCard>
</template>
