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
                    Configure your API credentials for broker integration
                  </p>
                </div>
                <UBadge
                  v-if="credentialsStatus"
                  :color="credentialsStatus.ibkrConfigured ? 'success' : 'warning'"
                  variant="subtle"
                >
                  {{ credentialsStatus.ibkrConfigured ? 'Configured' : 'Incomplete' }}
                </UBadge>
              </div>
            </template>

            <div class="space-y-6">
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

                  <UFormGroup label="Flex Query Token" help="Token for accessing Flex Query reports (valid for up to 1 year)">
                    <UInput
                      v-model="credentials.ibkrFlexQueryToken"
                      type="password"
                      placeholder="Enter your Flex Query Token"
                    />
                  </UFormGroup>

                  <UAlert
                    icon="i-lucide-info"
                    color="info"
                    variant="subtle"
                    title="How to setup IBKR Flex Query"
                    description="1. Login to IBKR Portal  2. Go to Reports > Flex Queries  3. Create a query with trade details  4. Copy the Query ID and Token"
                  />
                </div>
              </div>
            </div>
          </UCard>

          <!-- Position Sizing Section -->
          <UCard>
            <template #header>
              <div class="flex items-center justify-between">
                <div>
                  <h3 class="text-lg font-semibold">
                    Position Sizing
                  </h3>
                  <p class="text-sm text-gray-500 mt-1">
                    Configure default position sizing for scanner trades
                  </p>
                </div>
                <USwitch v-model="positionSizing.enabled" />
              </div>
            </template>

            <div v-if="positionSizing.enabled" class="space-y-4">
              <div class="grid grid-cols-3 gap-4">
                <UFormField>
                  <template #label>
                    <span class="flex items-center gap-1">
                      Portfolio Value ($)
                      <UTooltip text="Total capital allocated for trading. Used to calculate dollar risk and position sizes.">
                        <UIcon name="i-lucide-info" class="size-3.5 text-muted cursor-help" />
                      </UTooltip>
                    </span>
                  </template>
                  <UInput
                    v-model.number="positionSizing.portfolioValue"
                    type="number"
                    :min="1000"
                    :step="1000"
                    placeholder="100000"
                  />
                </UFormField>

                <UFormField>
                  <template #label>
                    <span class="flex items-center gap-1">
                      Risk Per Trade (%)
                      <UTooltip text="Max percentage of portfolio risked per trade. E.g., 1.5% of $100k = $1,500 risk per trade.">
                        <UIcon name="i-lucide-info" class="size-3.5 text-muted cursor-help" />
                      </UTooltip>
                    </span>
                  </template>
                  <UInput
                    v-model.number="positionSizing.riskPercentage"
                    type="number"
                    :min="0.1"
                    :max="10"
                    :step="0.1"
                    placeholder="1.5"
                  />
                </UFormField>

                <UFormField>
                  <template #label>
                    <span class="flex items-center gap-1">
                      ATR Multiplier
                      <UTooltip text="Shares = Risk$ / (nATR x ATR). Lower = more shares with a tighter stop. Higher = fewer shares with a wider stop.">
                        <UIcon name="i-lucide-info" class="size-3.5 text-muted cursor-help" />
                      </UTooltip>
                    </span>
                  </template>
                  <UInput
                    v-model.number="positionSizing.nAtr"
                    type="number"
                    :min="0.5"
                    :max="10"
                    :step="0.5"
                    placeholder="2.0"
                  />
                </UFormField>
              </div>

              <UAlert
                icon="i-lucide-calculator"
                color="neutral"
                variant="subtle"
                :description="formulaPreview"
              />
            </div>

            <div v-else class="text-sm text-muted">
              Position sizing is disabled. Enable it to automatically calculate trade quantities based on ATR risk.
            </div>
          </UCard>
        </div>
      </UDashboardPanelContent>
    </UDashboardPanel>
  </UDashboardPage>
</template>

<script setup lang="ts">
import type { PositionSizingSettings } from '~/types'

const toast = useToast()

const credentials = ref({
  ibkrAccountId: '',
  ibkrFlexQueryId: '',
  ibkrFlexQueryToken: ''
})

const credentialsStatus = ref<{
  ibkrConfigured: boolean
} | null>(null)

const positionSizing = ref<PositionSizingSettings>({
  enabled: true,
  portfolioValue: 100000,
  riskPercentage: 1.5,
  nAtr: 2.0
})

const saving = ref(false)

// Load settings on mount
onMounted(async () => {
  await Promise.all([loadCredentials(), loadCredentialsStatus(), loadPositionSizing()])
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

async function loadPositionSizing() {
  try {
    const data = await $fetch<PositionSizingSettings>('/udgaard/api/settings/position-sizing')
    positionSizing.value = data
  } catch (error) {
    console.error('Failed to load position sizing settings:', error)
  }
}

const formulaPreview = computed(() => {
  const { portfolioValue, riskPercentage, nAtr } = positionSizing.value
  return `shares = floor($${portfolioValue.toLocaleString()} x ${riskPercentage}% / (${nAtr} x ATR))`
})

async function saveSettings() {
  saving.value = true
  try {
    await Promise.all([
      $fetch('/udgaard/api/settings/credentials', {
        method: 'POST',
        body: credentials.value
      }),
      $fetch('/udgaard/api/settings/position-sizing', {
        method: 'POST',
        body: positionSizing.value
      })
    ])

    toast.add({
      title: 'Success',
      description: 'Settings saved successfully',
      color: 'success'
    })

    // Reload status
    await loadCredentialsStatus()
  } catch (error) {
    console.error('Failed to save settings:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to save settings',
      color: 'error'
    })
  } finally {
    saving.value = false
  }
}
</script>
