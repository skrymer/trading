<script setup lang="ts">
import { format } from 'date-fns'
import { z } from 'zod'
import type { InstrumentType, OptionType } from '~/types'
import type { FormSubmitEvent } from '@nuxt/ui'

const props = defineProps<{
  open: boolean
  currency: string
  currentBalance: number
  portfolioCreatedDate?: string
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'open-trade': [data: {
    symbol: string
    entryPrice: number
    entryDate: string
    quantity: number
    entryStrategy: string
    exitStrategy: string
    currency: string
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

const state = reactive({
  instrumentType: 'STOCK' as InstrumentType,
  symbol: '',
  entryPrice: 0,
  entryDate: format(new Date(), 'yyyy-MM-dd'),
  quantity: 100,
  entryStrategy: '',
  exitStrategy: '',
  underlyingSymbol: '',
  optionType: 'CALL' as OptionType,
  strikePrice: 0,
  expirationDate: '',
  contracts: 1,
  multiplier: 100,
  entryIntrinsicValue: undefined as number | undefined,
  entryExtrinsicValue: undefined as number | undefined
})

// Validation schema
const schema = computed(() => {
  const baseSchema = z.object({
    instrumentType: z.enum(['STOCK', 'OPTION', 'LEVERAGED_ETF']),
    symbol: z.string().min(1, 'Symbol is required').max(10, 'Symbol too long'),
    entryPrice: z.number().positive('Entry price must be greater than 0'),
    entryDate: z.string().min(1, 'Entry date is required').refine(
      (date) => {
        if (!props.portfolioCreatedDate) return true
        const entryDate = new Date(date)
        const createdDate = new Date(props.portfolioCreatedDate)
        // Extract just the date parts (ignore time)
        const entryDateOnly = new Date(entryDate.getFullYear(), entryDate.getMonth(), entryDate.getDate())
        const createdDateOnly = new Date(createdDate.getFullYear(), createdDate.getMonth(), createdDate.getDate())
        return entryDateOnly >= createdDateOnly
      },
      {
        message: props.portfolioCreatedDate
          ? `Entry date cannot be before portfolio creation date (${format(new Date(props.portfolioCreatedDate), 'yyyy-MM-dd')})`
          : 'Invalid entry date'
      }
    ),
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
    // Fallback to empty arrays - user can still type manually
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

// Set default strategies when they're loaded
watch([entryStrategies, exitStrategies], ([entry, exit]) => {
  if (entry.length > 0 && !state.entryStrategy) {
    state.entryStrategy = entry[0] || ''
  }
  if (exit.length > 0 && !state.exitStrategy) {
    state.exitStrategy = exit[0] || ''
  }
})

// Computed total cost
const totalCost = computed(() => {
  if (state.instrumentType === 'OPTION') {
    return state.entryPrice * state.contracts * state.multiplier
  }
  return state.entryPrice * state.quantity
})

// Check if position exceeds available balance
const exceedsBalance = computed(() => {
  return totalCost.value > props.currentBalance
})

// Format currency helper
function formatCurrency(value: number, currency: string = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency
  }).format(value)
}

async function onSubmit(event: FormSubmitEvent<Schema>) {
  const data = event.data as any
  emit('open-trade', {
    symbol: data.symbol.toUpperCase(),
    entryPrice: data.entryPrice,
    entryDate: data.entryDate,
    quantity: state.instrumentType === 'OPTION' ? state.contracts : data.quantity,
    entryStrategy: data.entryStrategy,
    exitStrategy: data.exitStrategy,
    currency: props.currency,
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

// Reset form when modal closes
watch(() => props.open, (isOpen) => {
  if (!isOpen && !props.loading) {
    state.instrumentType = 'STOCK'
    state.symbol = ''
    state.entryPrice = 0
    state.entryDate = format(new Date(), 'yyyy-MM-dd')
    state.quantity = 100
    state.entryStrategy = entryStrategies.value[0] || ''
    state.exitStrategy = exitStrategies.value[0] || ''
    state.underlyingSymbol = ''
    state.optionType = 'CALL'
    state.strikePrice = 0
    state.expirationDate = ''
    state.contracts = 1
    state.multiplier = 100
    state.entryIntrinsicValue = undefined
    state.entryExtrinsicValue = undefined
  }
})
</script>

<template>
  <UModal
    :open="open"
    title="Open Trade"
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
          <div class="flex items-center justify-between">
            <p class="text-sm text-muted">
              Total Cost:
            </p>
            <p class="text-sm font-semibold" :class="exceedsBalance ? 'text-red-600' : ''">
              {{ formatCurrency(totalCost, currency) }}
            </p>
          </div>
          <div class="flex items-center justify-between mt-1">
            <p class="text-sm text-muted">
              Available Balance:
            </p>
            <p class="text-sm font-semibold">
              {{ formatCurrency(currentBalance, currency) }}
            </p>
          </div>
          <p v-if="state.instrumentType === 'OPTION'" class="text-xs text-muted mt-2">
            {{ state.contracts }} contract{{ state.contracts !== 1 ? 's' : '' }} × {{ state.multiplier }} shares × {{ formatCurrency(state.entryPrice, currency) }} premium
          </p>
        </div>

        <!-- Warning when exceeding balance -->
        <div v-if="exceedsBalance" class="p-3 bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200 dark:border-red-800">
          <div class="flex items-start gap-2">
            <UIcon name="i-lucide-alert-triangle" class="w-4 h-4 text-red-600 dark:text-red-400 mt-0.5 flex-shrink-0" />
            <div>
              <p class="text-sm font-semibold text-red-900 dark:text-red-100">
                Insufficient Balance
              </p>
              <p class="text-xs text-red-700 dark:text-red-300 mt-1">
                This position costs {{ formatCurrency(totalCost, currency) }} but you only have {{ formatCurrency(currentBalance, currency) }} available. You need {{ formatCurrency(totalCost - currentBalance, currency) }} more to open this trade.
              </p>
            </div>
          </div>
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
            label="Open Trade"
            icon="i-lucide-trending-up"
            :loading="loading"
            :disabled="loading || exceedsBalance"
          />
        </div>
      </UForm>
    </template>
  </UModal>
</template>
