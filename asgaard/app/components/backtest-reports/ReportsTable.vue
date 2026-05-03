<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { BacktestReportListItem } from '~/types'

const props = defineProps<{
  reports: BacktestReportListItem[]
}>()

const emit = defineEmits<{
  'delete-one': [backtestId: string]
}>()

const selected = defineModel<Set<string>>('selected', { default: () => new Set() })

const UButton = resolveComponent('UButton')

function toggleSelect(id: string) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function toggleSelectAll() {
  if (selected.value.size === props.reports.length) {
    selected.value = new Set()
  } else {
    selected.value = new Set(props.reports.map(r => r.backtestId))
  }
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

function fmtPct(n: number | null | undefined, decimals = 2): string {
  if (n == null) return '—'
  return `${n.toFixed(decimals)}%`
}

function fmtNumber(n: number | null | undefined, decimals = 2): string {
  if (n == null) return '—'
  return n.toFixed(decimals)
}

const columns = computed<TableColumn<BacktestReportListItem>[]>(() => [
  {
    id: 'select',
    header: () => h('input', {
      'type': 'checkbox',
      'checked': selected.value.size === props.reports.length && props.reports.length > 0,
      'indeterminate': selected.value.size > 0 && selected.value.size < props.reports.length,
      'class': 'rounded',
      'aria-label': 'Select all reports',
      'onChange': () => toggleSelectAll()
    }),
    cell: ({ row }) => h('input', {
      'type': 'checkbox',
      'checked': selected.value.has(row.original.backtestId),
      'class': 'rounded',
      'aria-label': `Select report ${row.original.backtestId}`,
      'onChange': () => toggleSelect(row.original.backtestId)
    })
  },
  { accessorKey: 'createdAt', header: 'Created', cell: ({ row }) => fmtDate(row.original.createdAt) },
  { accessorFn: row => row.metadata.entryStrategyName, header: 'Entry' },
  { accessorFn: row => row.metadata.exitStrategyName, header: 'Exit' },
  { id: 'range', header: 'Date Range', cell: ({ row }) => `${row.original.metadata.startDate} → ${row.original.metadata.endDate}` },
  { accessorFn: row => row.summary.totalTrades, header: 'Trades' },
  { accessorFn: row => row.summary.edge, header: 'Edge', cell: ({ row }) => fmtPct(row.original.summary.edge) },
  { accessorFn: row => row.summary.cagr, header: 'CAGR', cell: ({ row }) => fmtPct(row.original.summary.cagr) },
  { accessorFn: row => row.summary.maxDrawdownPct, header: 'Max DD', cell: ({ row }) => fmtPct(row.original.summary.maxDrawdownPct) },
  { accessorFn: row => row.summary.sharpeRatio, header: 'Sharpe', cell: ({ row }) => fmtNumber(row.original.summary.sharpeRatio) },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h(UButton, {
      'icon': 'i-lucide-trash-2',
      'color': 'error',
      'variant': 'ghost',
      'size': 'sm',
      'aria-label': `Delete report ${row.original.backtestId}`,
      'onClick': () => emit('delete-one', row.original.backtestId)
    })
  }
])
</script>

<template>
  <div>
    <UTable
      v-if="reports.length > 0"
      :data="reports"
      :columns="columns"
    />
    <div v-else class="text-center py-12 text-gray-500 dark:text-gray-400">
      <UIcon name="i-lucide-folder-archive" class="w-12 h-12 mx-auto mb-3 opacity-50" />
      <p>No backtest reports found.</p>
      <p class="text-sm mt-1">
        Run a backtest from the
        <NuxtLink to="/backtesting" class="text-primary-500 hover:underline">
          Backtesting
        </NuxtLink> page to create one.
      </p>
    </div>
  </div>
</template>
