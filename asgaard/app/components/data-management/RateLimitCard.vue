<script setup lang="ts">
import type { RateLimitStats } from '~/types'

const props = defineProps<{
  rateLimit: RateLimitStats
}>()

const minuteUsagePercentage = computed(() =>
  (props.rateLimit.requestsLastMinute / props.rateLimit.minuteLimit) * 100
)

const dailyUsagePercentage = computed(() =>
  (props.rateLimit.requestsLastDay / props.rateLimit.dailyLimit) * 100
)

// Determine subscription tier based on limits
const subscriptionTier = computed(() => {
  const minuteLimit = props.rateLimit.minuteLimit
  if (minuteLimit <= 5) return 'FREE'
  if (minuteLimit <= 75) return 'PREMIUM'
  return 'ULTIMATE'
})

const tierColor = computed(() => {
  switch (subscriptionTier.value) {
    case 'FREE': return 'neutral'
    case 'PREMIUM': return 'primary'
    case 'ULTIMATE': return 'success'
    default: return 'neutral'
  }
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">
          AlphaVantage Rate Limits
        </h3>
        <UBadge
          :color="tierColor"
          size="lg"
        >
          {{ subscriptionTier }}
        </UBadge>
      </div>
    </template>

    <div class="grid grid-cols-2 gap-4">
      <!-- Per Minute Limit -->
      <div>
        <div class="flex justify-between mb-2">
          <span class="text-sm text-muted">Minute Limit</span>
          <span class="text-sm font-medium">
            {{ rateLimit.requestsLastMinute }} / {{ rateLimit.minuteLimit }}
          </span>
        </div>
        <UProgress
          :value="minuteUsagePercentage"
          :color="minuteUsagePercentage > 80 ? 'error' : 'primary'"
        />
        <p class="text-xs text-muted mt-1">
          Resets in {{ rateLimit.resetMinute }}s
        </p>
      </div>

      <!-- Daily Limit -->
      <div>
        <div class="flex justify-between mb-2">
          <span class="text-sm text-muted">Daily Limit</span>
          <span class="text-sm font-medium">
            {{ rateLimit.requestsLastDay }} / {{ rateLimit.dailyLimit }}
          </span>
        </div>
        <UProgress
          :value="dailyUsagePercentage"
          :color="dailyUsagePercentage > 80 ? 'error' : 'primary'"
        />
        <p class="text-xs text-muted mt-1">
          {{ rateLimit.remainingDaily }} requests remaining
        </p>
      </div>
    </div>
  </UCard>
</template>
