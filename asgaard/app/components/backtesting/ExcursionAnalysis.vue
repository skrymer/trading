<script setup lang="ts">
import type { ExcursionPoint, ExcursionSummary } from '~/types'

interface Props {
  excursionPoints: ExcursionPoint[]
  excursionSummary: ExcursionSummary | null
  loading: boolean
}

const props = defineProps<Props>()

// MFE vs MAE scatter data (all trades)
const mfeMaeScatterSeries = computed(() => {
  const winData = props.excursionPoints
    .filter(p => p.isWinner)
    .map(p => [p.mae, p.mfe])

  const lossData = props.excursionPoints
    .filter(p => !p.isWinner)
    .map(p => [p.mae, p.mfe])

  return [
    { name: 'Winners', data: winData },
    { name: 'Losers', data: lossData }
  ]
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

    <div v-else-if="excursionPoints.length === 0" class="text-center py-8">
      <UIcon name="i-lucide-trending-up-down" class="w-12 h-12 text-muted mx-auto mb-2" />
      <p class="text-muted">
        No excursion data available
      </p>
    </div>

    <div v-else class="space-y-6">
      <!-- Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Overall Summary -->
        <UCard v-if="excursionSummary" :ui="{ body: '!p-4' }">
          <div class="space-y-2">
            <h4 class="text-sm font-medium text-muted">
              Overall Summary
            </h4>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">Total Trades:</span>
                <span class="font-medium">{{ excursionSummary.totalTrades }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MFE:</span>
                <span class="font-medium text-success">{{ formatPercent(excursionSummary.avgMFE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MAE:</span>
                <span class="font-medium text-error">{{ formatPercent(excursionSummary.avgMAE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Reached Profit:</span>
                <span class="font-medium">{{ formatEfficiency(excursionSummary.profitReachRate) }}</span>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Winners Summary -->
        <UCard v-if="excursionSummary && excursionSummary.winnerCount > 0" :ui="{ body: '!p-4' }">
          <div class="space-y-2">
            <h4 class="text-sm font-medium text-success">
              Winning Trades ({{ excursionSummary.winnerCount }})
            </h4>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">Avg MFE:</span>
                <span class="font-medium text-success">{{ formatPercent(excursionSummary.winnerAvgMFE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MAE:</span>
                <span class="font-medium text-error">{{ formatPercent(excursionSummary.winnerAvgMAE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg Final:</span>
                <span class="font-medium text-success">{{ formatPercent(excursionSummary.winnerAvgFinalProfit) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">MFE Efficiency:</span>
                <span class="font-medium">{{ formatEfficiency(excursionSummary.avgMFEEfficiency) }}</span>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Losers Summary -->
        <UCard v-if="excursionSummary && excursionSummary.loserCount > 0" :ui="{ body: '!p-4' }">
          <div class="space-y-2">
            <h4 class="text-sm font-medium text-error">
              Losing Trades ({{ excursionSummary.loserCount }})
            </h4>
            <div class="space-y-1 text-sm">
              <div class="flex justify-between">
                <span class="text-muted">Avg MFE:</span>
                <span class="font-medium text-success">{{ formatPercent(excursionSummary.loserAvgMFE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg MAE:</span>
                <span class="font-medium text-error">{{ formatPercent(excursionSummary.loserAvgMAE) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Avg Final:</span>
                <span class="font-medium text-error">{{ formatPercent(excursionSummary.loserAvgFinalLoss) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted">Reached Profit:</span>
                <span class="font-medium text-warning">{{ formatEfficiency(excursionSummary.loserMissedWinRate) }}</span>
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
      <UCard v-if="excursionSummary" :ui="{ body: '!p-4' }">
        <div class="space-y-2">
          <h4 class="text-sm font-medium flex items-center gap-2">
            <UIcon name="i-lucide-lightbulb" class="w-4 h-4" />
            Key Insights
          </h4>
          <div class="space-y-2 text-sm text-muted">
            <div v-if="excursionSummary.winnerCount > 0" class="flex items-start gap-2">
              <UIcon name="i-lucide-check-circle" class="w-4 h-4 text-success mt-0.5 flex-shrink-0" />
              <p>
                Winning trades captured an average of <strong class="text-foreground">{{ formatEfficiency(excursionSummary.avgMFEEfficiency) }}</strong>
                of their maximum favorable excursion (MFE).
                <span v-if="excursionSummary.avgMFEEfficiency < 70" class="text-warning">
                  Consider tightening exits to capture more profits.
                </span>
                <span v-else-if="excursionSummary.avgMFEEfficiency > 90" class="text-success">
                  Excellent profit capture efficiency!
                </span>
              </p>
            </div>
            <div v-if="excursionSummary.loserMissedWinRate > 50" class="flex items-start gap-2">
              <UIcon name="i-lucide-alert-triangle" class="w-4 h-4 text-warning mt-0.5 flex-shrink-0" />
              <p>
                <strong class="text-foreground">{{ formatEfficiency(excursionSummary.loserMissedWinRate) }}</strong> of losing trades
                reached positive territory before exiting. This suggests potential for improved exit timing or trailing stops.
              </p>
            </div>
            <div v-if="excursionSummary.winnerCount > 0 && Math.abs(excursionSummary.winnerAvgMAE) > 3" class="flex items-start gap-2">
              <UIcon name="i-lucide-info" class="w-4 h-4 text-info mt-0.5 flex-shrink-0" />
              <p>
                Winning trades experienced an average drawdown of
                <strong class="text-foreground">{{ formatPercent(excursionSummary.winnerAvgMAE) }}</strong>
                before reaching their final profit. Ensure position sizing accounts for this volatility.
              </p>
            </div>
          </div>
        </div>
      </UCard>
    </div>
  </UCard>
</template>
