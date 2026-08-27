import { defineStore } from 'pinia'
import { ref } from 'vue'

import { ApiResponseError, prepareChatRequest, type IdempotentChatRequest } from '@/services/api/client'
import type { MissingField, XiaomingResponse } from '@/services/api/types'

export interface ConversationMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  response?: XiaomingResponse<Record<string, unknown>>
}

export const useConversationStore = defineStore('conversation', () => {
  const conversationId = ref(`conversation-${crypto.randomUUID()}`)
  const title = ref('新对话')
  const messageCount = ref(0)
  const messages = ref<ConversationMessage[]>([])
  const pending = ref(false)
  const context = ref<Record<string, unknown>>({})
  const lastFailedOperation = ref<IdempotentChatRequest | null>(null)
  const pendingQuestion = ref<string | null>(null)

  async function send(message: string): Promise<void> {
    const trimmed = message.trim()
    if (trimmed.length === 0 || pending.value) return
    messages.value.push({ id: crypto.randomUUID(), role: 'user', text: trimmed })
    pendingQuestion.value = trimmed
    await execute(prepareChatRequest({
      productId: '36743',
      conversationId: conversationId.value,
      message: trimmed,
      context: context.value,
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
    })
    await execute(prepareChatRequest({
      productId: '36743',
      conversationId: conversationId.value,
      message: pendingQuestion.value,
      context: context.value,
    }))
  }

  async function retry(): Promise<void> {
    if (lastFailedOperation.value === null || pending.value) return
    await execute(lastFailedOperation.value)
  }

  function setContextValue(name: string, value: unknown): void {
    context.value = { ...context.value, [name]: value }
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
      })
      lastFailedOperation.value = null
      if (response.status !== 'NEED_INPUT') pendingQuestion.value = null
      messageCount.value = messages.value.length
    } catch (error) {
      const response = error instanceof ApiResponseError
        ? error.response as XiaomingResponse<Record<string, unknown>> | null
        : null
      const failureMessage: ConversationMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        text: response?.message ?? '请求没有完成，请检查服务连接后重试。',
      }
      if (response !== null) failureMessage.response = response
      messages.value.push(failureMessage)
      lastFailedOperation.value = operation
      messageCount.value = messages.value.length
    } finally {
      pending.value = false
    }
  }

  function reset(): void {
    conversationId.value = `conversation-${crypto.randomUUID()}`
    title.value = '新对话'
    messageCount.value = 0
    messages.value = []
    pending.value = false
    context.value = {}
    lastFailedOperation.value = null
    pendingQuestion.value = null
  }

  return {
    conversationId,
    title,
    messageCount,
    messages,
    pending,
    lastFailedOperation,
    send,
    provideField,
    retry,
    setContextValue,
    reset,
  }
})
