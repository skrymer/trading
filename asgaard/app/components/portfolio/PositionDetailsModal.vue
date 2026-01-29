<template>
  <UModal
    :open="isOpen"
    title="Position Details"
    fullscreen
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div v-if="positionData" class="space-y-6">
        <!-- Position Summary -->
        <div class="grid grid-cols-2 gap-4 p-4 bg-gray-50 dark:bg-gray-900 rounded-lg">
          <div>
            <div class="text-sm text-gray-500 dark:text-gray-400">
              Symbol
            </div>
            <div class="font-medium">
              {{ formatPositionName(positionData.position) }}
            </div>
            <div v-if="formatOptionDetails(positionData.position)" class="text-xs text-gray-500 mt-1">
              {{ formatOptionDetails(positionData.position) }}
            </div>
          </div>
          <div>
            <div class="text-sm text-gray-500 dark:text-gray-400">
              Status
            </div>
            <UBadge :color="positionData.position.status === 'OPEN' ? 'primary' : 'neutral'" variant="subtle">
              {{ positionData.position.status }}
            </UBadge>
          </div>
          <div>
            <div class="text-sm text-gray-500 dark:text-gray-400">
              {{ positionData.position.instrumentType === 'OPTION' ? 'Contracts' : 'Quantity' }}
            </div>
            <div class="font-medium">
              {{ positionData.position.instrumentType === 'OPTION' ? positionData.position.currentContracts : positionData.position.currentQuantity }}
            </div>
          </div>
          <div>
            <div class="text-sm text-gray-500 dark:text-gray-400">
              Average Entry
            </div>
            <div class="font-medium">
              {{ formatCurrency(positionData.position.averageEntryPrice) }}
            </div>
          </div>
          <div>
            <div class="text-sm text-gray-500 dark:text-gray-400">
              Total Cost
            </div>
            <div class="font-medium">
              {{ formatCurrency(positionData.position.totalCost) }}
            </div>
          </div>
          <div>
            <div class="text-sm text-gray-500 dark:text-gray-400">
              {{ positionData.position.status === 'OPEN' ? 'Opened Date' : 'Realized P&L' }}
            </div>
            <div class="font-medium">
              <span v-if="positionData.position.status === 'OPEN'">{{ formatDate(positionData.position.openedDate) }}</span>
              <span
                v-else
                :class="[
                  'font-semibold',
                  (positionData.position.realizedPnl || 0) >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                ]"
              >
                {{ formatCurrency(positionData.position.realizedPnl || 0) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Strategy Information -->
        <div class="space-y-3">
          <h4 class="text-sm font-semibold">
            Strategy Information
          </h4>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <div class="text-sm text-gray-500 dark:text-gray-400">
                Entry Strategy
              </div>
              <div class="text-sm font-medium">
                {{ positionData.position.entryStrategy }}
              </div>
            </div>
            <div>
              <div class="text-sm text-gray-500 dark:text-gray-400">
                Exit Strategy
              </div>
              <div class="text-sm font-medium">
                {{ positionData.position.exitStrategy }}
              </div>
            </div>
          </div>
          <div v-if="positionData.position.notes">
            <div class="text-sm text-gray-500 dark:text-gray-400">
              Notes
            </div>
            <div class="text-sm">
              {{ positionData.position.notes }}
            </div>
          </div>
          <div v-if="positionData.position.source === 'BROKER'">
            <UBadge color="info" variant="subtle">
              <div class="flex items-center gap-1">
                <UIcon name="i-lucide-link" class="w-3 h-3" />
                Broker Import
              </div>
            </UBadge>
          </div>
        </div>

        <!-- Roll Information -->
        <div v-if="positionData.position.isRolled" class="space-y-2">
          <h4 class="text-sm font-semibold">
            Roll Information
          </h4>
          <div class="flex items-center gap-2 text-sm">
            <UBadge color="primary" variant="subtle">
              Roll #{{ positionData.position.rollNumber }}
            </UBadge>
            <span v-if="positionData.position.parentPositionId" class="text-gray-600 dark:text-gray-400">
              Rolled from Position #{{ positionData.position.parentPositionId }}
            </span>
            <span v-if="positionData.position.rolledToPositionId" class="text-gray-600 dark:text-gray-400">
              Rolled to Position #{{ positionData.position.rolledToPositionId }}
            </span>
          </div>
        </div>

        <!-- Roll Summary -->
        <div v-if="rollPairs.length > 0" class="bg-primary-50 dark:bg-primary-950 border border-primary-200 dark:border-primary-800 rounded-lg p-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-repeat" class="w-5 h-5 text-primary" />
              <h4 class="text-sm font-semibold">
                Roll Summary
              </h4>
            </div>
            <div class="flex items-center gap-3">
              <div class="text-right">
                <div class="text-xs text-gray-500 dark:text-gray-400">
                  Total Rolls
                </div>
                <div class="font-medium">
                  {{ rollPairs.length }}
                </div>
              </div>
              <div class="text-right">
                <div class="text-xs text-gray-500 dark:text-gray-400">
                  Total Credit
                </div>
                <div
                  :class="[
                    'font-semibold',
                    rollPairs.reduce((sum, r) => sum + r.credit, 0) >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                  ]"
                >
                  {{ rollPairs.reduce((sum, r) => sum + r.credit, 0) >= 0 ? '+' : '' }}{{ formatCurrency(rollPairs.reduce((sum, r) => sum + r.credit, 0)) }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Execution History -->
        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <h4 class="text-sm font-semibold">
              Execution History
            </h4>
            <UButton
              v-if="positionData.position.status === 'OPEN' && positionData.position.source === 'MANUAL'"
              label="Add Execution"
              icon="i-lucide-plus"
              size="sm"
              variant="soft"
              @click="handleAddExecution"
            />
          </div>

          <UTable
            :data="executionRows"
            :columns="[
              { accessorKey: 'rollGroupId', header: 'Group', aggregationFn: 'count' },
              { accessorKey: 'executionDate', header: 'Date' },
              { accessorKey: 'type', header: 'Type' },
              { accessorKey: 'quantity', header: positionData.position.instrumentType === 'OPTION' ? 'Contracts' : 'Quantity' },
              { accessorKey: 'price', header: 'Price' },
              { accessorKey: 'commission', header: 'Commission' },
              { accessorKey: 'total', header: 'Total' },
              { accessorKey: 'rollCredit', header: 'Roll Credit' }
            ]"
            :grouping="['rollGroupId']"
            :grouping-options="groupingOptions"
          >
            <template #rollGroupId-cell="{ row }">
              <div
                v-if="row.getIsGrouped()"
                class="flex items-center gap-2 cursor-pointer py-2"
                @click="row.toggleExpanded()"
              >
                <UIcon
                  name="i-lucide-chevron-right"
                  class="w-4 h-4 transition-transform flex-shrink-0"
                  :class="{ 'rotate-90': row.getIsExpanded() }"
                />
                <UIcon
                  v-if="(row.groupingValue as string).startsWith('Roll')"
                  name="i-lucide-repeat"
                  class="w-4 h-4 text-primary flex-shrink-0"
                />
                <span class="font-medium">{{ row.groupingValue as string }}</span>
                <UBadge v-if="!(row.groupingValue as string).startsWith('Roll')" variant="subtle">
                  {{ row.subRows.length }} {{ row.subRows.length === 1 ? 'execution' : 'executions' }}
                </UBadge>
                <UBadge
                  v-if="(row.groupingValue as string).startsWith('Roll') && row.subRows.length > 0"
                  :color="(row.subRows[0]?.original?.rollCredit ?? 0) >= 0 ? 'success' : 'error'"
                  variant="soft"
                >
                  {{ (row.subRows[0]?.original?.rollCredit ?? 0) >= 0 ? '+' : '' }}{{ formatCurrency(row.subRows[0]?.original?.rollCredit ?? 0) }} credit
                </UBadge>
              </div>
              <div v-else class="pl-6">
                <!-- Empty for non-grouped rows -->
              </div>
            </template>
            <template #executionDate-cell="{ row }">
              <div v-if="!row.getIsGrouped()" :class="row.original.isRoll ? 'flex items-center gap-2' : ''">
                <UIcon v-if="row.original.isRoll" name="i-lucide-repeat" class="w-4 h-4 text-primary" />
                {{ formatDate(row.original.executionDate) }}
              </div>
            </template>
            <template #type-cell="{ row }">
              <UBadge
                v-if="!row.getIsGrouped()"
                :color="row.original.type === 'BUY' ? 'success' : 'error'"
                :variant="row.original.isRoll ? 'solid' : 'subtle'"
              >
                {{ row.original.type }}
                <span v-if="row.original.isRoll" class="ml-1">↻</span>
              </UBadge>
            </template>
            <template #quantity-cell="{ row }">
              <span v-if="!row.getIsGrouped()">{{ Math.abs(row.original.quantity) }}</span>
            </template>
            <template #price-cell="{ row }">
              <span v-if="!row.getIsGrouped()">{{ formatCurrency(row.original.price) }}</span>
            </template>
            <template #commission-cell="{ row }">
              <span v-if="!row.getIsGrouped()">{{ row.original.commission ? formatCurrency(row.original.commission) : '-' }}</span>
            </template>
            <template #total-cell="{ row }">
              <span v-if="row.getIsGrouped()">
                {{ formatCurrency(row.subRows.reduce((sum: number, subRow: any) => sum + (subRow.original.total || 0), 0)) }}
              </span>
              <span v-else>{{ formatCurrency(row.original.total) }}</span>
            </template>
            <template #rollCredit-cell="{ row }">
              <div v-if="!row.getIsGrouped()">
                <div v-if="row.original.rollCredit !== undefined" class="flex items-center gap-1">
                  <UBadge
                    :color="row.original.rollCredit >= 0 ? 'success' : 'error'"
                    variant="soft"
                  >
                    {{ row.original.rollCredit >= 0 ? '+' : '' }}{{ formatCurrency(row.original.rollCredit) }}
                  </UBadge>
                </div>
                <span v-else-if="row.original.rollType === 'open'" class="text-xs text-gray-500">↳ Roll in</span>
                <span v-else>-</span>
              </div>
            </template>
          </UTable>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-between">
        <div class="flex gap-2">
          <UButton
            label="Edit Metadata"
            icon="i-lucide-edit"
            variant="soft"
            @click="handleEditMetadata"
          />
          <UButton
            v-if="positionData?.position.status === 'OPEN'"
            label="Close Position"
            icon="i-lucide-x-circle"
            variant="soft"
            color="warning"
            @click="handleClosePosition"
          />
        </div>
        <div class="flex gap-2">
          <UButton
            label="Delete"
            icon="i-lucide-trash"
            variant="soft"
            color="error"
            @click="handleDelete"
          />
          <UButton
            label="Close"
            variant="outline"
            @click="isOpen = false"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
import { getGroupedRowModel } from '@tanstack/vue-table'
import type { PositionWithExecutions } from '~/types'

const { formatPositionName, formatOptionDetails, formatCurrency, formatDate } = usePositionFormatters()

const props = defineProps<{
  position: any
}>()

const emit = defineEmits<{
  addExecution: [position: any]
  editMetadata: [position: any]
  closePosition: [position: any]
  delete: [position: any]
}>()

const model = defineModel<boolean>()

const isOpen = computed({
  get: () => model.value ?? false,
  set: (value) => {
    model.value = value
  }
})

const toast = useToast()
const positionData = ref<PositionWithExecutions | null>(null)
const loading = ref(false)

// Watch for position changes and fetch execution details
watch(() => props.position, async (newPosition) => {
  if (newPosition && isOpen.value) {
    await loadPositionDetails()
  }
}, { immediate: true })

watch(isOpen, async (newValue) => {
  if (newValue && props.position) {
    await loadPositionDetails()
  }
})

async function loadPositionDetails() {
  if (!props.position) return

  loading.value = true
  try {
    const data = await $fetch<PositionWithExecutions>(
      `/udgaard/api/positions/${props.position.portfolioId}/${props.position.id}`
    )
    positionData.value = data
  } catch (error) {
    console.error('Error loading position details:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load position details',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

// Detect roll pairs and calculate credits
const rollPairs = computed(() => {
  if (!positionData.value?.executions) return []

  const sorted = [...positionData.value.executions].sort(
    (a, b) => new Date(a.executionDate).getTime() - new Date(b.executionDate).getTime()
  )

  const pairs = []
  const used = new Set()

  for (let i = 0; i < sorted.length; i++) {
    if (used.has(i)) continue

    const current = sorted[i]
    if (!current || current.quantity >= 0) continue // Only look for SELLs

    // Look for matching BUY within 2 days
    for (let j = i + 1; j < sorted.length && j < i + 5; j++) {
      if (used.has(j)) continue

      const next = sorted[j]
      if (!next || next.quantity <= 0) continue // Must be BUY

      const daysDiff = Math.abs(
        (new Date(next.executionDate).getTime() - new Date(current.executionDate).getTime()) / (1000 * 60 * 60 * 24)
      )

      if (daysDiff <= 2 && Math.abs(current.quantity) === next.quantity) {
        const multiplier = positionData.value?.position.multiplier || 1
        const sellProceeds = Math.abs(current.quantity) * current.price * multiplier
        const buyCost = next.quantity * next.price * multiplier
        const totalCommission = (current.commission || 0) + (next.commission || 0)
        const credit = sellProceeds - buyCost - Math.abs(totalCommission)

        pairs.push({
          sellId: current.id,
          buyId: next.id,
          credit,
          sellDate: current.executionDate,
          buyDate: next.executionDate
        })

        used.add(i)
        used.add(j)
        break
      }
    }
  }

  return pairs
})

// Transform executions into table rows with roll highlighting
const executionRows = computed(() => {
  if (!positionData.value?.executions) return []

  return positionData.value.executions
    .map((execution) => {
      const isBuy = execution.quantity > 0
      const absQuantity = Math.abs(execution.quantity)
      const total = absQuantity * execution.price * (positionData.value?.position.multiplier || 1) + (execution.commission || 0)

      // Check if this execution is part of a roll and find its group ID
      const rollIndex = rollPairs.value.findIndex(r => r.sellId === execution.id || r.buyId === execution.id)
      const roll = rollIndex >= 0 ? rollPairs.value[rollIndex] : undefined
      const isRollSell = roll && roll.sellId === execution.id
      const isRollBuy = roll && roll.buyId === execution.id

      return {
        id: execution.id,
        rollGroupId: rollIndex >= 0 ? `Roll ${rollIndex + 1}` : 'Regular Trade',
        executionDate: execution.executionDate,
        type: isBuy ? 'BUY' : 'SELL',
        quantity: execution.quantity,
        price: execution.price,
        commission: execution.commission,
        total: isBuy ? -total : total,
        brokerTradeId: execution.brokerTradeId,
        isRoll: !!roll,
        rollCredit: isRollSell ? roll.credit : undefined,
        rollType: isRollSell ? 'close' : isRollBuy ? 'open' : undefined
      }
    })
    .sort((a, b) => new Date(a.executionDate).getTime() - new Date(b.executionDate).getTime())
})

// Grouping options
const groupingOptions = ref({
  groupedColumnMode: false as const,
  getGroupedRowModel: getGroupedRowModel()
})

function handleAddExecution() {
  emit('addExecution', positionData.value?.position)
  isOpen.value = false
}

function handleEditMetadata() {
  emit('editMetadata', positionData.value?.position)
  isOpen.value = false
}

function handleClosePosition() {
  emit('closePosition', positionData.value?.position)
  isOpen.value = false
}

function handleDelete() {
  emit('delete', positionData.value?.position)
  isOpen.value = false
}
</script>
