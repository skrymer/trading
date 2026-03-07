// https://nuxt.com/docs/api/configuration/nuxt-config

declare const process: { env: Record<string, string | undefined> }

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
    // NUXT_BACKEND_URL overrides the target (e.g., http://udgaard:8080 in Docker)
    '/udgaard/api/**': {
      proxy: {
        to: `${process.env.NUXT_BACKEND_URL || 'http://localhost:8080'}/udgaard/api/**`
      }
    },
    '/udgaard/actuator/**': {
      proxy: {
        to: `${process.env.NUXT_BACKEND_URL || 'http://localhost:8080'}/udgaard/actuator/**`
      }
    }
  },

  compatibilityDate: '2024-07-11',

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
