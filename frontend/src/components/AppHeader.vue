<script setup lang="ts">
defineProps<{
  productName: string
  connected: boolean
}>()
</script>

<template>
  <header class="app-header">
    <div class="brand-mark" aria-hidden="true">明</div>
    <div class="brand-copy">
      <p class="eyebrow">小明智能投保助手</p>
      <h1>{{ productName }}</h1>
    </div>
    <span class="connection-badge" :class="{ connected }">
      <span aria-hidden="true" class="status-dot"></span>
      {{ connected ? '服务已连接' : '工程预览' }}
    </span>
  </header>
</template>
