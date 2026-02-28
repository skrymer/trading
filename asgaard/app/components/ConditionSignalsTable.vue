<template>
  <div class="w-full">
    <div class="mb-3">
      <h3 class="text-lg font-semibold">
        Condition Signals
      </h3>
      <p class="text-sm text-muted">
        {{ conditionSignals.matchingQuotes }} of {{ conditionSignals.totalQuotes }} quotes matched
        ({{ conditionSignals.conditionDescriptions.join(` ${conditionSignals.operator} `) }})
      </p>
    </div>

    <UCard>
      <UTable
        :data="tableData"
        :columns="columns"
      >
        <template #empty-state>
          <div class="flex flex-col items-center justify-center py-12 text-center">
            <UIcon name="i-heroicons-funnel" class="w-12 h-12 text-muted mb-3" />
            <h4 class="text-lg font-semibold mb-2">
              No Matches
            </h4>
            <p class="text-sm text-muted max-w-md">
              No quotes matched all configured conditions.
            </p>
          </div>
        </template>

        <template #date-data="{ row }">
          <span class="font-mono text-sm">{{ formatDate(row.original.date) }}</span>
        </template>

        <template #price-data="{ row }">
          <span class="font-mono">${{ row.original.price.toFixed(2) }}</span>
        </template>

        <template #conditions-data="{ row }">
          <div class="flex flex-wrap gap-1">
            <UBadge
              v-for="(result, idx) in row.original.conditionResults"
              :key="idx"
              :color="result.passed ? 'success' : 'error'"
              variant="soft"
              size="xs"
            >
              {{ result.description }}
            </UBadge>
          </div>
        </template>

        <template #actions-data="{ row }">
          <UButton
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

    <ChartsSignalDetailsModal
      v-model:open="detailsModalOpen"
      :signal="selectedSignal"
    />
  </div>
</template>

<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { StockConditionSignals, ConditionEvaluationResult } from '~/types'

interface ConditionRow {
  id: string
  date: string
  price: number
  conditionResults: ConditionEvaluationResult[]
}

const props = defineProps<{
  conditionSignals: StockConditionSignals
}>()

const detailsModalOpen = ref(false)
const selectedSignal = ref<any>(null)

const columns: TableColumn<ConditionRow>[] = [
  {
    id: 'date',
    header: 'Date'
  },
  {
    id: 'price',
    header: 'Price'
  },
  {
    id: 'conditions',
    header: 'Conditions',
    cell: ({ row }: { row: { original: ConditionRow } }) => {
      const passed = row.original.conditionResults.filter(r => r.passed).length
      return `${passed}/${row.original.conditionResults.length}`
    }
  },
  {
    id: 'actions',
    header: ''
  }
]

const tableData = computed(() => {
  return props.conditionSignals.quotesWithConditions
    .map((qwc, index) => ({
      id: `${qwc.date}-${index}`,
      date: qwc.date,
      price: qwc.closePrice,
      conditionResults: qwc.conditionResults
    }))
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
})

function formatDate(dateString: string) {
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

function showDetails(row: ConditionRow) {
  selectedSignal.value = {
    date: row.date,
    price: row.price,
    entryDetails: {
      strategyName: 'Condition Evaluation',
      strategyDescription: props.conditionSignals.conditionDescriptions.join(` ${props.conditionSignals.operator} `),
      conditions: row.conditionResults,
      allConditionsMet: true
    }
  }
  detailsModalOpen.value = true
}
</script>
