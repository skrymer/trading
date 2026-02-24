<template>
  <UDashboardPage>
    <UDashboardPanel grow>
      <UDashboardNavbar title="Settings">
        <template #right>
          <UButton
            label="Save"
            color="primary"
            :loading="saving"
            @click="saveSettings"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardPanelContent>
        <div class="max-w-4xl mx-auto space-y-8">
          <!-- API Credentials Section -->
          <UCard>
            <template #header>
              <div class="flex items-center justify-between">
                <div>
                  <h3 class="text-lg font-semibold">
                    API Credentials
                  </h3>
                  <p class="text-sm text-gray-500 mt-1">
                    Configure your API credentials for stock data providers
                  </p>
                </div>
                <UBadge
                  v-if="credentialsStatus"
                  :color="allConfigured ? 'success' : 'warning'"
                  variant="subtle"
                >
                  {{ allConfigured ? 'Configured' : 'Incomplete' }}
                </UBadge>
              </div>
            </template>

            <div class="space-y-6">
              <!-- Ovtlyr Section -->
              <div>
                <h4 class="text-sm font-semibold mb-3 flex items-center gap-2">
                  Ovtlyr API
                  <UBadge
                    v-if="credentialsStatus"
                    :color="credentialsStatus.ovtlyrConfigured ? 'success' : 'neutral'"
                    size="xs"
                  >
                    {{ credentialsStatus.ovtlyrConfigured ? 'Configured' : 'Not configured' }}
                  </UBadge>
                </h4>

                <div class="space-y-4">
                  <UFormGroup label="Ovtlyr Token" help="Get from https://console.ovtlyr.com (browser cookies)">
                    <UInput
                      v-model="credentials.ovtlyrToken"
                      type="password"
                      placeholder="Enter your Ovtlyr token"
                    />
                  </UFormGroup>

                  <UFormGroup label="Ovtlyr User ID" help="Get from https://console.ovtlyr.com (browser cookies)">
                    <UInput
                      v-model="credentials.ovtlyrUserId"
                      type="password"
                      placeholder="Enter your Ovtlyr user ID"
                    />
                  </UFormGroup>

                  <UAlert
                    icon="i-lucide-info"
                    color="info"
                    variant="subtle"
                    title="How to get Ovtlyr credentials"
                    description="1. Login to console.ovtlyr.com  2. Open browser DevTools (F12)  3. Go to Application > Cookies  4. Copy 'token' and 'userid' values"
                  />
                </div>
              </div>

              <UDivider />

              <!-- IBKR Section -->
              <div>
                <h4 class="text-sm font-semibold mb-3 flex items-center gap-2">
                  Interactive Brokers (IBKR)
                  <UBadge
                    v-if="credentialsStatus"
                    :color="credentialsStatus.ibkrConfigured ? 'success' : 'neutral'"
                    size="xs"
                  >
                    {{ credentialsStatus.ibkrConfigured ? 'Configured' : 'Not configured' }}
                  </UBadge>
                </h4>

                <div class="space-y-4">
                  <UFormGroup label="Account ID" help="Your IBKR account number (e.g., U1234567)">
                    <UInput
                      v-model="credentials.ibkrAccountId"
                      placeholder="Enter your IBKR account ID"
                    />
                  </UFormGroup>

                  <UFormGroup label="Flex Query ID" help="Flex query ID for trade reports">
                    <UInput
                      v-model="credentials.ibkrFlexQueryId"
                      placeholder="Enter your Flex Query ID"
                    />
                  </UFormGroup>

                  <UAlert
                    icon="i-lucide-info"
                    color="info"
                    variant="subtle"
                    title="How to setup IBKR Flex Query"
                    description="1. Login to IBKR Portal  2. Go to Reports > Flex Queries  3. Create a query with trade details  4. Copy the Query ID"
                  />
                </div>
              </div>

              <UDivider />

              <!-- Alpha Vantage Section -->
              <div>
                <h4 class="text-sm font-semibold mb-3 flex items-center gap-2">
                  Alpha Vantage API
                  <UBadge
                    v-if="credentialsStatus"
                    :color="credentialsStatus.alphaVantageConfigured ? 'success' : 'neutral'"
                    size="xs"
                  >
                    {{ credentialsStatus.alphaVantageConfigured ? 'Configured' : 'Not configured' }}
                  </UBadge>
                </h4>

                <div class="space-y-4">
                  <UFormGroup label="API Key" help="Free tier: 5 requests/min, 500 requests/day">
                    <UInput
                      v-model="credentials.alphaVantageApiKey"
                      type="password"
                      placeholder="Enter your Alpha Vantage API key"
                    />
                  </UFormGroup>

                  <UAlert
                    icon="i-lucide-info"
                    color="info"
                    variant="subtle"
                    title="Get a free API key"
                  >
                    <template #description>
                      Visit
                      <a
                        href="https://www.alphavantage.co/support/#api-key"
                        target="_blank"
                        class="underline"
                      >
                        https://www.alphavantage.co/support/#api-key
                      </a>
                      to get your free API key
                    </template>
                  </UAlert>
                </div>
              </div>

              <UDivider />

              <!-- Massive API Section -->
              <div>
                <h4 class="text-sm font-semibold mb-3 flex items-center gap-2">
                  Massive API
                  <UBadge
                    v-if="credentialsStatus"
                    :color="credentialsStatus.massiveConfigured ? 'success' : 'neutral'"
                    size="xs"
                  >
                    {{ credentialsStatus.massiveConfigured ? 'Configured' : 'Not configured' }}
                  </UBadge>
                </h4>

                <div class="space-y-4">
                  <UFormGroup label="API Key" help="Used for company info (sector classification + market cap)">
                    <UInput
                      v-model="credentials.massiveApiKey"
                      type="password"
                      placeholder="Enter your Massive API key"
                    />
                  </UFormGroup>

                  <UAlert
                    icon="i-lucide-info"
                    color="info"
                    variant="subtle"
                    title="Massive API"
                    description="Provides company sector classification (SIC codes) and market capitalization data"
                  />
                </div>
              </div>
            </div>
          </UCard>

          <!-- Configuration File Info -->
          <UCard v-if="credentialsStatus">
            <template #header>
              <h3 class="text-lg font-semibold">
                Configuration File
              </h3>
            </template>

            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-600 dark:text-gray-400">Config file exists</span>
                <UBadge :color="credentialsStatus.configFileExists ? 'success' : 'neutral'">
                  {{ credentialsStatus.configFileExists ? 'Yes' : 'No' }}
                </UBadge>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-600 dark:text-gray-400">Location</span>
                <code class="text-xs bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-200 px-2 py-1 rounded">~/.trading-app/config.properties</code>
              </div>
              <UAlert
                icon="i-lucide-info"
                color="neutral"
                variant="subtle"
                description="Your credentials are saved locally and never sent to any server except the respective API providers"
              />
            </div>
          </UCard>
        </div>
      </UDashboardPanelContent>
    </UDashboardPanel>
  </UDashboardPage>
