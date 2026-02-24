export default defineNuxtRouteMiddleware(async (to) => {
  if (to.path === '/login') return

  const { checkAuth } = useAuth()
  const isAuthenticated = await checkAuth()

  if (!isAuthenticated) {
    return navigateTo('/login')
  }
})
