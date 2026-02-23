<script setup lang="ts">
import type { ExitCheckResult } from '~/types'

defineProps<{
  exitResults: Map<number, ExitCheckResult>
  tradeId: number
}>()
</script>

<template>
  <span v-if="exitResults.has(tradeId)">
    <UBadge
      v-if="exitResults.get(tradeId)?.exitTriggered"
      color="error"
      variant="solid"
      size="sm"
    >
      EXIT: {{ exitResults.get(tradeId)?.exitReason }}
    </UBadge>
    <UBadge
      v-else
      color="success"
      variant="subtle"
      size="sm"
    >
      Hold
    </UBadge>
  </span>
</template>
