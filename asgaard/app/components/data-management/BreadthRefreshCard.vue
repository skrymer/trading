<script setup lang="ts">
const toast = useToast()
const loading = ref(false)

async function refreshBreadth() {
  loading.value = true
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
    loading.value = false
  }
}
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-lg font-semibold">
        Breadth Refresh
      </h3>
    </template>

    <p class="text-sm text-muted mb-4">
      Recalculate market and sector breadth percentages and EMAs from existing stock quote data.
    </p>

    <UButton
      label="Refresh Breadth"
      icon="i-lucide-activity"
      :loading="loading"
      @click="refreshBreadth"
    />
  </UCard>
</template>
