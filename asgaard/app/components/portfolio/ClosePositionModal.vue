<template>
  <UModal
    :open="isOpen"
    title="Close Position"
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
          <div class="grid grid-cols-2 gap-2 mt-2 text-xs">
            <div>
              <span class="text-gray-500">{{ position.instrumentType === 'OPTION' ? 'Contracts' : 'Quantity' }}:</span>
              <span class="font-medium ml-1">{{ position.instrumentType === 'OPTION' ? position.currentContracts : position.currentQuantity }}</span>
            </div>
            <div>
              <span class="text-gray-500">Avg Entry:</span>
              <span class="font-medium ml-1">{{ formatCurrency(position.averageEntryPrice) }}</span>
            </div>
          </div>
        </div>

        <UForm
          :state="state"
          :schema="schema"
          class="space-y-4"
          @submit="onSubmit"
        >
          <!-- Exit Price -->
          <UFormField label="Exit Price" name="exitPrice" required>
            <UInput
              v-model.number="state.exitPrice"
              type="number"
              step="0.01"
              :min="0.01"
              placeholder="Enter exit price"
            >
              <template #leading>
                <span class="text-gray-500">$</span>
              </template>
            </UInput>
          </UFormField>

          <!-- Exit Date -->
          <UFormField label="Exit Date" name="exitDate" required>
            <UInput
              v-model="state.exitDate"
              type="date"
              :max="format(new Date(), 'yyyy-MM-dd')"
            />
          </UFormField>

          <!-- Commission -->
          <UFormField label="Commission (Optional)" name="commission">
            <UInput
              v-model.number="state.commission"
              type="number"
              step="0.01"
              :min="0"
              placeholder="0.00"
            >
              <template #leading>
                <span class="text-gray-500">$</span>
              </template>
            </UInput>
          </UFormField>

          <!-- Notes -->
          <UFormField label="Exit Notes (Optional)" name="notes">
            <UTextarea
              v-model="state.notes"
              placeholder="Add any notes about closing this position"
              :rows="2"
            />
          </UFormField>

          <!-- P&L Summary -->
          <div class="p-4 bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
            <div class="text-sm font-medium mb-3">
              Position Summary
            </div>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Entry Cost:</span>
                <span class="font-medium">{{ formatCurrency(position.totalCost) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Exit Proceeds:</span>
                <span class="font-medium">{{ formatCurrency(exitProceeds) }}</span>
              </div>
              <div v-if="state.commission" class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Exit Commission:</span>
                <span class="font-medium text-red-600 dark:text-red-400">-{{ formatCurrency(state.commission) }}</span>
              </div>
              <div class="flex justify-between pt-2 border-t border-gray-300 dark:border-gray-600">
                <span class="font-semibold">Estimated P&L:</span>
                <span
                  :class="[
                    'font-bold text-lg',
                    estimatedPnL >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                  ]"
                >
                  {{ estimatedPnL >= 0 ? '+' : '' }}{{ formatCurrency(estimatedPnL) }}
                </span>
              </div>
              <div class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Return:</span>
                <span
                  :class="[
                    'font-medium',
                    estimatedReturn >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                  ]"
                >
                  {{ estimatedReturn >= 0 ? '+' : '' }}{{ estimatedReturn.toFixed(2) }}%
                </span>
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
              label="Close Position"
              icon="i-lucide-x-circle"
              color="warning"
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
import { format } from 'date-fns'
import type { Position } from '~/types'

const { formatPositionName, formatOptionDetails, formatCurrency } = usePositionFormatters()

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

const state = reactive({
  exitPrice: undefined as number | undefined,
  exitDate: format(new Date(), 'yyyy-MM-dd'),
  commission: undefined as number | undefined,
  notes: ''
})

const schema = z.object({
  exitPrice: z.number().min(0.01, 'Exit price must be greater than 0'),
  exitDate: z.string().min(1, 'Exit date is required'),
  commission: z.number().min(0).optional().nullable(),
  notes: z.string().optional()
})

// Computed values for P&L calculation
const exitProceeds = computed(() => {
  if (!state.exitPrice || !props.position) return 0

  const quantity = props.position.currentQuantity
  const multiplier = props.position.multiplier || 1
  return quantity * state.exitPrice * multiplier
})

const estimatedPnL = computed(() => {
  if (!props.position) return 0

  const proceeds = exitProceeds.value
  const commission = state.commission || 0
  const entryCost = props.position.totalCost

  return proceeds - entryCost - commission
})

const estimatedReturn = computed(() => {
  if (!props.position || props.position.totalCost === 0) return 0
  return (estimatedPnL.value / props.position.totalCost) * 100
})

// Reset form when modal opens
watch(isOpen, (newValue) => {
  if (newValue) {
    state.exitPrice = undefined
    state.exitDate = format(new Date(), 'yyyy-MM-dd')
    state.commission = undefined
    state.notes = ''
  }
})

async function onSubmit() {
  if (!props.position) return

  loading.value = true
  try {
    await $fetch(`/udgaard/api/positions/${props.position.portfolioId}/${props.position.id}/close`, {
      method: 'PUT',
      body: {
        closeDate: state.exitDate,
        closePrice: state.exitPrice,
        commission: state.commission || null,
        notes: state.notes || null
      }
    })

    toast.add({
      title: 'Success',
      description: `Position closed with ${estimatedPnL.value >= 0 ? 'profit' : 'loss'} of ${formatCurrency(Math.abs(estimatedPnL.value))}`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    emit('success')
    isOpen.value = false
  } catch (error: any) {
    console.error('Error closing position:', error)
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to close position',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}
</script>
