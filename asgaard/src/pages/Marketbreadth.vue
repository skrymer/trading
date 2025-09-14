<template>
  <v-container>
    <v-card>
      <v-card-text>
        <v-container>
          <!-- <v-row>
            <v-col cols="12">
              <stock-chart :stock="spy" />
            </v-col>
          </v-row> -->
          <v-row>
            <v-col cols="9">
              <market-breadth-chart title="Market Breadth - FULLSTOCK" :marketBreadth="marketBreadthFullstock" />
            </v-col>
            <v-col cols="3">
              <v-card title="Market information">
                <v-card-text>
                  <ul class="text-body-1" style="list-style: none;">
                    <li>{{ `In an uptrend: ${marketBreadthFullstock?.inUptrend ? 'Yes' : 'No'}` }}</li>
                  </ul>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
          <v-row v-for="(market, index) in markets" :key="index">
            <v-col cols="9">
              <market-breadth-chart :title="`Market Breadth - ${market.name}`" :marketBreadth="market" />
            </v-col>
            <v-col cols="3">
              <v-card title="Market information">
                <v-card-text>
                  <ul class="text-body-1" style="list-style: none;">
                    <li :class="market?.inUptrend ? 'bg-green' : 'bg-red'">{{ `In an uptrend: ${market?.inUptrend ? 'Yes' : 'No'}` }}</li>
                    <li :class="gettingMoreGreedy(market) ? 'bg-green' : 'bg-red'">{{ `Heatmap: ${market?.heatmap.toFixed(2)}` }}</li>
                    <li>{{ `Previous heatmap: ${market?.previousHeatmap?.toFixed(2)}` }}</li>
                    <li>{{ `Donkey score: ${market?.donkeyChannelScore}` }}</li>
                  </ul>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </v-container>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script lang="ts" setup>
import { MarketSymbol } from '@/enums';
import { type MarketBreadth, type Stock } from '@/types';
import axios from 'axios';

const spy = ref<Stock | undefined>(undefined);
const marketBreadthFullstock = ref<MarketBreadth | undefined>(undefined);
const markets = ref<MarketBreadth[]>([]);

onBeforeMount(() => {
  fetchStock('SPY').then(resp => spy.value = resp);
  fetchtMarketBreadth(MarketSymbol.FULLSTOCK).then(resp => marketBreadthFullstock.value = resp);
  Object.keys(MarketSymbol).filter(it => it !== 'FULLSTOCK').forEach((market) => {
    fetchtMarketBreadth(market).then(resp => {
      if (resp) {
        markets.value.push(resp);
      }
    });
  });
})

const gettingMoreGreedy = (market: MarketBreadth) => {
  return market.heatmap > market.previousHeatmap;
}

const fetchtMarketBreadth = async (marketSymbol: string) => {
  try {
    const marketBreadth = await axios.get(`http://localhost:8080/api/market-breadth?marketSymbol=${marketSymbol}&refresh=false`);
    return marketBreadth.data as MarketBreadth
  } catch (error) {
    console.error("Error fetching market breadth data:", error);
    return undefined
  }
}

const fetchStock = async (symbol: string) => {
  try {
    const response = await axios.get(`http://localhost:8080/api/stock?symbol=${symbol}&refresh=true`);
    return response.data as Stock;
  } catch (error) {
    console.error("Error fetching stock data:", error);
  }
}
</script>