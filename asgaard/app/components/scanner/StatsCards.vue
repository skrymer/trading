<script setup lang="ts">
import type { ScannerTrade, ExitCheckResult } from '~/types'

const props = defineProps<{
  trades: ScannerTrade[]
  exitResults: Map<number, ExitCheckResult>
  capitalDeployed?: number
  portfolioValue?: number
  drawdownPct?: number
  effectiveRisk?: number
  baseRisk?: number
  drawdownScalingActive?: boolean
}>()

const activeTrades = computed(() => props.trades.length)

const exitsTriggered = computed(() => {
  let count = 0
  for (const [, result] of props.exitResults) {
    if (result.exitTriggered) count++
  }
  return count
})

const avgPnl = computed(() => {
  const results = Array.from(props.exitResults.values())
  if (results.length === 0) return 0
  return results.reduce((sum, r) => sum + r.unrealizedPnlPercent, 0) / results.length
})

const totalRolledCredits = computed(() => {
  return props.trades.reduce((sum, t) => sum + t.rolledCredits, 0)
})

const utilizationPct = computed(() => {
  if (!props.capitalDeployed || !props.portfolioValue || props.portfolioValue <= 0) return null
  return (props.capitalDeployed / props.portfolioValue) * 100
})

const showDrawdown = computed(() => props.drawdownScalingActive && props.drawdownPct != null)

const drawdownColor = computed(() => {
  if (props.drawdownPct == null) return ''
  if (props.drawdownPct >= 10) return 'text-red-600 dark:text-red-400'
  if (props.drawdownPct >= 5) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-green-600 dark:text-green-400'
})
</script>

<template>
  <div
    :class="[
      'grid grid-cols-2 gap-4',
      showDrawdown ? 'lg:grid-cols-6' : utilizationPct !== null ? 'lg:grid-cols-5' : 'lg:grid-cols-4'
    ]"
  >
    <div class="p-4 bg-muted/50 rounded-lg border border-default">
      <div class="text-sm text-muted">
        Active Trades
      </div>
      <div class="text-2xl font-bold mt-1">
        {{ activeTrades }}
      </div>
    </div>

    <div class="p-4 bg-muted/50 rounded-lg border border-default">
      <div class="text-sm text-muted">
        Exits Triggered
      </div>
      <div
        :class="[
          'text-2xl font-bold mt-1',
          exitsTriggered > 0 ? 'text-red-600 dark:text-red-400' : ''
        ]"
      >
        {{ exitsTriggered }}
      </div>
    </div>

    <div class="p-4 bg-muted/50 rounded-lg border border-default">
      <div class="text-sm text-muted">
        Avg Unrealized P&L
      </div>
      <div
        :class="[
          'text-2xl font-bold mt-1',
          avgPnl >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
        ]"
      >
        {{ avgPnl >= 0 ? '+' : '' }}{{ avgPnl.toFixed(1) }}%
      </div>
    </div>

    <div class="p-4 bg-muted/50 rounded-lg border border-default">
      <div class="text-sm text-muted">
        Total Rolled Credits
      </div>
      <div
        :class="[
          'text-2xl font-bold mt-1',
          totalRolledCredits >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
        ]"
      >
        {{ totalRolledCredits >= 0 ? '+' : '' }}${{ totalRolledCredits.toFixed(2) }}
      </div>
    </div>

    <div v-if="utilizationPct !== null" class="p-4 bg-muted/50 rounded-lg border border-default">
      <div class="text-sm text-muted">
        Capital Deployed
      </div>
      <div class="text-2xl font-bold mt-1">
        ${{ capitalDeployed!.toLocaleString('en-US', { maximumFractionDigits: 0 }) }}
      </div>
      <div class="text-xs text-muted mt-1">
        {{ utilizationPct!.toFixed(1) }}% of ${{ portfolioValue!.toLocaleString('en-US', { maximumFractionDigits: 0 }) }}
      </div>
    </div>

    <div v-if="showDrawdown" class="p-4 bg-muted/50 rounded-lg border border-default">
      <div class="text-sm text-muted">
        Drawdown
      </div>
      <div :class="['text-2xl font-bold mt-1', drawdownColor]">
        {{ drawdownPct!.toFixed(1) }}%
      </div>
      <div v-if="effectiveRisk !== baseRisk" class="text-xs text-muted mt-1">
        Risk: <span class="line-through">{{ baseRisk }}%</span>
        <span class="text-warning ml-1">{{ effectiveRisk!.toFixed(2) }}%</span>
      </div>
    </div>
  </div>
</template>
