<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

import { trapDialogFocus } from '@/composables/useFocusTrap'
import { createPageIsolation } from '@/composables/usePageIsolation'
import { useApplicationStore } from '@/stores/application'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  close: []
  navigate: [item: 'RULES' | 'OCCUPATION' | 'HEALTH' | 'CHECKLIST']
}>()
const application = useApplicationStore()
const dialog = ref<HTMLElement | null>(null)
const acknowledge = ref(false)
let returnFocus: HTMLElement | null = null
const incompleteItems = computed(() => application.items.filter(item => !item.complete))
const isolatePage = createPageIsolation()

watch(() => props.open, value => {
  if (value) application.hydrate()
})

watch(() => application.status, async value => {
  isolatePage(value === 'CONFIRMING' || value === 'BLOCKED')
  if (value !== 'CONFIRMING') return
  acknowledge.value = false
  await nextTick()
  dialog.value?.focus()
})

async function cancel(): Promise<void> {
  const target = returnFocus
  application.cancelConfirmation()
  await nextTick()
  if (target?.isConnected) target.focus()
  returnFocus = null
}

async function openLink(): Promise<void> {
  if (await application.openLink()) {
    returnFocus = null
    emit('close')
  }
}

async function requestLink(event: MouseEvent): Promise<void> {
  returnFocus = event.currentTarget instanceof HTMLElement
    ? event.currentTarget
    : document.activeElement instanceof HTMLElement ? document.activeElement : null
  await application.requestLink()
}

function onKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, dialog.value, () => { void cancel() })
}

onUnmounted(() => isolatePage(false))
</script>

<template>
  <section v-if="open" id="formal-application" class="application-panel" aria-labelledby="application-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">Phase 07 · 外链前确认</p>
        <h2 id="application-title">开始正式投保前检查</h2>
      </div>
      <button type="button" class="secondary-action" @click="emit('close')">关闭</button>
    </div>

    <div class="application-state" :class="`state-${application.checkState.toLowerCase()}`">
      <strong>{{ application.checkState === 'READY' ? '检查已完成' : application.checkState === 'STALE' ? '资料版本已变化' : '仍有未完成项' }}</strong>
      <span>检查仅用于提醒，不代替正式投保页面校验，也不代表承保结论。</span>
    </div>

    <ul class="application-check-list">
      <li v-for="item in application.items" :key="item.id">
        <span aria-hidden="true">{{ item.complete ? '✓' : '!' }}</span>
        <div>
          <strong>{{ item.label }}</strong><small>{{ item.detail }}</small>
          <button v-if="!item.complete" type="button" class="application-return" @click="emit('navigate', item.id)">
            返回完成此项
          </button>
        </div>
      </li>
    </ul>

    <p v-if="application.errorMessage" class="storage-error" role="alert">{{ application.errorMessage }}</p>
    <button type="button" class="primary-action" :disabled="application.status === 'LOADING'" @click="requestLink">
      {{ application.status === 'LOADING' ? '正在获取安全地址…' : '获取正式投保安全地址' }}
    </button>
    <p class="boundary-notice">当前环境未配置批准地址时会明确停止，绝不会自动跳转 UAT。</p>
  </section>

  <Teleport to="body">
    <div v-if="application.status === 'CONFIRMING' || application.status === 'BLOCKED'" class="modal-backdrop" @click.self="cancel">
      <section ref="dialog" class="confirm-dialog application-confirm" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="application-confirm-title" @keydown="onKeydown">
      <p class="eyebrow">外部安全页面</p>
      <h2 id="application-confirm-title">确认离开小明并进入正式投保？</h2>
      <p>当前检查状态：{{ application.checkState }}。正式页面将重新校验全部信息；小明不会代填、支付或判断投保成功。</p>
      <div v-if="incompleteItems.length" class="application-incomplete" role="status">
        <strong>尚未完成：</strong>
        <span>{{ incompleteItems.map(item => item.label).join('、') }}</span>
      </div>
      <p v-if="application.link"><strong>批准域名：</strong>{{ application.link.allowedDomain }}<br /><strong>有效至：</strong>{{ new Date(application.link.expiresAt).toLocaleTimeString() }}</p>
      <label class="application-acknowledge"><input v-model="acknowledge" type="checkbox" />我已了解并确认进入外部正式页面</label>
      <p v-if="application.blocked" class="inline-error" role="alert">{{ application.errorMessage }}</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="cancel">返回检查</button>
        <button v-if="application.blocked" type="button" class="secondary-action" @click="application.copyLink">复制安全地址</button>
        <button type="button" class="primary-action" :disabled="!acknowledge" @click="openLink">仍然进入</button>
      </div>
      </section>
    </div>
  </Teleport>
</template>
