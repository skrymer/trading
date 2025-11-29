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
  'delete-trade': [tradeId: string]
}>()

function handleDelete() {
  if (!props.trade.id) return
  emit('delete-trade', props.trade.id)
}

function handleClose() {
  if (props.loading) return
  emit('update:open', false)
}

// Format currency
function formatCurrency(value: number, currency: string = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency
  }).format(value)
}

// Calculate position value
const positionValue = computed(() => {
  if (props.trade.instrumentType === 'OPTION') {
    return props.trade.entryPrice * (props.trade.contracts || props.trade.quantity) * (props.trade.multiplier || 100)
  }
  return props.trade.entryPrice * props.trade.quantity
})
</script>

<template>
  <UModal
    :open="open"
    title="Delete Trade"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-4">
        <div class="p-4 bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200 dark:border-red-800">
          <div class="flex items-start gap-3">
            <UIcon name="i-lucide-alert-triangle" class="w-5 h-5 text-red-600 dark:text-red-400 mt-0.5" />
            <div>
              <p class="font-semibold text-red-900 dark:text-red-100">
                Are you sure you want to delete this trade?
              </p>
              <p class="text-sm text-red-700 dark:text-red-300 mt-1">
                This action cannot be undone. The trade will be permanently removed from your portfolio.
              </p>
            </div>
          </div>
        </div>

        <div class="space-y-3">
          <h4 class="font-semibold text-sm text-muted">
            Trade Details
          </h4>

          <div class="grid grid-cols-2 gap-4 p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
            <div>
              <p class="text-xs text-muted">Symbol</p>
              <p class="font-semibold">{{ trade.symbol }}</p>
            </div>

            <div>
              <p class="text-xs text-muted">Type</p>
              <p class="font-semibold">
                {{ trade.instrumentType === 'OPTION' ? 'Option' : trade.instrumentType === 'LEVERAGED_ETF' ? 'Leveraged ETF' : 'Stock' }}
              </p>
            </div>

            <div>
              <p class="text-xs text-muted">Entry Date</p>
              <p class="font-semibold">{{ format(new Date(trade.entryDate), 'MMM dd, yyyy') }}</p>
            </div>

            <div>
              <p class="text-xs text-muted">Entry Price</p>
              <p class="font-semibold">{{ formatCurrency(trade.entryPrice, trade.currency) }}</p>
            </div>

            <div>
              <p class="text-xs text-muted">
                {{ trade.instrumentType === 'OPTION' ? 'Contracts' : 'Quantity' }}
              </p>
              <p class="font-semibold">
                {{ trade.instrumentType === 'OPTION' ? trade.contracts || trade.quantity : trade.quantity }}
              </p>
            </div>

            <div>
              <p class="text-xs text-muted">Position Value</p>
              <p class="font-semibold">{{ formatCurrency(positionValue, trade.currency) }}</p>
            </div>
          </div>

          <div v-if="trade.instrumentType === 'OPTION'" class="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
            <p class="text-xs text-muted mb-2">Option Details</p>
            <div class="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span class="text-muted">Type:</span>
                <span class="ml-2 font-semibold">{{ trade.optionType }}</span>
              </div>
              <div>
                <span class="text-muted">Strike:</span>
                <span class="ml-2 font-semibold">{{ formatCurrency(trade.strikePrice || 0, trade.currency) }}</span>
              </div>
              <div>
                <span class="text-muted">Expiration:</span>
                <span class="ml-2 font-semibold">
                  {{ trade.expirationDate ? format(new Date(trade.expirationDate), 'MMM dd, yyyy') : 'N/A' }}
                </span>
              </div>
              <div>
                <span class="text-muted">Multiplier:</span>
                <span class="ml-2 font-semibold">{{ trade.multiplier }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <UButton
            label="Cancel"
            color="neutral"
            variant="outline"
            :disabled="loading"
            @click="handleClose"
          />
          <UButton
            label="Delete Trade"
            icon="i-lucide-trash-2"
            color="error"
            :loading="loading"
            :disabled="loading"
            @click="handleDelete"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
