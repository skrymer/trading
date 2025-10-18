<template>
    <div class="d-flex justify-space-evenly mt-2">
        <data-card title="Number of wins" :content="numberOfWinningTrades(trades)" heading="4" />
        <data-card title="Number of losses" :content="numberOfLosingTrades(trades)" heading="4" />
        <data-card title="Win rate" :content="(winRate(trades) * 100).toFixed(2) + '%'" heading="4" />
        <data-card title="Average win" :content="averageWinPercent(trades).toFixed(2) + '%'" heading="4" />
        <data-card title="Average loss" :content="averageLossPercent(trades).toFixed(2) + '%'" heading="4" />
        <data-card title="Edge" :content="edge(trades).toFixed(2) + '%'" heading="4" />
    </div>
</template>

<script setup lang="ts">
import { averageLossPercent, averageWinPercent, edge, numberOfLosingTrades, numberOfWinningTrades, winRate } from '@/utils/backtestUtils';
import type { Trade } from '@/types';

defineProps<{trades: Trade[] | undefined}>()

</script>

<style scoped>
</style>