<script setup lang="ts">
import type { DatabaseStats } from '~/types'

const props = defineProps<{
  stats: DatabaseStats
}>()
</script>

<template>
  <div>
    <h3 class="text-sm font-semibold text-muted mb-3">
      Database Statistics
    </h3>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
      <!-- Total Stocks -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            Stocks in Database
          </p>
          <p class="text-3xl font-bold">
            {{ stats.stockStats.totalStocks.toLocaleString() }}
          </p>
          <p class="text-xs text-muted mt-1">
            S&P 500 constituents
          </p>
        </div>
      </UCard>

      <!-- Breadth Symbols -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            Market Breadth Symbols
          </p>
          <p class="text-3xl font-bold">
            {{ stats.breadthStats.totalBreadthSymbols }}
          </p>
          <p class="text-xs text-muted mt-1">
            SPY, QQQ, IWM
          </p>
        </div>
      </UCard>

      <!-- Total ETFs -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            ETFs Tracked
          </p>
          <p class="text-3xl font-bold">
            {{ stats.etfStats.totalEtfs }}
          </p>
          <p class="text-xs text-muted mt-1">
            Sector ETFs
          </p>
        </div>
      </UCard>
    </div>
  </div>
</template>
