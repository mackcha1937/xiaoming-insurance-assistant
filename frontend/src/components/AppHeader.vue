<script setup lang="ts">
defineProps<{
  productName: string
  connected: boolean
  customerAlias: string
}>()

defineEmits<{
  history: []
  newCustomer: []
}>()
</script>

<template>
  <header class="app-header">
    <div class="brand-mark" aria-hidden="true">明</div>
    <div class="brand-copy">
      <p class="eyebrow">小明智能投保助手</p>
      <h1>{{ productName }}</h1>
      <small>当前客户：{{ customerAlias }}</small>
    </div>
    <div class="header-actions">
      <span class="connection-badge" :class="{ connected }">
        <span aria-hidden="true" class="status-dot"></span>
        {{ connected ? '服务已连接' : '工程预览' }}
      </span>
      <button type="button" @click="$emit('history')">历史咨询</button>
      <button type="button" class="header-primary" @click="$emit('newCustomer')">新建客户</button>
    </div>
  </header>
</template>
