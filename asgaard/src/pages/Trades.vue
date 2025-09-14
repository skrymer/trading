<template>
    <v-row>
        <v-col>
            <div class="d-flex justify-space-evenly mx-4 mt-4">
                <data-card title="Number of wins" :content="backTestreport?.numberOfWinningTrades || '0'" heading="4" />
                <data-card title="Number of losses" :content="backTestreport?.numberOfLosingTrades || '0'"
                    heading="4" />
                <data-card title="Win rate" :content="((backTestreport?.winRate || 0) * 100)?.toFixed(2) + '%'"
                    heading="4" />
                <data-card title="Average win"
                    :content="(backTestreport?.averageWinPercent?.toFixed(2) || '0') + '%'" heading="4" />
                <data-card title="Loss rate" :content="((backTestreport?.lossRate || 0) * 100)?.toFixed(2) + '%'"
                    heading="4" />
                <data-card title="Average loss"
                    :content="(backTestreport?.averageLossPercent?.toFixed(2) || '0') + '%'" heading="4" />
                <data-card title="Edge" :content="(backTestreport?.edge?.toFixed(2) || '0') + '%'" heading="4" />
            </div>
        </v-col>
    </v-row>

    <v-row>
        <v-col>
            <v-card class="mx-4 pa-2">
                <apexchart height="250" type="bar" :options="chartOptions" :series="series" />
                <v-data-table v-if="selectedTrade" :items="selectedTradeItems" :headers="selectedTradeHeaders" cla
                    :items-per-page="15" item-value="symbol" show-expand>
                    <template v-slot:item.data-table-expand="{ internalItem, isExpanded, toggleExpand }">
                        <v-btn :append-icon="isExpanded(internalItem) ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                            :text="isExpanded(internalItem) ? 'Collapse' : 'More info'" class="text-none"
                            color="medium-emphasis" size="small" variant="text" width="105" slim
                            @click="toggleExpand(internalItem)" />
                    </template>
                    <template #expanded-row="{ columns, item: { trade } }">
                        <tr class="">
                            <td :colspan="columns.length" class="py-2 bg-green-lighten-5">
                                <trade-chart :trade="trade" />
                                <v-data-table :items="[trade.entryQuote, ...trade.quotes]" :headers="[
                                    { title: 'Date', value: 'date' },
                                    { title: 'Last Buy Signal', value: 'lastBuySignal' },
                                    { title: 'Close Price', value: 'closePrice' },
                                    { title: 'Close Price 10 EMA', value: 'closePriceEMA10' },
                                    { title: 'Open Price', value: 'openPrice' },
                                    { title: 'High', value: 'high' },
                                    { title: 'Low', value: 'low' },
                                    { title: 'Heatmap', value: 'heatmap' },
                                    { title: 'Sector Heatmap', value: 'sectorHeatmap' }
                                ]" :items-per-page="15" dense class="px-4 mt-2" />
                            </td>
                        </tr>

                    </template>
                </v-data-table>
            </v-card>
        </v-col>
    </v-row>
    <loader :loading="loading" />
</template>

<script setup lang="ts">
import type { ApexOptions } from 'apexcharts';
import { ref } from 'vue'
import axios from 'axios'
import type { BacktestReport, Trade } from '@/types';
import { onBeforeMount } from 'vue'
import type { DataTableHeader } from 'vuetify';

onBeforeMount(() => {
    fetchTrades()
})

const backTestreport = ref<BacktestReport | undefined>()
const loading = ref<boolean>(false)
const selectedTrade = ref<{ date: string, profitPercentage: number, trades: Trade[] } | undefined>()
const selectedTradeHeaders = [
    { title: 'Symbol', value: 'symbol', width: '150px', sortable: true },
    { title: 'Entry', value: 'entry', width: '100px' },
    { title: 'Exit reason', value: 'exitReason' },
    { title: 'Profit %', value: 'profitPercentage', sortable: true },
    { title: 'Trading days', value: 'tradingDays'}
] as DataTableHeader[]
const selectedTradeItems = ref<any[]>([])

const chartOptions = ref<ApexOptions>({
    chart: {
        id: 'trades',
        zoom: {
            enabled: true,
            type: "x",
            autoScaleYaxis: true,
        },
        toolbar: {
            autoSelected: "zoom",
        },
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
        labels: {
            formatter: function (value: string) {
                return "Sonni";
            }
        }
    },
});
const series = ref<ApexAxisChartSeries>([
    { name: '#trades', data: [] },
]);

const handleChartClicked = (e: any, c: any, config: { dataPointIndex: number }) => {
    selectedTrade.value = backTestreport.value && backTestreport.value.tradesGroupedByDate.length > config.dataPointIndex
        ? backTestreport.value.tradesGroupedByDate[config.dataPointIndex]
        : undefined
}

// TODO move into a Service function
const fetchTrades = async () => {
    loading.value = true
    try {
        const resp = await axios.get(`http://localhost:8080/api/report/all`)
        console.log("Loaded data");
        console.log(resp.data);

        backTestreport.value = resp.data
    } finally {
        loading.value = false
    }
}

watch(
    () => backTestreport.value,
    (newValue) => {
        const entryDates = newValue?.tradesGroupedByDate.map(it => it.date)
        const profits = newValue?.tradesGroupedByDate.map(it => {
            return {
                x: it.date,
                y: it.trades.length,
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
            data: profits || []
        }]
    }
);

watch(
    () => selectedTrade.value,
    (newTrade) => {
        selectedTradeItems.value = newTrade?.trades.map(trade => ({
            trade: trade,
            symbol: trade.stockSymbol,
            entry: trade.entryQuote.closePrice,
            exitReason: trade.exitReason,
            profitPercentage: trade.profitPercentage?.toFixed(2) || '0',
            tradingDays: trade.tradingDays
        })) || [];
    }
);
</script>

<style scoped>
</style>