<template>
    <v-data-table :headers="tableHeaders" :items="items" class="mt-8">
        <template #item.actions="{ item }">
            <close-trade-dialog :item="item" />
        </template>
        <template #item.entryDate="{ item }">
            {{ item.entryDate }}
        </template>
        <template #item.optionPrice="{ item }">
            ${{ item.optionPrice }}
        </template>
    </v-data-table>
</template>

<script setup lang="ts">
import type { DataTableHeader } from 'vuetify';

const tableHeaders = [
    { title: "Entry Date", value: "entryDate" },
    { title: "Ticker", value: "ticker" },
    { title: "Quantity", value: "quantity" },
    { title: "Option Price", value: "optionPrice" },
    { title: "Intrinsic Value", value: "intrinsicValue" },
    { title: "Extrinsic Value", value: "extrinsicValue" },
    { title: "Fees", value: "fees" },
    { title: "Stock Price", value: "stockPrice" },
    { title: "Actions", value: "actions" }
] as DataTableHeader[];

const items = ref([
    {
        entryDate: "2023-10-01",
        ticker: "AAPL",
        quantity: 10,
        optionPrice: 5.00,
        intrinsicValue: 2.00,
        extrinsicValue: 3.00,
        fees: 1.00,
        stockPrice: 150.00
    },
    {
        entryDate: "2023-10-05",
        ticker: "MSFT",
        quantity: 5,
        optionPrice: 10.00,
        intrinsicValue: 4.00,
        extrinsicValue: 6.00,
        fees: 1.50,
        stockPrice: 250.00
    }
]);

</script>

<style scoped>
.trades-list {
    padding: 1rem;
}
</style>