<template>
  <div class="w-full">
    <div class="mb-3">
      <h3 class="text-lg font-semibold">Strategy Signals</h3>
      <p class="text-sm text-muted">
        <template v-if="entryStrategy && exitStrategy">
          Entry Strategy: <span class="font-medium">{{ entryStrategy }}</span>
          | Exit Strategy: <span class="font-medium">{{ exitStrategy }}</span>
          | Signals: <span class="font-medium">{{ signalsData.length }}</span>
        </template>
        <template v-else>
          <span class="text-muted-foreground">Select strategies and click "Show Signals" to analyze</span>
        </template>
      </p>
    </div>

    <UCard>
      <UTable
        :data="signalsData"
        :columns="columns"
      >
        <template #empty-state>
          <div class="flex flex-col items-center justify-center py-12 text-center">
            <UIcon name="i-heroicons-chart-bar" class="w-12 h-12 text-muted mb-3" />
            <h4 class="text-lg font-semibold mb-2">No Signals Found</h4>
            <p class="text-sm text-muted max-w-md">
              <template v-if="!entryStrategy || !exitStrategy">
                Select entry and exit strategies, then click "Show Signals" to view trading signals.
              </template>
              <template v-else>
                No entry or exit signals were generated for the selected strategies and date range.
              </template>
            </p>
          </div>
        </template>
        <template #date-data="{ row }">
          <span class="font-mono text-sm">{{ formatDate(row.original.date) }}</span>
        </template>

        <template #price-data="{ row }">
          <span class="font-mono">${{ row.original.price.toFixed(2) }}</span>
        </template>

        <template #signalType-data="{ row }">
          <UBadge
            :color="row.original.signalType === 'Entry' ? 'success' : row.original.signalType === 'Exit' ? 'error' : 'neutral'"
            variant="soft"
          >
            {{ row.original.signalType }}
          </UBadge>
        </template>

        <template #exitReason-data="{ row }">
          <span v-if="row.original.exitReason" class="text-sm">{{ row.original.exitReason }}</span>
          <span v-else class="text-muted text-sm">-</span>
        </template>

        <template #conditions-data="{ row }">
          <div v-if="row.original.entryDetails && row.original.entryDetails.conditions" class="space-y-1">
            <div
              v-for="(condition, idx) in row.original.entryDetails.conditions"
              :key="idx"
              class="flex items-start gap-2 text-sm"
            >
              <UIcon
                :name="condition.passed ? 'i-heroicons-check-circle' : 'i-heroicons-x-circle'"
                :class="condition.passed ? 'text-success' : 'text-error'"
                class="flex-shrink-0 mt-0.5"
              />
              <div class="flex-1 min-w-0">
                <div class="font-medium truncate">{{ condition.description }}</div>
                <div v-if="condition.message" class="text-xs text-muted truncate">
                  {{ condition.message }}
                </div>
              </div>
            </div>
          </div>
          <span v-else-if="row.original.signalType === 'Exit'" class="text-muted text-sm">-</span>
          <span v-else class="text-muted text-sm">No details available</span>
        </template>

        <template #actions-data="{ row }">
          <UButton
            v-if="row.original.entryDetails"
            icon="i-heroicons-eye"
            size="sm"
            color="neutral"
            variant="ghost"
            @click="showDetails(row.original)"
          >
            View
          </UButton>
        </template>
      </UTable>
    </UCard>

    <!-- Signal Details Modal -->
    <ChartsSignalDetailsModal
      v-model:open="detailsModalOpen"
      :signal="selectedSignalForModal"
    />
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'

interface SignalRow {
  id: string
  date: string
  price: number
  signalType: string
  exitReason: string | null
  entryDetails: any | null
  conditions: any | null
  actions: null
}

const props = defineProps<{
  signals: any
  entryStrategy?: string
  exitStrategy?: string
}>()

const detailsModalOpen = ref(false)
const selectedSignalForModal = ref<any>(null)

const columns: TableColumn<SignalRow>[] = [
  {
    id: 'date',
    header: 'Date',
    cell: ({ row }: { row: { original: SignalRow } }) => {
      const date = new Date(row.original.date)
      return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      })
    }
  },
  {
    id: 'price',
    header: 'Price',
    cell: ({ row }: { row: { original: SignalRow } }) => `$${row.original.price.toFixed(2)}`
  },
  {
    id: 'signalType',
    header: 'Signal Type',
    cell: ({ row }: { row: { original: SignalRow } }) => row.original.signalType
  },
  {
    id: 'exitReason',
    header: 'Exit Reason',
    cell: ({ row }: { row: { original: SignalRow } }) => row.original.exitReason || '-'
  },
  {
    id: 'conditions',
    header: 'Strategy Conditions',
    cell: ({ row }: { row: { original: SignalRow } }) => {
      if (row.original.entryDetails?.conditions) {
        const passedCount = row.original.entryDetails.conditions.filter((c: any) => c.passed).length
        const totalCount = row.original.entryDetails.conditions.length
        return `${passedCount}/${totalCount} passed`
      }
      return '-'
    }
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: () => ''
  }
]

const signalsData = computed(() => {
  if (!props.signals?.quotesWithSignals) {
    return []
  }

  return props.signals.quotesWithSignals
    .filter((qws: any) => qws.entrySignal || qws.exitSignal)
    .map((qws: any, index: number) => ({
      id: `${qws.quote.date}-${index}`,
      date: qws.quote.date,
      price: qws.quote.closePrice,
      signalType: qws.entrySignal ? 'Entry' : 'Exit',
      exitReason: qws.exitReason || null,
      entryDetails: qws.entryDetails || null,
      conditions: qws.entryDetails?.conditions || null,
      actions: null
    }))
    .sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime())
})

function formatDate(dateString: string) {
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

function showDetails(row: any) {
  selectedSignalForModal.value = {
    date: row.date,
    price: row.price,
    entryDetails: row.entryDetails
  }
  detailsModalOpen.value = true
}
</script>
