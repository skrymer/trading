<script setup lang="ts">
import type { RefreshProgress } from '~/types'

const props = defineProps<{
  progress: RefreshProgress
}>()

const emit = defineEmits<{
  'refresh-stocks': [minDate: string]
  'refresh-new-stocks': [minDate: string]
}>()

const minDate = ref('2016-01-01')

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

    <!-- Refresh Options -->
    <div class="mb-4 space-y-3">
      <div class="flex items-center gap-2">
        <label class="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
          Data start date:
        </label>
        <UInput
          v-model="minDate"
          type="date"
          :disabled="isActive"
          class="w-44"
        />
      </div>
    </div>

    <!-- Refresh Buttons -->
    <div class="flex gap-2 mb-4">
      <UButton
        label="Refresh New Stocks"
        icon="i-lucide-plus"
        :disabled="isActive"
        @click="emit('refresh-new-stocks', minDate)"
      />
      <UButton
        label="Refresh All Stocks"
        icon="i-lucide-refresh-cw"
        variant="soft"
        :disabled="isActive"
        @click="emit('refresh-stocks', minDate)"
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
