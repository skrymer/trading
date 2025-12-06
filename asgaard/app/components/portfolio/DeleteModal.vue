<script setup lang="ts">
import type { Portfolio } from '~/types'

const props = defineProps<{
  open: boolean
  portfolio: Portfolio
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'delete': []
}>()

const confirmText = ref('')

const isValid = computed(() => {
  return confirmText.value === props.portfolio.name
})

function handleDelete() {
  if (isValid.value) {
    emit('delete')
    confirmText.value = ''
  }
}

function handleCancel() {
  confirmText.value = ''
  emit('update:open', false)
}
</script>

<template>
  <UModal
    :open="open"
    title="Delete Portfolio"
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
              Warning: This action cannot be undone!
            </p>
            <p class="text-sm text-red-700 dark:text-red-300">
              Deleting this portfolio will permanently remove all trades, statistics, and historical data associated with it.
            </p>
          </div>
        </div>

        <div>
          <p class="text-sm text-muted mb-2">
            Portfolio to delete:
          </p>
          <div class="p-3 bg-muted/50 rounded-lg">
            <p class="font-semibold">
              {{ portfolio.name }}
            </p>
            <p class="text-sm text-muted">
              {{ portfolio.currency }}
            </p>
          </div>
        </div>

        <UFormField :label="`Type '${portfolio.name}' to confirm deletion`" required>
          <UInput
            v-model="confirmText"
            placeholder="Enter portfolio name"
            autocomplete="off"
          />
        </UFormField>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="handleCancel"
        />
        <UButton
          label="Delete Portfolio"
          color="error"
          icon="i-lucide-trash-2"
          :disabled="!isValid"
          @click="handleDelete"
        />
      </div>
    </template>
  </UModal>
</template>
