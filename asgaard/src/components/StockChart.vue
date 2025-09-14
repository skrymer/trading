<template>
    <v-card>
        <v-card-text>
            <apexchart height="350" :options="stockChartOptions" :series="stockSeries" />
        </v-card-text>
    </v-card>
</template>
<script lang="ts" setup>
import type { Stock } from '@/types';
import type { ApexOptions } from 'apexcharts';

const props = defineProps<{ stock: Stock | undefined }>()

const stockSeries = ref<ApexAxisChartSeries>([
    {
        name: 'candle',
        type: "candlestick",
        data: []
    },
    {
        name: "10 ema",
        type: "line",
        color: "#ff0000",
        data: []
    }
])

const stockChartOptions = ref<ApexOptions>({
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
        text: 'Trade chart',
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
    () => props.stock,
    (newValue: Stock| undefined) => {
        stockSeries.value = [
            {
                ...stockSeries.value[0],
                data: newValue?.quotes
                    .map(it => {
                        return {
                            x: it?.date,
                            y: [it?.openPrice.toFixed(2), it?.high.toFixed(2), it?.low.toFixed(2), it?.closePrice.toFixed(2)]
                        }
                    }) || []
            },
            {
                ...stockSeries.value[1],
                data: newValue?.quotes
                    .map(it => {
                        return {
                            x: it?.date,
                            y: it?.closePriceEMA10.toFixed(2)
                        }
                    }) || []
            }
        ]
    }
)

</script>    