<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

import AppHeader from '@/components/AppHeader.vue'
import ApplicationPanel from '@/components/ApplicationPanel.vue'
import CapabilityGrid from '@/components/CapabilityGrid.vue'
import ChecklistPanel from '@/components/ChecklistPanel.vue'
import ConversationPanel from '@/components/ConversationPanel.vue'
import HistoryDrawer from '@/components/HistoryDrawer.vue'
import HealthDisclosurePanel from '@/components/HealthDisclosurePanel.vue'
import NewConversationDialog from '@/components/NewConversationDialog.vue'
import OccupationPanel from '@/components/OccupationPanel.vue'
import PrivacyNotice from '@/components/PrivacyNotice.vue'
import { useNetworkStore } from '@/stores/network'
import { useProductStore } from '@/stores/product'
import { useUiStore } from '@/stores/ui'
import { useConversationStore } from '@/stores/conversation'
import type { XiaomingIntent } from '@/services/generated/contracts'

const productStore = useProductStore()
const networkStore = useNetworkStore()
const uiStore = useUiStore()
const conversationStore = useConversationStore()
const occupationOpen = ref(false)
const checklistOpen = ref(false)
const healthOpen = ref(false)
const applicationOpen = ref(false)
const historyOpen = ref(false)
const newCustomerOpen = ref(false)

async function ask(intent: XiaomingIntent, prompt: string): Promise<void> {
  if (intent === 'OCCUPATION_QUERY') {
    occupationOpen.value = true
    await nextTick()
    const panel = document.querySelector('#occupation-search')
    if (typeof panel?.scrollIntoView === 'function') {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    return
  }
  if (intent === 'MATERIAL_CHECKLIST') {
    checklistOpen.value = true
    await nextTick()
    const panel = document.querySelector('#material-checklist')
    if (typeof panel?.scrollIntoView === 'function') {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    return
  }
  if (intent === 'HEALTH_DISCLOSURE') {
    healthOpen.value = true
    await nextTick()
    const panel = document.querySelector('#health-disclosure')
    if (typeof panel?.scrollIntoView === 'function') {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    return
  }
  if (intent === 'FORMAL_APPLICATION') {
    applicationOpen.value = true
    await nextTick()
    document.querySelector('#formal-application')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  void conversationStore.send(prompt)
}

async function createCustomer(alias: string): Promise<void> {
  if (await conversationStore.createConversation(alias)) {
    newCustomerOpen.value = false
    occupationOpen.value = false
    checklistOpen.value = false
    healthOpen.value = false
    applicationOpen.value = false
    uiStore.announce('已保存原咨询并新建客户')
  }
}

async function returnToApplicationCheck(item: 'RULES' | 'OCCUPATION' | 'HEALTH' | 'CHECKLIST'): Promise<void> {
  applicationOpen.value = false
  if (item === 'OCCUPATION') occupationOpen.value = true
  if (item === 'HEALTH') healthOpen.value = true
  if (item === 'CHECKLIST') checklistOpen.value = true
  await nextTick()
  const target = item === 'RULES'
    ? document.querySelector<HTMLElement>('[data-intent="APPLICATION_RULES"]')
    : document.querySelector<HTMLElement>(item === 'OCCUPATION'
      ? '#occupation-search'
      : item === 'HEALTH' ? '#health-disclosure' : '#material-checklist')
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  target?.focus()
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

onMounted(async () => {
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  await productStore.load()
  await conversationStore.initialize(productStore.context.knowledgeVersion)
})

onUnmounted(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
})

watch(() => conversationStore.conversationId, () => {
  occupationOpen.value = false
  checklistOpen.value = false
  healthOpen.value = false
  applicationOpen.value = false
})
</script>

<template>
  <div class="page-shell">
    <AppHeader
      :product-name="productStore.productShortName"
      :connected="productStore.connected"
      :customer-alias="conversationStore.title"
      @history="historyOpen = true"
      @new-customer="newCustomerOpen = true"
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

      <div v-if="conversationStore.storageMessage" class="service-message storage-message" role="alert">
        <strong>本机历史提示</strong>
        <span>{{ conversationStore.storageMessage }}</span>
        <button type="button" @click="conversationStore.retryStorage">重试</button>
      </div>

      <div v-if="conversationStore.versionOutdated" class="version-update" role="status">
        <strong>正式资料已更新</strong>
        <p>当前历史保留 {{ conversationStore.conversationKnowledgeVersion }}；新查询将使用 {{ productStore.context.knowledgeVersion }}。</p>
      </div>

      <CapabilityGrid :capabilities="productStore.context.capabilities" @select="ask" />
      <OccupationPanel :open="occupationOpen" />
      <ChecklistPanel :open="checklistOpen" />
      <HealthDisclosurePanel :open="healthOpen" @close="healthOpen = false" />
      <ApplicationPanel
        :open="applicationOpen"
        @close="applicationOpen = false"
        @navigate="returnToApplicationCheck"
      />
      <ConversationPanel />
    </main>

    <footer>
      <p>内部开发预览 · 不构成保险建议或承保承诺</p>
    </footer>
    <p class="sr-only" aria-live="polite">{{ uiStore.liveMessage }}</p>
    <HistoryDrawer :open="historyOpen" @close="historyOpen = false" />
    <NewConversationDialog
      :open="newCustomerOpen"
      :error-message="conversationStore.storageMessage"
      @cancel="newCustomerOpen = false"
      @confirm="createCustomer"
    />
  </div>
</template>
