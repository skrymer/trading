export function useAuth() {
  const authenticated = useState('auth:authenticated', () => false)

  async function checkAuth(): Promise<boolean> {
    try {
      await $fetch('/udgaard/api/auth/check')
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
