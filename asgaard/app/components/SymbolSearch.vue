<script setup lang="ts">
import { refDebounced } from '@vueuse/core'

const props = withDefaults(defineProps<{
  placeholder?: string
  multiple?: boolean
}>(), {
  placeholder: 'Type to search stocks...',
  multiple: false
})

const model = defineModel<string | string[]>()

const searchTerm = ref('')
const searchTermDebounced = refDebounced(searchTerm, 200)
const { data: results, status } = useLazyFetch<string[]>('/udgaard/api/stocks/symbols/search', {
  params: { query: searchTermDebounced, limit: 20 }
})
</script>

<template>
  <UInputMenu
    v-model="model"
    v-model:search-term="searchTerm"
    :items="results || []"
    :placeholder="props.placeholder"
    :loading="status === 'pending'"
    :multiple="props.multiple"
    ignore-filter
    icon="i-lucide-search"
  />
</template>
