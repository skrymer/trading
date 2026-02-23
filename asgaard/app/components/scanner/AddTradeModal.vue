<script setup lang="ts">
import { z } from 'zod'
import type { ScanResult, AddScannerTradeRequest } from '~/types'

const props = defineProps<{
  scanResult: ScanResult | null
  entryStrategyName: string
  exitStrategyName: string
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
  instrumentType: 'STOCK' as string,
  quantity: 100,
  optionType: '' as string,
  strikePrice: undefined as number | undefined,
  expirationDate: '',
  multiplier: 100,
  notes: ''
})

const schema = z.object({
  instrumentType: z.string().min(1),
  quantity: z.number().min(1, 'Quantity must be at least 1'),
  notes: z.string().optional()
})

const instrumentTypeOptions = [
  { label: 'Stock', value: 'STOCK' },
  { label: 'Option', value: 'OPTION' },
  { label: 'Leveraged ETF', value: 'LEVERAGED_ETF' }
]

const optionTypeOptions = [
  { label: 'Call', value: 'CALL' },
  { label: 'Put', value: 'PUT' }
]

// Reset form when modal opens
watch(isOpen, (newValue) => {
  if (newValue) {
    state.instrumentType = 'STOCK'
    state.quantity = 100
    state.optionType = ''
    state.strikePrice = undefined
    state.expirationDate = ''
    state.multiplier = 100
    state.notes = ''
  }
})

async function onSubmit() {
  if (!props.scanResult) return

  loading.value = true
  try {
    const body: AddScannerTradeRequest = {
      symbol: props.scanResult.symbol,
      sectorSymbol: props.scanResult.sectorSymbol ?? undefined,
      instrumentType: state.instrumentType,
      entryPrice: props.scanResult.closePrice,
      entryDate: props.scanResult.date,
      quantity: state.quantity,
      entryStrategyName: props.entryStrategyName,
      exitStrategyName: props.exitStrategyName,
      notes: state.notes || undefined
    }

    if (state.instrumentType === 'OPTION') {
      body.optionType = state.optionType
      body.strikePrice = state.strikePrice
      body.expirationDate = state.expirationDate
      body.multiplier = state.multiplier
    }

    await $fetch('/udgaard/api/scanner/trades', {
      method: 'POST',
      body
    })

    toast.add({
      title: 'Trade Added',
      description: `Added ${props.scanResult.symbol} to scanner trades`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })

    emit('success')
    isOpen.value = false
  } catch (error: any) {
    toast.add({
      title: 'Error',
      description: error.data?.message || 'Failed to add trade',
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
    title="Add Scanner Trade"
    size="lg"
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div v-if="scanResult" class="space-y-4">
        <!-- Stock Info -->
        <div class="p-3 bg-muted/50 rounded-lg">
          <div class="flex items-center justify-between">
            <span class="font-semibold text-lg">{{ scanResult.symbol }}</span>
            <UBadge v-if="scanResult.sectorSymbol" variant="subtle" size="sm">
              {{ scanResult.sectorSymbol }}
            </UBadge>
          </div>
          <div class="grid grid-cols-3 gap-2 mt-2 text-sm">
            <div>
              <span class="text-muted">Price:</span>
              <span class="font-medium ml-1">${{ scanResult.closePrice.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">ATR:</span>
              <span class="font-medium ml-1">{{ scanResult.atr.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">Trend:</span>
              <span class="font-medium ml-1">{{ scanResult.trend }}</span>
            </div>
          </div>
        </div>

        <UForm
          :state="state"
          :schema="schema"
          class="space-y-4"
          @submit="onSubmit"
        >
          <UFormField label="Instrument Type" name="instrumentType" required>
            <USelect
              v-model="state.instrumentType"
              :items="instrumentTypeOptions"
              value-key="value"
            />
          </UFormField>

          <UFormField label="Quantity" name="quantity" required>
            <UInput v-model.number="state.quantity" type="number" :min="1" />
          </UFormField>

          <!-- Option fields -->
          <template v-if="state.instrumentType === 'OPTION'">
            <UFormField label="Option Type" required>
              <USelect
                v-model="state.optionType"
                :items="optionTypeOptions"
                value-key="value"
              />
            </UFormField>

            <div class="grid grid-cols-2 gap-4">
              <UFormField label="Strike Price" required>
                <UInput
                  v-model.number="state.strikePrice"
                  type="number"
                  step="0.50"
                  :min="0.01"
                >
                  <template #leading>
                    <span class="text-muted">$</span>
                  </template>
                </UInput>
              </UFormField>

              <UFormField label="Expiration Date" required>
                <UInput v-model="state.expirationDate" type="date" />
              </UFormField>
            </div>

            <UFormField label="Multiplier">
              <UInput v-model.number="state.multiplier" type="number" :min="1" />
            </UFormField>
          </template>

          <UFormField label="Notes (optional)" name="notes">
            <UTextarea v-model="state.notes" placeholder="Add notes..." :rows="2" />
          </UFormField>

          <div class="flex justify-end gap-2 pt-2">
            <UButton label="Cancel" variant="outline" @click="isOpen = false" />
            <UButton
              type="submit"
              label="Add Trade"
              icon="i-lucide-plus"
              :loading="loading"
            />
          </div>
        </UForm>
      </div>
    </template>
  </UModal>
</template>
