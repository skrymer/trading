<template>
    <v-card>
        <v-card-title class="bg-blue">
            {{ title }}
        </v-card-title>
        <v-card-text class="py-0">
            <div :class="`text-center text-h${heading || '3'} font-weight-medium`">
                {{ content }}
            </div>
        </v-card-text>
    </v-card>
</template>

<script setup lang="ts">
import { defineProps } from 'vue'

defineProps<{ title: string, content: string | number, heading?: '1' | '2' | '3' | '4' }>()

</script>