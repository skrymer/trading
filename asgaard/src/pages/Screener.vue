<template>
    <v-row class="mx-2">
        <v-col cols="9">
            <v-card class="my-4">
                <v-card-title>Plan ETF QQQ</v-card-title>
                <v-card-text>
                    <stock-chart :stock="qqq" title="QQQ" :height="450" :show-ten-ema="false"/>
                </v-card-text>
            </v-card>
        </v-col>
        <v-col cols="3">
            <v-card class="my-4">
                <v-card-title>QQQ status</v-card-title>
                <v-card-text>
                    <p>Has buy signal: <span :class="hasBuySignal ? 'bg-green' : 'bg-red'">{{ hasBuySignal }}</span></p>
                    <p>Heatmap is less than 70: <span :class="lastQuote?.heatmap < 70 ? 'bg-green' : 'bg-red'">{{ lastQuote?.heatmap < 70 }}</span> </p>
                    <p>Less than 2 ATR away from 20 EMA: <span :class="closePriceLessThan20EmaPlus2Atr ? 'bg-green' : 'bg-red'">{{ closePriceLessThan20EmaPlus2Atr }}</span></p>
                    <p>20 EMA plus 2 ATR: {{ (lastQuote?.closePriceEMA20 + (lastQuote?.atr * 2)) }}</p>
                    <p>Heatmap: {{ lastQuote?.heatmap }}</p>
                    <p>2 ATR: {{ lastQuote?.atr * 2 }}</p>
                    <p>Current price: {{ lastQuote?.closePrice }}</p>
                    <p>20 EMA: {{ lastQuote?.closePriceEMA20 }}</p>
                </v-card-text>
            </v-card>
        </v-col>
    </v-row>
    <loader :loading="loading" />
</template>

<script setup lang="ts">
import type { Stock } from '@/types';
import axios from 'axios';

onBeforeMount(() => {
    fetchQqq();
});

const loading = ref(false);
const qqq = ref<Stock | undefined>(undefined);

const lastQuote = computed(() => {
    if (!qqq.value || !qqq.value.quotes || qqq.value.quotes.length === 0) return undefined;
    return qqq.value.quotes[qqq.value.quotes.length - 1];
});

const hasBuySignal = computed(() => {
    const last = lastQuote.value;
    return last?.lastBuySignal && (!last?.lastSellSignal || new Date(last.lastBuySignal) > new Date(last.lastSellSignal));
});

const closePriceLessThan20EmaPlus2Atr = computed(() => {
    const last = lastQuote.value;
    if (!last) return false;
    return last.closePrice < (last.closePriceEMA20 + (last.atr * 2));
});

const fetchQqq = async () => {
    try {
        loading.value = true;
        const response = await axios.get('http://localhost:8080/api/stocks/QQQ?refresh=true');
        console.log(response.data);
        qqq.value = response.data;
        if (qqq.value && qqq.value.quotes) {
            // Keep last 100 quotes, remove the last one
            qqq.value.quotes = qqq.value.quotes.slice(-61, -1); 
        }
    } catch (error) {
        console.error('Error fetching data:', error);
    } finally {
        loading.value = false;
    }
};

</script>

<style scoped></style>