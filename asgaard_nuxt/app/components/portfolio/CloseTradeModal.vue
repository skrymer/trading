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
  'close-trade': [tradeId: string, exitPrice: number, exitDate: string]
}>()

const exitPrice = ref(0)
const exitDate = ref(format(new Date(), 'yyyy-MM-dd'))

// Calculate profit preview
const profitPreview = computed(() => {
  if (exitPrice.value <= 0) return null

  const profit = (exitPrice.value - props.trade.entryPrice) * props.trade.quantity
  const profitPercentage = ((exitPrice.value - props.trade.entryPrice) / props.trade.entryPrice) * 100

  return { profit, profitPercentage }
})

function handleCloseTrade() {
  if (!props.trade.id || exitPrice.value <= 0) {
    return
  }

  emit('close-trade', props.trade.id, exitPrice.value, exitDate.value)
}

function handleClose() {
  if (props.loading) return
  emit('update:open', false)
}

// Reset form when modal closes
watch(() => props.open, (isOpen) => {
  if (!isOpen && !props.loading) {
    exitPrice.value = 0
    exitDate.value = format(new Date(), 'yyyy-MM-dd')
  }
})
</script>

<template>
  <UModal
    :open="open"
    @update:open="emit('update:open', $event)"
    :title="`Close Trade - ${trade.symbol}`"
  >
    <template #body>
      <div class="space-y-4">
        <!-- Trade Info -->
        <div class="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg space-y-2">
          <div class="flex justify-between text-sm">
            <span class="text-muted">Entry Price:</span>
            <span class="font-semibold">{{ trade.entryPrice.toFixed(2) }} {{ trade.currency }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-muted">Quantity:</span>
            <span class="font-semibold">{{ trade.quantity }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-muted">Entry Date:</span>
            <span class="font-semibold">{{ format(new Date(trade.entryDate), 'MMM dd, yyyy') }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-muted">Total Cost:</span>
            <span class="font-semibold">{{ (trade.entryPrice * trade.quantity).toFixed(2) }} {{ trade.currency }}</span>
          </div>
        </div>

        <UFormField label="Exit Price" required>
          <UInput
            v-model.number="exitPrice"
            type="number"
            min="0"
            step="0.01"
            placeholder="Enter exit price"
          />
        </UFormField>

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
              <span class="text-muted">Total Value:</span>
              <span class="font-semibold">{{ (exitPrice * trade.quantity).toFixed(2) }} {{ trade.currency }}</span>
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

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          :disabled="loading"
          @click="handleClose"
        />
        <UButton
          label="Close Trade"
          icon="i-lucide-check"
          color="red"
          :loading="loading"
          :disabled="loading"
          @click="handleCloseTrade"
        />
      </div>
    </template>
  </UModal>
</template>
