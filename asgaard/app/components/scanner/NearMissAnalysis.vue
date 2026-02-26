<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { NearMissCandidate, ConditionFailureSummary } from '~/types'

defineProps<{
  nearMissCandidates: NearMissCandidate[]
  conditionFailureSummary: ConditionFailureSummary[]
  totalStocksScanned: number
  matchCount: number
}>()

function conditionBadgeColor(candidate: NearMissCandidate): string {
  if (candidate.conditionsTotal - candidate.conditionsPassed <= 1) return 'warning'
  return 'neutral'
}

const expanded = ref<Record<number, boolean>>({})

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')

const columns: TableColumn<NearMissCandidate>[] = [
  {
    id: 'expand',
    cell: ({ row }) => h(UButton, {
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
    id: 'conditions',
    header: 'Conditions',
    cell: ({ row }) => {
      const c = row.original
      const color = conditionBadgeColor(c)
      return h('button', {
        class: 'cursor-pointer',
        onClick: () => row.toggleExpanded()
      }, [
        h(UBadge, {
          color,
          variant: color === 'warning' ? 'solid' : 'subtle',
          size: 'sm'
        }, () => `${c.conditionsPassed}/${c.conditionsTotal}`)
      ])
    }
  },
  {
    id: 'trend',
    header: 'Trend',
    cell: ({ row }) => {
      const trend = row.original.trend ?? 'Unknown'
      const color = trend === 'Uptrend' ? 'success' : trend === 'Downtrend' ? 'error' : 'neutral'
      return h(UBadge, { color, variant: 'subtle', size: 'sm' }, () => trend)
    }
  }
]
</script>

<template>
  <div class="space-y-4">
    <!-- Condition Failure Summary -->
    <UCard v-if="conditionFailureSummary.length > 0">
      <template #header>
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-bar-chart-3" class="w-4 h-4" />
          <span class="font-medium text-sm">Condition Failure Summary</span>
          <span class="text-xs text-muted">
            ({{ totalStocksScanned - matchCount }} stocks evaluated)
          </span>
        </div>
      </template>
      <div class="space-y-3">
        <div
          v-for="condition in conditionFailureSummary"
          :key="condition.conditionType"
          class="space-y-1"
        >
          <div class="flex items-center justify-between text-sm">
            <span>{{ condition.description }}</span>
            <span class="text-muted tabular-nums">
              {{ condition.stocksBlocked }}/{{ condition.totalStocksEvaluated }} blocked
            </span>
          </div>
          <UProgress
            :model-value="(condition.stocksBlocked / condition.totalStocksEvaluated) * 100"
            :color="condition.stocksBlocked / condition.totalStocksEvaluated > 0.8 ? 'error' : condition.stocksBlocked / condition.totalStocksEvaluated > 0.5 ? 'warning' : 'primary'"
            size="xs"
          />
        </div>
      </div>
    </UCard>

    <!-- Near-Miss Candidates -->
    <UCard v-if="nearMissCandidates.length > 0">
      <template #header>
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-target" class="w-4 h-4" />
          <span class="font-medium text-sm">Near-Miss Candidates</span>
          <UBadge color="neutral" variant="subtle" size="xs">
            {{ nearMissCandidates.length }}
          </UBadge>
        </div>
      </template>
      <UTable
        v-model:expanded="expanded"
        :data="nearMissCandidates"
        :columns="columns"
        :ui="{ tr: 'data-[expanded=true]:bg-elevated/50' }"
      >
        <template #expanded="{ row }">
          <div class="p-3 text-sm">
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
    </UCard>
  </div>
</template>
