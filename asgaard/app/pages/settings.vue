<template>
  <UDashboardPanel id="settings">
    <template #header>
      <UDashboardNavbar title="Settings">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            label="Save"
            color="primary"
            :loading="saving"
            @click="saveSettings"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="max-w-4xl mx-auto space-y-8 p-6">
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
                    Starting Capital ($)
                    <UTooltip text="Initial capital baseline. Position sizing uses current equity (starting capital + realized P/L + unrealized P/L), so risk scales dynamically as your portfolio grows or shrinks.">
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
                    <UTooltip text="Max percentage of current equity risked per trade. Applied against live equity so risk increases with gains and decreases with losses.">
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

            <!-- Drawdown Scaling Section -->
            <USeparator class="my-4" />

            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span class="text-sm font-semibold">Drawdown Scaling</span>
                <UTooltip text="Automatically reduces risk per trade when portfolio is in drawdown. Halts new entries when multiplier is 0.">
                  <UIcon name="i-lucide-info" class="size-3.5 text-muted cursor-help" />
                </UTooltip>
              </div>
              <USwitch v-model="positionSizing.drawdownScalingEnabled" size="sm" />
            </div>

            <div v-if="positionSizing.drawdownScalingEnabled" class="space-y-3 mt-3">
              <div
                v-for="(threshold, index) in positionSizing.drawdownThresholds"
                :key="index"
                class="flex items-end gap-3"
              >
                <UFormField label="Drawdown %" class="flex-1">
                  <UInput
                    v-model.number="threshold.drawdownPercent"
                    type="number"
                    :min="1"
                    :max="50"
                    :step="1"
                    placeholder="5"
                  />
                </UFormField>
                <UFormField class="flex-1">
                  <template #label>
                    <span class="flex items-center gap-1">
                      Risk Per Trade (%)
                      <UTooltip :text="threshold.riskMultiplier === 0 ? 'Set to 0 to halt new entries at this drawdown level' : `${(threshold.riskMultiplier * 100).toFixed(0)}% of base risk`">
                        <UIcon name="i-lucide-info" class="size-3.5 text-muted cursor-help" />
                      </UTooltip>
                    </span>
                  </template>
                  <UInput
                    :model-value="(positionSizing.riskPercentage * threshold.riskMultiplier)"
                    type="number"
                    :min="0"
                    :max="positionSizing.riskPercentage"
                    :step="0.05"
                    placeholder="0.75"
                    @update:model-value="(v: number) => threshold.riskMultiplier = positionSizing.riskPercentage > 0 ? Math.min(1, Math.max(0, v / positionSizing.riskPercentage)) : 0"
                  />
                </UFormField>
                <UFormField label=" " class="shrink-0">
                  <UButton
                    icon="i-lucide-trash-2"
                    color="error"
                    variant="ghost"
                    size="sm"
                    @click="removeThreshold(index)"
                  />
                </UFormField>
              </div>

              <UButton
                icon="i-lucide-plus"
                label="Add Threshold"
                variant="soft"
                size="sm"
                @click="addThreshold"
              />

              <UAlert
                v-if="positionSizing.drawdownThresholds && positionSizing.drawdownThresholds.length > 0"
                icon="i-lucide-shield"
                color="neutral"
                variant="subtle"
                :description="drawdownScalingPreview"
              />
            </div>
          </div>

          <div v-else class="text-sm text-muted">
            Position sizing is disabled. Enable it to automatically calculate trade quantities based on ATR risk.
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>
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
  nAtr: 2.0,
  drawdownScalingEnabled: true,
  drawdownThresholds: [
    { drawdownPercent: 5.0, riskMultiplier: 0.67 },
    { drawdownPercent: 10.0, riskMultiplier: 0.33 }
  ]
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
  const { riskPercentage, nAtr } = positionSizing.value
  return `shares = floor(currentEquity x ${riskPercentage}% / (${nAtr} x ATR))  —  currentEquity = startingCapital + realizedP&L + unrealizedP&L`
})

function addThreshold() {
  if (!positionSizing.value.drawdownThresholds) {
    positionSizing.value.drawdownThresholds = []
  }
  const existing = positionSizing.value.drawdownThresholds
  const maxPct = existing.length > 0 ? Math.max(...existing.map(t => t.drawdownPercent)) : 0
  existing.push({ drawdownPercent: maxPct + 5, riskMultiplier: 0 })
}

function removeThreshold(index: number) {
  positionSizing.value.drawdownThresholds?.splice(index, 1)
}

const drawdownScalingPreview = computed(() => {
  const thresholds = positionSizing.value.drawdownThresholds ?? []
  const base = positionSizing.value.riskPercentage
  const sorted = [...thresholds].sort((a, b) => a.drawdownPercent - b.drawdownPercent)
  const parts = sorted.map((t) => {
    const eff = (base * t.riskMultiplier).toFixed(2)
    return t.riskMultiplier === 0
      ? `>=${t.drawdownPercent}% DD → halt new entries`
      : `>=${t.drawdownPercent}% DD → ${eff}% risk`
  })
  return `Below thresholds: ${base}% risk. ${parts.join(' | ')}`
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
