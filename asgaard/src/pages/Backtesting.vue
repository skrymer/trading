<template>
    <v-row>
        <v-col>
            <backtest-cards :trades="trades" />
        </v-col>
    </v-row>

    <v-row>
        <v-col>
            <v-card class="mx-4 pa-2">
                <apexchart height="350" type="bar" :options="chartOptions" :series="series" />
                <span class="h1 mx-4">Filters</span>
                <v-divider class="my-2 mx-4" />

                <div class="d-flex mb-4 mx-4">
                    <v-select v-model="selectedSector" :items="sectorOptions" label=" Sector" clearable
                        density="compact" style="max-width: 200px;" @update:modelValue="filterTradesBySector" />
                    <v-date-input class="ml-4" v-model="fromDate" label="From" density="compact"
                        style="max-width: 200px;" clearable />
                    <v-date-input class="ml-4" v-model="toDate" label="To" density="compact" style="max-width: 200px;"
                        clearable />
                </div>

            </v-card>
        </v-col>
    </v-row>
    <v-row v-if="selectedTrades">
        <v-col cols="12">
            <v-card class="mx-4 mb-4">
                <v-card-title>Selected trade</v-card-title>
                <backtest-cards :trades="selectedTrades.trades" />
                <!-- Selected trade table -->
                <v-data-table :items="selectedTradeItems" :headers="selectedTradeHeaders" cla
                    :items-per-page="5" item-value="symbol" show-expand class="border mt-4" dense>

                    <template v-slot:item.data-table-expand="{ internalItem, isExpanded, toggleExpand }">
                        <v-btn :append-icon="isExpanded(internalItem) ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                            :text="isExpanded(internalItem) ? 'Collapse' : 'More info'" class="text-none"
                            color="medium-emphasis" size="small" variant="text" width="105" slim
                            @click="toggleExpand(internalItem)" />
                    </template>

                    <!-- Expanded row content -->
                    <template #expanded-row="{ columns, item: { trade } }">
                        <tr class="">
                            <td :colspan="columns.length" class="py-2 bg-green-lighten-5">
                                <trade-chart :trade="trade" />
                                <v-data-table :items="[trade.entryQuote, ...trade.quotes]" :headers="quoteTableHeaders"
                                    :items-per-page="10" dense class="px-4 mt-2" />
                            </td>
                        </tr>
                    </template>
                </v-data-table>
            </v-card>
        </v-col>
    </v-row>
    <v-row>
        <v-col cols="4">
            <v-card class="mx-4 mb-4">
                <v-card-title>Top 10 Performing Stocks</v-card-title>
                <v-data-table :items="topPerformingStocks" :headers="[
                    { title: 'Symbol', value: 'symbol', sortable: true },
                    { title: 'Profit %', value: 'profitPercentage', sortable: true }
                ]" :items-per-page="10" item-value="symbol" class="px-4" dense hide-default-footer />
            </v-card>
        </v-col>
        <v-col cols="4">
            <v-card class="mx-4 mb-4">
                <v-card-title>Sector performance</v-card-title>
                <v-data-table :items="sectorPerformance" :headers="[
                    { title: 'Symbol', value: 'symbol', sortable: true },
                    { title: 'Profit %', value: 'profitPercentage', sortable: true }
                ]" :items-per-page="10" item-value="symbol" class="px-4" dense hide-default-footer />
            </v-card>

        </v-col>
    </v-row>

    <loader :loading="loading" />
    <generate-backtest-report-dialog @click="fetchTrades" />
</template>

<script setup lang="ts">
import type { ApexOptions } from 'apexcharts';
import { ref } from 'vue'
import axios from 'axios'
import type { BacktestReport, Trade } from '@/types';
import type { DataTableHeader } from 'vuetify';
import { groupByProp } from 'remeda';

const backTestreport = ref<BacktestReport | undefined>()
const loading = ref<boolean>(false)
const selectedTrades = ref<{ date: string, profitPercentage: number, trades: Trade[] } | undefined>()
const selectedTradeHeaders = [
    { title: 'Symbol', value: 'symbol', width: '150px', sortable: true },
    { title: 'Entry', value: 'entry', width: '100px' },
    { title: 'Exit reason', value: 'exitReason' },
    { title: 'Profit %', value: 'profitPercentage', sortable: true },
    { title: 'Trading days', value: 'tradingDays' }
] as DataTableHeader[]
const quoteTableHeaders = [
    { title: 'Date', value: 'date' },
    { title: 'Last Buy Signal', value: 'lastBuySignal' },
    { title: 'Close Price', value: 'closePrice' },
    { title: 'Close Price 10 EMA', value: 'closePriceEMA10' },
    { title: 'Open Price', value: 'openPrice' },
    { title: 'High', value: 'high' },
    { title: 'Low', value: 'low' },
    { title: 'Heatmap', value: 'heatmap' },
    { title: 'Sector Heatmap', value: 'sectorHeatmap' }
] as DataTableHeader[]
const selectedTradeItems = ref<any[]>([])

const trades = computed(() =>
    selectedSector.value
        ? backTestreport.value?.trades.filter(trade => trade.sector === selectedSector.value)
        : backTestreport.value?.trades
);

