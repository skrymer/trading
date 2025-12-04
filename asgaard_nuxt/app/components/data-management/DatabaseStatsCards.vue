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

    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <!-- Total Stocks -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            Total Stocks
          </p>
          <p class="text-2xl font-bold">
            {{ stats.stockStats.totalStocks }}
          </p>
          <p class="text-xs text-muted mt-1">
            {{ Math.round(stats.stockStats.averageQuotesPerStock) }} avg quotes/stock
          </p>
        </div>
      </UCard>

      <!-- Total Quotes -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            Total Quotes
          </p>
          <p class="text-2xl font-bold">
            {{ stats.stockStats.totalQuotes.toLocaleString() }}
          </p>
          <p class="text-xs text-muted mt-1">
            {{ stats.stockStats.dateRange?.days || 0 }} days range
          </p>
        </div>
      </UCard>

      <!-- Breadth Data -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            Breadth Symbols
          </p>
          <p class="text-2xl font-bold">
            {{ stats.breadthStats.totalBreadthSymbols }}
          </p>
          <p class="text-xs text-muted mt-1">
            {{ stats.breadthStats.totalBreadthQuotes }} quotes
          </p>
        </div>
      </UCard>

      <!-- Last Updated -->
      <UCard>
        <div>
          <p class="text-sm text-muted">
            Last Updated
          </p>
          <p class="text-xl font-bold">
            {{ stats.stockStats.lastUpdatedStock?.symbol || 'N/A' }}
          </p>
          <p class="text-xs text-muted mt-1">
            {{ stats.stockStats.lastUpdatedStock?.lastQuoteDate || 'Never' }}
          </p>
        </div>
      </UCard>
    </div>

    <!-- Date Range -->
    <UCard v-if="stats.stockStats.dateRange" class="mt-4">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-muted">
            Data Coverage
          </p>
          <p class="text-lg font-semibold">
            {{ stats.stockStats.dateRange.earliest }} to {{ stats.stockStats.dateRange.latest }}
          </p>
        </div>
        <UBadge color="success" size="lg">
          {{ stats.stockStats.dateRange.days }} days
        </UBadge>
      </div>
    </UCard>
  </div>
</template>
