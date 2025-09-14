<template>
    <div class="text-center">
        <v-overlay :model-value="loading" class="align-center justify-center">
            <v-progress-circular color="primary" size="128" :width="10" indeterminate>
                <template v-slot:default><span class="text-h6"> {{ spinnerWord }} </span></template>
            </v-progress-circular>
        </v-overlay>
    </div>
</template>

<script setup lang="ts">
const props = defineProps({
    loading: Boolean
})

const spinnerWords = [
    "blorping",
    "snorfing",
    "zibbing",
    "quorxing",
    "flarning",
    "glimbling",
    "frozzing",
    "plonxing",
    "scrimzing",
    "nubbitting",
    "wompusing",
    "klonking",
    "zotching",
    "quibboing",
    "splurning",
    "glorping",
    "twizzing",
    "bazzling",
    "zozzling",
    "flibbiting",
    "glimzing",
    "prundling",
    "sprockling",
    "vinkaling",
    "borkling",
    "jorbling",
    "skorfing",
    "plixoring",
    "nurgloing",
    "jibloing",
    "zarping",
    "skwibbing",
    "drumbling",
    "torpling",
    "quazzying",
    "frimbling",
    "zottoing",
    "blipnaring",
    "snarfeling",
    "glonking",
    "zundling",
    "splonking",
    "murpling",
    "fizzloing",
    "borpling",
    "grobbling",
    "twobboing",
    "krindling",
    "yorfling",
    "plaxining",
    "snorbling",
    "quumpling",
    "zibbixing",
    "drimzoing",
    "glaxoring",
    "plinxing",
    "vortling",
    "splimsing",
    "tronzling"
];

const spinnerWord = ref(spinnerWords[Math.floor(Math.random() * spinnerWords.length)]);

watch(() => props.loading, (newVal) => {
    if (newVal) {
        spinnerWord.value = spinnerWords[Math.floor(Math.random() * spinnerWords.length)];
    }
});

</script>