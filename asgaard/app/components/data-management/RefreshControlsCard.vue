<script setup lang="ts">
import type { RefreshProgress } from '~/types'

const props = defineProps<{
  progress: RefreshProgress
}>()

const emit = defineEmits<{
  'refresh-stocks': []
  'refresh-breadth': []
}>()

const progressPercentage = computed(() => {
  if (props.progress.total === 0) return 0
  return (props.progress.completed / props.progress.total) * 100
})

const isActive = computed(() => props.progress.total > 0 && props.progress.completed < props.progress.total)
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-lg font-semibold">
        Data Refresh
      </h3>
    </template>

    <!-- Refresh Buttons -->
    <div class="flex gap-2 mb-4">
      <UButton
        label="Refresh All Stocks"
        icon="i-lucide-refresh-cw"
        :disabled="isActive"
        @click="emit('refresh-stocks')"
      />
      <UButton
        label="Refresh Breadth"
        icon="i-lucide-activity"
        variant="outline"
        :disabled="isActive"
        @click="emit('refresh-breadth')"
      />
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
