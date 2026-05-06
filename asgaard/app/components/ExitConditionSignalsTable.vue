<template>
  <div class="w-full">
    <div class="mb-3">
      <h3 class="text-lg font-semibold">
        Exit Condition Signals
      </h3>
      <p class="text-sm text-muted">
        Hypothetical entry: <span class="font-mono">{{ formatDate(exitConditionSignals.entryDate) }}</span>
        — {{ exitConditionSignals.matchingQuotes }} of {{ exitConditionSignals.totalQuotes }} post-entry quotes matched
        ({{ exitConditionSignals.conditionDescriptions.join(` ${exitConditionSignals.operator} `) }})
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
              No Exits Triggered
            </h4>
            <p class="text-sm text-muted max-w-md">
              No post-entry quote matched the configured exit conditions. Try a different operator or earlier entry date.
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
              :color="result.passed ? 'error' : 'neutral'"
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
import type { StockExitConditionSignals, ConditionEvaluationResult } from '~/types'

interface ConditionRow {
  id: string
  date: string
  price: number
  conditionResults: ConditionEvaluationResult[]
}

const props = defineProps<{
  exitConditionSignals: StockExitConditionSignals
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
    header: 'Conditions Fired',
    cell: ({ row }: { row: { original: ConditionRow } }) => {
      const fired = row.original.conditionResults.filter(r => r.passed).length
      return `${fired}/${row.original.conditionResults.length}`
    }
  },
  {
    id: 'actions',
    header: ''
  }
]

const tableData = computed(() => {
  return props.exitConditionSignals.quotesWithConditions
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
      strategyName: 'Exit Condition Evaluation',
      strategyDescription: `From entry ${props.exitConditionSignals.entryDate} — `
        + props.exitConditionSignals.conditionDescriptions.join(` ${props.exitConditionSignals.operator} `),
      conditions: row.conditionResults,
      allConditionsMet: true
    }
  }
  detailsModalOpen.value = true
}
</script>
