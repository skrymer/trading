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
    '/udgaard/api/**': {
      proxy: { to: 'http://localhost:8080/api/**' }
    }
  },

  // Increase timeout for long-running API calls
  nitro: {
    experimental: {
      websocket: false
    },
    devProxy: {
      '/udgaard/api': {
        target: 'http://localhost:8080/api',
        changeOrigin: true,
        prependPath: true
      }
    }
  },

  // Vite proxy configuration for dev server
  vite: {
    server: {
      proxy: {
        '/udgaard/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/udgaard\/api/, '/api')
        }
      }
    }
  },

  compatibilityDate: '2024-07-11',

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