// Filters
const selectedSector = ref<string | null>(null);
const fromDate = ref<string | null>(null);
const toDate = ref<string | null>(null);

const sectorOptions = ref<{ title: string, value: string }[]>([
    { title: 'Energy', value: 'XLE' },
    { title: 'Health', value: 'XLV' },
    { title: 'Materials', value: 'XLB' },
    { title: 'Communications', value: 'XLC' },
    { title: 'Technology', value: 'XLK' },
    { title: 'Realestate', value: 'XLRE' },
    { title: 'Industrials', value: 'XLI' },
    { title: 'Financials', value: 'XLF' },
    { title: 'Discretionary', value: 'XLY' },
    { title: 'Staples', value: 'XLP' },
    { title: 'Utilities', value: 'XLU' }
]);

const topPerformingStocks = computed<{ symbol: string, profitPercentage: number }[]>(() => {
    if (!backTestreport.value) return [];

    const profitByStock = backTestreport.value.trades.reduce((acc, trade) => {
        if (!acc[trade.stockSymbol]) {
            acc[trade.stockSymbol] = 0;
        }
        acc[trade.stockSymbol] += trade.profitPercentage || 0;
        return acc;
    }, {} as Record<string, number>);

    return Object.entries(profitByStock)
        .map(([symbol, profitPercentage]) => ({ symbol, profitPercentage }))
        .sort((a, b) => b.profitPercentage - a.profitPercentage)
        .slice(0, 10);
});

const sectorPerformance = computed<{ symbol: string, profitPercentage: number }[]>(() => {
    if (!backTestreport.value) return [];

    const profitBySector = backTestreport.value.trades.reduce((acc, trade) => {
        if (!acc[trade.sector]) {
            acc[trade.sector] = 0;
        }
        acc[trade.sector] += trade.profitPercentage || 0;
        return acc;
    }, {} as Record<string, number>);

    return Object.entries(profitBySector)
        .map(([symbol, profitPercentage]) => ({ symbol, profitPercentage }))
        .sort((a, b) => b.profitPercentage - a.profitPercentage)
        .slice(0, 10);
});

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
        tickAmount: 11
    },
});
const series = ref<ApexAxisChartSeries>([
    { name: '#trades', data: [] },
]);

const handleChartClicked = (e: any, c: any, config: { dataPointIndex: number }) => {
    selectedTrades.value = backTestreport.value && backTestreport.value.tradesGroupedByDate.length > config.dataPointIndex
        ? backTestreport.value.tradesGroupedByDate[config.dataPointIndex]
        : undefined
}

const filterTradesBySector = () => {
    if (!backTestreport.value) return;

    if (selectedSector.value) {
        const filteredTrades = backTestreport.value.trades.filter(trade => trade.sector === selectedSector.value);
        const groupedByStartDate = groupTradesByDate(filteredTrades).map(it => {
            return {
                x: it[0],
                y: it[1].length,
                fillColor: it[1].map(trade => trade.profitPercentage).reduce((acc, curr) => acc + curr, 0) > 0 ? '#39fc03' : "#fc2403"
            }
        });

        chartOptions.value = {
            ...chartOptions.value,
            xaxis: {
                categories: groupedByStartDate.map(it => it.x)
            }
        };
        series.value = [{
            ...series.value[0],
            data: groupedByStartDate
        }];
    } else {
        // Reset to original data if no sector is selected
        const groupByStartDate = groupTradesByDate(backTestreport.value.trades).map(it => {
            return {
                x: it[0],
                y: it[1].length,
                fillColor: it[1].map(trade => trade.profitPercentage).reduce((acc, curr) => acc + curr, 0) > 0 ? '#39fc03' : "#fc2403"
            }
        });

        chartOptions.value = {
            ...chartOptions.value,
            xaxis: {
                categories: groupByStartDate.map(it => it.x)
            }
        };
        series.value = [{
            ...series.value[0],
            data: groupByStartDate
        }];
    }
};

// TODO move into a Service function
const fetchTrades = async () => {
    loading.value = true
    try {
        const resp = await axios.get(`http://localhost:8080/api/report/all`)
        backTestreport.value = resp.data
    } finally {
        loading.value = false
    }
}

const groupTradesByDate = (trades: Trade[]) => {
    return Object.entries(groupByProp(trades ? trades : [], 'startDate'))
}

watch(
    () => backTestreport.value,
    (newValue) => {
        const groupByStartDate = groupTradesByDate(newValue ? newValue.trades : []).map(it => {
            return {
                x: it[0],
                y: it[1].length,
                fillColor: it[1].map(trade => trade.profitPercentage).reduce((acc, curr) => acc + curr, 0) > 0 ? '#39fc03' : "#fc2403"
            }
        });
        ;

        chartOptions.value = {
            ...chartOptions.value,
            xaxis: {
                categories: groupByStartDate.map(it => it.x)
            }
        }
        series.value = [{
            ...series.value[0],
            data: groupByStartDate
        }]
    }
);

watch(
    () => selectedTrades.value,
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

<style scoped></style>