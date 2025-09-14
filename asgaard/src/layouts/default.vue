<template>
  <v-app-bar>
    <v-app-bar-title>TODO - insert awesome name.</v-app-bar-title>
  </v-app-bar>
  
  <v-navigation-drawer v-model="drawer" :rail="rail" permanent >
    <v-btn :icon="rail ? 'mdi-chevron-right' : 'mdi-chevron-left'" variant="text" @click.stop="rail = !rail"></v-btn>
    <v-list density="compact" nav>
      <v-list-item prepend-icon="mdi-test-tube" title="Backtesting" value="backtest" @click="router.push('/backtest')"></v-list-item>
      <v-list-item prepend-icon="mdi-shopping" title="Market breadth" value="marketbreadth" @click="router.push('/marketbreadth')"></v-list-item>
      <v-list-item prepend-icon="mdi-archive" title="Portfolio manager" value="portfoliomanager" @click="router.push('/portfoliomanager')"></v-list-item>
      <v-list-item prepend-icon="mdi-abacus" title="Trades" value="trades" @click="router.push('/trades')"></v-list-item>
    </v-list>
  </v-navigation-drawer>

  <v-main class="bg-grey-lighten-3">
    <router-view />
  </v-main>

  <app-footer />
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router';
const drawer = ref(true)
const rail = ref(true)
const router = useRouter();
</script>
