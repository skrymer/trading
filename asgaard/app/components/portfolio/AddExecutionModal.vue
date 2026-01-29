<template>
  <UModal
    :open="isOpen"
    title="Add Execution"
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
            Current {{ position.instrumentType === 'OPTION' ? 'Contracts' : 'Quantity' }}: {{ position.instrumentType === 'OPTION' ? position.currentContracts : position.currentQuantity }}
          </div>
        </div>

        <UForm
          :state="state"
          :schema="schema"
          class="space-y-4"
          @submit="onSubmit"
        >
          <!-- Execution Type -->
          <UFormField label="Type" name="type" required>
            <URadioGroup
              v-model="state.type"
              :options="[
                { label: 'Buy', value: 'BUY' },
                { label: 'Sell', value: 'SELL' }
              ]"
            />
          </UFormField>

          <!-- Quantity -->
          <UFormField
            :label="position.instrumentType === 'OPTION' ? 'Contracts' : 'Quantity'"
            name="quantity"
            required
          >
            <UInput
              v-model.number="state.quantity"
              type="number"
              :min="1"
              placeholder="Enter quantity"
            />
          </UFormField>

          <!-- Price -->
          <UFormField label="Price" name="price" required>
            <UInput
              v-model.number="state.price"
              type="number"
              step="0.01"
              :min="0.01"
              placeholder="Enter price"
            >
              <template #leading>
                <span class="text-gray-500">$</span>
              </template>
            </UInput>
          </UFormField>

          <!-- Execution Date -->
          <UFormField label="Execution Date" name="executionDate" required>
            <UInput
              v-model="state.executionDate"
              type="date"
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
          <UFormField label="Notes (Optional)" name="notes">
            <UTextarea
              v-model="state.notes"
              placeholder="Add any notes about this execution"
              :rows="3"
            />
          </UFormField>

          <!-- Summary -->
          <div class="p-3 bg-blue-50 dark:bg-blue-950 rounded-lg border border-blue-200 dark:border-blue-800">
            <div class="text-sm font-medium mb-2">
              Execution Summary
            </div>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Action:</span>
                <span class="font-medium">{{ state.type }} {{ state.quantity || 0 }} {{ position.instrumentType === 'OPTION' ? 'contracts' : 'shares' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Price per unit:</span>
                <span class="font-medium">{{ formatCurrency(state.price || 0) }}</span>
              </div>
              <div v-if="state.commission" class="flex justify-between">
                <span class="text-gray-600 dark:text-gray-400">Commission:</span>
                <span class="font-medium">{{ formatCurrency(state.commission) }}</span>
              </div>
              <div class="flex justify-between pt-2 border-t border-blue-200 dark:border-blue-800">
                <span class="text-gray-600 dark:text-gray-400">Total Cost:</span>
                <span class="font-semibold">{{ formatCurrency(totalCost) }}</span>
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
              label="Add Execution"
              icon="i-lucide-plus"
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
  type: 'BUY' as 'BUY' | 'SELL',
  quantity: undefined as number | undefined,
  price: undefined as number | undefined,
  executionDate: format(new Date(), 'yyyy-MM-dd'),
  commission: undefined as number | undefined,
  notes: ''
})

const schema = z.object({
  type: z.enum(['BUY', 'SELL']),
  quantity: z.number().min(1, 'Quantity must be at least 1'),
  price: z.number().min(0.01, 'Price must be greater than 0'),
  executionDate: z.string().min(1, 'Execution date is required'),
  commission: z.number().min(0).optional().nullable(),
  notes: z.string().optional()
})

// Computed total cost
const totalCost = computed(() => {
  if (!state.quantity || !state.price || !props.position) return 0

  const multiplier = props.position.multiplier || 1
  const cost = state.quantity * state.price * multiplier
  const commission = state.commission || 0

  return state.type === 'BUY' ? cost + commission : -(cost - commission)
})

// Reset form when modal opens
watch(isOpen, (newValue) => {
  if (newValue) {
    state.type = 'BUY'
    state.quantity = undefined
    state.price = undefined
    state.executionDate = format(new Date(), 'yyyy-MM-dd')
    state.commission = undefined
    state.notes = ''
  }
})

async function onSubmit() {
  if (!props.position) return

  loading.value = true
  try {
    // Convert quantity to signed value (negative for SELL)
    const signedQuantity = state.type === 'SELL' ? -state.quantity! : state.quantity!

    await $fetch(`/udgaard/api/positions/${props.position.portfolioId}/${props.position.id}/executions`, {
      method: 'POST',
      body: {
        quantity: signedQuantity,
        price: state.price,
        executionDate: state.executionDate,
        commission: state.commission || null,
        notes: state.notes || null
      }
    })

    toast.add({
      title: 'Success',
      description: 'Execution added successfully',
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    emit('success')
    isOpen.value = false
  } catch (error: any) {
    console.error('Error adding execution:', error)
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to add execution',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}
</script>
