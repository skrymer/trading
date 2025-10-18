<template>

    <div class="text-center">
        <v-menu open-on-hover>
            <template v-slot:activator="{ props }">
                <fab-button v-bind="props" />
            </template>

            <v-list>
                <v-list-item v-for="(item, index) in menuItems" :key="index" :value="index">
                    <v-list-item-title>{{ item.title }}</v-list-item-title>
                </v-list-item>
            </v-list>
        </v-menu>
    </div>
</template>

<script setup lang="ts">

defineProps<{menuItems: { title: string }[]}>()

</script>

<style scoped></style>