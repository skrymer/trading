<template>
    <div class="ma-4">
        <div class="d-flex justify-space-evenly">
            <data-card title="Total Trades YTD" content="64" />
            <data-card title="YTD Return" content="64" />
            <data-card title="Annualized Return" content="32" />
            <data-card title="Avg Win" content="32%" />
            <data-card title="Avg Loss" content="68%" />
            <data-card title="Win Rate" content="60%" />
            <data-card title="Proven Edge" content="23%" />
        </div>
        <portfolio-entry-dialog />
        <trades-list/>
    </div>
</template>

<script setup>
import { ref } from 'vue'

const message = ref('Welcome to your portfolio manager.')
</script>
