<template>
  <UPageCard
    icon="i-lucide-circle-dollar-sign"
    :title="props.title"
    variant="subtle"
    :ui="{
      container: 'gap-y-1.5',
      wrapper: 'items-start',
      leading: 'p-2.5 rounded-full bg-primary/10 ring ring-inset ring-primary/25 flex-col',
      title: 'font-normal text-muted text-xs uppercase'
    }"
    class="lg:rounded-none first:rounded-l-lg last:rounded-r-lg hover:z-1"
  >
    <div class="flex items-center gap-2">
      <span class="text-2xl font-semibold text-highlighted">
        {{ props.content }}
      </span>

      <!-- <UBadge
          :color="stat.variation > 0 ? 'success' : 'error'"
          variant="subtle"
          class="text-xs"
        >
          {{ stat.variation > 0 ? '+' : '' }}{{ stat.variation }}%
        </UBadge> -->
    </div>
  </UPageCard>
</template>

<script lang="ts" setup>
const props = defineProps<{
  title: string
  content: string | number
  heading?: '1' | '2' | '3' | '4'
}>()
</script>

<style>

</style>
