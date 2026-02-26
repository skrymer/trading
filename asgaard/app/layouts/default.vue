<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

const route = useRoute()
const toast = useToast()

const open = ref(false)

const links = [[{
  label: 'Home',
  icon: 'i-lucide-house',
  to: '/',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Backtesting',
  icon: 'i-lucide-bar-chart-3',
  to: '/backtesting',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Portfolio',
  icon: 'i-lucide-briefcase',
  to: '/portfolio',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Scanner',
  icon: 'i-lucide-scan-search',
  to: '/scanner',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Stock Data',
  icon: 'i-lucide-database',
  to: '/stock-data',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Breadth',
  icon: 'i-lucide-activity',
  to: '/breadth',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Data Manager',
  icon: 'i-lucide-hard-drive-download',
  to: '/data-manager',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'App Metrics',
  icon: 'i-lucide-gauge',
  to: '/app-metrics',
  onSelect: () => {
    open.value = false
  }
},
{
  label: 'Settings',
  icon: 'i-lucide-settings',
  to: '/settings',
  onSelect: () => {
    open.value = false
  }
}]] satisfies NavigationMenuItem[][]

const groups = computed(() => [{
  id: 'links',
  label: 'Go to',
  items: links.flat()
}, {
  id: 'code',
  label: 'Code',
  items: [{
    id: 'source',
    label: 'View page source',
    icon: 'i-simple-icons-github',
    to: `https://github.com/nuxt-ui-templates/dashboard/blob/main/app/pages${route.path === '/' ? '/index' : route.path}.vue`,
    target: '_blank'
  }]
}])

onMounted(async () => {
  const cookie = useCookie('cookie-consent')
  if (cookie.value === 'accepted') {
    return
  }

  toast.add({
    title: 'We use first-party cookies to enhance your experience on our website.',
    duration: 0,
    close: false,
    actions: [{
      label: 'Accept',
      color: 'neutral',
      variant: 'outline',
      onClick: () => {
        cookie.value = 'accepted'
      }
    }, {
      label: 'Opt out',
      color: 'neutral',
      variant: 'ghost'
    }]
  })
})
</script>

<template>
  <UDashboardGroup unit="rem">
    <UDashboardSidebar
      id="default"
      v-model:open="open"
      collapsible
      resizable
      class="bg-elevated/25"
      :ui="{ footer: 'lg:border-t lg:border-default' }"
    >
      <template #default="{ collapsed }">
        <UDashboardSearchButton :collapsed="collapsed" class="bg-transparent ring-default" />

        <UNavigationMenu
          :collapsed="collapsed"
          :items="links[0]"
          orientation="vertical"
          tooltip
          popover
        />
      </template>

      <template #footer="{ collapsed }">
        <UserMenu :collapsed="collapsed" />
      </template>
    </UDashboardSidebar>

    <UDashboardSearch :groups="groups" />

    <slot />
  </UDashboardGroup>
</template>
