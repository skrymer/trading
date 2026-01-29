<template>
  <UModal
    :open="isOpen"
    title="Delete Position"
    size="lg"
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div v-if="position" class="space-y-4">
        <div class="p-4 bg-red-50 dark:bg-red-950 rounded-lg border border-red-200 dark:border-red-800">
          <div class="flex items-start gap-3">
            <UIcon name="i-lucide-alert-triangle" class="w-5 h-5 mt-0.5 text-red-600 dark:text-red-400 flex-shrink-0" />
            <div class="flex-1">
              <p class="font-medium text-red-900 dark:text-red-100 mb-1">
                Are you sure you want to delete this position?
              </p>
              <p class="text-sm text-red-700 dark:text-red-300 mb-3">
                This action cannot be undone. All execution history for this position will be permanently deleted.
              </p>
              <div class="mt-3 p-3 bg-white dark:bg-gray-900 rounded border border-red-200 dark:border-red-800">
                <div class="text-sm space-y-1">
                  <div class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">Symbol:</span>
                    <span class="font-medium">{{ formatPositionName(position) }}</span>
                  </div>
                  <div v-if="formatOptionDetails(position)" class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">Details:</span>
                    <span class="text-xs">{{ formatOptionDetails(position) }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">Status:</span>
                    <UBadge :color="position.status === 'OPEN' ? 'primary' : 'neutral'" variant="subtle" size="xs">
                      {{ position.status }}
                    </UBadge>
                  </div>
                  <div v-if="position.status === 'CLOSED' && position.realizedPnl" class="flex justify-between">
                    <span class="text-gray-600 dark:text-gray-400">Realized P&L:</span>
                    <span
                      :class="[
                        'font-medium',
                        position.realizedPnl >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                      ]"
                    >
                      {{ formatCurrency(position.realizedPnl) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          variant="outline"
          @click="isOpen = false"
        />
        <UButton
          label="Delete Position"
          icon="i-lucide-trash"
          color="error"
          :loading="loading"
          @click="handleDelete"
        />
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
import type { Position } from '~/types'

const { formatPositionName, formatOptionDetails, formatCurrency } = usePositionFormatters()

const _props = defineProps<{
  position: Position | null
}>()

const emit = defineEmits<{
  delete: []
}>()

const model = defineModel<boolean>()

const isOpen = computed({
  get: () => model.value ?? false,
  set: (value) => {
    model.value = value
  }
})

const loading = ref(false)

function handleDelete() {
  emit('delete')
  loading.value = true
  // Loading will be reset when modal closes after successful deletion
}
</script>
