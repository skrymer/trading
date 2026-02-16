<script setup lang="ts">
import type { RateLimitStats, DatabaseStats, RefreshProgress } from '~/types'

const toast = useToast()

// Reactive data
const rateLimitStats = ref<RateLimitStats | null>(null)
const dbStats = ref<DatabaseStats | null>(null)
const refreshProgress = ref<RefreshProgress>({
  total: 0,
  completed: 0,
  failed: 0,
  lastSuccess: null,
  lastError: null
})

const loading = ref(false)
const pollingInterval = ref<number | null>(null)

// Load initial data
async function loadData() {
  loading.value = true
  try {
    const [rateLimitData, statsData, progressData] = await Promise.all([
      $fetch<RateLimitStats>('/udgaard/api/data-management/rate-limit'),
      $fetch<DatabaseStats>('/udgaard/api/data-management/stats'),
      $fetch<RefreshProgress>('/udgaard/api/data-management/refresh/progress')
    ])

    rateLimitStats.value = rateLimitData
    dbStats.value = statsData
    refreshProgress.value = progressData
  } catch (error) {
    console.error('Failed to load data:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load data management information',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

// Refresh all stocks
async function handleRefreshStocks(minDate: string) {
  try {
    await $fetch(`/udgaard/api/data-management/refresh/all-stocks?minDate=${minDate}`, {
      method: 'POST'
    })
    toast.add({
      title: 'Refresh Started',
      description: 'Stock data refresh has been queued',
      color: 'success'
    })
    startPolling()
  } catch (error) {
    console.error('Failed to start refresh:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to start stock refresh',
      color: 'error'
    })
  }
}

// Start polling for progress updates
function startPolling() {
  if (pollingInterval.value) return

  pollingInterval.value = setInterval(async () => {
    try {
      const [progressData, rateLimitData] = await Promise.all([
        $fetch<RefreshProgress>('/udgaard/api/data-management/refresh/progress'),
        $fetch<RateLimitStats>('/udgaard/api/data-management/rate-limit')
      ])

      refreshProgress.value = progressData
      rateLimitStats.value = rateLimitData

      // Stop polling if refresh is complete
      if (progressData.total > 0 && progressData.completed >= progressData.total) {
        stopPolling()
        toast.add({
          title: 'Refresh Complete',
          description: `Successfully refreshed ${progressData.completed} items`,
          color: 'success'
        })
        // Reload database stats
        loadData()
      }
    } catch (error) {
      console.error('Failed to poll progress:', error)
    }
  }, 3000) // Poll every 3 seconds
}

// Stop polling
function stopPolling() {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
    pollingInterval.value = null
  }
}

// Load data on mount
onMounted(() => {
  loadData()
})

// Cleanup on unmount
onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <UDashboardPanel id="data-manager">
    <template #header>
      <UDashboardNavbar title="Data Management">
        <template #right>
          <UButton
            label="Refresh Stats"
            icon="i-lucide-refresh-cw"
            variant="ghost"
            :loading="loading"
            @click="loadData"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <div v-if="loading && !rateLimitStats && !dbStats" class="flex items-center justify-center h-96">
      <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin" />
    </div>

    <div v-else class="space-y-6 p-6">
      <!-- Section 1: Rate Limit Status -->
      <DataManagementRateLimitCard v-if="rateLimitStats" :rate-limit="rateLimitStats" />

      <!-- Section 2: Database Statistics -->
      <DataManagementDatabaseStatsCards v-if="dbStats" :stats="dbStats" />

      <!-- Section 3: Refresh Controls -->
      <DataManagementRefreshControlsCard
        :progress="refreshProgress"
        @refresh-stocks="handleRefreshStocks"
      />

      <!-- Section 4: Breadth Data -->
      <DataManagementBreadthRefreshCard />
    </div>
  </UDashboardPanel>
</template>
