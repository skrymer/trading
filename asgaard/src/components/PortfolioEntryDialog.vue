<template>
    <v-dialog max-width="500">
        <template v-slot:activator="{ props: activatorProps }">
            <fab-button v-bind="activatorProps" />
        </template>

        <template v-slot:default="{ isActive }">
            <div class="bg-white pa-6 ma-4">
                <h2>Portfolio entry</h2>
                <v-form @submit.prevent="handleSubmit" class="mt-4">
                    <v-row dense>
                        <!-- Entry Date -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="entryDate" type="date" label="Entry Date" variant="outlined"
                                density="comfortable" required />
                        </v-col>

                        <!-- Ticker -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="ticker" label="Ticker" placeholder="e.g. AAPL" variant="outlined"
                                density="comfortable" required />
                        </v-col>

                        <!-- Quantity -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="quantity" type="number" label="Quantity" variant="outlined"
                                density="comfortable" required />
                        </v-col>
                        <!-- Option Price -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="optionPrice" type="number" label="Option Price" prefix="$"
                                variant="outlined" density="comfortable" required />
                        </v-col>

                        <!-- Intrinsic Value -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="intrinsicValue" type="number" label="Intrinsic Value" prefix="$"
                                variant="outlined" density="comfortable" />
                        </v-col>

                        <!-- Extrinsic Value -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="extrinsicValue" type="number" label="Extrinsic Value" prefix="$"
                                variant="outlined" density="comfortable" />
                        </v-col>

                        <!-- Stock Price -->
                        <v-col cols="12" sm="6">
                            <v-text-field v-model="stockPrice" type="number" label="Stock Price" prefix="$"
                                variant="outlined" density="comfortable" />
                        </v-col>

                        <!-- Submit Button -->
                        <v-col cols="12" class="text-right">
                            <v-btn type="submit" color="primary" variant="flat">Submit</v-btn>
                        </v-col>
                    </v-row>
                </v-form>
            </div>
        </template>
    </v-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { DataTableHeader } from "vuetify";

// Format today's date as YYYY-MM-DD
const today = new Date().toISOString().split("T")[0];

const entryDate = ref<string>(today);
const optionPrice = ref<number | null>(null);
const intrinsicValue = ref<number | null>(null);
const extrinsicValue = ref<number | null>(null);
const fees = ref<number | null>(null);
const stockPrice = ref<number | null>(null);
const ticker = ref<string>("");
const quantity = ref<number | null>(null);


const handleSubmit = () => {
    console.log({
        entryDate: entryDate.value,
        optionPrice: optionPrice.value,
        intrinsicValue: intrinsicValue.value,
        extrinsicValue: extrinsicValue.value,
        fees: fees.value,
        stockPrice: stockPrice.value,
    });
};
</script>