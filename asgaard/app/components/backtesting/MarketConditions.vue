<script setup lang="ts">
import type { BacktestReport } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

// Market condition averages from report
const marketAvgs = computed(() => props.report?.marketConditionAverages)

// Pre-computed market condition stats from backend
const mcStats = computed(() => props.report?.marketConditionStats)

// ApexCharts series for Market Breadth scatter
const breadthSeries = computed(() => {
  if (!mcStats.value) return []

  const winners = mcStats.value.scatterPoints.filter(d => d.isWinner)
  const losers = mcStats.value.scatterPoints.filter(d => !d.isWinner)

  return [
    {
      name: 'Winning Trades',
      data: winners.map(d => [d.breadth, d.profitPercentage])
    },
    {
      name: 'Losing Trades',
      data: losers.map(d => [d.breadth, d.profitPercentage])
    }
  ]
})

// Calculate correlation coefficient
function calculateCorrelation(data: Array<{ x: number, y: number }>): number {
  if (data.length < 2) return 0

  const n = data.length
  const sumX = data.reduce((sum, d) => sum + d.x, 0)
  const sumY = data.reduce((sum, d) => sum + d.y, 0)
  const sumXY = data.reduce((sum, d) => sum + d.x * d.y, 0)
  const sumX2 = data.reduce((sum, d) => sum + d.x * d.x, 0)
  const sumY2 = data.reduce((sum, d) => sum + d.y * d.y, 0)

  const numerator = n * sumXY - sumX * sumY
  const denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))

  return denominator === 0 ? 0 : numerator / denominator
}

const breadthCorrelation = computed(() => {
  if (!mcStats.value) return 0
  const data = mcStats.value.scatterPoints.map(p => ({ x: p.breadth, y: p.profitPercentage }))
  return calculateCorrelation(data)
})

const totalTradesWithData = computed(() => {
  if (!mcStats.value) return 0
  return mcStats.value.uptrendCount + mcStats.value.downtrendCount
})

