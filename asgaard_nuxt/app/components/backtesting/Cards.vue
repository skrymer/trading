<script setup lang="ts">
import { averageLossPercent, averageWinPercent, edge, numberOfLosingTrades, numberOfWinningTrades, winRate } from '@/utils/backtesting';
import type { Trade } from '@/types';

defineProps<{trades: Trade[] | undefined}>()

</script>

<template>
  <UPageGrid class="lg:grid-cols-6 gap-4 sm:gap-6 lg:gap-px w-full">
    <BacktestingDataCard title="Number of wins" :content="numberOfWinningTrades(trades)"/>   
    <BacktestingDataCard title="Number of losses" :content="numberOfLosingTrades(trades)"/>   
    <BacktestingDataCard title="Win rate" :content="(winRate(trades) * 100).toFixed(2) + '%'" />   
    <BacktestingDataCard title="Average win" :content="averageWinPercent(trades).toFixed(2) + '%'" />   
    <BacktestingDataCard title="Average loss" :content="averageLossPercent(trades).toFixed(2) + '%'" />   
    <BacktestingDataCard title="Edge" :content="edge(trades).toFixed(2) + '%'" />   
  </UPageGrid>
</template>

<style scoped>
</style>