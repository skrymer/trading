<script setup lang="ts">
import { z } from 'zod'
import { format } from 'date-fns'
import type { ScannerTrade, RollScannerTradeRequest } from '~/types'

const props = defineProps<{
  trade: ScannerTrade | null
}>()

const emit = defineEmits<{
  success: []
}>()

const model = defineModel<boolean>()

const isOpen = computed({
  get: () => model.value ?? false,
  set: (value) => { model.value = value }
})

const toast = useToast()
const loading = ref(false)

const state = reactive({
  closePrice: undefined as number | undefined,
  newStrikePrice: undefined as number | undefined,
  newExpirationDate: '',
  newOptionType: '' as string,
  newEntryPrice: undefined as number | undefined,
  newEntryDate: format(new Date(), 'yyyy-MM-dd'),
  newQuantity: 1
})

const schema = z.object({
  closePrice: z.number().min(0, 'Close price is required'),
  newStrikePrice: z.number().min(0.01, 'Strike price is required'),
  newExpirationDate: z.string().min(1, 'Expiration date is required'),
  newEntryPrice: z.number().min(0, 'Entry price is required'),
  newEntryDate: z.string().min(1, 'Entry date is required'),
  newQuantity: z.number().min(1, 'Quantity must be at least 1')
})

const rollCredit = computed(() => {
  if (!props.trade || state.closePrice === undefined) return 0
  return (state.closePrice - props.trade.entryPrice) * props.trade.quantity * props.trade.multiplier
})

const totalRolledCredits = computed(() => {
  if (!props.trade) return 0
  return props.trade.rolledCredits + rollCredit.value
})

// Reset form when modal opens
watch(isOpen, (newValue) => {
  if (newValue && props.trade) {
    state.closePrice = undefined
    state.newStrikePrice = undefined
    state.newExpirationDate = ''
    state.newOptionType = props.trade.optionType ?? ''
    state.newEntryPrice = undefined
    state.newEntryDate = format(new Date(), 'yyyy-MM-dd')
    state.newQuantity = props.trade.quantity
  }
})

async function onSubmit() {
  if (!props.trade) return

  loading.value = true
  try {
    const body: RollScannerTradeRequest = {
      closePrice: state.closePrice!,
      newStrikePrice: state.newStrikePrice!,
      newExpirationDate: state.newExpirationDate,
      newOptionType: state.newOptionType || undefined,
      newEntryPrice: state.newEntryPrice!,
      newEntryDate: state.newEntryDate,
      newQuantity: state.newQuantity
    }

    await $fetch(`/udgaard/api/scanner/trades/${props.trade.id}/roll`, {
      method: 'POST',
      body
    })

    toast.add({
      title: 'Trade Rolled',
      description: `Rolled ${props.trade.symbol} — credit: $${rollCredit.value.toFixed(2)}`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    emit('success')
    isOpen.value = false
  } catch (error: any) {
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to roll trade',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <UModal
    :open="isOpen"
    title="Roll Trade"
    size="lg"
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div v-if="trade" class="space-y-4">
        <!-- Current Trade Info -->
        <div class="p-3 bg-muted/50 rounded-lg">
          <div class="font-semibold mb-1">
            {{ trade.symbol }} — Current Position
          </div>
          <div class="grid grid-cols-3 gap-2 text-sm">
            <div>
              <span class="text-muted">Entry:</span>
              <span class="font-medium ml-1">${{ trade.entryPrice.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">Strike:</span>
              <span class="font-medium ml-1">${{ trade.strikePrice?.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">Rolls:</span>
              <span class="font-medium ml-1">{{ trade.rollCount }}</span>
            </div>
          </div>
        </div>

        <UForm
          :state="state"
          :schema="schema"
          class="space-y-4"
          @submit="onSubmit"
        >
          <!-- Close price for old leg -->
          <UFormField label="Close Price (Old Leg)" name="closePrice" required>
            <UInput
              v-model.number="state.closePrice"
              type="number"
              step="0.01"
              :min="0"
            >
              <template #leading>
                <span class="text-muted">$</span>
              </template>
            </UInput>
          </UFormField>

          <!-- New option details -->
          <div class="grid grid-cols-2 gap-4">
            <UFormField label="New Strike Price" name="newStrikePrice" required>
              <UInput
                v-model.number="state.newStrikePrice"
                type="number"
                step="0.50"
                :min="0.01"
              >
                <template #leading>
                  <span class="text-muted">$</span>
                </template>
              </UInput>
            </UFormField>

            <UFormField label="New Expiration" name="newExpirationDate" required>
              <UInput v-model="state.newExpirationDate" type="date" />
            </UFormField>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <UFormField label="New Entry Price" name="newEntryPrice" required>
              <UInput
                v-model.number="state.newEntryPrice"
                type="number"
                step="0.01"
                :min="0"
              >
                <template #leading>
                  <span class="text-muted">$</span>
                </template>
              </UInput>
            </UFormField>

            <UFormField label="New Entry Date" name="newEntryDate" required>
              <UInput v-model="state.newEntryDate" type="date" />
            </UFormField>
          </div>

          <UFormField label="New Quantity" name="newQuantity" required>
            <UInput v-model.number="state.newQuantity" type="number" :min="1" />
          </UFormField>

          <!-- Roll Credit Summary -->
          <div class="p-4 bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800 rounded-lg border border-default">
            <div class="text-sm font-medium mb-2">
              Roll Summary
            </div>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">This Roll Credit:</span>
                <span
                  :class="[
                    'font-medium',
                    rollCredit >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                  ]"
                >
                  {{ rollCredit >= 0 ? '+' : '' }}${{ rollCredit.toFixed(2) }}
                </span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Previous Credits:</span>
                <span class="font-medium">${{ trade.rolledCredits.toFixed(2) }}</span>
              </div>
              <div class="flex justify-between pt-1 border-t border-default">
                <span class="font-semibold">Total Rolled Credits:</span>
                <span
                  :class="[
                    'font-bold',
                    totalRolledCredits >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                  ]"
                >
                  {{ totalRolledCredits >= 0 ? '+' : '' }}${{ totalRolledCredits.toFixed(2) }}
                </span>
              </div>
            </div>
          </div>

          <div class="flex justify-end gap-2 pt-2">
            <UButton label="Cancel" variant="outline" @click="isOpen = false" />
            <UButton
              type="submit"
              label="Roll Trade"
              icon="i-lucide-refresh-cw"
              :loading="loading"
            />
          </div>
        </UForm>
      </div>
    </template>
  </UModal>
</template>
