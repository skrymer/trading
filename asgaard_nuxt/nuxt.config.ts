// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui',
    '@vueuse/nuxt'
  ],

  devtools: {
    enabled: true
  },

  css: ['~/assets/css/main.css'],

  routeRules: {
    // Proxy all backend API calls to /udgaard/api/** (avoids conflicts with Nuxt internal /api/*)
    '/udgaard/api/**': {
      proxy: {
        to: 'http://localhost:8080/udgaard/api/**',
        // Increase timeout for long-running backtests and Monte Carlo simulations (30 minutes)
        timeout: 1800000
      }
    }
  },

  compatibilityDate: '2024-07-11',

  // Increase timeout for long-running API calls
  nitro: {
    experimental: {
      websocket: false
    }
  },

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
