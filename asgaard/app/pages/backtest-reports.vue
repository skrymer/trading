<script setup lang="ts">
import type { BacktestReportListItem } from '~/types'

const toast = useToast()

const reports = ref<BacktestReportListItem[]>([])
const selected = ref<Set<string>>(new Set())
const loading = ref(false)
const deleting = ref(false)
const deleteModalOpen = ref(false)
const pendingDelete = ref<string[]>([])

async function loadReports() {
  if (loading.value) return
  loading.value = true
  try {
    reports.value = await $fetch<BacktestReportListItem[]>('/udgaard/api/backtest/reports')
  } catch (error) {
    console.error('Failed to load backtest reports:', error)
    toast.add({ title: 'Error', description: 'Failed to load backtest reports', color: 'error' })
  } finally {
    loading.value = false
  }
}

function openDeleteOne(backtestId: string) {
  if (deleting.value) return
  pendingDelete.value = [backtestId]
  deleteModalOpen.value = true
}

function openDeleteSelected() {
  if (deleting.value || selected.value.size === 0) return
  pendingDelete.value = Array.from(selected.value)
  deleteModalOpen.value = true
}

async function confirmDelete() {
  if (deleting.value) return
  const ids = pendingDelete.value
  deleteModalOpen.value = false
  if (ids.length === 0) return
  deleting.value = true
  try {
    if (ids.length === 1) {
      await $fetch(`/udgaard/api/backtest/reports/${encodeURIComponent(ids[0]!)}`, { method: 'DELETE' })
      toast.add({ title: 'Deleted', description: 'Report deleted', color: 'success' })
    } else {
      const response = await $fetch<{ deleted: number }>('/udgaard/api/backtest/reports/batch-delete', {
        method: 'POST',
        body: ids
      })
      toast.add({
        title: 'Deleted',
        description: response.deleted === ids.length
          ? `${ids.length} reports deleted`
          : `${response.deleted} of ${ids.length} reports deleted (others were already gone)`,
        color: 'success'
      })
    }
    const removed = new Set(ids)
    selected.value = new Set([...selected.value].filter(id => !removed.has(id)))
  } catch (error: unknown) {
    console.error('Failed to delete reports:', error)
    const status = (error as { statusCode?: number })?.statusCode
    if (ids.length === 1 && status === 404) {
      toast.add({ title: 'Already deleted', description: 'Report no longer exists', color: 'warning' })
      const removed = new Set(ids)
      selected.value = new Set([...selected.value].filter(id => !removed.has(id)))
    } else {
      toast.add({ title: 'Error', description: 'Failed to delete reports', color: 'error' })
    }
  } finally {
    pendingDelete.value = []
    deleting.value = false
    await loadReports()
  }
}

onMounted(loadReports)
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold">
          Backtest Reports
        </h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          Stored backtest results. Retention is "day or two max" — clean up runs you no longer need.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <UButton
          icon="i-lucide-refresh-cw"
          variant="ghost"
          :loading="loading"
          @click="loadReports"
        >
          Refresh
        </UButton>
        <UButton
          icon="i-lucide-trash-2"
          color="error"
          :disabled="selected.size === 0"
          @click="openDeleteSelected"
        >
          Delete selected ({{ selected.size }})
        </UButton>
      </div>
    </div>

    <BacktestReportsTable
      v-model:selected="selected"
      :reports="reports"
      @delete-one="openDeleteOne"
    />

    <BacktestReportsDeleteConfirmModal
      v-model:open="deleteModalOpen"
      :count="pendingDelete.length"
      @confirm="confirmDelete"
    />
  </div>
</template>
