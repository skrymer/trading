/**
 * plugins/index.ts
 *
 * Automatically included in `./src/main.ts`
 */

// Plugins
import vuetify from './vuetify'
import pinia from '../stores'
import router from '../router'

// Types
import type { App } from 'vue'
import VueApexCharts from 'vue3-apexcharts';

export function registerPlugins (app: App) {
  // @ts-ignore - does not like VueApexCharts, but works fine.
  app
    .use(vuetify)
    .use(router)
    .use(pinia)
    .use(VueApexCharts)
}
