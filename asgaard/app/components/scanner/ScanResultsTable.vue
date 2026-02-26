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

const expanded = ref<Record<number, boolean>>({})

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')

const columns: TableColumn<ScanResult>[] = [
  {
    id: 'expand',
    cell: ({ row }) => {
      if (!row.original.entrySignalDetails) return null
      return h(UButton, {
        'color': 'neutral',
        'variant': 'ghost',
        'size': 'xs',
        'icon': 'i-lucide-chevron-down',
        'square': true,
        'aria-label': 'Expand',
        'ui': {
          leadingIcon: ['transition-transform', row.getIsExpanded() ? 'duration-200 rotate-180' : '']
        },
        'onClick': () => row.toggleExpanded()
      })
    }
  },
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
      return h(UBadge, { color, variant: 'subtle', size: 'sm' }, () => trend)
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
        onClick: () => row.toggleExpanded()
      }, `${details.conditions.length} conditions`)
    }
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h(UButton, {
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
  <UTable
    v-model:expanded="expanded"
    :data="results"
    :columns="columns"
    :ui="{ tr: 'data-[expanded=true]:bg-elevated/50' }"
  >
    <template #empty-state>
      <div class="flex flex-col items-center justify-center py-8">
        <UIcon name="i-lucide-search-x" class="w-10 h-10 text-muted mb-2" />
        <p class="text-muted text-sm">
          No matching stocks found. Try adjusting your scan parameters.
        </p>
      </div>
    </template>

    <template #expanded="{ row }">
      <div v-if="row.original.entrySignalDetails" class="p-3 text-sm">
        <div class="font-medium mb-2">
          {{ row.original.entrySignalDetails.strategyDescription }}
        </div>
        <div class="space-y-1">
          <div
            v-for="condition in row.original.entrySignalDetails.conditions"
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
    </template>
  </UTable>
</template>
