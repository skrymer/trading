<script setup lang="ts">
import { format } from 'date-fns'
import { z } from 'zod'
import type { InstrumentType, OptionType, PortfolioTrade } from '~/types'
import type { FormSubmitEvent } from '@nuxt/ui'

const props = defineProps<{
  open: boolean
  trade: PortfolioTrade
  currency: string
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'update-trade': [data: {
    tradeId: string
    symbol: string
    entryPrice: number
    entryDate: string
    quantity: number
    entryStrategy: string
    exitStrategy: string
    underlyingSymbol?: string
    instrumentType: InstrumentType
    optionType?: OptionType
    strikePrice?: number
    expirationDate?: string
    contracts?: number
    multiplier?: number
    entryIntrinsicValue?: number
    entryExtrinsicValue?: number
  }]
}>()

// Initialize state with trade data
const state = reactive({
  instrumentType: props.trade.instrumentType as InstrumentType,
  symbol: props.trade.symbol,
  entryPrice: props.trade.entryPrice,
  entryDate: format(new Date(props.trade.entryDate), 'yyyy-MM-dd'),
  quantity: props.trade.quantity,
  entryStrategy: props.trade.entryStrategy,
  exitStrategy: props.trade.exitStrategy,
  underlyingSymbol: props.trade.underlyingSymbol || '',
  optionType: (props.trade.optionType || 'CALL') as OptionType,
  strikePrice: props.trade.strikePrice || 0,
  expirationDate: props.trade.expirationDate ? format(new Date(props.trade.expirationDate), 'yyyy-MM-dd') : '',
  contracts: props.trade.contracts || 1,
  multiplier: props.trade.multiplier || 100,
  entryIntrinsicValue: props.trade.entryIntrinsicValue,
  entryExtrinsicValue: props.trade.entryExtrinsicValue
})

// Validation schema
const schema = computed(() => {
  const baseSchema = z.object({
    instrumentType: z.enum(['STOCK', 'OPTION', 'LEVERAGED_ETF']),
    symbol: z.string().min(1, 'Symbol is required').max(10, 'Symbol too long'),
    entryPrice: z.number().positive('Entry price must be greater than 0'),
    entryDate: z.string().min(1, 'Entry date is required'),
    entryStrategy: z.string().min(1, 'Entry strategy is required'),
    exitStrategy: z.string().min(1, 'Exit strategy is required'),
    underlyingSymbol: z.string().optional()
  })

  if (state.instrumentType === 'OPTION') {
    return baseSchema.extend({
      optionType: z.enum(['CALL', 'PUT'] as const, { message: 'Option type is required' }),
      strikePrice: z.number().positive('Strike price must be greater than 0'),
      expirationDate: z.string().min(1, 'Expiration date is required'),
      contracts: z.number().int().positive('Contracts must be at least 1'),
      multiplier: z.number().int().positive('Multiplier must be at least 1'),
      entryIntrinsicValue: z.number().nonnegative().nullish(),
      entryExtrinsicValue: z.number().nonnegative().nullish()
    })
  } else {
    return baseSchema.extend({
      quantity: z.number().int().positive('Quantity must be at least 1')
    })
  }
})

type Schema = z.output<typeof schema.value>

// Fetch strategies from backend
const entryStrategies = ref<string[]>([])
const exitStrategies = ref<string[]>([])
const loadingStrategies = ref(true)

async function loadStrategies() {
  try {
    const response = await $fetch<{ entryStrategies: string[], exitStrategies: string[] }>('/udgaard/api/backtest/strategies')
    // Sort alphabetically
    entryStrategies.value = response.entryStrategies.sort((a, b) => a.localeCompare(b))
    exitStrategies.value = response.exitStrategies.sort((a, b) => a.localeCompare(b))
  } catch (error) {
    console.error('Failed to load strategies:', error)
    entryStrategies.value = []
    exitStrategies.value = []
  } finally {
    loadingStrategies.value = false
  }
}

// Load strategies when component is mounted
onMounted(() => {
  loadStrategies()
})

