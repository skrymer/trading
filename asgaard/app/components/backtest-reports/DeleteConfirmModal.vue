<script setup lang="ts">
defineProps<{
  open: boolean
  count: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'confirm': []
}>()

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('update:open', false)
}
</script>

<template>
  <UModal
    :open="open"
    :title="count === 1 ? 'Delete report' : `Delete ${count} reports`"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-4">
        <div class="flex items-center gap-3 p-4 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 rounded-lg">
          <div class="p-2 bg-red-100 dark:bg-red-900/20 rounded-lg shrink-0">
            <UIcon name="i-lucide-alert-triangle" class="w-5 h-5 text-red-600 dark:text-red-400" />
          </div>
          <div>
            <p class="text-sm text-red-800 dark:text-red-200 font-medium mb-1">
              This cannot be undone.
            </p>
            <p class="text-sm text-red-700 dark:text-red-300">
              {{ count === 1
                ? 'The selected backtest report and any backtestId references to it will stop resolving.'
                : `${count} backtest reports will be permanently removed. Any backtestId references to them will stop resolving.` }}
            </p>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2 w-full">
        <UButton variant="ghost" @click="handleCancel">
          Cancel
        </UButton>
        <UButton color="error" icon="i-lucide-trash-2" @click="handleConfirm">
          Delete
        </UButton>
      </div>
    </template>
  </UModal>
</template>
