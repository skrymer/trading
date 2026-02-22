<script setup lang="ts">
import type { BacktestReport } from '@/types'

const props = defineProps<{
  report: BacktestReport | null
  loading?: boolean
}>()

// Count trades using underlying assets (pre-computed by backend)
const underlyingAssetTrades = computed(() => props.report?.underlyingAssetTradeCount ?? 0)

const underlyingAssetPercentage = computed(() => {
  if (!props.report || props.report.totalTrades === 0) return 0
  return (underlyingAssetTrades.value / props.report.totalTrades) * 100
})

// Format profit factor, showing ∞ when there are no losing trades
const formattedProfitFactor = computed(() => {
  if (!props.report) return '0.00'
  if (props.report.profitFactor === null) return '∞'
  return props.report.profitFactor.toFixed(2)
})

const formattedEdgeConsistency = computed(() => {
  const ecs = props.report?.edgeConsistencyScore
  if (!ecs) return 'N/A'
  return `${ecs.score.toFixed(0)} (${ecs.interpretation})`
})

const positionSizing = computed(() => props.report?.positionSizing)

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(value)
}
</script>

<template>
  <!-- Loading skeleton -->
  <UPageGrid v-if="loading" class="lg:grid-cols-8 gap-4 sm:gap-6 lg:gap-px w-full">
    <UPageCard
      v-for="i in 8"
      :key="i"
      variant="subtle"
      :ui="{
        container: 'gap-y-1.5',
        wrapper: 'items-start',
        leading: 'p-2.5 rounded-full bg-primary/10 ring ring-inset ring-primary/25 flex-col'
      }"
      class="lg:rounded-none first:rounded-l-lg last:rounded-r-lg"
    >
      <template #leading>
        <USkeleton class="h-5 w-5 rounded-full" />
      </template>
      <USkeleton class="h-3 w-24" />
      <USkeleton class="h-8 w-16" />
    </UPageCard>
  </UPageGrid>

  <!-- Loaded content -->
  <div v-else class="space-y-4">
    <UPageGrid class="lg:grid-cols-8 gap-4 sm:gap-6 lg:gap-px w-full">
      <BacktestingDataCard title="Number of wins" :content="report?.numberOfWinningTrades || 0" />
      <BacktestingDataCard title="Number of losses" :content="report?.numberOfLosingTrades || 0" />
      <BacktestingDataCard title="Win rate" :content="((report?.winRate || 0) * 100).toFixed(2) + '%'" />
      <BacktestingDataCard title="Average win" :content="(report?.averageWinPercent || 0).toFixed(2) + '%'" />
      <BacktestingDataCard title="Average loss" :content="(report?.averageLossPercent || 0).toFixed(2) + '%'" />
      <BacktestingDataCard title="Edge" :content="(report?.edge || 0).toFixed(2) + '%'" />
      <BacktestingDataCard title="Profit factor" :content="formattedProfitFactor" />
      <BacktestingDataCard title="Edge Consistency" :content="formattedEdgeConsistency" />
    </UPageGrid>

    <!-- Position Sizing Summary -->
    <UPageGrid v-if="positionSizing" class="lg:grid-cols-5 gap-4 sm:gap-6 lg:gap-px w-full">
      <BacktestingDataCard title="Starting Capital" :content="formatCurrency(positionSizing.startingCapital)" />
      <BacktestingDataCard title="Final Capital" :content="formatCurrency(positionSizing.finalCapital)" />
      <BacktestingDataCard title="Total Return" :content="positionSizing.totalReturnPct.toFixed(2) + '%'" />
      <BacktestingDataCard title="Max Drawdown" :content="positionSizing.maxDrawdownPct.toFixed(2) + '%'" />
      <BacktestingDataCard title="Peak Capital" :content="formatCurrency(positionSizing.peakCapital)" />
    </UPageGrid>

    <!-- Underlying Asset Info -->
    <UAlert
      v-if="underlyingAssetTrades > 0"
      color="info"
      variant="subtle"
      icon="i-lucide-info"
    >
      <template #description>
        <p class="text-sm">
          <span class="font-semibold">{{ underlyingAssetTrades }}</span> of <span class="font-semibold">{{ report?.totalTrades }}</span> trades
          (<span class="font-semibold">{{ underlyingAssetPercentage.toFixed(1) }}%</span>) used underlying asset signals for strategy evaluation.
        </p>
      </template>
    </UAlert>
  </div>
</template>

<style scoped>
</style>
