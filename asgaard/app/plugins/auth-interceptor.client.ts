export default defineNuxtPlugin(() => {
  const { authenticated } = useAuth()
  const originalFetch = globalThis.$fetch
  let redirecting = false

  globalThis.$fetch = Object.assign(
    (request: any, options: any = {}) => {
      const onResponseError = options.onResponseError

      return originalFetch(request, {
        ...options,
        onResponseError(context: any) {
          if (context.response?.status === 401 && !redirecting) {
            authenticated.value = false
            const route = useRoute()
            if (route.path !== '/login') {
              redirecting = true
              const result = navigateTo('/login')
              if (result instanceof Promise) {
                result.finally(() => {
                  redirecting = false
                })
              } else {
                redirecting = false
              }
            }
          }
          onResponseError?.(context)
        }
      })
    },
    originalFetch
  ) as typeof globalThis.$fetch
})