// Insights
const insights = computed(() => {
  const results: string[] = []

  // Breadth insights
  if (Math.abs(breadthCorrelation.value) > 0.3) {
    const direction = breadthCorrelation.value > 0 ? 'positive' : 'negative'
    const strength = Math.abs(breadthCorrelation.value) > 0.5 ? 'strong' : 'moderate'
    results.push(`${strength} ${direction} correlation between market breadth and trade performance (r=${breadthCorrelation.value.toFixed(2)})`)
  }

  // Uptrend insights
  if (mcStats.value) {
    const diff = mcStats.value.uptrendWinRate - mcStats.value.downtrendWinRate
    if (Math.abs(diff) > 10) {
      const better = diff > 0 ? 'uptrends' : 'downtrends'
      results.push(`Strategy performs ${Math.abs(diff).toFixed(1)}% better during SPY ${better}`)
    }
  }

  return results
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-globe" class="w-5 h-5" />
        <h3 class="text-lg font-semibold">
          Market Conditions Analysis
        </h3>
        <UTooltip text="Analyze how market conditions at entry affect trade performance">
          <UIcon name="i-lucide-help-circle" class="w-4 h-4 text-muted" />
        </UTooltip>
      </div>
    </template>

    <div v-if="loading" class="space-y-4">
      <USkeleton class="h-32 w-full" />
      <USkeleton class="h-64 w-full" />
    </div>

    <div v-else-if="!marketAvgs || !mcStats" class="text-center py-8">
      <UIcon name="i-lucide-globe" class="w-12 h-12 text-muted mx-auto mb-2" />
      <p class="text-muted">
        No market condition data available
      </p>
      <p class="text-sm text-muted mt-2">
        Market conditions are captured when SPY data is available during backtest
      </p>
    </div>

    <div v-else class="space-y-6">
      <!-- Summary Cards -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Market Conditions at Entry (Averages)
        </h4>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <UCard :ui="{ body: 'p-3' }">
            <div class="text-xs text-muted mb-1">
              Avg Market Breadth
            </div>
            <div class="font-semibold text-lg">
              {{ typeof marketAvgs.avgMarketBreadth === 'number' && !isNaN(marketAvgs.avgMarketBreadth) ? marketAvgs.avgMarketBreadth.toFixed(1) : 'N/A' }}
            </div>
            <div class="text-xs text-muted">
              % in uptrend
            </div>
          </UCard>

          <UCard :ui="{ body: 'p-3' }">
            <div class="text-xs text-muted mb-1">
              Trades in SPY Uptrend
            </div>
            <div class="font-semibold text-lg">
              {{ typeof marketAvgs.spyUptrendPercent === 'number' && !isNaN(marketAvgs.spyUptrendPercent) ? marketAvgs.spyUptrendPercent.toFixed(1) : 'N/A' }}%
            </div>
            <div class="text-xs text-muted">
              {{ mcStats.uptrendCount }} of {{ totalTradesWithData }} trades
            </div>
          </UCard>
        </div>
      </div>

      <!-- SPY Uptrend Performance Comparison -->
      <div v-if="mcStats.uptrendCount > 0 || mcStats.downtrendCount > 0">
        <h4 class="text-sm font-medium mb-3">
          Performance by Market Regime
        </h4>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <UCard :ui="{ body: 'p-3' }" class="border-2 border-success/20">
            <div class="flex items-center gap-2 mb-2">
              <UIcon name="i-lucide-trending-up" class="w-4 h-4 text-success" />
              <span class="text-sm font-medium text-success">SPY Uptrend</span>
            </div>
            <div class="font-semibold text-2xl">
              {{ mcStats.uptrendWinRate.toFixed(1) }}%
            </div>
            <div class="text-xs text-muted">
              Win rate ({{ mcStats.uptrendCount }} trades)
            </div>
          </UCard>

          <UCard :ui="{ body: 'p-3' }" class="border-2 border-error/20">
            <div class="flex items-center gap-2 mb-2">
              <UIcon name="i-lucide-trending-down" class="w-4 h-4 text-error" />
              <span class="text-sm font-medium text-error">SPY Downtrend</span>
            </div>
            <div class="font-semibold text-2xl">
              {{ mcStats.downtrendWinRate.toFixed(1) }}%
            </div>
            <div class="text-xs text-muted">
              Win rate ({{ mcStats.downtrendCount }} trades)
            </div>
          </UCard>
        </div>
      </div>

      <!-- Scatter Plot -->
      <div v-if="mcStats.scatterPoints.length > 0">
        <h4 class="text-sm font-medium mb-3">
          Trade Profit vs Market Breadth at Entry
        </h4>
        <ChartsScatterChart
          :series="breadthSeries"
          x-axis-label="Market Breadth (% in uptrend)"
          y-axis-label="Trade Profit %"
          :height="350"
          :colors="['#10b981', '#ef4444']"
        />
        <div class="text-xs text-muted mt-2 text-center">
          Correlation: {{ breadthCorrelation.toFixed(2) }}
          <span v-if="Math.abs(breadthCorrelation) < 0.3" class="text-warning">(weak)</span>
          <span v-else-if="Math.abs(breadthCorrelation) < 0.5" class="text-primary">(moderate)</span>
          <span v-else class="text-success">(strong)</span>
        </div>
      </div>

      <!-- Insights -->
      <UCard v-if="insights.length > 0" :ui="{ body: 'p-4' }" class="bg-primary/5">
        <div class="flex items-start gap-3">
          <UIcon name="i-lucide-lightbulb" class="w-5 h-5 text-primary mt-0.5" />
          <div class="flex-1">
            <h4 class="font-semibold mb-2">
              Market Condition Insights
            </h4>
            <ul class="space-y-2 text-sm">
              <li v-for="(insight, idx) in insights" :key="idx">
                <span class="text-muted">•</span>
                <span class="ml-2">{{ insight }}</span>
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
                <strong>Correlation</strong> measures the relationship between market conditions and trade performance.
              </p>
              <p>
                <strong class="text-success">Positive correlation (> 0.3)</strong> means trades perform better when the metric is higher.
              </p>
              <p>
                <strong class="text-error">Negative correlation (&lt; -0.3)</strong> means trades perform better when the metric is lower.
              </p>
              <p>
                <strong class="text-warning">Weak correlation (|r| &lt; 0.3)</strong> suggests market conditions don't strongly predict trade outcomes for this strategy.
              </p>
              <p class="text-muted">
                Use these insights to refine entry filters or avoid trading during unfavorable market conditions.
              </p>
            </div>
          </div>
        </div>
      </UCard>
    </div>
  </UCard>
</template>
