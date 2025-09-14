<template>
    <v-dialog max-width="500">
        <template v-slot:activator="{ props: activatorProps }">
            <v-btn v-bind="activatorProps" color="error" size="small" @click="closeTrade()">
                Close Trade
            </v-btn>
        </template>

        <template v-slot:default="{ isActive }">
            <div class="close-trade-dialog">
                <v-form>
                    <v-text-field
                        label="Close Date"
                        type="date"
                        required
                    />
                    <v-text-field
                        label="Option Price"
                        type="number"
                        step="0.01"
                        required
                    />
                    <v-text-field
                        label="Stock Price"
                        type="number"
                        step="0.01"
                        required
                    />
                    <v-btn color="primary" @click="closeTrade">
                        Close Trade
                    </v-btn>
                </v-form>
            </div>
        </template>
    </v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';

defineProps<{item: any}>()

const isClosed = ref(false);

function closeTrade() {
    isClosed.value = true;
    // Add your close trade logic here
}
</script>

<style scoped>
.close-trade-dialog {
    padding: 16px;
    border: 1px solid #ccc;
    border-radius: 8px;
    background: #fff;
}
</style>