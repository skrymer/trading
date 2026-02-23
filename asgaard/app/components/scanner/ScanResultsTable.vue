<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { ScanResult } from '~/types'

defineProps<{
  results: ScanResult[]
}>()

const emit = defineEmits<{
  'add-trade': [result: ScanResult]
}>()

const expandedRows = ref<Set<string>>(new Set())

function toggleRow(symbol: string) {
  if (expandedRows.value.has(symbol)) {
    expandedRows.value.delete(symbol)
  } else {
    expandedRows.value.add(symbol)
  }
}

const columns: TableColumn<ScanResult>[] = [
  {
    id: 'symbol',
    header: 'Symbol',
    cell: ({ row }) => h('span', { class: 'font-semibold' }, row.original.symbol)
  },
  {
    id: 'sector',
    header: 'Sector',
    cell: ({ row }) => row.original.sectorSymbol ?? '-'
  },
  {
    id: 'price',
    header: 'Price',
    cell: ({ row }) => `$${row.original.closePrice.toFixed(2)}`
  },
  {
    id: 'atr',
    header: 'ATR',
    cell: ({ row }) => row.original.atr.toFixed(2)
  },
  {
    id: 'trend',
    header: 'Trend',
    cell: ({ row }) => {
      const trend = row.original.trend ?? 'Unknown'
      const color = trend === 'Uptrend' ? 'success' : trend === 'Downtrend' ? 'error' : 'neutral'
      return h(resolveComponent('UBadge'), { color, variant: 'subtle', size: 'sm' }, () => trend)
    }
  },
  {
    id: 'conditions',
    header: 'Conditions',
    cell: ({ row }) => {
      const details = row.original.entrySignalDetails
      if (!details) return '-'
      return h('button', {
        class: 'text-primary hover:underline text-sm',
        onClick: () => toggleRow(row.original.symbol)
      }, `${details.conditions.length} conditions`)
    }
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h(resolveComponent('UButton'), {
      label: 'Add Trade',
      size: 'xs',
      variant: 'soft',
      icon: 'i-lucide-plus',
      onClick: () => emit('add-trade', row.original)
    })
  }
]
</script>

<template>
  <div>
    <UTable :data="results" :columns="columns">
      <template #empty-state>
        <div class="flex flex-col items-center justify-center py-8">
          <UIcon name="i-lucide-search-x" class="w-10 h-10 text-muted mb-2" />
          <p class="text-muted text-sm">
            No matching stocks found. Try adjusting your scan parameters.
          </p>
        </div>
      </template>
    </UTable>

    <!-- Expanded condition details -->
    <div v-for="result in results" :key="result.symbol">
      <div
        v-if="expandedRows.has(result.symbol) && result.entrySignalDetails"
        class="mx-4 mb-2 p-3 bg-muted/50 rounded-lg border border-default text-sm"
      >
        <div class="font-medium mb-2">
          {{ result.entrySignalDetails.strategyDescription }}
        </div>
        <div class="space-y-1">
          <div
            v-for="condition in result.entrySignalDetails.conditions"
            :key="condition.conditionType"
            class="flex items-center gap-2"
          >
            <UIcon
              :name="condition.passed ? 'i-lucide-check-circle' : 'i-lucide-x-circle'"
              :class="condition.passed ? 'text-green-500' : 'text-red-500'"
              class="w-4 h-4 shrink-0"
            />
            <span>{{ condition.description }}</span>
            <span v-if="condition.message" class="text-muted">
              — {{ condition.message }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
