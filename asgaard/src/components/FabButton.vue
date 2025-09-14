<script setup>
const emit = defineEmits(["click"]);
const handleClick = (e) => {
  emit("click", e);
};
</script>

<template>
  <v-btn
    v-bind="$attrs"
    class="fab-btn"
    color="primary"
    icon="mdi-plus"
    size="small"
    @click="handleClick"
  />
</template>

<style scoped>
.fab-btn {
  position: fixed;
  bottom: 64px;
  right: 16px;
  border-radius: 50%;
  z-index: 1000;
}
</style>
