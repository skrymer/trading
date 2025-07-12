<template>
  <v-container>
    <v-row>
      <v-col cols="3">
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
            </ul>
          </v-card-text>
        </v-card>

        <v-card class="mt-4">
          <v-card-text>
            <v-form fast-fail @submit.prevent>
              <v-container class="fill-width">
                <v-row>
                  <v-col sm="12" cols="8">
                    <v-text-field density="compact" v-model="symbol" label="Symbol" required />
                    <v-checkbox label="Refresh" v-model="refresh" />
                  </v-col>
                </v-row>
                <v-row>
                  <v-col cols="4" offset="8">
                    <v-btn type="submit" @click="handleSubmit" block>Submit</v-btn>
                  </v-col>
                </v-row>
              </v-container>
            </v-form>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="9">
        <v-card>
          <v-card-text>
            <trades-chart :report="backTestreport" @trade-selected="(trade) => selectedTrade = trade"/>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="3">
        <v-card title="Trade results">
          <v-card-text>
            <ul class="text-body-1" style="list-style: none;">
              <li>{{ `Exit reason: ${selectedTrade?.exitReason}` }}</li>
              <li>{{ `Profit percentage: ${selectedTrade?.profitPercentage.toFixed(2) || "0"}%` }}</li>
              <li>{{ `Profit: ${selectedTrade?.profit.toFixed(2) || "0"}$` }}</li>
            </ul>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="9">
        <v-card>
          <v-card-text>
            <v-data-table :headers="headers"
              :items="selectedTrade ? [selectedTrade?.entryQuote, ...selectedTrade?.quotes, selectedTrade?.exitQuote] : []" />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="12">
        <trade-chart :trade="selectedTrade" />
      </v-col>
    </v-row>
  </v-container>
  <loader :loading="loading" />
</template>

<script setup lang="ts">
import type { BacktestReport, ChartOption, Serie, Trade } from '@/types';
import axios from 'axios'

// variables
const headers = [
  { title: 'Date', value: 'date' },
  { title: "Close price", value: "closePrice" },
  { title: "Heatmap", value: "heatmap" },
  { title: "Sector heatmap", value: "sectorHeatmap" }
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
