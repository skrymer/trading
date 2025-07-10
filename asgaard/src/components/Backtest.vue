<template>
  <v-container>
    <v-row>
      <v-col cols="3">
        <v-card title="Report results">
          <v-card-text>
            <ul class="text-body-1" style="list-style: none;">
              <li>{{ `Number of wins: ${backTestreport?.numberOfWinningTrades || "0"}` }}</li>
              <li>{{ `Number of losses: ${backTestreport?.numberOfLosingTrades || "0"}` }}</li>
              <li>{{ `Win rate ${backTestreport?.winRate.toFixed(2) || "0"}` }}</li>
              <li>{{ `Average win amount ${backTestreport?.averageWinAmount.toFixed(2) || "0"}$` }}</li>
              <li>{{ `Loss rate ${backTestreport?.lossRate.toFixed(2) || "0"}` }}</li>
              <li>{{ `Average loss amount ${backTestreport?.averageLossAmount.toFixed(2) || "0"}$` }}</li>
              <li>{{ `Edge: ${backTestreport?.edge.toFixed(2) || "0"}$` }}</li>
            </ul>
          </v-card-text>
        </v-card>
        
        <v-form fast-fail @submit.prevent>
          <v-container class="fill-width">
            <v-row>
              <v-col cols="12" md="8">
                <v-text-field density="compact" v-model="symbol" label="Symbol" required></v-text-field>
              </v-col>
              <v-col cols="12" md="4">
                <v-btn type="submit" @click="handleSubmit" block>Submit</v-btn>
              </v-col>
            </v-row>
          </v-container>
        </v-form>
      </v-col>

      <v-col cols="9">
        <apexchart height="550" type="bar" :options="chartOptions" :series="series" />
      </v-col>      
    </v-row>
    <v-row>
      <v-data-table :headers="headers" :items="selectedTrade ? [selectedTrade?.entryQuote, ...selectedTrade?.quotes, selectedTrade?.exitQuote] : []"></v-data-table>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import axios from 'axios'

// variables
const headers = [
  { title: 'Date', value: 'date' }, 
  {title: "Close price", value: "closePrice"},
  {title: "Heatmap", value: "heatmap"},
  {title: "Sector heatmap", value: "sectorHeatmap"}
]
const chartOptions = ref<ChartOption>({
  chart: {
    id: 'trades',
    events: {
      dataPointSelection: function (e, c, config) {
        handleChartClicked(e, c, config)
      }
    },
  },
  colors: ['#000000'],
  xaxis: {
    categories: [],
  },
});
const series = ref<Serie[]>([
  { name: 'winning', data: [] },
]);
const symbol = ref<string>("")
const backTestreport = ref<BacktestReport | null>(null)
const selectedTrade = ref<Trade | undefined>()

// Functions

const handleChartClicked = (e: any, c: any, config: {dataPointIndex: number}) => {
  console.log(config.dataPointIndex);
  selectedTrade.value = backTestreport.value?.trades[config.dataPointIndex]  
  console.log(selectedTrade.value);
  
}

const handleSubmit = () => {
  fetchTrades(symbol.value)
}

// TODO move into a Service function
// TODO spinner
const fetchTrades = async (symbol: String) => {
  const resp = await axios.get(`http://localhost:8080/api/report?stock=${symbol}`)
  backTestreport.value = resp.data
  const entryDates = backTestreport.value?.trades?.map((it: Trade) => {
    return it.entryQuote.date
  })
  const profit = backTestreport.value?.trades?.map((it: Trade) => {
    return { x: it.entryQuote.date, y: it.profit.toFixed(2), fillColor: it.profit > 0 ? '#39fc03' : "#fc2403" }
  })

  chartOptions.value = {
    ...chartOptions.value,
    xaxis: {
      categories: entryDates
    }
  }
  series.value = [{
    ...series.value[0],
    data: profit
  }]  
}

// TODO extract types

interface BacktestReport {
  trades: Trade[]
  numberOfLosingTrades: number
  numberOfWinningTrades: number
  winRate: number
  averageWinAmount: number
  lossRate: number
  averageLossAmount: number
  totalTrades: number
  edge: number
  mostProfitable: any
  stockProfits: any
}

interface StockQuote {
  date: string
  closePrice: number
  heatmap: number
  sectorHeatmap: number
}

interface Trade {
  profit: number
  entryQuote: StockQuote
  exitQuote: StockQuote
  quotes: StockQuote[]
}

interface Serie {
  name: string
  data: Data[] | undefined
}

interface Data {
  x: number | string | undefined
  y: number | string | undefined
  fillColor: string | undefined
}

interface ChartOption {
  colors: string[] | undefined
  chart: {
    id: string
    events: {
      dataPointSelection: (event: any, chartContext: any, config: any) => void
    }
  }
  xaxis: {
    categories: number[] | string[] | undefined
  }
}

</script>
