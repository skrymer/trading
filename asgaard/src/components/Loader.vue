<template>
    <div class="text-center">
        <v-overlay :model-value="loading" class="align-center justify-center">
            <v-progress-circular color="primary" size="64" indeterminate></v-progress-circular>
        </v-overlay>
    </div>
</template>

<script setup lang="ts">
const props = defineProps({
    loading: Boolean
})
</script>