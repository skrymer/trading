<script setup lang="ts">
import type { ScannerTrade } from '~/types'

defineProps<{
  open: boolean
  trade: ScannerTrade | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()
</script>

<template>
  <UModal
    :open="open"
    title="Trade Details"
    size="lg"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div v-if="trade" class="space-y-4">
        <!-- Symbol header -->
        <div class="flex items-center justify-between">
          <div>
            <span class="text-xl font-bold">{{ trade.symbol }}</span>
            <UBadge
              v-if="trade.sectorSymbol"
              variant="subtle"
              size="sm"
              class="ml-2"
            >
              {{ trade.sectorSymbol }}
            </UBadge>
          </div>
          <UBadge :color="trade.instrumentType === 'OPTION' ? 'warning' : 'info'" variant="subtle">
            {{ trade.instrumentType }}
          </UBadge>
        </div>

        <!-- Trade info grid -->
        <div class="grid grid-cols-2 gap-4 text-sm">
          <div class="space-y-2">
            <div>
              <span class="text-muted block">Entry Price</span>
              <span class="font-semibold text-lg">${{ trade.entryPrice.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted block">Entry Date</span>
              <span class="font-medium">{{ trade.entryDate }}</span>
            </div>
            <div>
              <span class="text-muted block">Quantity</span>
              <span class="font-medium">{{ trade.quantity }}</span>
            </div>
          </div>

          <div class="space-y-2">
            <div>
              <span class="text-muted block">Entry Strategy</span>
              <span class="font-medium">{{ trade.entryStrategyName }}</span>
            </div>
            <div>
              <span class="text-muted block">Exit Strategy</span>
              <span class="font-medium">{{ trade.exitStrategyName }}</span>
            </div>
          </div>
        </div>

        <!-- Option details -->
        <div v-if="trade.instrumentType === 'OPTION'" class="p-3 bg-muted/50 rounded-lg">
          <div class="text-sm font-medium mb-2">
            Option Details
          </div>
          <div class="grid grid-cols-3 gap-2 text-sm">
            <div>
              <span class="text-muted">Type:</span>
              <span class="font-medium ml-1">{{ trade.optionType }}</span>
            </div>
            <div>
              <span class="text-muted">Strike:</span>
              <span class="font-medium ml-1">${{ trade.strikePrice?.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">Expiry:</span>
              <span class="font-medium ml-1">{{ trade.expirationDate }}</span>
            </div>
          </div>
        </div>

        <!-- Roll info -->
        <div v-if="trade.rollCount > 0" class="p-3 bg-blue-50 dark:bg-blue-900/10 rounded-lg border border-blue-200 dark:border-blue-800">
          <div class="text-sm font-medium mb-2">
            Roll History
          </div>
          <div class="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span class="text-muted">Times Rolled:</span>
              <span class="font-medium ml-1">{{ trade.rollCount }}</span>
            </div>
            <div>
              <span class="text-muted">Total Credits:</span>
              <span
                :class="[
                  'font-medium ml-1',
                  trade.rolledCredits >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                ]"
              >
                {{ trade.rolledCredits >= 0 ? '+' : '' }}${{ trade.rolledCredits.toFixed(2) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Notes -->
        <div v-if="trade.notes">
          <span class="text-muted text-sm block mb-1">Notes</span>
          <p class="text-sm p-3 bg-muted/50 rounded-lg">
            {{ trade.notes }}
          </p>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end">
        <UButton
          label="Close"
          color="neutral"
          variant="outline"
          @click="emit('update:open', false)"
        />
      </div>
    </template>
  </UModal>
</template>
