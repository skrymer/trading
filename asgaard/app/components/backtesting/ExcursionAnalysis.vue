<script setup lang="ts">
import type { BacktestReport } from '~/types'

interface Props {
  report: BacktestReport | null
  loading: boolean
}

const props = defineProps<Props>()

// Filter trades with excursion metrics
const tradesWithExcursions = computed(() => {
  if (!props.report?.trades) return []
  return props.report.trades.filter(t => t.excursionMetrics !== undefined && t.excursionMetrics !== null)
})

// Separate winning and losing trades
const winningTrades = computed(() => tradesWithExcursions.value.filter(t => t.profitPercentage > 0))
const losingTrades = computed(() => tradesWithExcursions.value.filter(t => t.profitPercentage <= 0))

// MFE vs MAE scatter data (all trades)
const mfeMaeScatterSeries = computed(() => {
  const winData = winningTrades.value.map(t => [
    t.excursionMetrics!.maxAdverseExcursion,
    t.excursionMetrics!.maxFavorableExcursion
  ])

  const lossData = losingTrades.value.map(t => [
    t.excursionMetrics!.maxAdverseExcursion,
    t.excursionMetrics!.maxFavorableExcursion
  ])

  return [
    { name: 'Winners', data: winData },
    { name: 'Losers', data: lossData }
  ]
})

// MFE Efficiency - How much of MFE was captured at exit
const mfeEfficiency = computed(() => {
  return winningTrades.value.map((t) => {
    const mfe = t.excursionMetrics!.maxFavorableExcursion
    const finalProfit = t.profitPercentage
    return mfe > 0 ? (finalProfit / mfe) * 100 : 0
  })
})

const avgMfeEfficiency = computed(() => {
  if (mfeEfficiency.value.length === 0) return 0
  return mfeEfficiency.value.reduce((sum, eff) => sum + eff, 0) / mfeEfficiency.value.length
})

// Summary statistics
const summaryStats = computed(() => {
  const trades = tradesWithExcursions.value
  if (trades.length === 0) return null

  const avgMFE = trades.reduce((sum, t) => sum + t.excursionMetrics!.maxFavorableExcursion, 0) / trades.length
  const avgMAE = trades.reduce((sum, t) => sum + t.excursionMetrics!.maxAdverseExcursion, 0) / trades.length
  const avgMFEATR = trades.reduce((sum, t) => sum + t.excursionMetrics!.maxFavorableExcursionATR, 0) / trades.length
  const avgMAEATR = trades.reduce((sum, t) => sum + t.excursionMetrics!.maxAdverseExcursionATR, 0) / trades.length

  const tradesReachingProfit = trades.filter(t => t.excursionMetrics!.mfeReached).length
  const profitReachRate = (tradesReachingProfit / trades.length) * 100

  return {
    totalTrades: trades.length,
    avgMFE,
    avgMAE,
    avgMFEATR,
    avgMAEATR,
    profitReachRate,
    avgEfficiency: avgMfeEfficiency.value
  }
})

// Winning trades summary
const winningSummary = computed(() => {
  if (winningTrades.value.length === 0) return null

  const avgMFE = winningTrades.value.reduce((sum, t) => sum + t.excursionMetrics!.maxFavorableExcursion, 0) / winningTrades.value.length
  const avgMAE = winningTrades.value.reduce((sum, t) => sum + t.excursionMetrics!.maxAdverseExcursion, 0) / winningTrades.value.length
  const avgFinalProfit = winningTrades.value.reduce((sum, t) => sum + t.profitPercentage, 0) / winningTrades.value.length

  return { avgMFE, avgMAE, avgFinalProfit, count: winningTrades.value.length }
})

// Losing trades summary
const losingSummary = computed(() => {
  if (losingTrades.value.length === 0) return null

  const avgMFE = losingTrades.value.reduce((sum, t) => sum + t.excursionMetrics!.maxFavorableExcursion, 0) / losingTrades.value.length
  const avgMAE = losingTrades.value.reduce((sum, t) => sum + t.excursionMetrics!.maxAdverseExcursion, 0) / losingTrades.value.length
  const avgFinalLoss = losingTrades.value.reduce((sum, t) => sum + t.profitPercentage, 0) / losingTrades.value.length

  const reachedProfit = losingTrades.value.filter(t => t.excursionMetrics!.mfeReached).length
  const missedWinRate = (reachedProfit / losingTrades.value.length) * 100

  return { avgMFE, avgMAE, avgFinalLoss, count: losingTrades.value.length, missedWinRate }
})

