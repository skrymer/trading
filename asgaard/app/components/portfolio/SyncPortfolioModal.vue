<script setup lang="ts">
import { BrokerTypeDescriptions } from '~/types/enums'
import type { Portfolio, PortfolioSyncResult, SyncPortfolioRequest } from '~/types'

const props = defineProps<{
  open: boolean
  portfolio: Portfolio
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'synced': [result: PortfolioSyncResult]
}>()

const token = ref('')
const isLoading = ref(false)
const error = ref<string>('')
const settingsCredentials = ref<{ queryId: string, accountId: string } | null>(null)

const toast = useToast()

// Load IBKR credentials from settings as fallback
onMounted(async () => {
  try {
    const data = await $fetch<{ ibkrAccountId?: string, ibkrFlexQueryId?: string }>('/udgaard/api/settings/credentials')
    if (data.ibkrFlexQueryId && data.ibkrAccountId) {
      settingsCredentials.value = {
        queryId: data.ibkrFlexQueryId,
        accountId: data.ibkrAccountId
      }
    }
  } catch (error) {
    console.error('Failed to load IBKR credentials from settings:', error)
  }
})

const brokerName = computed(() => {
  return props.portfolio.broker ? BrokerTypeDescriptions[props.portfolio.broker as keyof typeof BrokerTypeDescriptions] || props.portfolio.broker : 'Unknown'
})

const queryId = computed(() => {
  // Use portfolio value first, fallback to settings, then 'Not configured'
  return props.portfolio.brokerConfig?.queryId || settingsCredentials.value?.queryId || 'Not configured'
})

const accountId = computed(() => {
  // Use portfolio value first, fallback to settings, then 'Not configured'
  return props.portfolio.brokerAccountId || settingsCredentials.value?.accountId || 'Not configured'
})

async function handleSync() {
  if (!token.value) {
    toast.add({
      title: 'Missing Token',
      description: 'Please enter your Flex Query token',
      icon: 'i-lucide-alert-circle',
      color: 'error'
    })
    return
  }

  isLoading.value = true
  error.value = ''

  try {
    const request: SyncPortfolioRequest = {
      credentials: {
        token: token.value,
        queryId: queryId.value,
        accountId: accountId.value
      }
    }

    const result = await $fetch<PortfolioSyncResult>(`/udgaard/api/portfolio/${props.portfolio.id}/sync`, {
      method: 'POST',
      body: request
    })

    emit('synced', result)
    emit('update:open', false)

    // Reset form
    token.value = ''

    const totalChanges = result.tradesAdded + result.tradesUpdated
    toast.add({
      title: 'Sync Complete',
      description: `${totalChanges} trades imported/updated`,
      icon: 'i-lucide-check-circle',
      color: 'success'
    })
  } catch (e: any) {
    // Extract error message from response
    const errorMessage = e.data?.message || e.message || 'Failed to sync portfolio'
    error.value = errorMessage

    toast.add({
      title: 'Sync Failed',
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
  token.value = ''
}
</script>

<template>
  <UModal
    :open="open"
    title="Sync Portfolio with Broker"
    size="lg"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-4">
        <div class="p-4 bg-muted/30 rounded-lg">
          <div class="space-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-muted">Portfolio:</span>
              <span class="font-medium">{{ portfolio.name }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted">Broker:</span>
              <span class="font-medium">{{ brokerName }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted">Account ID:</span>
              <span class="font-medium">{{ accountId }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted">Query ID:</span>
              <span class="font-medium">{{ queryId }}</span>
            </div>
            <div v-if="portfolio.lastSyncDate" class="flex justify-between">
              <span class="text-muted">Last Synced:</span>
              <span class="font-medium">{{ new Date(portfolio.lastSyncDate).toLocaleString() }}</span>
            </div>
          </div>
        </div>

        <div class="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
          <div class="flex items-start gap-2">
            <UIcon name="i-lucide-info" class="w-4 h-4 text-blue-600 mt-0.5 flex-shrink-0" />
            <div class="text-sm text-blue-900 dark:text-blue-100">
              <p class="font-medium mb-1">
                Sync will:
              </p>
              <ul class="list-disc list-inside space-y-1 text-xs">
                <li>Import new trades since last sync</li>
                <li>Update existing trades if broker has newer information</li>
                <li>Detect and link option rolls automatically</li>
                <li>Split partial position closes into separate trades</li>
              </ul>
            </div>
          </div>
        </div>

        <UFormField label="Flex Query Token" help="6-hour expiry - generate new token each time" required>
          <UInput
            v-model="token"
            type="password"
            placeholder="Enter your IBKR Flex Query token"
          />
        </UFormField>

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
          label="Sync Now"
          icon="i-lucide-refresh-cw"
          :loading="isLoading"
          :disabled="!token"
          @click="handleSync"
        />
      </div>
    </template>
  </UModal>
</template>
