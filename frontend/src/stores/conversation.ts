import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  ConversationRepository,
  ConversationStorageError,
  type ConversationSnapshot,
  type StoredChecklist,
  type StoredChecklistItem,
  type StoredConversation,
  type StoredMessage,
  validateConversationAlias,
} from '@/db/conversationDb'
import { ApiResponseError, prepareChatRequest, type IdempotentChatRequest } from '@/services/api/client'
import type {
  ChecklistGenerateRequest,
  ChecklistResult,
  MissingField,
  XiaomingResponse,
} from '@/services/api/types'

export interface ConversationMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  createdAt: number
  response?: XiaomingResponse<Record<string, unknown>>
}

const repository = new ConversationRepository()
const newId = (): string => `conversation-${crypto.randomUUID()}`
const clonePersistable = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

export const useConversationStore = defineStore('conversation', () => {
  const conversationId = ref(newId())
  const title = ref('未命名客户')
  const messageCount = ref(0)
  const messages = ref<ConversationMessage[]>([])
  const pending = ref(false)
  const context = ref<Record<string, unknown>>({})
  const lastFailedOperation = ref<IdempotentChatRequest | null>(null)
  const pendingQuestion = ref<string | null>(null)
  const histories = ref<StoredConversation[]>([])
  const checklist = ref<StoredChecklist | null>(null)
  const initialized = ref(false)
  const storageStatus = ref<'IDLE' | 'LOADING' | 'READY' | 'ERROR'>('IDLE')
  const storageMessage = ref('')
  const conversationKnowledgeVersion = ref('')
  const runtimeKnowledgeVersion = ref('')

  const versionOutdated = computed(() => (
    conversationKnowledgeVersion.value.length > 0
    && runtimeKnowledgeVersion.value.length > 0
    && conversationKnowledgeVersion.value !== runtimeKnowledgeVersion.value
  ))

  async function initialize(knowledgeVersion: string): Promise<void> {
    runtimeKnowledgeVersion.value = knowledgeVersion
    if (initialized.value || storageStatus.value === 'LOADING') return
    storageStatus.value = 'LOADING'
    storageMessage.value = ''
    try {
      histories.value = await repository.list()
      if (histories.value.length === 0) {
        applyEmpty('未命名客户', knowledgeVersion)
        await persistCurrent()
      } else {
        const lastConversationId = await repository.getLastConversationId()
        const restoreId = lastConversationId !== null
          && histories.value.some(item => item.id === lastConversationId)
          ? lastConversationId
          : histories.value[0]!.id
        await load(restoreId)
      }
      initialized.value = true
      storageStatus.value = 'READY'
    } catch (error) {
      handleStorageError(error)
    }
  }

  async function send(message: string): Promise<void> {
    const trimmed = message.trim()
    if (trimmed.length === 0 || pending.value) return
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'user',
      text: trimmed,
      createdAt: Date.now(),
    })
    pendingQuestion.value = trimmed
    await persistWithoutThrow()
    await execute(prepareChatRequest({
      productId: '36743',
      conversationId: conversationId.value,
      message: trimmed,
      context: context.value,
      knowledgeVersionHint: runtimeKnowledgeVersion.value,
    }))
  }

  async function provideField(field: MissingField, rawValue: string): Promise<void> {
    if (pending.value || pendingQuestion.value === null) return
    const value = field.inputType === 'number' ? Number(rawValue) : rawValue.trim()
    if (value === '' || (typeof value === 'number' && !Number.isFinite(value))) return
    context.value = { ...context.value, [field.name]: value }
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'user',
      text: `${field.label}：${rawValue}`,
      createdAt: Date.now(),
    })
    await execute(prepareChatRequest({
      productId: '36743',
      conversationId: conversationId.value,
      message: pendingQuestion.value,
      context: context.value,
      knowledgeVersionHint: runtimeKnowledgeVersion.value,
    }))
  }

  async function retry(): Promise<void> {
    if (lastFailedOperation.value === null || pending.value) return
    await execute(lastFailedOperation.value)
  }

  function setContextValue(name: string, value: unknown): void {
    context.value = { ...context.value, [name]: value }
    void persistWithoutThrow()
  }

  async function execute(operation: IdempotentChatRequest): Promise<void> {
    pending.value = true
    try {
      const response = await operation.execute()
      messages.value.push({
        id: crypto.randomUUID(),
        role: 'assistant',
        text: response.message,
        response,
        createdAt: Date.now(),
      })
      conversationKnowledgeVersion.value = response.knowledgeVersion || conversationKnowledgeVersion.value
      lastFailedOperation.value = null
      if (response.status !== 'NEED_INPUT') pendingQuestion.value = null
    } catch (error) {
      const response = error instanceof ApiResponseError
        ? error.response as XiaomingResponse<Record<string, unknown>> | null
        : null
      const failureMessage: ConversationMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        text: response?.message ?? '请求没有完成，请检查服务连接后重试。',
        createdAt: Date.now(),
      }
      if (response !== null) failureMessage.response = response
      messages.value.push(failureMessage)
      lastFailedOperation.value = operation
    } finally {
      messageCount.value = messages.value.length
      pending.value = false
      await persistWithoutThrow()
    }
  }

  async function createConversation(alias: string): Promise<boolean> {
    const validation = validateConversationAlias(alias)
    if (validation !== null) {
      storageStatus.value = 'ERROR'
      storageMessage.value = validation
      return false
    }
    const previous = currentSnapshot()
    try {
      await persistCurrent()
      applyEmpty(alias.trim(), runtimeKnowledgeVersion.value)
      await persistCurrent()
      await refreshHistories()
      storageStatus.value = 'READY'
      storageMessage.value = ''
      return true
    } catch (error) {
      applySnapshot(previous)
      handleStorageError(error)
      return false
    }
  }

  async function switchConversation(id: string): Promise<boolean> {
    if (id === conversationId.value) return true
    try {
      await persistCurrent()
      await load(id)
      storageStatus.value = 'READY'
      storageMessage.value = ''
      return true
    } catch (error) {
      handleStorageError(error)
      return false
    }
  }

  async function renameConversation(id: string, alias: string): Promise<boolean> {
    const validation = validateConversationAlias(alias)
    if (validation !== null) {
      storageStatus.value = 'ERROR'
      storageMessage.value = validation
      return false
    }
    try {
      if (id === conversationId.value) {
        title.value = alias.trim()
        await persistCurrent()
      } else {
        await repository.updateAlias(id, alias.trim())
      }
      await refreshHistories()
      storageStatus.value = 'READY'
      storageMessage.value = ''
      return true
    } catch (error) {
      handleStorageError(error)
      return false
    }
  }

  async function deleteConversation(id: string): Promise<boolean> {
    try {
      if (id !== conversationId.value) await persistCurrent()
      await repository.delete(id)
      const remaining = await repository.list()
      if (id === conversationId.value) {
        if (remaining.length > 0) await load(remaining[0]!.id)
        else {
          applyEmpty('未命名客户', runtimeKnowledgeVersion.value)
          await persistCurrent()
        }
      }
      await refreshHistories()
      storageStatus.value = 'READY'
      storageMessage.value = ''
      return true
    } catch (error) {
      handleStorageError(error)
      return false
    }
  }

  async function saveChecklist(
    result: ChecklistResult,
    input: Omit<ChecklistGenerateRequest, 'productId' | 'conversationId' | 'knowledgeVersion'>,
  ): Promise<boolean> {
    const previous = checklist.value
    checklist.value = {
      conversationId: conversationId.value,
      checklistVersion: result.checklistVersion,
      knowledgeVersion: result.knowledgeVersion,
      input,
      items: result.items.map(item => ({ ...item, checked: false })),
      notice: result.notice,
      updatedAt: Date.now(),
    }
    conversationKnowledgeVersion.value = result.knowledgeVersion
    if (await persistWithoutThrow()) return true
    checklist.value = previous
    return false
  }

  async function toggleChecklistItem(id: string, checked: boolean): Promise<boolean> {
    if (checklist.value === null) return false
    const item = checklist.value.items.find(candidate => candidate.id === id)
    if (item === undefined) return false
    const previous = item.checked
    item.checked = checked
    checklist.value.updatedAt = Date.now()
    if (await persistWithoutThrow()) return true
    item.checked = previous
    return false
  }

  async function clearLocalData(): Promise<boolean> {
    try {
      await repository.clearAll()
      applyEmpty('未命名客户', runtimeKnowledgeVersion.value)
      await persistCurrent()
      await refreshHistories()
      storageStatus.value = 'READY'
      storageMessage.value = ''
      return true
    } catch (error) {
      handleStorageError(error)
      return false
    }
  }

  async function exportLocalData(): Promise<string | null> {
    try {
      return await repository.exportAll()
    } catch (error) {
      handleStorageError(error)
      return null
    }
  }

  async function retryStorage(): Promise<void> {
    storageStatus.value = 'IDLE'
    initialized.value = false
    await initialize(runtimeKnowledgeVersion.value)
  }

  function clearStorageMessage(): void {
    if (storageStatus.value !== 'LOADING') storageMessage.value = ''
  }

  function reset(): void {
    applyEmpty('未命名客户', runtimeKnowledgeVersion.value)
  }

  function currentSnapshot(): ConversationSnapshot {
    const now = Date.now()
    const existing = histories.value.find(item => item.id === conversationId.value)
    const conversation: StoredConversation = {
      id: conversationId.value,
      alias: title.value,
      productId: '36743',
      knowledgeVersion: conversationKnowledgeVersion.value || runtimeKnowledgeVersion.value,
      context: clonePersistable(context.value),
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    }
    const storedMessages: StoredMessage[] = messages.value.map(message => {
      const stored: StoredMessage = {
        id: message.id,
        conversationId: conversationId.value,
        role: message.role,
        text: message.text,
        createdAt: message.createdAt,
      }
      if (message.response !== undefined) stored.response = clonePersistable(message.response)
      return stored
    })
    return {
      conversation,
      messages: storedMessages,
      checklist: checklist.value === null ? null : clonePersistable(checklist.value),
    }
  }

  async function persistCurrent(): Promise<void> {
    await repository.save(currentSnapshot())
  }

  async function persistWithoutThrow(): Promise<boolean> {
    if (!initialized.value && storageStatus.value !== 'LOADING') return false
    try {
      await persistCurrent()
      await refreshHistories()
      storageStatus.value = 'READY'
      storageMessage.value = ''
      return true
    } catch (error) {
      handleStorageError(error)
      return false
    }
  }

  async function refreshHistories(): Promise<void> {
    histories.value = await repository.list()
  }

  async function load(id: string): Promise<void> {
    const snapshot = await repository.load(id)
    await repository.setLastConversationId(id)
    applySnapshot(snapshot)
    await refreshHistories()
  }

  function applySnapshot(snapshot: ConversationSnapshot): void {
    conversationId.value = snapshot.conversation.id
    title.value = snapshot.conversation.alias
    conversationKnowledgeVersion.value = snapshot.conversation.knowledgeVersion
    context.value = clonePersistable(snapshot.conversation.context)
    messages.value = snapshot.messages.map(message => {
      const restored: ConversationMessage = {
        id: message.id,
        role: message.role,
        text: message.text,
        createdAt: message.createdAt,
      }
      if (message.response !== undefined) restored.response = clonePersistable(message.response)
      return restored
    })
    messageCount.value = messages.value.length
    checklist.value = snapshot.checklist === null ? null : clonePersistable(snapshot.checklist)
    pending.value = false
    lastFailedOperation.value = null
    pendingQuestion.value = null
  }

  function applyEmpty(alias: string, knowledgeVersion: string): void {
    conversationId.value = newId()
    title.value = alias
    conversationKnowledgeVersion.value = knowledgeVersion
    messages.value = []
    messageCount.value = 0
    context.value = {}
    checklist.value = null
    pending.value = false
    lastFailedOperation.value = null
    pendingQuestion.value = null
  }

  function handleStorageError(error: unknown): void {
    storageStatus.value = 'ERROR'
    storageMessage.value = error instanceof ConversationStorageError
      ? error.message
      : '本机历史操作失败，当前咨询仍保留在页面中。'
  }

  return {
    conversationId,
    title,
    messageCount,
    messages,
    pending,
    context,
    lastFailedOperation,
    histories,
    checklist,
    initialized,
    storageStatus,
    storageMessage,
    conversationKnowledgeVersion,
    versionOutdated,
    initialize,
    send,
    provideField,
    retry,
    setContextValue,
    createConversation,
    switchConversation,
    renameConversation,
    deleteConversation,
    saveChecklist,
    toggleChecklistItem,
    clearLocalData,
    exportLocalData,
    retryStorage,
    clearStorageMessage,
    reset,
  }
})
