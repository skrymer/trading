<template>
  <UModal
    :open="isOpen"
    title="Edit Position Metadata"
    size="lg"
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div v-if="position" class="space-y-4">
        <!-- Position Info -->
        <div class="p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
          <div class="text-sm font-medium">
            {{ formatPositionName(position) }}
          </div>
          <div v-if="formatOptionDetails(position)" class="text-xs text-gray-500">
            {{ formatOptionDetails(position) }}
          </div>
          <div class="text-xs text-gray-500 mt-1">
            <UBadge :color="position.status === 'OPEN' ? 'primary' : 'neutral'" variant="subtle" size="xs">
              {{ position.status }}
            </UBadge>
          </div>
        </div>

        <UForm
          :state="state"
          :schema="schema"
          class="space-y-4"
          @submit="onSubmit"
        >
          <!-- Entry Strategy -->
          <UFormField label="Entry Strategy" name="entryStrategy" required>
            <USelect
              v-if="!useCustomEntry"
              v-model="state.entryStrategy"
              :items="entryOptions"
              value-key="value"
              placeholder="Select entry strategy"
              class="w-full"
            />
            <div v-else class="flex gap-2">
              <UInput
                v-model="state.entryStrategy"
                placeholder="Enter custom strategy name"
                class="flex-1"
              />
              <UButton
                icon="i-lucide-x"
                variant="ghost"
                size="sm"
                @click="useCustomEntry = false; state.entryStrategy = ''"
              />
            </div>
          </UFormField>

          <!-- Exit Strategy -->
          <UFormField label="Exit Strategy" name="exitStrategy" required>
            <USelect
              v-if="!useCustomExit"
              v-model="state.exitStrategy"
              :items="exitOptions"
              value-key="value"
              placeholder="Select exit strategy"
              class="w-full"
            />
            <div v-else class="flex gap-2">
              <UInput
                v-model="state.exitStrategy"
                placeholder="Enter custom strategy name"
                class="flex-1"
              />
              <UButton
                icon="i-lucide-x"
                variant="ghost"
                size="sm"
                @click="useCustomExit = false; state.exitStrategy = ''"
              />
            </div>
          </UFormField>

          <!-- Notes -->
          <UFormField label="Notes" name="notes">
            <UTextarea
              v-model="state.notes"
              placeholder="Add any notes or observations about this position"
              :rows="4"
            />
          </UFormField>

          <!-- Info box -->
          <div class="p-3 bg-blue-50 dark:bg-blue-950 rounded-lg border border-blue-200 dark:border-blue-800">
            <div class="flex items-start gap-2">
              <UIcon name="i-lucide-info" class="w-4 h-4 mt-0.5 text-blue-600 dark:text-blue-400 flex-shrink-0" />
              <div class="text-sm text-blue-900 dark:text-blue-100">
                <p class="font-medium mb-1">
                  About Metadata
                </p>
                <p class="text-xs text-blue-700 dark:text-blue-300">
                  These fields are for documentation and analysis purposes only. They don't affect P&L calculations or position mechanics.
                </p>
              </div>
            </div>
          </div>

          <div class="flex justify-end gap-2 pt-2">
            <UButton
              label="Cancel"
              variant="outline"
              @click="isOpen = false"
            />
            <UButton
              type="submit"
              label="Save Changes"
              icon="i-lucide-save"
              :loading="loading"
            />
          </div>
        </UForm>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
import { z } from 'zod'
import type { Position } from '~/types'

const { formatPositionName, formatOptionDetails } = usePositionFormatters()

const props = defineProps<{
  position: Position | null
}>()

const emit = defineEmits<{
  success: []
}>()

const model = defineModel<boolean>()

const isOpen = computed({
  get: () => model.value ?? false,
  set: (value) => {
    model.value = value
  }
})

const toast = useToast()
const loading = ref(false)
const useCustomEntry = ref(false)
const useCustomExit = ref(false)

const CUSTOM_VALUE = '__custom__'

const { data: strategies } = useFetch<{ entryStrategies: string[], exitStrategies: string[] }>('/udgaard/api/backtest/strategies')

const entryOptions = computed(() => {
  const items = (strategies.value?.entryStrategies || []).map(s => ({ label: s, value: s }))
  items.push({ label: 'Custom...', value: CUSTOM_VALUE })
  return items
})

const exitOptions = computed(() => {
  const items = (strategies.value?.exitStrategies || []).map(s => ({ label: s, value: s }))
  items.push({ label: 'Custom...', value: CUSTOM_VALUE })
  return items
})

const state = reactive({
  entryStrategy: '',
  exitStrategy: '',
  notes: ''
})

watch(() => state.entryStrategy, (val) => {
  if (val === CUSTOM_VALUE) {
    useCustomEntry.value = true
    state.entryStrategy = ''
  }
})

watch(() => state.exitStrategy, (val) => {
  if (val === CUSTOM_VALUE) {
    useCustomExit.value = true
    state.exitStrategy = ''
  }
})

const schema = z.object({
  entryStrategy: z.string().min(1, 'Entry strategy is required'),
  exitStrategy: z.string().min(1, 'Exit strategy is required'),
  notes: z.string().optional()
})

// Initialize form when position changes or modal opens
watch([() => props.position, isOpen], ([newPosition, modalOpen]) => {
  if (modalOpen && newPosition) {
    const entry = newPosition.entryStrategy || ''
    const exit = newPosition.exitStrategy || ''
    const knownEntries = strategies.value?.entryStrategies || []
    const knownExits = strategies.value?.exitStrategies || []

    useCustomEntry.value = entry !== '' && !knownEntries.includes(entry)
    useCustomExit.value = exit !== '' && !knownExits.includes(exit)
    state.entryStrategy = entry
    state.exitStrategy = exit
    state.notes = newPosition.notes || ''
  }
}, { immediate: true })

async function onSubmit() {
  if (!props.position) return

  loading.value = true
  try {
    await $fetch(`/udgaard/api/positions/${props.position.portfolioId}/${props.position.id}/metadata`, {
      method: 'PUT',
      body: {
        entryStrategy: state.entryStrategy,
        exitStrategy: state.exitStrategy,
        notes: state.notes || null
      }
    })

    toast.add({
      title: 'Success',
      description: 'Position metadata updated successfully',
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    emit('success')
    isOpen.value = false
  } catch (error: any) {
    console.error('Error updating position metadata:', error)
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to update position metadata',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}
</script>
