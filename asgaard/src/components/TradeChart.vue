<template>
    <v-card>
        <v-card-text>
            <apexchart height="250" :options="tradeChartOptions" :series="tradeSeries" />
        </v-card-text>
    </v-card>
</template>

<script setup lang="ts">
import type { Trade } from '@/types';
import type { ApexOptions } from 'apexcharts';

const props = defineProps<{ trade: Trade | undefined }>()

const candleData = (trade: Trade | undefined) => {
    return [trade?.entryQuote, ...(trade ? trade.quotes : [])]
        .map(it => {
            return {
                x: it?.date,
                y: [it?.openPrice.toFixed(2), it?.high.toFixed(2), it?.low.toFixed(2), it?.closePrice.toFixed(2)]
            }
        })
}

const tenEmaData = (trade: Trade | undefined) => {
    return [trade?.entryQuote, ...(trade ? trade.quotes : [])]
        .map(it => {
            return {
                x: it?.date,
                y: it?.closePriceEMA10.toFixed(2)
            }
        })
}

const tradeSeries = ref<ApexAxisChartSeries>([
    {
        name: 'candle',
        type: "candlestick",
        data: candleData(props.trade)
    },
    {
        name: "10 ema",
        type: "line",
        color: "#ff0000",
        data: tenEmaData(props.trade)
    }
])
const tradeChartOptions = ref<ApexOptions>({
    chart: {
        id: "trade-chart",
        height: 350,
        type: 'candlestick',
    },
    stroke: {
        width: [3, 4], // Series 1: 2px, Series 2: 4px
        curve: 'smooth'
    },
    title: {
        text: 'Trade',
        align: 'left'
    },
    tooltip: {
        enabled: true,
    },
    xaxis: {
        // type: 'category'
    },
    yaxis: {
        tooltip: {
            enabled: true
        }
    }
})

// Update chart when the report is updated
watch(
    () => props.trade,
    (newValue: Trade | undefined) => {
        tradeSeries.value = [
            {
                ...tradeSeries.value[0],
                data: candleData(newValue)
            },
            {
                ...tradeSeries.value[1],
                data: tenEmaData(newValue)
            }
        ]
    }
)


</script>