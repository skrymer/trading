<template>
  <v-app-bar>
    <v-app-bar-title>MATOM (Make a ton of money) trading analysis platform</v-app-bar-title>
  </v-app-bar>
  
  <v-navigation-drawer v-model="drawer" :rail="rail" permanent @click="rail = false">
    <v-list>
      <v-list-item prepend-avatar="https://randomuser.me/api/portraits/men/85.jpg" title="Sonni Nielsen">
        <template v-slot:append>
          <v-btn icon="mdi-chevron-left" variant="text" @click.stop="rail = !rail"></v-btn>
        </template>
      </v-list-item>
    </v-list>

    <v-divider></v-divider>

    <v-list density="compact" nav>
      <v-list-item prepend-icon="mdi-home-city" title="Home" value="home"></v-list-item>
      <v-list-item prepend-icon="mdi-account" title="My Account" value="account"></v-list-item>
      <v-list-item prepend-icon="mdi-account-group-outline" title="Users" value="users"></v-list-item>
    </v-list>
  </v-navigation-drawer>

  <v-main class="bg-grey-lighten-3">
    <router-view />
  </v-main>

  <AppFooter />
</template>

<script lang="ts" setup>
const drawer = ref(true)
const rail = ref(true)
</script>
