<script setup lang="ts">
const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'create': [data: { name: string; initialBalance: number; currency: string }]
}>()

const name = ref('')
const initialBalance = ref(10000)
const currency = ref('USD')

const currencies = ['USD', 'EUR', 'GBP', 'AUD', 'CAD']

function handleCreate() {
  if (!name.value || initialBalance.value <= 0) {
    return
  }

  emit('create', {
    name: name.value,
    initialBalance: initialBalance.value,
    currency: currency.value
  })

  // Reset form
  name.value = ''
  initialBalance.value = 10000
  currency.value = 'USD'
}

function handleClose() {
  emit('update:open', false)
}
</script>

<template>
  <UModal
    :open="open"
    @update:open="emit('update:open', $event)"
    title="Create Portfolio"
  >
    <template #body>
      <div class="space-y-4">
        <UFormField label="Portfolio Name" required>
          <UInput
            v-model="name"
            placeholder="My Trading Portfolio"
          />
        </UFormField>

        <UFormField label="Initial Balance" required>
          <UInput
            v-model.number="initialBalance"
            type="number"
            min="0"
            step="100"
          />
        </UFormField>

        <UFormField label="Currency" required>
          <USelect
            v-model="currency"
            :items="currencies"
          />
        </UFormField>
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
          label="Create"
          icon="i-lucide-check"
          @click="handleCreate"
        />
      </div>
    </template>
  </UModal>
</template>
