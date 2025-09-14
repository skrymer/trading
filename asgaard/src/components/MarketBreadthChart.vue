<template>
    <apexchart height="350" :options="chartOptions" :series="series" />
</template>

<script lang="ts" setup>
import type { MarketBreadth } from '@/types';
import type { ApexOptions } from 'apexcharts';

const props = defineProps<{ 
    marketBreadth: MarketBreadth | undefined, 
    title: string 
}>()

const series = ref<ApexAxisChartSeries>([
    {
        name: 'StocksInUptrend',
        type: "bar",
        data: props.marketBreadth?.quotes
            .map(it => {
                return {
                    x: it.quoteDate,
                    y: it.numberOfStocksInUptrend.toFixed(2),
                    strokeColor: it.donchianUpperBand > it.previousDonchianUpperBand ? '#39fc03' : "#9da1a6",
                    fillColor: it.donchianUpperBand > it.previousDonchianUpperBand ? '#39fc03' : "#9da1a6"
                }
            }) || [],
    },
    {
        name: 'StocksInDowntrend',
        type: "bar",
        data: props.marketBreadth?.quotes
            .map(it => {
                return {
                    x: it.quoteDate,
                    y: (it.numberOfStocksInDowntrend * -1).toFixed(2),
                    strokeColor: it.donchianLowerBand < it.previousDonchianLowerBand ? '#d60b2d' : "#9da1a6",
                    fillColor: it.donchianLowerBand < it.previousDonchianLowerBand ? '#d60b2d' : "#9da1a6",
                }
            }) || [],
    },
    {
        name: 'DonchianUpperBand',
        type: "line",
        color: "#000000",
        data: props.marketBreadth?.quotes
            .map(it => {
                return {
                    x: it.quoteDate,
                    y: it.donchianUpperBand.toFixed(2),
                }
            }) || []
    },
    {
        name: 'DonchianLowerBand',
        type: "line",
        color: "#000000",
        data: props.marketBreadth?.quotes
            .map(it => {
                return {
                    x: it.quoteDate,
                    y: (it.donchianLowerBand * -1).toFixed(2),
                }
            }) || []
    }
])

const chartOptions = ref<ApexOptions>({
    chart: {
        id: `marketbreadth-chart ${props.marketBreadth ? props.marketBreadth?.symbol : ""}`,
        height: 350,
        stacked: true,
    },
    stroke: {
        width: [1, 1], // Series 1: 1px, Series 2: 4px
        curve: 'straight'
    },
    title: {
        text: props.title,
        align: 'left'
    },
    tooltip: {
        enabled: true,
        shared: true
    },
    xaxis: {
        // type: 'category',
        overwriteCategories: props.marketBreadth?.quotes.map(it => it.quoteDate) || []
    },
    yaxis: {
        tooltip: {
            enabled: true
        }
    }
})

const updateChart = (marketBreadth: MarketBreadth) => {
    chartOptions.value = {
        ...chartOptions.value,
        chart: {
            ...chartOptions.value.chart,
            id: `marketbreadth-chart-${marketBreadth?.symbol}`,
        },
        xaxis: {
            overwriteCategories: marketBreadth?.quotes.map(it => it.quoteDate) || []
        }
    }
    series.value = [
        {
            ...series.value[0],
            data: marketBreadth?.quotes
                .map(it => {
                    return {
                        x: it.quoteDate,
                        y: it.numberOfStocksInUptrend.toFixed(2),
                        strokeColor: it.donchianUpperBand > it.previousDonchianUpperBand ? '#39fc03' : "#9da1a6",
                        fillColor: it.donchianUpperBand > it.previousDonchianUpperBand ? '#39fc03' : "#9da1a6"
                    }
                }) || []
        },
        {
            ...series.value[1],
            data: marketBreadth?.quotes
                .map(it => {
                    return {
                        x: it.quoteDate,
                        y: (it.numberOfStocksInDowntrend * -1).toFixed(2),
                        strokeColor: it.donchianLowerBand < it.previousDonchianLowerBand ? '#d60b2d' : "#9da1a6",
                        fillColor: it.donchianLowerBand < it.previousDonchianLowerBand ? '#d60b2d' : "#9da1a6",
                    }
                }) || []
        },
        {
            ...series.value[2],
            data: marketBreadth?.quotes
                .map(it => {
                    return {
                        x: it.quoteDate,
                        y: it.donchianUpperBand.toFixed(2),
                    }
                }) || []
        },
        {
            ...series.value[3],
            data: marketBreadth?.quotes
                .map(it => {
                    return {
                        x: it.quoteDate,
                        y: (it.donchianLowerBand * -1).toFixed(2),
                    }
                }) || []
        },
        {
            ...series.value[3],
            data: marketBreadth?.quotes
                .map(it => {
                    return {
                        x: it.quoteDate,
                        y: (it.donchianLowerBand * -1).toFixed(2),
                    }
                }) || []
        }
    ]
}

/**
 * Update chart options and series data when marketBreadth prop changes.
 */
watch(
    () => props.marketBreadth,
    (newValue: MarketBreadth | undefined) => {
        if (newValue) {
            updateChart(newValue);
        }
    }
)

watch(
    () => props.title,
    (newTitle: string) => {
        chartOptions.value = {
            ...chartOptions.value,
            title: {
                text: newTitle,
                align: 'left'
            },
        }
    }
);
</script>