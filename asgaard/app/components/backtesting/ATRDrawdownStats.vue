<script setup lang="ts">
import type { BacktestReport } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

const stats = computed(() => props.report?.atrDrawdownStats)
const loserStats = computed(() => stats.value?.losingTradesStats)

// Get distribution data sorted by range
const distributionData = computed(() => {
  if (!stats.value?.distribution) return []

  return Object.entries(stats.value.distribution)
    .map(([rangeKey, bucket]) => ({
      rangeKey,
      ...bucket
    }))
    .sort((a, b) => {
      // Sort by the starting value of the range
      const aStart = parseFloat(a.range?.split('-')[0] || '0')
      const bStart = parseFloat(b.range?.split('-')[0] || '0')
      return aStart - bStart
    })
})

// Histogram chart data
const histogramSeries = computed(() => {
  if (!distributionData.value.length) return []

  return [
    {
      name: 'Number of Trades',
      type: 'column' as const,
      data: distributionData.value.map(d => d.count)
    },
    {
      name: 'Cumulative %',
      type: 'line' as const,
      data: distributionData.value.map(d => d.cumulativePercentage)
    }
  ]
})

const histogramCategories = computed(() => {
  return distributionData.value.map(d => d.range + ' ATR')
})

// Percentile cards data
const percentiles = computed(() => {
  if (!stats.value) return []

  return [
    { label: '25th', value: stats.value.percentile25, key: 'p25' },
    { label: '50th (Median)', value: stats.value.percentile50, key: 'p50' },
    { label: '75th', value: stats.value.percentile75, key: 'p75' },
    { label: '90th', value: stats.value.percentile90, key: 'p90' },
    { label: '95th', value: stats.value.percentile95, key: 'p95' },
    { label: '99th', value: stats.value.percentile99, key: 'p99' }
  ]
})

// Get color for percentile based on severity
const getPercentileColor = (value: number) => {
  if (value < 1.0) return 'success'
  if (value < 2.0) return 'warning'
  return 'error'
}

