<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { loadColorScheme } from './utils/storage'

onMounted(() => {
  const scheme = loadColorScheme()
  if (scheme === 'light') {
    document.documentElement.dataset.theme = 'light'
  }
})
</script>
