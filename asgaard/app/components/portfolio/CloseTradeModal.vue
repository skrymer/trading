<script setup lang="ts">
import { format } from 'date-fns'
import type { PortfolioTrade } from '~/types'

const props = defineProps<{
  open: boolean
  trade: PortfolioTrade
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'close-trade': [tradeId: string, exitPrice: number, exitDate: string, exitIntrinsicValue?: number, exitExtrinsicValue?: number]
}>()

const exitPrice = ref(0)
const exitDate = ref(format(new Date(), 'yyyy-MM-dd'))
const exitIntrinsicValue = ref<number | undefined>(undefined)
const exitExtrinsicValue = ref<number | undefined>(undefined)

// Check if trade is an option
const isOption = computed(() => props.trade.instrumentType === 'OPTION')

// Calculate profit preview
const profitPreview = computed(() => {
  if (exitPrice.value <= 0) return null

  let profit: number
  let exitValue: number
  let entryValue: number

  if (isOption.value) {
    // For options: value = price × contracts × multiplier
    const contracts = props.trade.contracts || 1
    const multiplier = props.trade.multiplier || 100
    exitValue = exitPrice.value * contracts * multiplier
    entryValue = props.trade.entryPrice * contracts * multiplier
    profit = exitValue - entryValue
  } else {
    // For stocks: value = price × quantity
    exitValue = exitPrice.value * props.trade.quantity
    entryValue = props.trade.entryPrice * props.trade.quantity
    profit = exitValue - entryValue
  }

  const profitPercentage = (profit / entryValue) * 100

  return { profit, profitPercentage, exitValue, entryValue }
})

function handleCloseTrade() {
  if (!props.trade.id || exitPrice.value <= 0) {
    return
  }

  emit(
    'close-trade',
    props.trade.id,
    exitPrice.value,
    exitDate.value,
    exitIntrinsicValue.value,
    exitExtrinsicValue.value
  )
}

// Reset form when modal closes
watch(() => props.open, (isOpen) => {
  if (!isOpen && !props.loading) {
    exitPrice.value = 0
    exitDate.value = format(new Date(), 'yyyy-MM-dd')
    exitIntrinsicValue.value = undefined
    exitExtrinsicValue.value = undefined
  }
})
</script>

<template>
  <UModal
    :open="open"
    :title="`Close Trade - ${trade.symbol}`"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-4">
        <!-- Trade Info -->
        <div class="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg space-y-2">
          <div class="flex justify-between text-sm">
            <span class="text-muted">{{ isOption ? 'Entry Premium' : 'Entry Price' }}:</span>
            <span class="font-semibold">{{ trade.entryPrice.toFixed(2) }} {{ trade.currency }}</span>
          </div>
          <div v-if="isOption" class="flex justify-between text-sm">
            <span class="text-muted">Contracts:</span>
            <span class="font-semibold">{{ trade.contracts || 1 }}</span>
          </div>
          <div v-if="isOption" class="flex justify-between text-sm">
            <span class="text-muted">Multiplier:</span>
            <span class="font-semibold">{{ trade.multiplier || 100 }}</span>
          </div>
          <div v-if="!isOption" class="flex justify-between text-sm">
            <span class="text-muted">Quantity:</span>
            <span class="font-semibold">{{ trade.quantity }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-muted">Entry Date:</span>
            <span class="font-semibold">{{ format(new Date(trade.entryDate), 'MMM dd, yyyy') }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-muted">{{ isOption ? 'Position Value' : 'Total Cost' }}:</span>
            <span class="font-semibold">
              {{ isOption
                ? (trade.entryPrice * (trade.contracts || 1) * (trade.multiplier || 100)).toFixed(2)
                : (trade.entryPrice * trade.quantity).toFixed(2)
              }} {{ trade.currency }}
            </span>
          </div>
          <div v-if="isOption && trade.entryIntrinsicValue !== undefined && trade.entryIntrinsicValue !== null" class="flex justify-between text-sm">
            <span class="text-muted">Entry Intrinsic:</span>
            <span class="font-semibold">{{ trade.entryIntrinsicValue.toFixed(2) }} {{ trade.currency }}</span>
          </div>
          <div v-if="isOption && trade.entryExtrinsicValue !== undefined && trade.entryExtrinsicValue !== null" class="flex justify-between text-sm">
            <span class="text-muted">Entry Extrinsic:</span>
            <span class="font-semibold">{{ trade.entryExtrinsicValue.toFixed(2) }} {{ trade.currency }}</span>
          </div>
        </div>

        <UFormField :label="isOption ? 'Exit Premium' : 'Exit Price'" required>
          <UInput
            v-model.number="exitPrice"
            type="number"
            min="0"
            step="0.01"
            :placeholder="isOption ? 'Enter exit premium' : 'Enter exit price'"
          />
        </UFormField>

        <!-- Option-specific fields -->
        <div v-if="isOption" class="grid grid-cols-2 gap-4">
          <UFormField label="Exit Intrinsic Value">
            <UInput
              v-model.number="exitIntrinsicValue"
              type="number"
              min="0"
              step="0.01"
              placeholder="Optional"
            />
          </UFormField>

          <UFormField label="Exit Extrinsic Value">
            <UInput
              v-model.number="exitExtrinsicValue"
              type="number"
              min="0"
              step="0.01"
              placeholder="Optional"
            />
          </UFormField>
        </div>

        <UFormField label="Exit Date" required>
          <UInput
            v-model="exitDate"
            type="date"
          />
        </UFormField>

        <!-- Profit Preview -->
        <div v-if="profitPreview" class="p-3 rounded-lg" :class="profitPreview.profit >= 0 ? 'bg-green-50 dark:bg-green-900/20' : 'bg-red-50 dark:bg-red-900/20'">
          <p class="text-sm font-semibold mb-2" :class="profitPreview.profit >= 0 ? 'text-green-600' : 'text-red-600'">
            Profit Preview
          </p>
          <div class="space-y-1">
            <div class="flex justify-between text-sm">
              <span class="text-muted">{{ isOption ? 'Exit Position Value' : 'Total Value' }}:</span>
              <span class="font-semibold">{{ profitPreview.exitValue.toFixed(2) }} {{ trade.currency }}</span>
            </div>
            <div class="flex justify-between text-sm">
              <span class="text-muted">Profit/Loss:</span>
              <span class="font-bold" :class="profitPreview.profit >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ profitPreview.profit >= 0 ? '+' : '' }}{{ profitPreview.profit.toFixed(2) }} {{ trade.currency }}
                ({{ profitPreview.profitPercentage >= 0 ? '+' : '' }}{{ profitPreview.profitPercentage.toFixed(2) }}%)
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer="{ close }">
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          :disabled="loading"
          @click="close"
        />
        <UButton
          label="Close Trade"
          icon="i-lucide-check"
          color="error"
          :loading="loading"
          :disabled="loading"
          @click="handleCloseTrade"
        />
      </div>
    </template>
  </UModal>
</template>
