<script setup lang="ts">
import type { MonteCarloResult } from '@/types'
import MonteCarloEquityCurve from './MonteCarloEquityCurve.client.vue'

defineProps<{
  result: MonteCarloResult | null
  loading?: boolean
}>()
</script>

<template>
  <div class="space-y-6">
    <!-- Metrics Section -->
    <BacktestingMonteCarloMetrics
      :result="result"
      :loading="loading"
    />

    <!-- Equity Curve Distribution Chart -->
    <div v-if="result && !loading" class="mt-8">
      <MonteCarloEquityCurve :result="result" />
    </div>
  </div>
</template>
