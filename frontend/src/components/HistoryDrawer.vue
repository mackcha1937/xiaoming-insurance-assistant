<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import { focusFirst, trapDialogFocus } from '@/composables/useFocusTrap'
import { useConversationStore } from '@/stores/conversation'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const conversation = useConversationStore()
const search = ref('')
const renameId = ref<string | null>(null)
const renameValue = ref('')
const confirmDeleteId = ref<string | null>(null)
const confirmClear = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)
const drawer = ref<HTMLElement | null>(null)
const confirmationDialog = ref<HTMLElement | null>(null)
let drawerReturnFocus: HTMLElement | null = null
let confirmationReturnFocus: HTMLElement | null = null

const filtered = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  if (!query) return conversation.histories
  return conversation.histories.filter(item => item.alias.toLocaleLowerCase().includes(query))
})

watch(() => props.open, async value => {
  if (!value) {
    await nextTick()
    if (drawerReturnFocus?.isConnected) drawerReturnFocus.focus()
    drawerReturnFocus = null
    return
  }
  drawerReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  search.value = ''
  await nextTick()
  searchInput.value?.focus()
})

watch([confirmDeleteId, confirmClear], async ([deleteId, clear]) => {
  await nextTick()
  if (deleteId !== null || clear) focusFirst(confirmationDialog.value)
  else if (props.open) {
    if (confirmationReturnFocus?.isConnected) confirmationReturnFocus.focus()
    else searchInput.value?.focus()
    confirmationReturnFocus = null
  }
})

async function restore(id: string): Promise<void> {
  if (await conversation.switchConversation(id)) emit('close')
}

function startRename(id: string, alias: string): void {
  renameId.value = id
  renameValue.value = alias
}

async function saveRename(): Promise<void> {
  if (renameId.value === null) return
  if (await conversation.renameConversation(renameId.value, renameValue.value)) renameId.value = null
}

async function remove(): Promise<void> {
  if (confirmDeleteId.value === null) return
  if (await conversation.deleteConversation(confirmDeleteId.value)) confirmDeleteId.value = null
}

async function clearAll(): Promise<void> {
  if (await conversation.clearLocalData()) {
    confirmClear.value = false
    emit('close')
  }
}

async function exportAll(): Promise<void> {
  const content = await conversation.exportLocalData()
  if (content === null) return
  const url = URL.createObjectURL(new Blob([content], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `xiaoming-local-history-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
}

function onKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, drawer.value, () => emit('close'))
}

function closeConfirmation(): void {
  confirmDeleteId.value = null
  confirmClear.value = false
}

function requestDelete(event: MouseEvent, id: string): void {
  confirmationReturnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  confirmDeleteId.value = id
}

function requestClear(event: MouseEvent): void {
  confirmationReturnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  confirmClear.value = true
}

function onConfirmationKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, confirmationDialog.value, closeConfirmation)
}
</script>

<template>
  <div v-if="open" class="drawer-backdrop" @click.self="emit('close')">
    <aside
      ref="drawer"
      class="history-drawer"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="history-title"
      :aria-hidden="confirmDeleteId || confirmClear ? 'true' : undefined"
      :inert="confirmDeleteId || confirmClear ? true : undefined"
      @keydown="onKeydown"
    >
      <div class="drawer-header">
        <div>
          <p class="eyebrow">仅保存在当前浏览器</p>
          <h2 id="history-title">历史咨询</h2>
        </div>
        <button type="button" aria-label="关闭历史咨询" @click="emit('close')">关闭</button>
      </div>
      <p class="shared-device-warning">共享设备可能被其他人看到本机历史；请勿保存真实身份或健康信息。</p>
      <label for="history-search">搜索非敏感备注</label>
      <input id="history-search" ref="searchInput" v-model="search" type="search" autocomplete="off" />

      <ul v-if="filtered.length" class="history-list">
        <li v-for="item in filtered" :key="item.id" :class="{ current: item.id === conversation.conversationId }">
          <template v-if="renameId === item.id">
            <label :for="`rename-${item.id}`">修改备注</label>
            <input :id="`rename-${item.id}`" v-model="renameValue" maxlength="40" autocomplete="off" />
            <div class="compact-actions">
              <button type="button" @click="renameId = null">取消</button>
              <button type="button" @click="saveRename">保存</button>
            </div>
          </template>
          <template v-else>
            <button type="button" class="history-restore" @click="restore(item.id)">
              <strong>{{ item.alias }}</strong>
              <span>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</span>
              <small>{{ item.knowledgeVersion }}{{ item.id === conversation.conversationId ? ' · 当前' : '' }}</small>
            </button>
            <details class="item-menu">
              <summary aria-label="更多历史操作">…</summary>
              <div class="item-menu-popover">
                <button type="button" @click="startRename(item.id, item.alias)">修改备注</button>
                <button type="button" class="danger-text" @click="requestDelete($event, item.id)">删除</button>
              </div>
            </details>
          </template>
        </li>
      </ul>
      <p v-else class="empty-state">没有匹配的本机历史。</p>

      <div v-if="conversation.storageMessage" class="storage-error" role="alert">
        <span>{{ conversation.storageMessage }}</span>
        <button type="button" @click="conversation.retryStorage">重试存储</button>
      </div>

      <div class="history-footer-actions">
        <button type="button" @click="exportAll">导出本机数据</button>
        <button type="button" class="danger-text" @click="requestClear($event)">清空全部本机数据</button>
      </div>
    </aside>
  </div>

  <div v-if="confirmDeleteId" class="modal-backdrop" @click.self="confirmDeleteId = null">
    <section ref="confirmationDialog" class="confirm-dialog" tabindex="-1" role="alertdialog" aria-modal="true" aria-labelledby="delete-history-title" @keydown="onConfirmationKeydown">
      <h2 id="delete-history-title">确认删除这条历史？</h2>
      <p>删除后不可恢复；取消不会改变任何数据。</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="confirmDeleteId = null">取消</button>
        <button type="button" class="danger-action" @click="remove">确认删除</button>
      </div>
    </section>
  </div>

  <div v-if="confirmClear" class="modal-backdrop" @click.self="confirmClear = false">
    <section ref="confirmationDialog" class="confirm-dialog" tabindex="-1" role="alertdialog" aria-modal="true" aria-labelledby="clear-history-title" @keydown="onConfirmationKeydown">
      <h2 id="clear-history-title">清空全部本机数据？</h2>
      <p>所有客户咨询、消息和清单都会删除且不可恢复。建议先导出。</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="confirmClear = false">取消</button>
        <button type="button" class="danger-action" @click="clearAll">确认清空</button>
      </div>
    </section>
  </div>
</template>
