<script setup lang="ts">
import type { BreadthCoverageStats, DatabaseStats } from '~/types'

defineProps<{
  stats: DatabaseStats
}>()

const coverage = ref<BreadthCoverageStats | null>(null)

async function loadCoverage() {
  try {
    coverage.value = await $fetch<BreadthCoverageStats>('/udgaard/api/data-management/breadth-coverage')
  } catch (error) {
    console.error('Failed to load breadth coverage:', error)
  }
}

onMounted(() => {
  loadCoverage()
})
</script>

<template>
  <div>
    <h3 class="text-sm font-semibold text-muted mb-3">
      Database Statistics
    </h3>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
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

      <!-- Breadth Coverage -->
      <UCard v-if="coverage">
        <div>
          <p class="text-sm text-muted">
            Market Breadth
          </p>
          <p class="text-3xl font-bold">
            {{ coverage.totalStocks }} stocks
          </p>
          <div v-if="coverage.sectors.length > 0" class="mt-2">
            <p class="text-xs text-muted mb-1">
              Sector Breadth
            </p>
            <div class="flex flex-wrap gap-1">
              <UBadge
                v-for="sector in coverage.sectors"
                :key="sector.sectorSymbol"
                variant="subtle"
                color="neutral"
                size="lg"
              >
                {{ sector.sectorSymbol }}: {{ sector.totalStocks }}
              </UBadge>
            </div>
          </div>
        </div>
      </UCard>
    </div>
  </div>
</template>
