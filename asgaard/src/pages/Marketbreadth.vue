<template>
  <v-container>
    <v-card>
      <v-card-text>
        <v-container>
          <v-row>
            <v-col cols="2">
              <v-select density="compact" v-model="selectedMarket" :items="markets" item-text="title" item-value="value"
                label="Select Market Symbol" />
            </v-col>
            <v-col cols="2">
              <v-checkbox v-model="refresh" label="Refresh"></v-checkbox>
            </v-col>
          </v-row>
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
          <v-row>
            <v-col cols="9">
              <market-breadth-chart
                :title="`Market Breadth - ${MarketSymbol[selectedMarket as keyof typeof MarketSymbol]}`"
                :marketBreadth="marketBreadthSector" />
            </v-col>
            <v-col cols="3">
              <v-card title="Market information">
                <v-card-text>
                  <ul class="text-body-1" style="list-style: none;">
                    <li>{{ `In an uptrend: ${marketBreadthSector?.inUptrend ? 'Yes' : 'No'}` }}</li>
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
import { type MarketBreadth } from '@/types';
import axios from 'axios';

const marketBreadthFullstock = ref<MarketBreadth | undefined>(undefined);
const marketBreadthSector = ref<MarketBreadth | undefined>(undefined);
const selectedMarket = ref<MarketSymbol>();
const refresh = ref(false);
const markets = ref<{ title: string, value: string }[]>(Object.keys(MarketSymbol).map((it) => {
  return {
    title: MarketSymbol[it as keyof typeof MarketSymbol],
    value: it
  }
}).sort((a, b) => a.title.localeCompare(b.title)))

onBeforeMount(() => {
  fetchtMarketBreadth(MarketSymbol.FULLSTOCK).then(resp => marketBreadthFullstock.value = resp);
})

watch(selectedMarket, (newValue) => {
  if (newValue !== undefined) {
    fetchtMarketBreadth(newValue).then(resp => marketBreadthSector.value = resp);
  }
})

const fetchtMarketBreadth = async (marketSymbol: MarketSymbol) => {
  try {
    const marketBreadth = await axios.get(`http://localhost:8080/api/market-breadth?marketSymbol=${marketSymbol}&refresh=${refresh.value}`);
    return marketBreadth.data as MarketBreadth
  } catch (error) {
    console.error("Error fetching market breadth data:", error);
    return undefined
  }
}
</script>