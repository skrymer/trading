<template>
  <UModal
    :open="isOpen"
    title="Edit Strategy"
    size="lg"
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div class="space-y-4">
        <!-- Selected positions info -->
        <div class="p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
          <div class="text-sm font-medium">
            {{ positions.length }} position{{ positions.length === 1 ? '' : 's' }} selected
          </div>
          <div class="text-xs text-gray-500 mt-1">
            {{ displaySymbols }}
          </div>
        </div>

        <!-- Entry Strategy -->
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <input
              v-model="skipEntry"
              type="checkbox"
              class="rounded"
            >
            <label class="text-sm text-gray-500">Don't change entry strategy</label>
          </div>
          <UFormField v-if="!skipEntry" label="Entry Strategy" name="entryStrategy">
            <USelect
              v-if="!useCustomEntry"
              v-model="entryStrategy"
              :items="entryOptions"
              value-key="value"
              placeholder="Select entry strategy"
              class="w-full"
            />
            <div v-else class="flex gap-2">
              <UInput
                v-model="customEntry"
                placeholder="Enter custom strategy name"
                class="flex-1"
              />
              <UButton
                icon="i-lucide-x"
                variant="ghost"
                size="sm"
                @click="useCustomEntry = false; customEntry = ''"
              />
            </div>
          </UFormField>
        </div>

        <!-- Exit Strategy -->
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <input
              v-model="skipExit"
              type="checkbox"
              class="rounded"
            >
            <label class="text-sm text-gray-500">Don't change exit strategy</label>
          </div>
          <UFormField v-if="!skipExit" label="Exit Strategy" name="exitStrategy">
            <USelect
              v-if="!useCustomExit"
              v-model="exitStrategy"
              :items="exitOptions"
              value-key="value"
              placeholder="Select exit strategy"
              class="w-full"
            />
            <div v-else class="flex gap-2">
              <UInput
                v-model="customExit"
                placeholder="Enter custom strategy name"
                class="flex-1"
              />
              <UButton
                icon="i-lucide-x"
                variant="ghost"
                size="sm"
                @click="useCustomExit = false; customExit = ''"
              />
            </div>
          </UFormField>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <UButton
            label="Cancel"
            variant="outline"
            @click="isOpen = false"
          />
          <UButton
            :label="`Apply to ${positions.length} Position${positions.length === 1 ? '' : 's'}`"
            icon="i-lucide-save"
            :loading="loading"
            :disabled="!canSubmit"
            @click="onSubmit"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
import type { Position } from '~/types'

const { formatPositionName } = usePositionFormatters()

const CUSTOM_VALUE = '__custom__'

const props = defineProps<{
  positions: Position[]
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

const entryStrategy = ref('')
const exitStrategy = ref('')
const customEntry = ref('')
const customExit = ref('')
const useCustomEntry = ref(false)
const useCustomExit = ref(false)
const skipEntry = ref(false)
const skipExit = ref(false)

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

// Handle custom option selection
watch(entryStrategy, (val) => {
  if (val === CUSTOM_VALUE) {
    useCustomEntry.value = true
    entryStrategy.value = ''
  }
})

watch(exitStrategy, (val) => {
  if (val === CUSTOM_VALUE) {
    useCustomExit.value = true
    exitStrategy.value = ''
  }
})

const displaySymbols = computed(() => {
  const names = props.positions.map(p => formatPositionName(p))
  if (names.length <= 5) return names.join(', ')
  return names.slice(0, 5).join(', ') + ` + ${names.length - 5} more`
})

const resolvedEntry = computed(() => useCustomEntry.value ? customEntry.value : entryStrategy.value)
const resolvedExit = computed(() => useCustomExit.value ? customExit.value : exitStrategy.value)

const canSubmit = computed(() => {
  if (skipEntry.value && skipExit.value) return false
  if (!skipEntry.value && !resolvedEntry.value) return false
  if (!skipExit.value && !resolvedExit.value) return false
  return true
})

// Pre-populate if all selected positions share the same strategy
watch([() => props.positions, isOpen], ([newPositions, modalOpen]) => {
  if (modalOpen && newPositions.length > 0) {
    const entries = new Set(newPositions.map(p => p.entryStrategy).filter(Boolean))
    const exits = new Set(newPositions.map(p => p.exitStrategy).filter(Boolean))

    if (entries.size === 1) {
      entryStrategy.value = [...entries][0]!
    } else {
      entryStrategy.value = ''
    }

    if (exits.size === 1) {
      exitStrategy.value = [...exits][0]!
    } else {
      exitStrategy.value = ''
    }

    customEntry.value = ''
    customExit.value = ''
    useCustomEntry.value = false
    useCustomExit.value = false
    skipEntry.value = false
    skipExit.value = false
  }
}, { immediate: true })

async function onSubmit() {
  loading.value = true
  try {
    const body: Record<string, string | null> = {}
    if (!skipEntry.value) body.entryStrategy = resolvedEntry.value
    if (!skipExit.value) body.exitStrategy = resolvedExit.value

    const results = await Promise.allSettled(
      props.positions.map(p =>
        $fetch(`/udgaard/api/positions/${p.portfolioId}/${p.id}/metadata`, {
          method: 'PUT',
          body
        })
      )
    )

    const succeeded = results.filter(r => r.status === 'fulfilled').length
    const failed = results.filter(r => r.status === 'rejected').length

    if (failed === 0) {
      toast.add({
        title: 'Success',
        description: `Updated strategy for ${succeeded} position${succeeded === 1 ? '' : 's'}`,
        icon: 'i-lucide-check-circle',
        color: 'success'
      })
    } else {
      toast.add({
        title: 'Partial Success',
        description: `Updated ${succeeded} of ${props.positions.length} positions. ${failed} failed.`,
        icon: 'i-lucide-alert-triangle',
        color: 'warning'
      })
    }

    emit('success')
    isOpen.value = false
  } catch (error: any) {
    console.error('Error updating positions:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to update positions',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}
</script>
