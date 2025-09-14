<template>
  <div>
    <v-row v-if="error" class="mt-2">
      <v-alert v-model="error" border="start" close-label="Close error" color="#C51162"
        title="Error!" variant="tonal" closable>
        Error fetching trades, please try again.
      </v-alert>
    </v-row>
    <v-row>
      <v-col>
        <div class="d-flex justify-space-evenly mt-2">
          <data-card title="Number of wins" :content="numberOfWinningTrades" heading="4" />
          <data-card title="Number of losses" :content="numberOfLosingTrades" heading="4" />
          <data-card title="Win rate" :content="winRate + '%'" heading="4" />
          <data-card title="Average win" :content="averageWinPercent + '%'" heading="4" />
          <data-card title="Loss rate" :content="lossRate + '%'" heading="4" />
          <data-card title="Average loss" :content="averageLossPercent + '%'" heading="4" />
          <data-card title="Edge" :content="edge + '%'" heading="4" />
        </div>
      </v-col>
    </v-row>

    <v-row class="mx-2">
      <v-col cols="10">
        <v-card>
          <v-card-text>
            <trades-chart :report="backTestreport" @trade-selected="(trade) => selectedTrade = trade" />
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="2">
        <v-card>
          <v-card-text>
            <v-form fast-fail @submit.prevent>
              <div>
                <v-text-field density="compact" v-model="symbol" label="Symbol" required />
                <v-checkbox label="Refresh" v-model="refresh" />
                <v-btn type="submit" @click="handleSubmit" block>Submit</v-btn>
              </div>
            </v-form>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="selectedTrade" class="mx-2 mb-2">
      <v-col cols="9">
        <trade-chart :trade="selectedTrade" />
      </v-col>
      <v-col cols="3">
        <v-card>
          <v-card-title class="bg-blue">Trade results</v-card-title>
          <v-card-text>
            <ul class="text-body-1" style="list-style: none;">
              <li>{{ `Exit reason: ${selectedTrade?.exitReason}` }}</li>
              <li>{{ `Profit: ${selectedTrade?.profitPercentage?.toFixed(2) || "0"}%` }}</li>
              <li>{{ `Buy signal date: ${selectedTrade?.entryQuote.lastBuySignal}` }}</li>
              <li>{{ `Entry heatmap: ${selectedTrade?.entryQuote.heatmap?.toFixed(2)}` }}</li>
              <li>{{ `Sector entry heatmap: ${selectedTrade?.entryQuote.sectorHeatmap?.toFixed(2)}` }}</li>
            </ul>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </div>
  <loader :loading="loading" />
</template>

<script setup lang="ts">
import type { BacktestReport, Trade } from '@/types';
import axios from 'axios'

// variables
const symbol = ref<string>("")
const refresh = ref<boolean>(false)
const backTestreport = ref<BacktestReport | undefined>()
const selectedTrade = ref<Trade | undefined>()
const loading = ref<boolean>(false)
const error = ref<boolean>(false)

// Functions
const handleSubmit = () => {
  fetchTradesForStock(symbol.value)
}

const numberOfWinningTrades = computed(() => backTestreport.value?.trades?.filter(trade => trade.profitPercentage > 0).length || 0)
const numberOfLosingTrades = computed(() => backTestreport.value?.trades?.filter(trade => trade.profitPercentage < 0).length || 0)
const winRate = computed(() => {
  const total = numberOfWinningTrades.value + numberOfLosingTrades.value
  return total > 0 ? numberOfWinningTrades.value / total : 0
})
const lossRate = computed(() => {
  const total = numberOfWinningTrades.value + numberOfLosingTrades.value
  return total > 0 ? numberOfLosingTrades.value / total : 0
})
const averageWinPercent = computed(() => {
  const wins = backTestreport.value?.trades?.filter(trade => trade.profitPercentage > 0) || []
  const total = wins.length
  const sum = wins.reduce((acc, trade) => acc + trade.profitPercentage, 0)
  return total > 0 ? sum / total : 0
})

const averageLossPercent = computed(() => {
  const losses = backTestreport.value?.trades?.filter(trade => trade.profitPercentage < 0) || []
  const total = losses.length
  const sum = losses.reduce((acc, trade) => acc + trade.profitPercentage, 0)
  return total > 0 ? sum / total : 0
})

// (AvgWinPercentage × WinRate) − ((1−WinRate) × AvgLossPercentage)
const edge = (averageWinPercent.value * winRate.value) - ((1.0 - winRate.value) * averageLossPercent.value)

// TODO move into a Service function
const fetchTradesForStock = async (symbol: String) => {  
  try {
    loading.value = true
    const resp = await axios.get(`http://localhost:8080/api/report?stockSymbol=${symbol}&refresh=${refresh.value}`)
    backTestreport.value = resp.data
    error.value = false
  } catch (e) {
    console.log("Error fetching trades");
    console.error(e);
    error.value = true;
  } finally {
    loading.value = false
  }
}
</script>
