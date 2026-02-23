<script setup lang="ts">
import type { ScannerTrade, ExitCheckResult } from '~/types'

const props = defineProps<{
  trades: ScannerTrade[]
  exitResults: Map<number, ExitCheckResult>
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
</script>

<template>
  <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
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
  </div>
</template>