// Format helpers
const formatPercent = (value: number) => `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
const formatEfficiency = (value: number) => `${value.toFixed(1)}%`
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-trending-up-down" class="w-5 h-5" />
        <h3 class="text-lg font-semibold">
          Excursion Analysis (MFE/MAE)
        </h3>
        <UTooltip
          text="Maximum Favorable Excursion (MFE) is the highest profit reached during a trade. Maximum Adverse Excursion (MAE) is the deepest drawdown. These metrics help evaluate trade management and exit timing."
        >
          <UIcon name="i-lucide-info" class="w-4 h-4 text-muted cursor-help" />
        </UTooltip>
      </div>
    </template>

    <div v-if="loading" class="space-y-4">
      <USkeleton class="h-64 w-full" />
      <USkeleton class="h-64 w-full" />
    </div>

    <div v-else-if="!report || tradesWithExcursions.length === 0" class="text-center py-8">
      <UIcon name="i-lucide-trending-up-down" class="w-12 h-12 text-muted mx-auto mb-2" />
      <p class="text-muted">
        No excursion data available
      </p>
    </div>

    <div v-else class="space-y-6">
      <!-- Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Overall Summary -->
        <UCard v-if="summaryStats" :ui="{ body: '!p-4' }">
          <div class="space-y-2">
            <h4 class="text-sm font-medium text-muted">
              Overall Summary
            </h4>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">Total Trades:</span>
                <span class="font-medium">{{ summaryStats.totalTrades }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MFE:</span>
                <span class="font-medium text-success">{{ formatPercent(summaryStats.avgMFE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MAE:</span>
                <span class="font-medium text-error">{{ formatPercent(summaryStats.avgMAE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Reached Profit:</span>
                <span class="font-medium">{{ formatEfficiency(summaryStats.profitReachRate) }}</span>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Winners Summary -->
        <UCard v-if="winningSummary" :ui="{ body: '!p-4' }">
          <div class="space-y-2">
            <h4 class="text-sm font-medium text-success">
              Winning Trades ({{ winningSummary.count }})
            </h4>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">Avg MFE:</span>
                <span class="font-medium text-success">{{ formatPercent(winningSummary.avgMFE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MAE:</span>
                <span class="font-medium text-error">{{ formatPercent(winningSummary.avgMAE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg Final:</span>
                <span class="font-medium text-success">{{ formatPercent(winningSummary.avgFinalProfit) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">MFE Efficiency:</span>
                <span class="font-medium">{{ formatEfficiency(avgMfeEfficiency) }}</span>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Losers Summary -->
        <UCard v-if="losingSummary" :ui="{ body: '!p-4' }">
          <div class="space-y-2">
            <h4 class="text-sm font-medium text-error">
              Losing Trades ({{ losingSummary.count }})
            </h4>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">Avg MFE:</span>
                <span class="font-medium text-success">{{ formatPercent(losingSummary.avgMFE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MAE:</span>
                <span class="font-medium text-error">{{ formatPercent(losingSummary.avgMAE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg Final:</span>
                <span class="font-medium text-error">{{ formatPercent(losingSummary.avgFinalLoss) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Reached Profit:</span>
                <span class="font-medium text-warning">{{ formatEfficiency(losingSummary.missedWinRate) }}</span>
              </div>
            </div>
          </div>
        </UCard>
      </div>

      <!-- MFE vs MAE Scatter Plot -->
      <div>
        <h4 class="text-sm font-medium mb-3">
          Maximum Favorable vs Adverse Excursion
        </h4>
        <p class="text-xs text-muted mb-3">
          Shows the relationship between drawdown (MAE) and maximum profit reached (MFE) for each trade.
          Winners typically have higher MFE relative to MAE, while losers show limited MFE.
        </p>
        <ChartsScatterChart
          :series="mfeMaeScatterSeries"
          x-axis-label="MAE (Max Adverse Excursion %)"
          y-axis-label="MFE (Max Favorable Excursion %)"
          :colors="['#10b981', '#ef4444']"
          :height="350"
        />
      </div>

      <!-- Insights -->
      <UCard :ui="{ body: '!p-4' }">
        <div class="space-y-2">
          <h4 class="text-sm font-medium flex items-center gap-2">
            <UIcon name="i-lucide-lightbulb" class="w-4 h-4" />
            Key Insights
          </h4>
          <div class="space-y-2 text-sm text-muted">
            <div v-if="winningSummary" class="flex items-start gap-2">
              <UIcon name="i-lucide-check-circle" class="w-4 h-4 text-success mt-0.5 flex-shrink-0" />
              <p>
                Winning trades captured an average of <strong class="text-foreground">{{ formatEfficiency(avgMfeEfficiency) }}</strong>
                of their maximum favorable excursion (MFE).
                <span v-if="avgMfeEfficiency < 70" class="text-warning">
                  Consider tightening exits to capture more profits.
                </span>
                <span v-else-if="avgMfeEfficiency > 90" class="text-success">
                  Excellent profit capture efficiency!
                </span>
              </p>
            </div>
            <div v-if="losingSummary && losingSummary.missedWinRate > 50" class="flex items-start gap-2">
              <UIcon name="i-lucide-alert-triangle" class="w-4 h-4 text-warning mt-0.5 flex-shrink-0" />
              <p>
                <strong class="text-foreground">{{ formatEfficiency(losingSummary.missedWinRate) }}</strong> of losing trades
                reached positive territory before exiting. This suggests potential for improved exit timing or trailing stops.
              </p>
            </div>
            <div v-if="winningSummary && Math.abs(winningSummary.avgMAE) > 3" class="flex items-start gap-2">
              <UIcon name="i-lucide-info" class="w-4 h-4 text-info mt-0.5 flex-shrink-0" />
              <p>
                Winning trades experienced an average drawdown of
                <strong class="text-foreground">{{ formatPercent(winningSummary.avgMAE) }}</strong>
                before reaching their final profit. Ensure position sizing accounts for this volatility.
              </p>
            </div>
          </div>
        </div>
      </UCard>
    </div>
  </UCard>
</template>
