<script setup lang="ts">
import type { MonteCarloResult } from '@/types'

const props = defineProps<{
  result: MonteCarloResult | null
  loading?: boolean
}>()

const formatNumber = (value: number, decimals: number = 2) => {
  return value.toFixed(decimals)
}

const formatPercentage = (value: number, decimals: number = 2) => {
  return `${formatNumber(value, decimals)}%`
}

const probabilityColor = computed(() => {
  if (!props.result) return 'neutral'
  const prob = props.result.statistics.probabilityOfProfit
  if (prob >= 70) return 'success'
  if (prob >= 50) return 'info'
  if (prob >= 30) return 'warning'
  return 'error'
})
</script>

<template>
  <!-- Loading skeleton -->
  <div v-if="loading" class="space-y-6">
    <UPageGrid class="lg:grid-cols-4 gap-4 sm:gap-6">
      <UPageCard
        v-for="i in 8"
        :key="i"
        variant="subtle"
        :ui="{
          container: 'gap-y-1.5',
          wrapper: 'items-start',
          leading: 'p-2.5 rounded-full bg-primary/10 ring ring-inset ring-primary/25 flex-col'
        }"
      >
        <template #leading>
          <USkeleton class="h-5 w-5 rounded-full" />
        </template>
        <USkeleton class="h-3 w-32" />
        <USkeleton class="h-8 w-24" />
      </UPageCard>
    </UPageGrid>
  </div>

  <!-- Monte Carlo Results -->
  <div v-else-if="result" class="space-y-6">
    <!-- Technique Info -->
    <UAlert
      color="info"
      variant="subtle"
      icon="i-lucide-chart-scatter"
    >
      <template #description>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <div>
              <span class="font-semibold">{{ result.technique }}</span> simulation with
              <span class="font-semibold">{{ result.iterations.toLocaleString() }}</span> iterations
              completed in <span class="font-semibold">{{ (result.executionTimeMs / 1000).toFixed(2) }}s</span>
            </div>
          </div>
          <div v-if="result.technique === 'Trade Shuffling'" class="text-sm">
            ℹ️ Returns are constant (same trades, different order). Drawdowns vary based on sequence.
          </div>
          <div v-else-if="result.technique === 'Bootstrap Resampling'" class="text-sm">
            ℹ️ Returns vary (different trade combinations with replacement). Tests robustness across scenarios.
          </div>
        </div>
      </template>
    </UAlert>

    <!-- Key Metrics -->
    <div>
      <h3 class="text-lg font-semibold mb-4">
        Probability Analysis
      </h3>
      <UPageGrid class="lg:grid-cols-4 gap-4 sm:gap-6">
        <BacktestingDataCard
          title="Probability of Profit"
          :content="formatPercentage(result.statistics.probabilityOfProfit)"
        />
        <BacktestingDataCard
          title="Mean Return"
          :content="formatPercentage(result.statistics.meanReturnPercentage)"
        />
        <BacktestingDataCard
          title="Median Return"
          :content="formatPercentage(result.statistics.medianReturnPercentage)"
        />
        <BacktestingDataCard
          title="Std Deviation"
          :content="formatPercentage(result.statistics.stdDevReturnPercentage)"
        />
      </UPageGrid>
    </div>

    <!-- Return Percentiles -->
    <div>
      <h3 class="text-lg font-semibold mb-4">
        Return Distribution
      </h3>
      <UPageGrid class="lg:grid-cols-5 gap-4 sm:gap-6">
        <BacktestingDataCard
          title="5th Percentile (Worst)"
          :content="formatPercentage(result.statistics.returnPercentiles.p5)"
        />
        <BacktestingDataCard
          title="25th Percentile"
          :content="formatPercentage(result.statistics.returnPercentiles.p25)"
        />
        <BacktestingDataCard
          title="50th Percentile (Median)"
          :content="formatPercentage(result.statistics.returnPercentiles.p50)"
        />
        <BacktestingDataCard
          title="75th Percentile"
          :content="formatPercentage(result.statistics.returnPercentiles.p75)"
        />
        <BacktestingDataCard
          title="95th Percentile (Best)"
          :content="formatPercentage(result.statistics.returnPercentiles.p95)"
        />
      </UPageGrid>
    </div>

    <!-- Edge & Win Rate -->
    <div>
      <h3 class="text-lg font-semibold mb-4">
        Strategy Robustness
      </h3>
      <UPageGrid class="lg:grid-cols-4 gap-4 sm:gap-6">
        <BacktestingDataCard
          title="Mean Edge"
          :content="formatPercentage(result.statistics.meanEdge)"
        />
        <BacktestingDataCard
          title="Mean Win Rate"
          :content="formatPercentage(result.statistics.meanWinRate * 100, 1)"
        />
        <BacktestingDataCard
          title="Mean Max Drawdown"
          :content="formatPercentage(result.statistics.meanMaxDrawdown)"
        />
        <BacktestingDataCard
          title="Median Max Drawdown"
          :content="formatPercentage(result.statistics.medianMaxDrawdown)"
        />
      </UPageGrid>
    </div>

    <!-- Comparison with Original -->
    <div>
      <h3 class="text-lg font-semibold mb-4">
        Original vs. Simulation
      </h3>
      <UPageGrid class="lg:grid-cols-3 gap-4 sm:gap-6">
        <BacktestingDataCard
          title="Original Return"
          :content="formatPercentage(result.originalReturnPercentage)"
        />
        <BacktestingDataCard
          title="Original Edge"
          :content="formatPercentage(result.originalEdge)"
        />
        <BacktestingDataCard
          title="Original Win Rate"
          :content="formatPercentage(result.originalWinRate * 100, 1)"
        />
      </UPageGrid>
    </div>

    <!-- Confidence Intervals -->
    <UAlert
      :color="probabilityColor"
      variant="subtle"
      icon="i-lucide-trending-up"
    >
      <template #description>
        <div class="space-y-2">
          <p class="font-semibold">
            95% Confidence Intervals
          </p>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span class="text-muted">Total Return:</span>
              <span class="ml-2 font-semibold">
                {{ formatPercentage(result.statistics.returnConfidenceInterval95.lower) }}
                to
                {{ formatPercentage(result.statistics.returnConfidenceInterval95.upper) }}
              </span>
            </div>
            <div>
              <span class="text-muted">Max Drawdown:</span>
              <span class="ml-2 font-semibold">
                {{ formatPercentage(result.statistics.drawdownConfidenceInterval95.lower) }}
                to
                {{ formatPercentage(result.statistics.drawdownConfidenceInterval95.upper) }}
              </span>
            </div>
          </div>
        </div>
      </template>
    </UAlert>
  </div>

  <!-- No data -->
  <UAlert
    v-else
    color="neutral"
    variant="subtle"
    icon="i-lucide-chart-scatter"
  >
    <template #description>
      No Monte Carlo simulation results available. Run a backtest first, then click "Run Monte Carlo" to test strategy robustness.
    </template>
  </UAlert>
</template>
