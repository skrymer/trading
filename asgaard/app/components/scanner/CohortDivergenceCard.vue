<script setup lang="ts">
import type { CohortDivergenceReport } from '~/types'

const props = defineProps<{
  report: CohortDivergenceReport | null
}>()

const todaySkipRate = computed(() => {
  const today = props.report?.today
  if (!today || today.signalsEmitted === 0) return null
  return 1 - today.signalsTaken / today.signalsEmitted
})

const jaccardColor = computed(() => {
  const j = props.report?.rolling.jaccard
  if (j == null) return ''
  if (j < 0.3) return 'text-red-600 dark:text-red-400'
  if (j < 0.5) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-green-600 dark:text-green-400'
})

const hasAlert = computed(() =>
  Boolean(props.report?.alerts.executionDrift || props.report?.alerts.traderFiltering)
)

const hasData = computed(() => (props.report?.scanRunsInWindow ?? 0) > 0)
</script>

<template>
  <div
    v-if="report"
    class="p-4 bg-muted/50 rounded-lg border border-default"
  >
    <div class="text-sm text-muted">
      Scanner Cohort
    </div>
    <template v-if="hasData">
      <div :class="['text-2xl font-bold mt-1', jaccardColor]">
        {{ report.rolling.jaccard.toFixed(2) }}
      </div>
      <div class="text-xs text-muted mt-1">
        {{ report.today.signalsTaken }}/{{ report.today.signalsEmitted }} today
        <span v-if="todaySkipRate != null">
          &middot; {{ (todaySkipRate * 100).toFixed(0) }}% skip
        </span>
      </div>
      <div
        v-if="hasAlert"
        class="flex flex-wrap gap-1 mt-1.5"
      >
        <UBadge
          v-if="report.alerts.executionDrift"
          color="error"
          size="xs"
          variant="soft"
        >
          Drift
        </UBadge>
        <UBadge
          v-if="report.alerts.traderFiltering"
          color="warning"
          size="xs"
          variant="soft"
        >
          Filtering
        </UBadge>
      </div>
    </template>
    <template v-else>
      <div class="text-2xl font-bold mt-1 text-muted">
        &mdash;
      </div>
      <div class="text-xs text-muted mt-1">
        No scans in last {{ report.config.windowDays }}d
      </div>
    </template>
  </div>
</template>
