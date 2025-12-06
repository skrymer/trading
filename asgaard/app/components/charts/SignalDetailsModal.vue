<script setup lang="ts">
import { format } from 'date-fns'

interface ConditionEvaluationResult {
  conditionType: string
  description: string
  passed: boolean
  actualValue?: string | null
  threshold?: string | null
  message?: string | null
}

interface EntrySignalDetails {
  strategyName: string
  strategyDescription: string
  conditions: ConditionEvaluationResult[]
  allConditionsMet: boolean
}

interface SignalData {
  date: string
  price: number
  entryDetails?: EntrySignalDetails | null
}

const props = defineProps<{
  open: boolean
  signal?: SignalData | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()
</script>

<template>
  <UModal :open="open" title="Entry Signal Details" @update:open="emit('update:open', $event)">
    <template #body>
      <div v-if="signal" class="space-y-4">
        <!-- Quote Info -->
        <div class="flex justify-between items-center pb-3 border-b border-default">
          <div>
            <p class="text-sm text-muted">
              Date
            </p>
            <p class="font-semibold">
              {{ format(new Date(signal.date), 'MMM dd, yyyy') }}
            </p>
          </div>
          <div class="text-right">
            <p class="text-sm text-muted">
              Price
            </p>
            <p class="font-semibold">
              ${{ signal.price.toFixed(2) }}
            </p>
          </div>
        </div>

        <!-- Strategy Info -->
        <div v-if="signal.entryDetails" class="space-y-4">
          <div>
            <p class="text-xs text-muted mb-1">
              Strategy
            </p>
            <p class="font-medium">
              {{ signal.entryDetails.strategyName }}
            </p>
          </div>

          <!-- Conditions List -->
          <div class="space-y-3">
            <p class="text-xs text-muted uppercase tracking-wide">
              Conditions
            </p>

            <div
              v-for="(condition, index) in signal.entryDetails.conditions"
              :key="index"
              class="p-3 rounded-lg border"
              :class="condition.passed ? 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800' : 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800'"
            >
              <!-- Condition Header -->
              <div class="flex items-start gap-2">
                <UIcon
                  :name="condition.passed ? 'i-lucide-check-circle' : 'i-lucide-x-circle'"
                  :class="condition.passed ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                  class="w-5 h-5 flex-shrink-0 mt-0.5"
                />
                <div class="flex-1 min-w-0">
                  <p class="font-medium text-sm" :class="condition.passed ? 'text-green-900 dark:text-green-100' : 'text-red-900 dark:text-red-100'">
                    {{ condition.description }}
                  </p>

                  <!-- Actual Value vs Threshold -->
                  <div v-if="condition.actualValue || condition.threshold" class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs">
                    <span v-if="condition.actualValue" class="text-muted">
                      <span class="font-medium">Actual:</span> {{ condition.actualValue }}
                    </span>
                    <span v-if="condition.threshold" class="text-muted">
                      <span class="font-medium">Threshold:</span> {{ condition.threshold }}
                    </span>
                  </div>

                  <!-- Message -->
                  <p v-if="condition.message" class="mt-2 text-sm" :class="condition.passed ? 'text-green-700 dark:text-green-300' : 'text-red-700 dark:text-red-300'">
                    {{ condition.message }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- Summary -->
          <div
            class="p-3 rounded-lg border font-medium text-center"
            :class="signal.entryDetails.allConditionsMet ? 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800 text-green-900 dark:text-green-100' : 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800 text-red-900 dark:text-red-100'"
          >
            <UIcon
              :name="signal.entryDetails.allConditionsMet ? 'i-lucide-check-circle-2' : 'i-lucide-alert-circle'"
              class="w-5 h-5 inline-block mr-2"
            />
            {{ signal.entryDetails.allConditionsMet ? 'All conditions met ✓' : 'Some conditions failed ✗' }}
          </div>
        </div>

        <!-- No Details Available -->
        <div v-else class="text-center py-8 text-muted">
          <UIcon name="i-lucide-info" class="w-8 h-8 mx-auto mb-2" />
          <p>No detailed condition information available for this signal.</p>
        </div>
      </div>

      <!-- No Signal Selected -->
      <div v-else class="text-center py-8 text-muted">
        <UIcon name="i-lucide-alert-triangle" class="w-8 h-8 mx-auto mb-2" />
        <p>No signal data available.</p>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end">
        <UButton color="neutral" @click="emit('update:open', false)">
          Close
        </UButton>
      </div>
    </template>
  </UModal>
</template>