// Computed total cost
const totalCost = computed(() => {
  if (state.instrumentType === 'OPTION') {
    return state.entryPrice * state.contracts * state.multiplier
  }
  return state.entryPrice * state.quantity
})

async function onSubmit(event: FormSubmitEvent<Schema>) {
  const data = event.data as any
  emit('update-trade', {
    tradeId: props.trade.id!,
    symbol: data.symbol.toUpperCase(),
    entryPrice: data.entryPrice,
    entryDate: data.entryDate,
    quantity: state.instrumentType === 'OPTION' ? state.contracts : data.quantity,
    entryStrategy: data.entryStrategy,
    exitStrategy: data.exitStrategy,
    underlyingSymbol: data.underlyingSymbol ? data.underlyingSymbol.toUpperCase() : undefined,
    instrumentType: data.instrumentType,
    optionType: state.instrumentType === 'OPTION' ? data.optionType : undefined,
    strikePrice: state.instrumentType === 'OPTION' ? data.strikePrice : undefined,
    expirationDate: state.instrumentType === 'OPTION' ? data.expirationDate : undefined,
    contracts: state.instrumentType === 'OPTION' ? data.contracts : undefined,
    multiplier: state.instrumentType === 'OPTION' ? data.multiplier : undefined,
    entryIntrinsicValue: state.instrumentType === 'OPTION' ? state.entryIntrinsicValue : undefined,
    entryExtrinsicValue: state.instrumentType === 'OPTION' ? state.entryExtrinsicValue : undefined
  })
}

function handleClose() {
  if (props.loading) return
  emit('update:open', false)
}

// Reset form when trade changes
watch(() => props.trade, (newTrade) => {
  if (newTrade && props.open) {
    state.instrumentType = newTrade.instrumentType as InstrumentType
    state.symbol = newTrade.symbol
    state.entryPrice = newTrade.entryPrice
    state.entryDate = format(new Date(newTrade.entryDate), 'yyyy-MM-dd')
    state.quantity = newTrade.quantity
    state.entryStrategy = newTrade.entryStrategy
    state.exitStrategy = newTrade.exitStrategy
    state.underlyingSymbol = newTrade.underlyingSymbol || ''
    state.optionType = (newTrade.optionType || 'CALL') as OptionType
    state.strikePrice = newTrade.strikePrice || 0
    state.expirationDate = newTrade.expirationDate ? format(new Date(newTrade.expirationDate), 'yyyy-MM-dd') : ''
    state.contracts = newTrade.contracts || 1
    state.multiplier = newTrade.multiplier || 100
    state.entryIntrinsicValue = newTrade.entryIntrinsicValue
    state.entryExtrinsicValue = newTrade.entryExtrinsicValue
  }
})
</script>

