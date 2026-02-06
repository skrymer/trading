<script setup lang="ts">
import type { BacktestReport, Trade } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

// Market condition averages from report
const marketAvgs = computed(() => props.report?.marketConditionAverages)

// Filter trades with market condition data
const tradesWithMarketData = computed(() => {
  if (!props.report?.trades) return []
  return props.report.trades.filter(t => t.marketConditionAtEntry != null)
})

// Scatter plot data: Trade Profit vs SPY Heatmap
const heatmapScatterData = computed(() => {
  return tradesWithMarketData.value
    .filter(t => t.marketConditionAtEntry?.spyHeatmap != null)
    .map(t => ({
      x: t.marketConditionAtEntry!.spyHeatmap!,
      y: t.profitPercentage,
      isWinner: t.profit > 0
    }))
})

// Scatter plot data: Trade Profit vs Market Breadth
const breadthScatterData = computed(() => {
  return tradesWithMarketData.value
    .filter(t => t.marketConditionAtEntry?.marketBreadthBullPercent != null)
    .map(t => ({
      x: t.marketConditionAtEntry!.marketBreadthBullPercent!,
      y: t.profitPercentage,
      isWinner: t.profit > 0
    }))
})

// ApexCharts series for SPY Heatmap scatter
const heatmapSeries = computed(() => {
  const winners = heatmapScatterData.value.filter(d => d.isWinner)
  const losers = heatmapScatterData.value.filter(d => !d.isWinner)

  return [
    {
      name: 'Winning Trades',
      data: winners.map(d => [d.x, d.y])
    },
    {
      name: 'Losing Trades',
      data: losers.map(d => [d.x, d.y])
    }
  ]
})

