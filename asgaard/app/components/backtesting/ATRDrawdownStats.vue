<script setup lang="ts">
import type { BacktestReport, ATRDrawdownStats, DrawdownBucket } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

const stats = computed(() => props.report?.atrDrawdownStats)

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
const stopLossRecommendation = computed(() => {
  if (!stats.value) return null

  // Find what percentage would be cut at different stop levels
  const at1ATR = distributionData.value.find(d => d.range.startsWith('1'))?.cumulativePercentage || 0
  const at1_5ATR = distributionData.value.find(d => d.range.startsWith('1.5'))?.cumulativePercentage || 0
  const at2ATR = distributionData.value.find(d => d.range.startsWith('2'))?.cumulativePercentage || 0

  return {
    at1ATR,
    at1_5ATR,
    at2ATR,
    median: stats.value.medianDrawdown,
    p90: stats.value.percentile90,
    p95: stats.value.percentile95
  }
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-trending-down" class="w-5 h-5" />
        <h3 class="text-lg font-semibold">
          ATR Drawdown Analysis
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
      <!-- Summary Stats -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <UCard :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Winning Trades</div>
          <div class="font-semibold text-2xl">{{ stats.totalWinningTrades }}</div>
        </UCard>

        <UCard :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Median Drawdown</div>
          <div class="font-semibold text-2xl" :class="getPercentileColor(stats.medianDrawdown)">
            {{ stats.medianDrawdown.toFixed(2) }} ATR
          </div>
        </UCard>

        <UCard :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Mean Drawdown</div>
          <div class="font-semibold text-2xl" :class="getPercentileColor(stats.meanDrawdown)">
            {{ stats.meanDrawdown.toFixed(2) }} ATR
          </div>
        </UCard>

        <UCard :ui="{ body: 'p-4' }">
          <div class="text-sm text-muted mb-1">Max Drawdown</div>
          <div class="font-semibold text-2xl text-error">
            {{ stats.maxDrawdown.toFixed(2) }} ATR
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
            <div class="text-xs text-muted mb-1">{{ p.label }}</div>
            <div
              class="font-semibold text-lg"
              :class="`text-${getPercentileColor(p.value)}`"
            >
              {{ p.value.toFixed(2) }}
            </div>
            <div class="text-xs text-muted">ATR</div>
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

      <!-- Insights -->
      <UCard v-if="stopLossRecommendation" :ui="{ body: 'p-4' }" class="bg-primary/5">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-lightbulb" class="w-5 h-5 text-primary mt-0.5" />
          <div class="flex-1">
            <h4 class="font-semibold mb-2">Stop Loss Optimization Insights</h4>
            <ul class="space-y-2 text-sm">
              <li>
                <span class="text-muted">•</span>
                <strong class="ml-2">{{ (100 - stopLossRecommendation.at1ATR).toFixed(1) }}%</strong>
                of winning trades stayed within 1.0 ATR drawdown.
              </li>
              <li>
                <span class="text-muted">•</span>
                <strong class="ml-2">{{ (100 - stopLossRecommendation.at2ATR).toFixed(1) }}%</strong>
                of winning trades stayed within 2.0 ATR drawdown.
              </li>
              <li>
                <span class="text-muted">•</span>
                Median drawdown is
                <strong class="ml-2">{{ stopLossRecommendation.median.toFixed(2) }} ATR</strong>
                - half of winners endured this much or less.
              </li>
              <li>
                <span class="text-muted">•</span>
                <strong class="ml-2">90% of winners</strong>
                stayed under {{ stopLossRecommendation.p90.toFixed(2) }} ATR drawdown
                - consider this for stop loss placement.
              </li>
              <li v-if="stopLossRecommendation.p95 < 2.0">
                <span class="text-muted">•</span>
                <strong class="ml-2 text-success">Excellent risk control:</strong>
                95% of winners stayed under {{ stopLossRecommendation.p95.toFixed(2) }} ATR,
                suggesting tight stops are viable.
              </li>
              <li v-else-if="stopLossRecommendation.p95 > 3.0">
                <span class="text-muted">•</span>
                <strong class="ml-2 text-warning">Wide drawdowns:</strong>
                95th percentile is {{ stopLossRecommendation.p95.toFixed(2) }} ATR,
                consider if tighter entries could reduce this.
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
            <h4 class="font-semibold mb-2">How to Interpret This Data</h4>
            <div class="text-sm space-y-2">
              <p>
                <strong>ATR Drawdown</strong> measures how much adverse price movement
                (in ATR units) winning trades endured before becoming profitable.
              </p>
              <p>
                <strong class="text-success">Lower values (< 1.0 ATR)</strong> suggest tight,
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
