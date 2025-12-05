<script setup lang="ts">
import type { PortfolioTrade, RollChainResponse } from '~/types'

const props = defineProps<{
  trade: PortfolioTrade
  portfolioId: number
}>()

const emit = defineEmits<{
  close: []
}>()

const isOpen = defineModel<boolean>('open', { required: true })
const toast = useToast()

const rollChain = ref<PortfolioTrade[]>([])
const loading = ref(false)

// Load roll chain when modal opens
watch(isOpen, async (open) => {
  if (open && props.trade.id) {
    await loadRollChain()
  }
})

async function loadRollChain() {
  loading.value = true
  try {
    const response: RollChainResponse = await $fetch(
      `/udgaard/api/portfolio/${props.portfolioId}/trades/${props.trade.id}/roll-chain`
    )
    rollChain.value = response.trades
  } catch (error) {
    console.error('Failed to load roll chain:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load roll chain',
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

function close() {
  isOpen.value = false
  emit('close')
}

function formatCurrency(value: number | undefined) {
  if (!value) return '$0.00'
  return `$${value.toFixed(2)}`
}

function formatPercent(value: number | undefined) {
  if (!value) return '0.00%'
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}

// Calculate cumulative metrics for display
function getCumulativeProfit(trade: PortfolioTrade): number | undefined {
  if (trade.status !== 'CLOSED' || !trade.profit) return undefined
  return (trade.profit || 0) + (trade.cumulativeRealizedProfit || 0)
}

function getCumulativeReturnPct(trade: PortfolioTrade): number | undefined {
  const cumProfit = getCumulativeProfit(trade)
  if (!cumProfit) return undefined

  const totalCostBasis = (trade.originalCostBasis || trade.positionSize || 0) + (trade.totalRollCost || 0)
  if (totalCostBasis === 0) return undefined

  return (cumProfit / totalCostBasis) * 100
}
</script>

<template>
  <UModal
    :open="isOpen"
    :ui="{ width: 'sm:max-w-4xl' }"
    @update:open="isOpen = $event"
  >
    <template #header>
      <div>
        <h3 class="text-lg font-semibold">
          Roll Chain History
        </h3>
        <p class="text-sm text-muted mt-1">
          {{ trade.symbol }} - {{ rollChain.length }} position(s) in chain
        </p>
      </div>
    </template>

    <template #body>
      <div v-if="loading" class="flex justify-center py-8">
        <div class="text-muted">
          Loading roll chain...
        </div>
      </div>

      <div v-else class="space-y-4">
        <div
          v-for="(chainTrade, index) in rollChain"
          :key="chainTrade.id"
          class="relative"
        >
          <!-- Roll indicator between trades -->
          <div v-if="index > 0" class="flex justify-center my-2">
            <div class="flex items-center gap-2 text-xs text-muted">
              <div class="h-px w-12 bg-border" />
              <UBadge color="info" size="xs">
                Rolled {{ chainTrade.rollDate }}
              </UBadge>
              <div class="h-px w-12 bg-border" />
            </div>
          </div>

          <UCard :ui="{ body: 'p-4' }">
            <div class="space-y-3">
              <!-- Header -->
              <div class="flex items-start justify-between">
                <div class="flex items-center gap-2">
                  <UBadge
                    :color="index === 0 ? 'neutral' : 'info'"
                    size="xs"
                  >
                    {{ index === 0 ? 'Original' : `Roll #${index}` }}
                  </UBadge>
                  <UBadge
                    :color="chainTrade.status === 'OPEN' ? 'success' : 'neutral'"
                    size="xs"
                  >
                    {{ chainTrade.status }}
                  </UBadge>
                </div>
                <div
                  v-if="chainTrade.status === 'CLOSED'"
                  class="text-sm font-medium"
                  :class="(chainTrade.profit || 0) >= 0 ? 'text-green-600' : 'text-red-600'"
                >
                  {{ formatPercent(chainTrade.profitPercentage) }}
                </div>
              </div>

              <!-- Position details -->
              <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                <div>
                  <div class="text-xs text-muted">
                    Strike
                  </div>
                  <div class="font-medium">
                    ${{ chainTrade.strikePrice?.toFixed(2) }}
                  </div>
                </div>
                <div>
                  <div class="text-xs text-muted">
                    Expiration
                  </div>
                  <div class="font-medium">
                    {{ chainTrade.expirationDate }}
                  </div>
                </div>
                <div>
                  <div class="text-xs text-muted">
                    Entry
                  </div>
                  <div class="font-medium">
                    {{ chainTrade.contracts }} @ {{ formatCurrency(chainTrade.entryPrice) }}
                  </div>
                </div>
                <div v-if="chainTrade.status === 'CLOSED'">
                  <div class="text-xs text-muted">
                    Exit
                  </div>
                  <div class="font-medium">
                    {{ formatCurrency(chainTrade.exitPrice) }} ({{ chainTrade.exitDate }})
                  </div>
                </div>
              </div>

              <!-- Roll cost (if rolled from previous) -->
              <div v-if="chainTrade.rollCost !== undefined && chainTrade.rollCost !== null" class="pt-2 border-t">
                <div class="flex justify-between text-sm">
                  <span class="text-muted">Roll Cost:</span>
                  <span
                    class="font-medium"
                    :class="chainTrade.rollCost > 0 ? 'text-red-600' : 'text-green-600'"
                  >
                    {{ formatCurrency(Math.abs(chainTrade.rollCost)) }}
                    {{ chainTrade.rollCost > 0 ? '(Debit)' : '(Credit)' }}
                  </span>
                </div>
              </div>

              <!-- Cumulative metrics (for closed trades) -->
              <div v-if="chainTrade.status === 'CLOSED' && index > 0" class="pt-2 border-t">
                <div class="space-y-1">
                  <div class="flex justify-between text-sm">
                    <span class="text-muted">This Leg P/L:</span>
                    <span
                      class="font-medium"
                      :class="(chainTrade.profit || 0) >= 0 ? 'text-green-600' : 'text-red-600'"
                    >
                      {{ formatCurrency(chainTrade.profit) }} ({{ formatPercent(chainTrade.profitPercentage) }})
                    </span>
                  </div>
                  <div class="flex justify-between text-sm font-semibold">
                    <span>Cumulative P/L:</span>
                    <span
                      :class="(getCumulativeProfit(chainTrade) || 0) >= 0 ? 'text-green-600' : 'text-red-600'"
                    >
                      {{ formatCurrency(getCumulativeProfit(chainTrade)) }}
                      ({{ formatPercent(getCumulativeReturnPct(chainTrade)) }})
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </UCard>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end">
        <UButton
          label="Close"
          color="neutral"
          variant="outline"
          @click="close"
        />
      </div>
    </template>
  </UModal>
</template>