// ApexCharts series for Market Breadth scatter
const breadthSeries = computed(() => {
  const winners = breadthScatterData.value.filter(d => d.isWinner)
  const losers = breadthScatterData.value.filter(d => !d.isWinner)

  return [
    {
      name: 'Winning Trades',
      data: winners.map(d => [d.x, d.y])
    },
    {
      name: 'Losing Trades',
      data: losers.map(d => [d.x, d.y])
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

const heatmapCorrelation = computed(() => calculateCorrelation(heatmapScatterData.value))
const breadthCorrelation = computed(() => calculateCorrelation(breadthScatterData.value))

// Performance during SPY uptrend vs downtrend
const uptrendStats = computed(() => {
  if (!tradesWithMarketData.value.length) return null

  const uptrendTrades = tradesWithMarketData.value.filter(t => t.marketConditionAtEntry?.spyInUptrend)
  const downtrendTrades = tradesWithMarketData.value.filter(t => !t.marketConditionAtEntry?.spyInUptrend)

  const calcWinRate = (trades: Trade[]) => {
    if (trades.length === 0) return 0
    return (trades.filter(t => t.profit > 0).length / trades.length) * 100
  }

  return {
    uptrendWinRate: calcWinRate(uptrendTrades),
    downtrendWinRate: calcWinRate(downtrendTrades),
    uptrendCount: uptrendTrades.length,
    downtrendCount: downtrendTrades.length
  }
})

// Insights
const insights = computed(() => {
  const results: string[] = []

  // Heatmap insights
  if (Math.abs(heatmapCorrelation.value) > 0.3) {
    const direction = heatmapCorrelation.value > 0 ? 'positive' : 'negative'
    const strength = Math.abs(heatmapCorrelation.value) > 0.5 ? 'strong' : 'moderate'
    results.push(`${strength} ${direction} correlation between SPY heatmap and trade performance (r=${heatmapCorrelation.value.toFixed(2)})`)
  }

  // Breadth insights
  if (Math.abs(breadthCorrelation.value) > 0.3) {
    const direction = breadthCorrelation.value > 0 ? 'positive' : 'negative'
    const strength = Math.abs(breadthCorrelation.value) > 0.5 ? 'strong' : 'moderate'
    results.push(`${strength} ${direction} correlation between market breadth and trade performance (r=${breadthCorrelation.value.toFixed(2)})`)
  }

  // Uptrend insights
  if (uptrendStats.value) {
    const diff = uptrendStats.value.uptrendWinRate - uptrendStats.value.downtrendWinRate
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

    <div v-else-if="!marketAvgs || tradesWithMarketData.length === 0" class="text-center py-8">
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
        <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
          <UCard :ui="{ body: 'p-3' }">
            <div class="text-xs text-muted mb-1">
              Avg SPY Heatmap
            </div>
            <div class="font-semibold text-lg">
              {{ typeof marketAvgs.avgSpyHeatmap === 'number' && !isNaN(marketAvgs.avgSpyHeatmap) ? marketAvgs.avgSpyHeatmap.toFixed(1) : 'N/A' }}
            </div>
            <div class="text-xs text-muted">
              0-100 scale
            </div>
          </UCard>

          <UCard :ui="{ body: 'p-3' }">
            <div class="text-xs text-muted mb-1">
              Avg Market Breadth
            </div>
            <div class="font-semibold text-lg">
              {{ typeof marketAvgs.avgMarketBreadth === 'number' && !isNaN(marketAvgs.avgMarketBreadth) ? marketAvgs.avgMarketBreadth.toFixed(1) : 'N/A' }}
            </div>
            <div class="text-xs text-muted">
              % bullish
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
              {{ uptrendStats?.uptrendCount ?? 0 }} of {{ tradesWithMarketData.length }} trades
            </div>
          </UCard>
        </div>
      </div>

      <!-- SPY Uptrend Performance Comparison -->
      <div v-if="uptrendStats && (uptrendStats.uptrendCount > 0 || uptrendStats.downtrendCount > 0)">
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
              {{ uptrendStats.uptrendWinRate.toFixed(1) }}%
            </div>
            <div class="text-xs text-muted">
              Win rate ({{ uptrendStats.uptrendCount }} trades)
            </div>
          </UCard>

          <UCard :ui="{ body: 'p-3' }" class="border-2 border-error/20">
            <div class="flex items-center gap-2 mb-2">
              <UIcon name="i-lucide-trending-down" class="w-4 h-4 text-error" />
              <span class="text-sm font-medium text-error">SPY Downtrend</span>
            </div>
            <div class="font-semibold text-2xl">
              {{ uptrendStats.downtrendWinRate.toFixed(1) }}%
            </div>
            <div class="text-xs text-muted">
              Win rate ({{ uptrendStats.downtrendCount }} trades)
            </div>
          </UCard>
        </div>
      </div>

      <!-- Scatter Plots -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- SPY Heatmap Scatter -->
        <div v-if="heatmapScatterData.length > 0">
          <h4 class="text-sm font-medium mb-3">
            Trade Profit vs SPY Heatmap at Entry
          </h4>
          <ChartsScatterChart
            :series="heatmapSeries"
            x-axis-label="SPY Heatmap (0-100)"
            y-axis-label="Trade Profit %"
            :height="350"
            :colors="['#10b981', '#ef4444']"
          />
          <div class="text-xs text-muted mt-2 text-center">
            Correlation: {{ heatmapCorrelation.toFixed(2) }}
            <span v-if="Math.abs(heatmapCorrelation) < 0.3" class="text-warning">(weak)</span>
            <span v-else-if="Math.abs(heatmapCorrelation) < 0.5" class="text-primary">(moderate)</span>
            <span v-else class="text-success">(strong)</span>
          </div>
        </div>

        <!-- Market Breadth Scatter -->
        <div v-if="breadthScatterData.length > 0">
          <h4 class="text-sm font-medium mb-3">
            Trade Profit vs Market Breadth at Entry
          </h4>
          <ChartsScatterChart
            :series="breadthSeries"
            x-axis-label="Market Breadth (% bullish)"
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