// Insights
const drawdownInsights = computed(() => {
  if (!stats.value) return null

  const at2ATR = distributionData.value.find(d => d.range.startsWith('2'))?.cumulativePercentage || 0

  return {
    at2ATR,
    median: stats.value.medianDrawdown,
    p90: stats.value.percentile90,
    p95: stats.value.percentile95,
    max: stats.value.maxDrawdown
  }
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-trending-down" class="w-5 h-5" />
        <h3 class="text-lg font-semibold">
          ATR Drawdown Analysis (Winning Trades)
        </h3>
        <UTooltip text="Shows how much adverse movement winning trades endured before becoming profitable">
          <UIcon name="i-lucide-help-circle" class="w-4 h-4 text-muted" />
        </UTooltip>
      </div>
    </template>

    <div v-if="loading" class="space-y-4">
      <USkeleton class="h-64 w-full" />
      <USkeleton class="h-32 w-full" />
    </div>

    <div v-else-if="!stats" class="text-center py-8">
      <UIcon name="i-lucide-trending-down" class="w-12 h-12 text-muted mx-auto mb-2" />
      <p class="text-muted">
        No ATR drawdown statistics available
      </p>
      <p class="text-sm text-muted mt-2">
        Run a new backtest to see drawdown analysis for winning trades
      </p>
    </div>

    <div v-else class="space-y-6">
      <!-- Comparison View: Winners vs Losers -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Winning Trades Column -->
        <UCard :ui="{ body: 'p-4' }" class="border-2 border-success/20">
          <div class="flex items-center gap-2 mb-4">
            <UIcon name="i-lucide-trending-up" class="w-5 h-5 text-success" />
            <h4 class="font-semibold text-success">
              Winning Trades
            </h4>
            <UBadge color="success" variant="subtle">
              {{ stats.totalWinningTrades }} trades
            </UBadge>
          </div>

          <div class="space-y-3">
            <div class="flex justify-between">
              <span class="text-sm text-muted">Median Drawdown</span>
              <span class="font-semibold">{{ stats.medianDrawdown.toFixed(2) }} ATR</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">Mean Drawdown</span>
              <span class="font-semibold">{{ stats.meanDrawdown.toFixed(2) }} ATR</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">90th Percentile</span>
              <span class="font-semibold">{{ stats.percentile90.toFixed(2) }} ATR</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">Max Drawdown</span>
              <span class="font-semibold text-error">{{ stats.maxDrawdown.toFixed(2) }} ATR</span>
            </div>
          </div>
        </UCard>

        <!-- Losing Trades Column -->
        <UCard v-if="loserStats" :ui="{ body: 'p-4' }" class="border-2 border-error/20">
          <div class="flex items-center gap-2 mb-4">
            <UIcon name="i-lucide-trending-down" class="w-5 h-5 text-error" />
            <h4 class="font-semibold text-error">
              Losing Trades
            </h4>
            <UBadge color="error" variant="subtle">
              {{ loserStats.totalLosingTrades }} trades
            </UBadge>
          </div>

          <div class="space-y-3">
            <div class="flex justify-between">
              <span class="text-sm text-muted">Median Loss</span>
              <span class="font-semibold">{{ loserStats.medianLoss.toFixed(2) }} ATR</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">Mean Loss</span>
              <span class="font-semibold">{{ loserStats.meanLoss.toFixed(2) }} ATR</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">90th Percentile</span>
              <span class="font-semibold">{{ loserStats.percentile90.toFixed(2) }} ATR</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">Max Loss</span>
              <span class="font-semibold text-error">{{ loserStats.maxLoss.toFixed(2) }} ATR</span>
            </div>
          </div>
        </UCard>
      </div>

      <!-- Percentile Cards -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Drawdown Percentiles
        </h4>
        <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          <UCard
            v-for="p in percentiles"
            :key="p.key"
            :ui="{ body: 'p-3' }"
          >
            <div class="text-xs text-muted mb-1">
              {{ p.label }}
            </div>
            <div
              class="font-semibold text-lg"
              :class="`text-${getPercentileColor(p.value)}`"
            >
              {{ p.value.toFixed(2) }}
            </div>
            <div class="text-xs text-muted">
              ATR
            </div>
          </UCard>
        </div>
      </div>

      <!-- Distribution Histogram -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Drawdown Distribution
        </h4>
        <ChartsHistogramChart
          :series="histogramSeries"
          :categories="histogramCategories"
          :height="350"
          y-axis-label="Number of Trades"
          y2-axis-label="Cumulative %"
        />
      </div>

      <!-- Entry Quality & Risk Insights -->
      <UCard v-if="drawdownInsights" :ui="{ body: 'p-4' }" class="bg-primary/5">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-lightbulb" class="w-5 h-5 text-primary mt-0.5" />
          <div class="flex-1">
            <h4 class="font-semibold mb-2">
              Entry Quality & Risk Insights
            </h4>
            <ul class="space-y-2 text-sm">
              <!-- Entry Precision -->
              <li>
                <span class="text-muted">•</span>
                <strong class="ml-2">Entry Precision:</strong>
                Median winner drawdown is {{ drawdownInsights.median.toFixed(2) }} ATR.
                <span v-if="drawdownInsights.median < 0.5" class="text-success">
                  Excellent entry precision — winners barely drawdown before moving in your favor.
                </span>
                <span v-else-if="drawdownInsights.median < 1.0" class="text-success">
                  Good entry precision — winners experience modest drawdown.
                </span>
                <span v-else-if="drawdownInsights.median < 2.0" class="text-warning">
                  Moderate entry precision — winners require patience through drawdowns.
                </span>
                <span v-else class="text-error">
                  Entries may be early — winners endure significant drawdown before recovering.
                </span>
              </li>

              <!-- Winner vs Loser Separation -->
              <li v-if="loserStats && stats">
                <span class="text-muted">•</span>
                <strong class="ml-2">Winner vs Loser Separation:</strong>
                <template v-if="stats.medianDrawdown > 0">
                  {{ (loserStats.medianLoss / stats.medianDrawdown).toFixed(1) }}x ratio
                  ({{ loserStats.medianLoss.toFixed(2) }} vs {{ stats.medianDrawdown.toFixed(2) }} ATR median).
                  <span v-if="loserStats.medianLoss / stats.medianDrawdown > 3" class="text-success">
                    Strong separation — losers drawdown much deeper than winners, making them distinguishable early.
                  </span>
                  <span v-else-if="loserStats.medianLoss / stats.medianDrawdown > 1.5" class="text-warning">
                    Moderate separation — some overlap between winner and loser drawdown profiles.
                  </span>
                  <span v-else class="text-error">
                    Weak separation — winners and losers have similar drawdown profiles, making early identification difficult.
                  </span>
                </template>
              </li>

              <!-- Risk Boundary -->
              <li v-if="loserStats">
                <span class="text-muted">•</span>
                <strong class="ml-2">Risk Boundary:</strong>
                <template v-if="loserStats.percentile90 - drawdownInsights.p90 > 1.0">
                  <span class="text-success">
                    Clear risk boundary — {{ (loserStats.percentile90 - drawdownInsights.p90).toFixed(2) }} ATR gap
                    between the drawdown that 90% of winners tolerate ({{ drawdownInsights.p90.toFixed(2) }} ATR)
                    and 90% of losers reach ({{ loserStats.percentile90.toFixed(2) }} ATR).
                  </span>
                </template>
                <template v-else-if="loserStats.percentile90 - drawdownInsights.p90 > 0.5">
                  <span class="text-warning">
                    Narrow risk boundary — limited gap ({{ (loserStats.percentile90 - drawdownInsights.p90).toFixed(2) }} ATR)
                    between winner tolerance ({{ drawdownInsights.p90.toFixed(2) }} ATR) and loser depth ({{ loserStats.percentile90.toFixed(2) }} ATR).
                  </span>
                </template>
                <template v-else>
                  <span class="text-error">
                    Overlapping risk profiles — winner and loser drawdown ranges overlap significantly.
                  </span>
                </template>
              </li>

              <!-- Drawdown Containment -->
              <li>
                <span class="text-muted">•</span>
                <strong class="ml-2">Drawdown Containment:</strong>
                <span v-if="drawdownInsights.at2ATR > 95" class="text-success">
                  Excellent containment — {{ drawdownInsights.at2ATR.toFixed(1) }}% of winners stay within 2.0 ATR drawdown.
                </span>
                <span v-else-if="drawdownInsights.at2ATR >= 80" class="text-success">
                  Good containment — {{ drawdownInsights.at2ATR.toFixed(1) }}% of winners stay within 2.0 ATR drawdown.
                </span>
                <span v-else class="text-warning">
                  Wide drawdowns — only {{ drawdownInsights.at2ATR.toFixed(1) }}% of winners contained within 2.0 ATR.
                </span>
              </li>

              <!-- Tail Risk -->
              <li>
                <span class="text-muted">•</span>
                <strong class="ml-2">Tail Risk:</strong>
                95th percentile at {{ drawdownInsights.p95.toFixed(2) }} ATR, max at {{ drawdownInsights.max.toFixed(2) }} ATR.
                <span v-if="drawdownInsights.max > drawdownInsights.p95 * 2" class="text-error">
                  Significant tail risk — worst case is far beyond 95th percentile.
                </span>
                <span v-else class="text-success">
                  Contained tail risk — worst case is within reasonable range of 95th percentile.
                </span>
              </li>
            </ul>
          </div>
        </div>
      </UCard>

      <!-- Interpretation Guide -->
      <UCard :ui="{ body: 'p-4' }">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-info" class="w-5 h-5 text-muted mt-0.5" />
          <div class="flex-1">
            <h4 class="font-semibold mb-2">
              How to Interpret This Data
            </h4>
            <div class="text-sm space-y-2">
              <p>
                <strong>ATR Drawdown</strong> measures how much adverse price movement
                (in ATR units) winning trades endured before becoming profitable.
              </p>
              <p>
                <strong class="text-success">Lower values (&lt; 1.0 ATR)</strong> suggest tight,
                precise entries with minimal drawdown.
              </p>
              <p>
                <strong class="text-warning">Medium values (1.0-2.0 ATR)</strong> indicate
                normal volatility and reasonable stop placement.
              </p>
              <p>
                <strong class="text-error">Higher values (> 2.0 ATR)</strong> may suggest
                entries could be improved or stops are too tight.
              </p>
              <p class="text-muted">
                Use the 90th percentile as a guide for stop loss placement -
                this keeps 90% of winning trades intact while protecting against runaway losses.
              </p>
            </div>
          </div>
        </div>
      </UCard>
    </div>
  </UCard>
</template>
