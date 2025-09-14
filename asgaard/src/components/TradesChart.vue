<template>
    <apexchart height="250" type="bar" :options="chartOptions" :series="series" />
</template>

<script lang="ts" setup>
import type { BacktestReport, Trade } from '@/types';
import type { ApexOptions } from 'apexcharts';

const props = defineProps<{ report: BacktestReport | undefined }>()
const emit = defineEmits<{ tradeSelected: [trade: Trade | undefined] }>()

const chartOptions = ref<ApexOptions>({
    chart: {
        id: 'trades',
        events: {
            dataPointSelection: function (e, c, config) {
                handleChartClicked(e, c, config)
            }
        },
    },
    title: {
        text: 'Trades',
        align: 'left'
    },
    colors: ['#000000'],
    xaxis: {
        categories: [],
    },
});

const series = ref<ApexAxisChartSeries>([
    { name: 'Profit%', data: [] },
]);

const handleChartClicked = (e: any, c: any, config: { dataPointIndex: number }) => {
    emit('tradeSelected', props.report?.trades[config.dataPointIndex])
}

// Update chart when the report is updated
watch(
    () => props.report,
    (newValue) => {
        const entryDates = newValue?.trades?.map((it: Trade) => {
            return it.entryQuote.date
        })
        const profit = newValue?.trades?.map((it: Trade) => {
            return { 
                x: it.entryQuote.date, 
                y: it.profitPercentage.toFixed(2), 
                fillColor: it.profitPercentage > 0 ? '#39fc03' : "#fc2403" 
            }
        })

        chartOptions.value = {
            ...chartOptions.value,
            xaxis: {
                categories: entryDates
            }
        }
        series.value = [{
            ...series.value[0],
            data: profit || []
        }]
    }
);
</script>