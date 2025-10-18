<template>
    <v-dialog max-width="500">
        <template v-slot:activator="{ props: activatorProps }">
            <fab-button v-bind="activatorProps"  />
        </template>

        <template v-slot:default="{ isActive }">
            <div class="bg-white pa-6 ma-4">
                <h2>Report Generation</h2>
                <v-form @submit.prevent="handleSubmit" >
                    <v-row>
                        <v-autocomplete label="Stock" v-model="selectedStock" :items="stockOptions" chips multiple clearable />
                    </v-row>
                    <v-row>
                        <v-select label="Entry strategy" v-model="selectedEntryStrategy" :items="entryStrategyOptions" clearable></v-select>
                    </v-row>
                    <v-row>
                        <v-select label="Exit strategy" v-model="selectedExitStrategy" :items="exitStrategyOptions" clearable></v-select>
                    </v-row>
                    <v-row justify="center" class="my-4">
                        <v-btn type="submit" color="primary" @click="$emit('click')">Generate</v-btn>
                    </v-row>
                </v-form>
            </div>
        </template>
    </v-dialog>
</template>

<script setup lang="ts">
import axios from 'axios';

const selectedStock = ref<string | null>(null);
const stockOptions = ref<string[]>([]);
const selectedEntryStrategy = ref<string | null>(null);
const entryStrategyOptions = ref<string[]>(['Buy and Hold', 'Moving Average Crossover', 'RSI Oversold', 'MACD Crossover', 'Bollinger Band Breakout']);
const selectedExitStrategy = ref<string | null>(null);
const exitStrategyOptions = ref<string[]>(['Take Profit', 'Stop Loss', 'Trailing Stop', 'Time-based Exit', 'Volatility-based Exit']);

onBeforeMount(async () => {
    try{
        const response = await axios.get(`http://localhost:8080/api/stock/symbols`)
        stockOptions.value = response.data;
    } catch (error) {
        console.error("Error fetching stock symbols:", error);
    }
}); 

defineEmits<{
    (e: 'click'): void
}>()

const handleSubmit = () => {
    console.log("Submitting form to generate backtest report...");
};
</script>

<style scoped>
h2 {
    margin-bottom: 1rem;
}
button {
    padding: 0.5rem 1rem;
}
</style>