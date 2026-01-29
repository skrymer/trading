<script setup lang="ts">
import { BrokerType, BrokerTypeDescriptions } from '~/types/enums'
import type { CreatePortfolioFromBrokerRequest, CreateFromBrokerResult, TestBrokerConnectionResponse } from '~/types'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'created': [result: CreateFromBrokerResult]
}>()

const name = ref('')
const broker = ref<BrokerType>(BrokerType.IBKR)
const currency = ref('USD')
const initialBalance = ref<number | undefined>(undefined)
const startDate = ref('')
const token = ref('')
const queryId = ref('')
const accountId = ref('')
const isLoading = ref(false)
const error = ref<string>('')
const testConnectionStatus = ref<'idle' | 'testing' | 'success' | 'error'>('idle')
const testConnectionMessage = ref('')
const credentialsLoaded = ref(false)

const currencies = ['USD', 'EUR', 'GBP', 'AUD', 'CAD']

const brokerOptions = Object.entries(BrokerTypeDescriptions).map(([value, label]) => ({
  label,
  value
})).filter(opt => opt.value !== BrokerType.MANUAL)

const toast = useToast()

// Load IBKR credentials from settings on mount
onMounted(async () => {
  await loadCredentials()
})

async function loadCredentials() {
  try {
    const data = await $fetch('/udgaard/api/settings/credentials')
    if (data.ibkrAccountId) {
      accountId.value = data.ibkrAccountId
    }
    if (data.ibkrFlexQueryId) {
      queryId.value = data.ibkrFlexQueryId
    }
    credentialsLoaded.value = true
  } catch (error) {
    console.error('Failed to load IBKR credentials from settings:', error)
  }
}

// Calculate max start date (366 days ago)
const maxStartDate = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() - 366)
  return date.toISOString().split('T')[0]
})

