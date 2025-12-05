<script setup lang="ts">
import type { PortfolioTrade, RollTradeRequest, RollTradeResponse } from '~/types'

const props = defineProps<{
  trade: PortfolioTrade
  portfolioId: number
}>()

const emit = defineEmits<{
  close: []
  rolled: [trade: PortfolioTrade]
}>()

const isOpen = defineModel<boolean>('open', { required: true })
const toast = useToast()

// Form state
const rollDate = ref<string>(new Date().toISOString().split('T')[0] || '')
const exitPrice = ref<number>(props.trade.exitPrice || 0)
const newStrikePrice = ref<number>(props.trade.strikePrice || 0)
const newExpirationDate = ref<string>(props.trade.expirationDate || '')
const newEntryPrice = ref<number>(0)
const contracts = ref<number>(props.trade.contracts || 1)

// Computed values
const exitValue = computed(() => {
  return exitPrice.value * (props.trade.contracts || 1) * (props.trade.multiplier || 100)
})

const newEntryCost = computed(() => {
  return newEntryPrice.value * contracts.value * (props.trade.multiplier || 100)
})

const rollCost = computed(() => {
  return newEntryCost.value - exitValue.value
})

const isRollDebit = computed(() => rollCost.value > 0)

const loading = ref(false)

async function rollTrade() {
  if (!newEntryPrice.value || !newStrikePrice.value || !newExpirationDate.value || !props.trade.optionType || !rollDate.value) {
    toast.add({
      title: 'Error',
      description: 'Please fill in all required fields',
      color: 'error'
    })
    return
  }

  loading.value = true
  try {
    const request: RollTradeRequest = {
      newSymbol: props.trade.symbol || '',
      newStrikePrice: newStrikePrice.value,
      newExpirationDate: newExpirationDate.value,
      newOptionType: props.trade.optionType,
      newEntryPrice: newEntryPrice.value,
      rollDate: rollDate.value,
      contracts: contracts.value,
      exitPrice: exitPrice.value
    }

    const response = await $fetch<RollTradeResponse>(`/udgaard/api/portfolio/${props.portfolioId}/trades/${props.trade.id}/roll`, {
      method: 'POST',
      body: request
    })

    toast.add({
      title: 'Success',
      description: 'Position rolled successfully',
      color: 'success'
    })

    emit('rolled', response.newTrade)
    isOpen.value = false
  } catch (error) {
    console.error('Failed to roll trade:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to roll position',
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
</script>

<template>
  <UModal
    :open="isOpen"
    title="Roll Option Position"
    :ui="{ width: 'sm:max-w-2xl' }"
    @update:open="isOpen = $event"
  >
    <template #body>
      <div class="space-y-6">
        <!-- Current Position Summary -->
        <UCard>
          <template #header>
            <h4 class="text-sm font-semibold">
              Current Position
            </h4>
          </template>

          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <div class="text-muted text-xs">
                Symbol
              </div>
              <div class="font-medium">
                {{ trade.symbol }}
              </div>
            </div>
            <div>
              <div class="text-muted text-xs">
                Contracts
              </div>
              <div class="font-medium">
                {{ trade.contracts }} @ ${{ trade.entryPrice.toFixed(2) }}
              </div>
            </div>
            <div>
              <div class="text-muted text-xs">
                Strike
              </div>
              <div class="font-medium">
                ${{ trade.strikePrice?.toFixed(2) }}
              </div>
            </div>
            <div>
              <div class="text-muted text-xs">
                Expiration
              </div>
              <div class="font-medium">
                {{ trade.expirationDate }}
              </div>
            </div>
          </div>
        </UCard>

        <!-- Roll Details -->
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <UFormField label="Roll Date" required>
              <UInput v-model="rollDate" type="date" />
            </UFormField>

            <UFormField label="Current Price" hint="Current value per contract" required>
              <UInput v-model="exitPrice" type="number" step="0.01" />
            </UFormField>
          </div>

          <UDivider label="New Position" />

          <div class="grid grid-cols-2 gap-4">
            <UFormField label="New Strike Price" required>
              <UInput v-model="newStrikePrice" type="number" step="0.01" />
            </UFormField>

            <UFormField label="New Expiration" required>
              <UInput v-model="newExpirationDate" type="date" />
            </UFormField>

            <UFormField label="New Entry Price" hint="Price per contract for new position" required>
              <UInput v-model="newEntryPrice" type="number" step="0.01" />
            </UFormField>

            <UFormField label="Contracts" required>
              <UInput v-model="contracts" type="number" />
            </UFormField>
          </div>

          <!-- Roll Cost Summary -->
          <UAlert
            :color="isRollDebit ? 'warning' : 'success'"
            :title="isRollDebit ? 'Roll Debit' : 'Roll Credit'"
          >
            <template #description>
              <div class="space-y-1 text-sm">
                <div class="flex justify-between">
                  <span>Exit value:</span>
                  <span class="font-medium">${{ exitValue.toFixed(2) }}</span>
                </div>
                <div class="flex justify-between">
                  <span>New entry cost:</span>
                  <span class="font-medium">${{ newEntryCost.toFixed(2) }}</span>
                </div>
                <UDivider />
                <div class="flex justify-between font-semibold">
                  <span>{{ isRollDebit ? 'You will pay:' : 'You will receive:' }}</span>
                  <span>${{ Math.abs(rollCost).toFixed(2) }}</span>
                </div>
              </div>
            </template>
          </UAlert>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="close"
        />
        <UButton
          label="Roll Position"
          color="primary"
          :loading="loading"
          @click="rollTrade"
        />
      </div>
    </template>
  </UModal>
</template>
