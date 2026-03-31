<script setup lang="ts">
import { format } from 'date-fns'
import type { ScanResult, AddScannerTradeRequest, LatestQuote, OptionContractResponse } from '~/types'

type QuoteStatus = 'loading' | 'loaded' | 'error'

const props = defineProps<{
  results: ScanResult[]
  entryStrategyName: string
  exitStrategyName: string
  instrumentMode: 'STOCK' | 'OPTION'
  calculateQuantity: (atr: number, symbol?: string) => number
  optionContracts: Map<string, OptionContractResponse>
  selectedCapital: number
  portfolioUtilization: number
}>()

const emit = defineEmits<{
  'success': [count: number]
  'remove-symbol': [symbol: string]
}>()

const model = defineModel<boolean>()

const isOpen = computed({
  get: () => model.value ?? false,
  set: (value) => { model.value = value }
})

const toast = useToast()
const submitting = ref(false)
const quoteStatuses = ref<Map<string, QuoteStatus>>(new Map())
const liveQuotes = ref<Map<string, LatestQuote>>(new Map())

const isOptionsMode = computed(() => props.instrumentMode === 'OPTION')

const allQuotesSettled = computed(() =>
  quoteStatuses.value.size > 0 && [...quoteStatuses.value.values()].every(s => s !== 'loading')
)

const allQuotesFailed = computed(() =>
  allQuotesSettled.value && [...quoteStatuses.value.values()].every(s => s === 'error')
)

const tradesWithPrices = computed(() =>
  props.results.map((r) => {
    const quote = liveQuotes.value.get(r.symbol)
    const contract = props.optionContracts.get(r.symbol)
    const qty = props.calculateQuantity(r.atr, r.symbol) || (isOptionsMode.value ? 1 : 100)
    const entryPrice = isOptionsMode.value && contract
      ? contract.price
      : (quote?.price ?? r.closePrice)
    return {
      result: r,
      quote,
      contract,
      quantity: qty,
      entryPrice,
      isLive: !!quote,
      status: quoteStatuses.value.get(r.symbol) ?? 'loading'
    }
  })
)

watch(isOpen, async (newValue) => {
  if (!newValue || props.results.length === 0) return

  liveQuotes.value = new Map()
  quoteStatuses.value = new Map(props.results.map(r => [r.symbol, 'loading' as QuoteStatus]))

  await Promise.allSettled(
    props.results.map(async (r) => {
      try {
        const quote = await $fetch<LatestQuote>(`/udgaard/api/stocks/${r.symbol}/latest-quote`)
        liveQuotes.value.set(r.symbol, quote)
        quoteStatuses.value.set(r.symbol, 'loaded')
      } catch {
        quoteStatuses.value.set(r.symbol, 'error')
      }
    })
  )
})