async function testConnection() {
  if (!token.value || !queryId.value || !accountId.value) {
    toast.add({
      title: 'Missing Fields',
      description: 'Please fill in all broker credentials',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
    return
  }

  testConnectionStatus.value = 'testing'
  testConnectionMessage.value = 'Testing connection...'

  try {
    const response = await $fetch<TestBrokerConnectionResponse>('/udgaard/api/portfolio/broker/test', {
      method: 'POST',
      body: {
        broker: broker.value,
        credentials: {
          token: token.value,
          queryId: queryId.value,
          accountId: accountId.value
        }
      }
    })

    if (response.success) {
      testConnectionStatus.value = 'success'
      testConnectionMessage.value = response.message
      toast.add({
        title: 'Connection Successful',
        description: 'Broker credentials are valid',
        icon: 'i-lucide-check-circle',
        color: 'success'
      })
    } else {
      testConnectionStatus.value = 'error'
      testConnectionMessage.value = response.message
      toast.add({
        title: 'Connection Failed',
        description: response.message,
        icon: 'i-lucide-alert-circle',
        color: 'error'
      })
    }
  } catch (e: any) {
    testConnectionStatus.value = 'error'
    testConnectionMessage.value = e.message || 'Connection test failed'
    toast.add({
      title: 'Error',
      description: 'Failed to test connection',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  }
}

async function handleCreate() {
  if (!name.value || !token.value || !queryId.value || !accountId.value) {
    toast.add({
      title: 'Missing Fields',
      description: 'Please fill in all required fields',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
    return
  }

  isLoading.value = true
  error.value = ''

  try {
    const request: CreatePortfolioFromBrokerRequest = {
      name: name.value,
      broker: broker.value,
      credentials: {
        token: token.value,
        queryId: queryId.value,
        accountId: accountId.value
      },
      startDate: startDate.value || undefined,
      currency: currency.value,
      initialBalance: initialBalance.value
    }

    const result = await $fetch<CreateFromBrokerResult>('/udgaard/api/portfolio/import', {
      method: 'POST',
      body: request
    })

    emit('created', result)
    emit('update:open', false)

    // Reset form
    name.value = ''
    broker.value = BrokerType.IBKR
    currency.value = 'USD'
    initialBalance.value = undefined
    startDate.value = ''
    token.value = ''
    queryId.value = ''
    accountId.value = ''
    testConnectionStatus.value = 'idle'
    testConnectionMessage.value = ''

    toast.add({
      title: 'Portfolio Created',
      description: `${result.portfolio.name} created with ${result.tradesImported} trades imported`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (e: any) {
    // Extract error message from response
    const errorMessage = e.data?.message || e.message || 'Failed to create portfolio from broker'
    error.value = errorMessage

    toast.add({
      title: 'Import Failed',
      description: errorMessage,
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
  } finally {
    isLoading.value = false
  }
}

function handleClose() {
  emit('update:open', false)
  error.value = ''
  testConnectionStatus.value = 'idle'
  testConnectionMessage.value = ''
}
</script>

<template>
  <UModal
    :open="open"
    title="Create Portfolio from Broker"
    fullscreen
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="max-w-4xl mx-auto py-8 space-y-6">
        <UCard>
          <template #header>
            <h4 class="text-base font-semibold">
              Portfolio Settings
            </h4>
          </template>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <UFormField label="Portfolio Name" required class="md:col-span-2">
              <UInput
                v-model="name"
                placeholder="My IBKR Portfolio"
              />
            </UFormField>

            <UFormField label="Broker" required>
              <USelect
                v-model="broker"
                :items="brokerOptions"
                value-key="value"
                :disabled="brokerOptions.length === 1"
              />
            </UFormField>

            <UFormField label="Currency" required>
              <USelect
                v-model="currency"
                :items="currencies"
              />
            </UFormField>

            <UFormField label="Initial Balance" help="Optional - leave empty to calculate from broker data">
              <UInput
                v-model.number="initialBalance"
                type="number"
                min="0"
                step="100"
                placeholder="Auto-calculate from broker"
              />
            </UFormField>

            <UFormField label="Import Start Date" help="Optional - leave empty to use your Flex Query template defaults. If specified, fetches from this date until yesterday. Maximum 366 day range.">
              <UInput
                v-model="startDate"
                type="date"
                :min="maxStartDate"
                placeholder="Leave empty for template defaults"
              />
            </UFormField>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <h4 class="text-base font-semibold">
              Interactive Brokers Credentials
            </h4>
          </template>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <UFormField
              label="Flex Query Token"
              help="6-hour expiry - generate new token each time"
              required
              class="md:col-span-2"
            >
              <UInput
                v-model="token"
                type="password"
                placeholder="Enter your IBKR Flex Query token"
              />
            </UFormField>

            <UFormField label="Query ID" :help="credentialsLoaded ? 'Loaded from Settings' : 'Configure in Settings to auto-fill'" required>
              <UInput
                v-model="queryId"
                placeholder="Your Flex Query ID"
              />
            </UFormField>

            <UFormField label="Account ID" :help="credentialsLoaded ? 'Loaded from Settings' : 'Configure in Settings to auto-fill'" required>
              <UInput
                v-model="accountId"
                placeholder="Your IBKR account ID"
              />
            </UFormField>

            <div class="flex items-center gap-2 md:col-span-2">
              <UButton
                label="Test Connection"
                icon="i-lucide-plug"
                size="sm"
                variant="outline"
                :loading="testConnectionStatus === 'testing'"
                :disabled="!token || !queryId || !accountId"
                @click="testConnection"
              />

              <div v-if="testConnectionStatus === 'success'" class="flex items-center gap-1 text-sm text-green-600">
                <UIcon name="i-lucide-check-circle" class="w-4 h-4" />
                <span>Connected</span>
              </div>
              <div v-else-if="testConnectionStatus === 'error'" class="flex items-center gap-1 text-sm text-red-600">
                <UIcon name="i-lucide-alert-circle" class="w-4 h-4" />
                <span>Failed</span>
              </div>
            </div>

            <p v-if="testConnectionMessage" class="text-xs text-muted md:col-span-2">
              {{ testConnectionMessage }}
            </p>
          </div>
        </UCard>

        <div v-if="error" class="p-3 bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200 dark:border-red-800">
          <div class="flex items-start gap-2">
            <UIcon name="i-lucide-alert-circle" class="w-4 h-4 text-red-600 mt-0.5 flex-shrink-0" />
            <p class="text-sm text-red-900 dark:text-red-100">
              {{ error }}
            </p>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="handleClose"
        />
        <UButton
          label="Import Trades"
          icon="i-lucide-download"
          :loading="isLoading"
          :disabled="!name || !token || !queryId || !accountId"
          @click="handleCreate"
        />
      </div>
    </template>
  </UModal>
</template>