</template>

<script setup lang="ts">
const toast = useToast()

const credentials = ref({
  ovtlyrToken: '',
  ovtlyrUserId: '',
  alphaVantageApiKey: '',
  massiveApiKey: '',
  ibkrAccountId: '',
  ibkrFlexQueryId: ''
})

const credentialsStatus = ref<{
  ovtlyrConfigured: boolean
  alphaVantageConfigured: boolean
  massiveConfigured: boolean
  ibkrConfigured: boolean
  configFileExists: boolean
} | null>(null)

const saving = ref(false)

const allConfigured = computed(() => {
  return credentialsStatus.value?.ovtlyrConfigured
    && credentialsStatus.value?.alphaVantageConfigured
    && credentialsStatus.value?.massiveConfigured
})

// Load credentials on mount
onMounted(async () => {
  await loadCredentials()
  await loadCredentialsStatus()
})

async function loadCredentials() {
  try {
    const data = await $fetch('/udgaard/api/settings/credentials')
    credentials.value = data as typeof credentials.value
  } catch (error) {
    console.error('Failed to load credentials:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load credentials',
      color: 'error'
    })
  }
}

async function loadCredentialsStatus() {
  try {
    const data = await $fetch('/udgaard/api/settings/credentials/status')
    credentialsStatus.value = data as typeof credentialsStatus.value
  } catch (error) {
    console.error('Failed to load credentials status:', error)
  }
}

async function saveSettings() {
  saving.value = true
  try {
    await $fetch('/udgaard/api/settings/credentials', {
      method: 'POST',
      body: credentials.value
    })

    toast.add({
      title: 'Success',
      description: 'Credentials saved successfully',
      color: 'success'
    })

    // Reload status
    await loadCredentialsStatus()
  } catch (error) {
    console.error('Failed to save credentials:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to save credentials',
      color: 'error'
    })
  } finally {
    saving.value = false
  }
}
</script>
