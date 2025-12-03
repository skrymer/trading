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
  props.condition.parameters.forEach((param) => {
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

// Get category color - using NuxtUI 4 valid colors
type BadgeColor = 'error' | 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'neutral'
function getCategoryColor(category: string): BadgeColor {
  const colors: Record<string, BadgeColor> = {
    Stock: 'info',
    SPY: 'success',
    Market: 'secondary',
    Sector: 'warning',
    OrderBlock: 'primary',
    RiskManagement: 'warning',
    Signal: 'error',
    Trend: 'info',
    ProfitTaking: 'success',
    StopLoss: 'error'
  }
  return colors[category] || 'neutral'
}
</script>

<template>
  <UCard :ui="{ body: 'space-y-3' }">
    <div class="flex items-start justify-between">
      <div class="flex-1">
        <div class="flex items-center gap-2 mb-1">
          <h4 class="font-medium text-sm">
            {{ condition.displayName }}
          </h4>
          <UBadge :color="getCategoryColor(condition.category)" size="xs">
            {{ condition.category }}
          </UBadge>
        </div>
        <p class="text-xs text-muted">
          {{ condition.description }}
        </p>
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
          type="number"
          :min="param.min"
          :max="param.max"
          size="xs"
          @update:model-value="updateParameter(param.name, $event)"
        />

        <!-- Select for number with options -->
        <USelect
          v-else-if="param.type === 'number' && param.options"
          :model-value="String(parameters[param.name])"
          :items="param.options.map((o: number) => ({ label: o, value: o }))"
          value-key="value"
          size="xs"
          @update:model-value="updateParameter(param.name, Number($event))"
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
          size="xs"
          @update:model-value="updateParameter(param.name, $event)"
        />
      </div>
    </div>
  </UCard>
</template>
