export function useAuth() {
  const authenticated = useState('auth:authenticated', () => false)

  async function checkAuth(): Promise<boolean> {
    try {
      // Forward browser cookies during SSR so the session is recognized
      const headers = import.meta.server ? useRequestHeaders(['cookie']) : {}
      await $fetch('/udgaard/api/auth/check', { headers })
      authenticated.value = true
      return true
    } catch (error: any) {
      // 404 = auth endpoint doesn't exist (security disabled) — allow access
      if (error?.response?.status === 404) {
        authenticated.value = true
        return true
      }
      authenticated.value = false
      return false
    }
  }

  async function logout() {
    try {
      await $fetch('/udgaard/api/auth/logout', { method: 'POST' })
    } catch {
      // Ignore errors on logout
    }
    authenticated.value = false
    await navigateTo('/login')
  }

  return {
    authenticated,
    checkAuth,
    logout
  }
}
