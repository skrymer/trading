<script setup lang="ts">
import { format } from 'date-fns'
import { z } from 'zod'
import type { ScanResult, AddScannerTradeRequest, OptionContractResponse, LatestQuote } from '~/types'

const props = defineProps<{
  scanResult: ScanResult | null
  entryStrategyName: string
  exitStrategyName: string
  calculatedQuantity?: number
  optionContract?: OptionContractResponse
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
const liveQuote = ref<LatestQuote | null>(null)
const quoteFetching = ref(false)

const state = reactive({
  instrumentType: 'STOCK' as string,
  quantity: 100,
  optionType: '' as string,
  strikePrice: undefined as number | undefined,
  expirationDate: '',
  optionPrice: undefined as number | undefined,
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
  { label: 'ETF', value: 'ETF' },
  { label: 'Leveraged ETF', value: 'LEVERAGED_ETF' }
]

const optionTypeOptions = [
  { label: 'Call', value: 'CALL' },
  { label: 'Put', value: 'PUT' }
]

const entryPrice = computed(() => liveQuote.value?.price ?? props.scanResult?.closePrice ?? 0)
const priceChange = computed(() => liveQuote.value?.change ?? null)
const priceChangePercent = computed(() => liveQuote.value?.changePercent ?? null)

// Reset form and fetch live quote when modal opens
watch(isOpen, async (newValue) => {
  if (newValue) {
    if (props.optionContract) {
      state.instrumentType = 'OPTION'
      state.optionType = 'CALL'
      state.strikePrice = props.optionContract.strike
      state.expirationDate = props.optionContract.expiration
      state.optionPrice = props.optionContract.price
    } else {
      state.instrumentType = 'STOCK'
      state.optionType = ''
      state.strikePrice = undefined
      state.expirationDate = ''
      state.optionPrice = undefined
    }
    state.quantity = (props.calculatedQuantity && props.calculatedQuantity > 0) ? props.calculatedQuantity : (props.optionContract ? 1 : 100)
    state.notes = ''

    liveQuote.value = null
    if (props.scanResult) {
      quoteFetching.value = true
      try {
        liveQuote.value = await $fetch<LatestQuote>(`/udgaard/api/stocks/${props.scanResult.symbol}/latest-quote`)
      } catch {
        // Fall back to closePrice — shown in UI as "Prev Close"
      } finally {
        quoteFetching.value = false
      }
    }
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
      entryPrice: entryPrice.value,
      entryDate: liveQuote.value ? format(new Date(), 'yyyy-MM-dd') : props.scanResult.date,
      quantity: state.quantity,
      entryStrategyName: props.entryStrategyName,
      exitStrategyName: props.exitStrategyName,
      notes: state.notes || undefined
    }

    if (state.instrumentType === 'OPTION') {
      body.optionType = state.optionType
      body.strikePrice = state.strikePrice
      body.expirationDate = state.expirationDate
      body.optionPrice = state.optionPrice
      body.delta = props.optionContract?.delta
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
              <span class="text-muted">{{ liveQuote ? 'Live:' : 'Prev Close:' }}</span>
              <UIcon v-if="quoteFetching" name="i-lucide-loader-2" class="ml-1 size-3.5 animate-spin" />
              <template v-else>
                <span class="font-medium ml-1">${{ entryPrice.toFixed(2) }}</span>
                <span
                  v-if="priceChange !== null"
                  class="ml-1 text-xs"
                  :class="priceChange >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                >
                  {{ priceChange >= 0 ? '+' : '' }}{{ priceChange.toFixed(2) }} ({{ priceChangePercent!.toFixed(2) }}%)
                </span>
              </template>
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
          <div v-if="optionContract" class="grid grid-cols-3 gap-2 mt-2 text-sm border-t border-default pt-2">
            <div>
              <span class="text-muted">Strike:</span>
              <span class="font-medium ml-1">${{ optionContract.strike.toFixed(0) }}</span>
            </div>
            <div>
              <span class="text-muted">Delta:</span>
              <span class="font-medium ml-1">{{ optionContract.delta.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">Extr:</span>
              <span class="font-medium ml-1">${{ optionContract.extrinsic.toFixed(2) }}</span>
            </div>
          </div>
        </div>

        <UForm
          :state="state"
          :schema="schema"
          @submit="onSubmit"
        >
          <div class="grid grid-cols-2 gap-x-6 gap-y-4">
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

              <UFormField label="Option Price" required>
                <UInput
                  v-model.number="state.optionPrice"
                  type="number"
                  step="0.01"
                  :min="0.01"
                >
                  <template #leading>
                    <span class="text-muted">$</span>
                  </template>
                </UInput>
              </UFormField>

              <UFormField label="Strike Price" required>
                <UInput
                  v-model.number="state.strikePrice"
                  type="number"
                  step="any"
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
            </template>

            <UFormField label="Notes (optional)" name="notes" class="col-span-2">
              <UTextarea v-model="state.notes" placeholder="Add notes..." :rows="2" />
            </UFormField>
          </div>

          <div class="flex justify-end gap-2 pt-4">
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