<template>
  <UModal
    :open="open"
    title="Edit Trade"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <UForm
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <!-- Instrument Type Selection -->
        <UFormField label="Instrument Type" name="instrumentType" required>
          <URadioGroup
            v-model="state.instrumentType"
            :items="[
              { value: 'STOCK', label: 'Stock' },
              { value: 'OPTION', label: 'Option' },
              { value: 'LEVERAGED_ETF', label: 'Leveraged ETF' }
            ]"
          />
        </UFormField>

        <UFormField :label="state.instrumentType === 'OPTION' ? 'Underlying Symbol' : 'Stock Symbol'" name="symbol" required>
          <UInput
            v-model="state.symbol"
            :placeholder="state.instrumentType === 'OPTION' ? 'SPY' : 'AAPL'"
            class="uppercase"
          />
        </UFormField>

        <!-- Options-specific fields -->
        <div v-if="state.instrumentType === 'OPTION'" class="space-y-4 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
          <div class="grid grid-cols-2 gap-4">
            <UFormField label="Option Type" name="optionType" required>
              <URadioGroup
                v-model="state.optionType"
                :items="[
                  { value: 'CALL', label: 'Call' },
                  { value: 'PUT', label: 'Put' }
                ]"
              />
            </UFormField>

            <UFormField label="Strike Price" name="strikePrice" required>
              <UInput
                v-model.number="state.strikePrice"
                type="number"
                min="0"
                step="0.01"
                placeholder="450.00"
              />
            </UFormField>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <UFormField label="Expiration Date" name="expirationDate" required>
              <UInput
                v-model="state.expirationDate"
                type="date"
              />
            </UFormField>

            <UFormField label="Contracts" name="contracts" required>
              <UInput
                v-model.number="state.contracts"
                type="number"
                min="1"
                placeholder="1"
              />
            </UFormField>
          </div>

          <UFormField label="Contract Multiplier" name="multiplier">
            <UInput
              v-model.number="state.multiplier"
              type="number"
              min="1"
              placeholder="100"
            />
            <template #help>
              <span class="text-xs text-muted">Standard is 100 shares per contract</span>
            </template>
          </UFormField>

          <div class="grid grid-cols-2 gap-4">
            <UFormField label="Intrinsic Value (Optional)" name="entryIntrinsicValue">
              <UInput
                v-model.number="state.entryIntrinsicValue"
                type="number"
                min="0"
                step="0.01"
                placeholder="0.00"
              />
            </UFormField>

            <UFormField label="Extrinsic Value (Optional)" name="entryExtrinsicValue">
              <UInput
                v-model.number="state.entryExtrinsicValue"
                type="number"
                min="0"
                step="0.01"
                placeholder="0.00"
              />
            </UFormField>
          </div>
        </div>

        <!-- Stock-specific Underlying Symbol field -->
        <UFormField v-if="state.instrumentType !== 'OPTION'" label="Underlying Symbol (Optional)" name="underlyingSymbol">
          <UInput
            v-model="state.underlyingSymbol"
            placeholder="e.g., QQQ for TQQQ"
            class="uppercase"
          />
          <template #help>
            <span class="text-xs text-muted">
              For leveraged ETFs: Use the underlying asset's signals for strategy evaluation.
              Leave blank for automatic detection.
            </span>
          </template>
        </UFormField>

        <div class="grid grid-cols-2 gap-4">
          <UFormField :label="state.instrumentType === 'OPTION' ? 'Entry Premium' : 'Entry Price'" name="entryPrice" required>
            <UInput
              v-model.number="state.entryPrice"
              type="number"
              min="0"
              step="0.01"
            />
          </UFormField>

          <UFormField
            v-if="state.instrumentType !== 'OPTION'"
            label="Quantity"
            name="quantity"
            required
          >
            <UInput
              v-model.number="state.quantity"
              type="number"
              min="1"
            />
          </UFormField>
        </div>

        <UFormField label="Entry Date" name="entryDate" required>
          <UInput
            v-model="state.entryDate"
            type="date"
          />
        </UFormField>

        <UFormField label="Entry Strategy" name="entryStrategy" required>
          <USelect
            v-model="state.entryStrategy"
            :items="entryStrategies"
            :loading="loadingStrategies"
            :disabled="loadingStrategies"
            placeholder="Select entry strategy..."
          />
        </UFormField>

        <UFormField label="Exit Strategy" name="exitStrategy" required>
          <USelect
            v-model="state.exitStrategy"
            :items="exitStrategies"
            :loading="loadingStrategies"
            :disabled="loadingStrategies"
            placeholder="Select exit strategy..."
          />
        </UFormField>

        <div class="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
          <p class="text-sm text-muted">
            Total Cost: {{ totalCost.toFixed(2) }} {{ currency }}
          </p>
          <p v-if="state.instrumentType === 'OPTION'" class="text-xs text-muted mt-1">
            {{ state.contracts }} contract{{ state.contracts !== 1 ? 's' : '' }} × {{ state.multiplier }} shares × ${{ state.entryPrice.toFixed(2) }} premium
          </p>
        </div>

        <div class="flex justify-end gap-2">
          <UButton
            label="Cancel"
            color="neutral"
            variant="outline"
            :disabled="loading"
            @click="handleClose"
          />
          <UButton
            type="submit"
            label="Update Trade"
            icon="i-lucide-save"
            :loading="loading"
            :disabled="loading"
          />
        </div>
      </UForm>
    </template>
  </UModal>
</template>
