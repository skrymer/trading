<template>
  <v-container>
    <v-row>
      <v-col cols="10">
        <v-card>
          <v-card-text>
            <trades-chart :report="backTestreport" @trade-selected="(trade) => selectedTrade = trade" />
            <v-container>
              <v-row>
                <v-col cols="9">
                  <trade-chart :trade="selectedTrade" />
                </v-col>
                <v-col cols="3">
                  <v-card title="Trade results">
                    <v-card-text>
                      <ul class="text-body-1" style="list-style: none;">
                        <li>{{ `Exit reason: ${selectedTrade?.exitReason}` }}</li>
                        <li>{{ `Profit percentage: ${selectedTrade?.profitPercentage.toFixed(2) || "0"}%` }}</li>
                        <li>{{ `Profit: ${selectedTrade?.profit.toFixed(2) || "0"}$` }}</li>
                        <li>{{ `Buy signal date: ${selectedTrade?.entryQuote.lastBuySignal}` }}</li>
                        <li>{{ `Entry heatmap: ${selectedTrade?.entryQuote.heatmap.toFixed(2)}` }}</li>
                        <li>{{ `Sector entry heatmap: ${selectedTrade?.entryQuote.sectorHeatmap.toFixed(2)}` }}</li>
                      </ul>
                    </v-card-text>
                  </v-card>
                </v-col>
              </v-row>
            </v-container>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="2">
        <v-card title="Report results">
          <v-card-text>
            <ul class="text-body-1" style="list-style: none;">
              <li>{{ `Number of wins: ${backTestreport?.numberOfWinningTrades || "0"}` }}</li>
              <li>{{ `Number of losses: ${backTestreport?.numberOfLosingTrades || "0"}` }}</li>
              <li>{{ `Win rate ${((backTestreport?.winRate || 0) * 100).toFixed(2)}%` }}</li>
              <li>{{ `Average win amount ${backTestreport?.averageWinAmount.toFixed(2) || "0"}$` }}</li>
              <li>{{ `Loss rate ${((backTestreport?.lossRate || 0) * 100).toFixed(2)}%` }}</li>
              <li>{{ `Average loss amount ${backTestreport?.averageLossAmount.toFixed(2) || "0"}$` }}</li>
              <li>{{ `Edge: ${backTestreport?.edge.toFixed(2) || "0"}%` }}</li>
              <li>Main reason for exiting trade TODO</li>
            </ul>
          </v-card-text>
        </v-card>

        <v-card class="mt-4">
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
  </v-container>
  <loader :loading="loading" />
</template>

<script setup lang="ts">
import type { BacktestReport, Trade } from '@/types';
import axios from 'axios'

// variables
const headers = [
  { title: 'Date', value: 'date' },
  { title: "Close price", value: "closePrice" },
  { title: "Heatmap", value: "heatmap" },
  { title: "Sector heatmap", value: "sectorHeatmap" },
  { title: "ATR", value: "atr" }
]

const symbol = ref<string>("")
const refresh = ref<boolean>(false)
const backTestreport = ref<BacktestReport | undefined>()
const selectedTrade = ref<Trade | undefined>()
const loading = ref<boolean>(false)

// Functions

const handleSubmit = () => {
  fetchTrades(symbol.value)
}

// TODO move into a Service function
const fetchTrades = async (symbol: String) => {
  loading.value = true
  try {
    const resp = await axios.get(`http://localhost:8080/api/report?stockSymbol=${symbol}&refresh=${refresh.value}`)
    backTestreport.value = resp.data
  } finally {
    loading.value = false
  }
}
</script>
