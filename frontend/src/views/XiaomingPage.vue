<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

import AppHeader from '@/components/AppHeader.vue'
import CapabilityGrid from '@/components/CapabilityGrid.vue'
import ConversationPanel from '@/components/ConversationPanel.vue'
import PrivacyNotice from '@/components/PrivacyNotice.vue'
import { useNetworkStore } from '@/stores/network'
import { useProductStore } from '@/stores/product'
import { useUiStore } from '@/stores/ui'
import { useConversationStore } from '@/stores/conversation'

const productStore = useProductStore()
const networkStore = useNetworkStore()
const uiStore = useUiStore()
const conversationStore = useConversationStore()

function ask(prompt: string): void {
  void conversationStore.send(prompt)
}

function handleOnline(): void {
  networkStore.setOnline(true)
  uiStore.announce('网络已恢复')
  void productStore.load()
}

function handleOffline(): void {
  networkStore.setOnline(false)
  uiStore.announce('网络已断开，当前页面仍可查看')
}

onMounted(() => {
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  void productStore.load()
})

onUnmounted(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
})
</script>

<template>
  <div class="page-shell">
    <AppHeader
      :product-name="productStore.productShortName"
      :connected="productStore.connected"
    />

    <main id="main-content" class="content-shell" tabindex="-1">
      <PrivacyNotice />

      <section class="welcome-card" aria-labelledby="welcome-title">
        <p class="eyebrow">达尔文12号专属助手</p>
        <h2 id="welcome-title">你好，我是小明。</h2>
        <p>我只围绕当前固定产品提供可核验的信息与办理引导，不回答超出范围的问题。</p>
        <dl class="foundation-facts">
          <div>
            <dt>产品编号</dt>
            <dd>{{ productStore.context.productId }}</dd>
          </div>
          <div>
            <dt>知识版本</dt>
            <dd>{{ productStore.context.knowledgeVersion }}</dd>
          </div>
          <div>
            <dt>访问方式</dt>
            <dd>POC 免登录</dd>
          </div>
        </dl>
      </section>

      <div v-if="networkStore.lastError" class="service-message" role="status">
        <strong>服务连接提示</strong>
        <span>{{ networkStore.lastError }}</span>
      </div>

      <CapabilityGrid :capabilities="productStore.context.capabilities" @select="ask" />
      <ConversationPanel />
    </main>

    <footer>
      <p>内部开发预览 · 不构成保险建议或承保承诺</p>
    </footer>
    <p class="sr-only" aria-live="polite">{{ uiStore.liveMessage }}</p>
  </div>
</template>
