<script setup lang="ts">
defineProps<{
  symbol: string
}>()
</script>

<template>
  <div class="flex items-center gap-1.5">
    <NuxtLink
      :to="`/stock-data/${symbol.toLowerCase()}`"
      target="_blank"
      class="font-semibold text-primary hover:underline"
    >
      {{ symbol }}
    </NuxtLink>
    <UButton
      :href="`https://www.tradingview.com/chart/?symbol=${encodeURIComponent(symbol)}`"
      target="_blank"
      icon="i-simple-icons-tradingview"
      size="xs"
      variant="ghost"
      color="neutral"
    />
  </div>
</template>
