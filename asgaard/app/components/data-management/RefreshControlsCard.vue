<script setup lang="ts">
import type { RefreshProgress } from '~/types'

const toast = useToast()

const props = defineProps<{
  progress: RefreshProgress
}>()

const emit = defineEmits<{
  'refresh-stocks': []
}>()

const breadthLoading = ref(false)

const progressPercentage = computed(() => {
  if (props.progress.total === 0) return 0
  return (props.progress.completed / props.progress.total) * 100
})

const isActive = computed(() => props.progress.total > 0 && props.progress.completed < props.progress.total)

async function refreshBreadth() {
  breadthLoading.value = true
  try {
    await $fetch('/udgaard/api/data-management/refresh/recalculate-breadth', {
      method: 'POST'
    })
    toast.add({
      title: 'Success',
      description: 'Market and sector breadth refreshed from stock data',
      color: 'success'
    })
  } catch (error) {
    console.error('Failed to refresh breadth:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to refresh breadth data',
      color: 'error'
    })
  } finally {
    breadthLoading.value = false
  }
}
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-lg font-semibold">
        Data Refresh
      </h3>
    </template>

    <!-- Refresh Buttons -->
    <div class="flex items-center justify-between mb-4">
      <UButton
        label="Refresh All Stocks"
        icon="i-lucide-refresh-cw"
        :disabled="isActive"
        @click="emit('refresh-stocks')"
      />

      <div class="flex items-center gap-3">
        <span class="text-sm text-muted">Recalculate breadth percentages and EMAs from existing stock data</span>
        <UButton
          label="Refresh Breadth"
          icon="i-lucide-activity"
          variant="soft"
          :loading="breadthLoading"
          @click="refreshBreadth"
        />
      </div>
    </div>

    <!-- Progress Bar -->
    <div v-if="isActive" class="space-y-2">
      <div class="flex justify-between text-sm">
        <span>Progress: {{ progress.completed }} / {{ progress.total }}</span>
        <span>{{ progressPercentage.toFixed(0) }}%</span>
      </div>
      <UProgress :value="progressPercentage" />

      <div class="flex justify-between text-xs text-muted">
        <span v-if="progress.lastSuccess">Last: {{ progress.lastSuccess }}</span>
        <span v-if="progress.failed > 0" class="text-red-600">
          Failed: {{ progress.failed }}
        </span>
      </div>

      <!-- Error Display -->
      <UAlert v-if="progress.lastError" color="error" :title="progress.lastError" />
    </div>
  </UCard>
</template>
