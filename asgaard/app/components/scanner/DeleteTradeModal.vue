<script setup lang="ts">
import type { ScannerTrade } from '~/types'

defineProps<{
  open: boolean
  trade: ScannerTrade | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'delete': []
}>()
</script>

<template>
  <UModal
    :open="open"
    title="Remove Scanner Trade"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div v-if="trade" class="space-y-4">
        <p class="text-sm">
          Are you sure you want to remove <span class="font-semibold">{{ trade.symbol }}</span> from your scanner trades?
        </p>
        <div class="p-3 bg-muted/50 rounded-lg text-sm">
          <div class="grid grid-cols-2 gap-2">
            <div>
              <span class="text-muted">Entry Price:</span>
              <span class="font-medium ml-1">${{ trade.entryPrice.toFixed(2) }}</span>
            </div>
            <div>
              <span class="text-muted">Entry Date:</span>
              <span class="font-medium ml-1">{{ trade.entryDate }}</span>
            </div>
            <div>
              <span class="text-muted">Strategy:</span>
              <span class="font-medium ml-1">{{ trade.entryStrategyName }}</span>
            </div>
          </div>
        </div>
        <p class="text-xs text-muted">
          This only removes the trade from the scanner. It does not affect your portfolio.
        </p>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="emit('update:open', false)"
        />
        <UButton
          label="Remove"
          color="error"
          icon="i-lucide-trash-2"
          @click="emit('delete')"
        />
      </div>
    </template>
  </UModal>
</template>
