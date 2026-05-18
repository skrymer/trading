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
    :ui="{ content: 'max-w-5xl' }"
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

        <!-- Signal evidence: the frozen snapshot of what the scanner saw on the bar that
             triggered this trade. Decoupled from current-data evaluation by design — see
             docs/adr/0004. Null on legacy / manual-add trades. -->
        <div v-if="trade.signalDate || trade.signalSnapshot" class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="text-muted">Signal Evidence</span>
            <span v-if="trade.signalDate" class="font-medium">
              Bar: {{ trade.signalDate }}
            </span>
          </div>
          <div v-if="trade.signalSnapshot" class="space-y-2">
            <div class="grid grid-cols-2 gap-2">
              <div
                v-for="(condition, index) in trade.signalSnapshot.conditions"
                :key="index"
                class="p-2 rounded border text-xs"
                :class="condition.passed ? 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800' : 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800'"
              >
                <div class="flex items-start gap-2">
                  <UIcon
                    :name="condition.passed ? 'i-lucide-check-circle' : 'i-lucide-x-circle'"
                    :class="condition.passed ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                    class="w-4 h-4 flex-shrink-0 mt-0.5"
                  />
                  <div class="flex-1 min-w-0">
                    <p class="font-medium" :class="condition.passed ? 'text-green-900 dark:text-green-100' : 'text-red-900 dark:text-red-100'">
                      {{ condition.description }}
                    </p>
                    <div v-if="condition.actualValue || condition.threshold" class="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-muted">
                      <span v-if="condition.actualValue">
                        <span class="font-medium">Actual:</span> {{ condition.actualValue }}
                      </span>
                      <span v-if="condition.threshold">
                        <span class="font-medium">Threshold:</span> {{ condition.threshold }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div
              class="p-2 rounded border text-xs text-center font-medium"
              :class="trade.signalSnapshot.allConditionsMet ? 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800 text-green-900 dark:text-green-100' : 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800 text-red-900 dark:text-red-100'"
            >
              <UIcon
                :name="trade.signalSnapshot.allConditionsMet ? 'i-lucide-check-circle-2' : 'i-lucide-alert-circle'"
                class="w-4 h-4 inline-block mr-1"
              />
              {{ trade.signalSnapshot.allConditionsMet ? 'All conditions met at scan time' : 'Conditions partially failed at scan time' }}
            </div>
          </div>
          <p v-else class="text-xs text-muted italic p-2 bg-muted/30 rounded">
            Snapshot unavailable — trade pre-dates V21 schema or was added without a scanner match.
          </p>
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
