<script setup lang="ts">
definePageMeta({
  layout: false
})

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  error.value = ''
  loading.value = true

  try {
    const body = new URLSearchParams({
      username: username.value,
      password: password.value
    })

    await $fetch('/udgaard/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    })

    await navigateTo('/')
  } catch {
    error.value = 'Invalid username or password'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-default">
    <UCard class="w-full max-w-sm">
      <template #header>
        <div class="text-center">
          <h1 class="text-xl font-bold">
            Trading Platform
          </h1>
          <p class="text-sm text-dimmed mt-1">
            Sign in to continue
          </p>
        </div>
      </template>

      <form class="space-y-4" @submit.prevent="onSubmit">
        <UFormField label="Username">
          <UInput
            v-model="username"
            placeholder="Enter username"
            icon="i-lucide-user"
            autofocus
            class="w-full"
          />
        </UFormField>

        <UFormField label="Password">
          <UInput
            v-model="password"
            type="password"
            placeholder="Enter password"
            icon="i-lucide-lock"
            class="w-full"
          />
        </UFormField>

        <UAlert
          v-if="error"
          color="error"
          icon="i-lucide-alert-circle"
          :title="error"
        />

        <UButton
          type="submit"
          block
          :loading="loading"
          :disabled="!username || !password"
        >
          Sign in
        </UButton>
      </form>
    </UCard>
  </div>
</template>
