<script setup lang="ts">
definePageMeta({
  layout: 'default'
})

interface MetricResponse {
  name: string
  measurements: { statistic: string, value: number }[]
  availableTags: { tag: string, values: string[] }[]
}

const loading = ref(false)
const heapUsed = ref(0)
const heapMax = ref(0)
const nonHeapUsed = ref(0)
const uptime = ref(0)
const cpuUsage = ref(0)
const liveThreads = ref(0)
const cpuCount = ref(0)

function formatBytes(bytes: number): string {
  const mb = bytes / (1024 * 1024)
  if (mb >= 1024) {
    return `${(mb / 1024).toFixed(2)} GB`
  }
  return `${mb.toFixed(1)} MB`
}

function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  parts.push(`${minutes}m`)
  return parts.join(' ')
}

function extractValue(data: MetricResponse): number {
  return data.measurements?.[0]?.value ?? 0
}

async function fetchMetric(name: string, tags?: string): Promise<number> {
  const url = tags
    ? `/udgaard/actuator/metrics/${name}?tag=${tags}`
    : `/udgaard/actuator/metrics/${name}`
  const data = await $fetch<MetricResponse>(url)
  return extractValue(data)
}

const heapPercent = computed(() => {
  if (heapMax.value === 0) return 0
  return (heapUsed.value / heapMax.value) * 100
})

const heapColor = computed(() => {
  if (heapPercent.value > 80) return 'error' as const
  if (heapPercent.value > 60) return 'warning' as const
  return 'success' as const
})

async function loadMetrics() {
  loading.value = true
  try {
    const [hu, hm, nhu, ut, cpu, threads, cores] = await Promise.all([
      fetchMetric('jvm.memory.used', 'area:heap'),
      fetchMetric('jvm.memory.max', 'area:heap'),
      fetchMetric('jvm.memory.used', 'area:nonheap'),
      fetchMetric('process.uptime'),
      fetchMetric('process.cpu.usage'),
      fetchMetric('jvm.threads.live'),
      fetchMetric('system.cpu.count')
    ])
    heapUsed.value = hu
    heapMax.value = hm
    nonHeapUsed.value = nhu
    uptime.value = ut
    cpuUsage.value = cpu
    liveThreads.value = threads
    cpuCount.value = cores
  } catch (error) {
    console.error('Failed to fetch metrics:', error)
    useToast().add({
      title: 'Error',
      description: 'Failed to load application metrics',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMetrics()
})
</script>

<template>
  <UDashboardPanel id="app-metrics">
    <template #header>
      <UDashboardNavbar title="App Metrics">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            :loading="loading"
            @click="loadMetrics"
          >
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Loading State -->
      <div v-if="loading && heapMax === 0" class="flex justify-center py-12">
        <UIcon name="i-lucide-loader" class="animate-spin text-4xl" />
      </div>

      <!-- Metrics Content -->
      <div v-else class="space-y-6 p-4">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <!-- Card 1: JVM Memory -->
          <UCard>
            <template #header>
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-memory-stick" class="w-5 h-5 text-primary" />
                <h3 class="font-semibold">
                  JVM Memory
                </h3>
              </div>
            </template>

            <div class="space-y-4">
              <div>
                <div class="flex items-center justify-between text-sm mb-1">
                  <span class="text-muted">Heap Usage</span>
                  <span class="font-medium">{{ formatBytes(heapUsed) }} / {{ formatBytes(heapMax) }}</span>
                </div>
                <UProgress :value="heapPercent" :color="heapColor" size="md" />
                <p class="text-xs text-muted mt-1">
                  {{ heapPercent.toFixed(1) }}% used
                </p>
              </div>

              <div class="flex items-center justify-between text-sm pt-2 border-t border-default">
                <span class="text-muted">Non-Heap Used</span>
                <span class="font-medium">{{ formatBytes(nonHeapUsed) }}</span>
              </div>
            </div>
          </UCard>

          <!-- Card 2: Runtime -->
          <UCard>
            <template #header>
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-timer" class="w-5 h-5 text-primary" />
                <h3 class="font-semibold">
                  Runtime
                </h3>
              </div>
            </template>

            <div class="space-y-3">
              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">Uptime</span>
                <span class="font-medium">{{ formatUptime(uptime) }}</span>
              </div>

              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">CPU Usage</span>
                <span class="font-medium">{{ (cpuUsage * 100).toFixed(1) }}%</span>
              </div>

              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">Live Threads</span>
                <span class="font-medium">{{ liveThreads }}</span>
              </div>

              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">CPU Cores</span>
                <span class="font-medium">{{ cpuCount }}</span>
              </div>
            </div>
          </UCard>

          <!-- Card 3: JVM Info -->
          <UCard>
            <template #header>
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-info" class="w-5 h-5 text-primary" />
                <h3 class="font-semibold">
                  JVM Info
                </h3>
              </div>
            </template>

            <div class="space-y-3">
              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">Max Heap</span>
                <span class="font-medium">{{ formatBytes(heapMax) }}</span>
              </div>

              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">Heap Free</span>
                <span class="font-medium">{{ formatBytes(heapMax - heapUsed) }}</span>
              </div>

              <div class="flex items-center justify-between text-sm">
                <span class="text-muted">Total Allocated</span>
                <span class="font-medium">{{ formatBytes(heapUsed + nonHeapUsed) }}</span>
              </div>
            </div>
          </UCard>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
