<script setup lang="ts">
interface ProviderStats {
  requestsLastSecond: number
  requestsLastMinute: number
  requestsLastDay: number
  remainingSecond: number
  remainingMinute: number
  remainingDaily: number
  secondLimit: number
  minuteLimit: number
  dailyLimit: number
}

const providers = ref<Record<string, ProviderStats>>({})
const loading = ref(true)

async function loadProviders() {
  try {
    providers.value = await $fetch<Record<string, ProviderStats>>('/udgaard/api/data-management/rate-limit/all')
  } catch (error) {
    console.error('Failed to load provider stats:', error)
  } finally {
    loading.value = false
  }
}

function formatProvider(id: string): string {
  return id.charAt(0).toUpperCase() + id.slice(1)
}

onMounted(() => loadProviders())
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-lg font-semibold">
        Data Providers
      </h3>
    </template>

    <div v-if="loading" class="flex items-center justify-center py-4">
      <UIcon name="i-lucide-loader-2" class="w-5 h-5 animate-spin" />
    </div>

    <div v-else class="space-y-4">
      <div v-for="(stats, providerId) in providers" :key="providerId" class="flex items-center justify-between p-3 rounded-lg bg-(--ui-bg-elevated)">
        <div>
          <p class="font-medium">
            {{ formatProvider(providerId as string) }}
          </p>
          <p class="text-sm text-(--ui-text-muted)">
            {{ stats.secondLimit }}/sec &middot; {{ stats.minuteLimit }}/min &middot; {{ stats.dailyLimit.toLocaleString() }}/day
          </p>
        </div>
        <div class="text-right text-sm">
          <p class="text-(--ui-text-muted)">
            Today: {{ stats.requestsLastDay.toLocaleString() }} requests
          </p>
        </div>
      </div>

      <p v-if="Object.keys(providers).length === 0" class="text-sm text-(--ui-text-muted)">
        No providers registered
      </p>
    </div>
  </UCard>
</template>
