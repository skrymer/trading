<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { ScanResult, OptionContractResponse } from '~/types'

const props = defineProps<{
  results: ScanResult[]
  positionSizingEnabled?: boolean
  calculateQuantity?: (atr: number, symbol?: string) => number
  instrumentMode?: 'STOCK' | 'OPTION'
  optionContracts?: Map<string, OptionContractResponse>
}>()

const emit = defineEmits<{
  'add-trade': [result: ScanResult]
}>()

const selected = defineModel<Set<string>>({ default: () => new Set() })

const expanded = ref<Record<number, boolean>>({})

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')

const isOptionsMode = computed(() => props.instrumentMode === 'OPTION')

function toggleSelect(symbol: string) {
  const next = new Set(selected.value)
  if (next.has(symbol)) {
    next.delete(symbol)
  } else {
    next.add(symbol)
  }
  selected.value = next
}

function toggleSelectAll() {
  if (selected.value.size === props.results.length) {
    selected.value = new Set()
  } else {
    selected.value = new Set(props.results.map(r => r.symbol))
  }
}

const columns = computed<TableColumn<ScanResult>[]>(() => {
  const cols: TableColumn<ScanResult>[] = []

  if (props.positionSizingEnabled) {
    cols.push({
      id: 'select',
      header: () => h('input', {
        type: 'checkbox',
        checked: selected.value.size === props.results.length && props.results.length > 0,
        indeterminate: selected.value.size > 0 && selected.value.size < props.results.length,
        class: 'rounded',
        onChange: () => toggleSelectAll()
      }),
      cell: ({ row }) => h('input', {
        type: 'checkbox',
        checked: selected.value.has(row.original.symbol),
        class: 'rounded',
        onChange: () => toggleSelect(row.original.symbol)
      })
    })
  }

  cols.push(
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
      header: 'Stock Price',
      cell: ({ row }) => `$${row.original.closePrice.toFixed(2)}`
    },
    {
      id: 'atr',
      header: 'ATR',
      cell: ({ row }) => row.original.atr.toFixed(2)
    },
    {
      id: 'rank',
      header: 'Rank',
      cell: ({ row }) => row.original.rankScore != null ? row.original.rankScore.toFixed(2) : '-'
    }
  )

  if (props.positionSizingEnabled && props.calculateQuantity) {
    const calcFn = props.calculateQuantity
    cols.push(
      {
        id: 'quantity',
        header: isOptionsMode.value ? 'Contracts' : 'Shares',
        cell: ({ row }) => {
          const qty = calcFn(row.original.atr, row.original.symbol)
          return qty > 0 ? qty.toLocaleString() : 'N/A'
        }
      },
      {
        id: 'cost',
        header: 'Cost',
        cell: ({ row }) => {
          const qty = calcFn(row.original.atr, row.original.symbol)
          if (qty <= 0) return 'N/A'
          if (isOptionsMode.value) {
            const contract = props.optionContracts?.get(row.original.symbol)
            if (!contract) return '-'
            return `$${(qty * contract.price * 100).toLocaleString('en-US', { maximumFractionDigits: 0 })}`
          }
          return `$${(qty * row.original.closePrice).toLocaleString('en-US', { maximumFractionDigits: 0 })}`
        }
      }
    )

    if (isOptionsMode.value) {
      cols.push(
        {
          id: 'strike',
          header: 'Strike',
          cell: ({ row }) => {
            const contract = props.optionContracts?.get(row.original.symbol)
            return contract ? `$${contract.strike.toFixed(0)}` : '-'
          }
        },
        {
          id: 'delta',
          header: 'Delta',
          cell: ({ row }) => {
            const contract = props.optionContracts?.get(row.original.symbol)
            return contract ? contract.delta.toFixed(2) : '-'
          }
        },
        {
          id: 'expiry',
          header: 'Expiry',
          cell: ({ row }) => {
            const contract = props.optionContracts?.get(row.original.symbol)
            return contract?.expiration ?? '-'
          }
        },
        {
          id: 'extrinsic',
          header: 'Extr %',
          cell: ({ row }) => {
            const contract = props.optionContracts?.get(row.original.symbol)
            if (!contract || contract.price <= 0) return '-'
            return `${((contract.extrinsic / contract.price) * 100).toFixed(1)}%`
          }
        },
        {
          id: 'openInterest',
          header: 'OI',
          cell: ({ row }) => {
            const contract = props.optionContracts?.get(row.original.symbol)
            return contract?.openInterest?.toLocaleString() ?? '-'
          }
        }
      )
    }
  }

  cols.push(
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
  )

  return cols
})
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