async function onSubmit() {
  submitting.value = true
  let added = 0
  try {
    const today = format(new Date(), 'yyyy-MM-dd')
    for (const trade of tradesWithPrices.value) {
      const body: AddScannerTradeRequest = {
        symbol: trade.result.symbol,
        sectorSymbol: trade.result.sectorSymbol ?? undefined,
        instrumentType: isOptionsMode.value ? 'OPTION' : 'STOCK',
        entryPrice: trade.entryPrice,
        entryDate: trade.isLive ? today : trade.result.date,
        quantity: trade.quantity,
        entryStrategyName: props.entryStrategyName,
        exitStrategyName: props.exitStrategyName
      }
      if (isOptionsMode.value && trade.contract) {
        body.optionType = 'CALL'
        body.strikePrice = trade.contract.strike
        body.expirationDate = trade.contract.expiration
        body.multiplier = 100
        body.optionPrice = trade.contract.price
        body.delta = trade.contract.delta
      }
      await $fetch('/udgaard/api/scanner/trades', { method: 'POST', body })
      added++
    }
    toast.add({
      title: 'Trades Added',
      description: `Added ${added} trades to scanner`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
    emit('success', added)
    isOpen.value = false
  } catch (error: any) {
    toast.add({
      title: 'Error',
      description: error.data?.message || `Failed after adding ${added} of ${tradesWithPrices.value.length} trades`,
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
    if (added > 0) emit('success', added)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal
    :open="isOpen"
    title="Confirm Batch Add"
    size="xl"
    @update:open="(value) => isOpen = value"
  >
    <template #body>
      <div class="space-y-3">
        <UAlert
          v-if="allQuotesFailed"
          icon="i-lucide-alert-triangle"
          color="warning"
          variant="subtle"
          description="Live prices unavailable — trades will use yesterday's close price."
        />

        <div class="overflow-x-auto max-h-[60vh] overflow-y-auto">
          <table class="w-full text-sm">
            <thead class="sticky top-0 bg-default z-10">
              <tr class="border-b border-default text-left text-muted text-xs uppercase tracking-wide">
                <th class="pb-2 pr-3">
                  Symbol
                </th>
                <th class="pb-2 pr-3">
                  Sector
                </th>
                <th class="pb-2 pr-3 text-right">
                  Entry Price
                </th>
                <th class="pb-2 pr-3 text-right">
                  Change
                </th>
                <th class="pb-2 pr-3 text-right">
                  Qty
                </th>
                <th v-if="isOptionsMode" class="pb-2 pr-3 text-right">
                  Strike
                </th>
                <th v-if="isOptionsMode" class="pb-2 pr-3 text-right">
                  Delta
                </th>
                <th class="pb-2 w-8" />
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="trade in tradesWithPrices"
                :key="trade.result.symbol"
                class="border-b border-default/50 hover:bg-muted/30"
              >
                <td class="py-2 pr-3 font-medium">
                  {{ trade.result.symbol }}
                </td>
                <td class="py-2 pr-3 text-muted">
                  {{ trade.result.sectorSymbol }}
                </td>
                <td class="py-2 pr-3 text-right">
                  <UIcon v-if="trade.status === 'loading'" name="i-lucide-loader-2" class="size-3.5 animate-spin" />
                  <template v-else>
                    ${{ trade.entryPrice.toFixed(2) }}
                    <UBadge
                      :label="trade.isLive ? 'LIVE' : 'CLOSE'"
                      :color="trade.isLive ? 'success' : 'warning'"
                      variant="subtle"
                      size="xs"
                      class="ml-1"
                    />
                  </template>
                </td>
                <td class="py-2 pr-3 text-right">
                  <span
                    v-if="trade.quote"
                    class="text-xs"
                    :class="trade.quote.change >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                  >
                    {{ trade.quote.change >= 0 ? '+' : '' }}{{ trade.quote.changePercent.toFixed(2) }}%
                  </span>
                  <span v-else class="text-muted">—</span>
                </td>
                <td class="py-2 pr-3 text-right">
                  {{ trade.quantity }}
                </td>
                <td v-if="isOptionsMode" class="py-2 pr-3 text-right">
                  {{ trade.contract ? `$${trade.contract.strike.toFixed(0)}` : '—' }}
                </td>
                <td v-if="isOptionsMode" class="py-2 pr-3 text-right">
                  {{ trade.contract ? trade.contract.delta.toFixed(2) : '—' }}
                </td>
                <td class="py-2">
                  <UButton
                    icon="i-lucide-x"
                    color="neutral"
                    variant="ghost"
                    size="xs"
                    @click="emit('remove-symbol', trade.result.symbol)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          v-if="allQuotesSettled && !allQuotesFailed && liveQuotes.size < results.length"
          class="text-xs text-amber-600 dark:text-amber-400"
        >
          Live prices unavailable for {{ results.length - liveQuotes.size }} symbol(s) — using previous close.
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <div class="flex items-center gap-4 text-sm text-muted">
          <span>Capital: <strong class="text-foreground">${{ selectedCapital.toLocaleString(undefined, { maximumFractionDigits: 0 }) }}</strong></span>
          <span>
            Utilization:
            <strong :class="portfolioUtilization > 80 ? 'text-amber-600 dark:text-amber-400' : 'text-foreground'">
              {{ portfolioUtilization.toFixed(1) }}%
            </strong>
          </span>
        </div>
        <div class="flex gap-2">
          <UButton label="Cancel" variant="outline" @click="isOpen = false" />
          <UButton
            :label="`Add ${results.length} Trade${results.length > 1 ? 's' : ''}`"
            icon="i-lucide-plus"
            :loading="submitting"
            :disabled="!allQuotesSettled || results.length === 0"
            @click="onSubmit"
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
