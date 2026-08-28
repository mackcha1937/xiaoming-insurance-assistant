import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

import { ApiResponseError, prepareFeedback, type IdempotentFeedbackRequest } from '@/services/api/client'
import type { FeedbackReason, XiaomingResponse } from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'
import { useProductStore } from '@/stores/product'

export type FeedbackStatus = 'IDLE' | 'SUBMITTING' | 'SUCCESS' | 'ERROR'

export const useFeedbackStore = defineStore('feedback', () => {
  const conversation = useConversationStore()
  const product = useProductStore()
  const status = ref<FeedbackStatus>('IDLE')
  const errorMessage = ref('')
  const lastOperation = ref<IdempotentFeedbackRequest | null>(null)

  async function submit(
    response: XiaomingResponse<Record<string, unknown>>,
    question: string,
    answer: string,
    reason: FeedbackReason,
    includeContext: boolean,
  ): Promise<boolean> {
    if (response.intent === null) return false
    const allowContext = includeContext && response.intent !== 'HEALTH_DISCLOSURE'
    const operation = prepareFeedback({
      productId: '36743',
      conversationId: conversation.conversationId,
      knowledgeVersion: response.knowledgeVersion || product.context.knowledgeVersion,
      intent: response.intent,
      reason,
      includeContext: allowContext,
      ...(allowContext ? { questionSummary: question.slice(0, 240), answerSummary: answer.slice(0, 500) } : {}),
      evidenceIds: response.evidence.slice(0, 10).map(item => item.evidenceId),
    })
    lastOperation.value = operation
    return execute(operation)
  }

  async function retry(): Promise<boolean> {
    return lastOperation.value === null ? false : execute(lastOperation.value)
  }

  async function execute(operation: IdempotentFeedbackRequest): Promise<boolean> {
    status.value = 'SUBMITTING'
    errorMessage.value = ''
    try {
      const response = await operation.execute()
      if (response.data === null) throw new Error('empty feedback response')
      status.value = 'SUCCESS'
      lastOperation.value = null
      return true
    } catch (error) {
      status.value = 'ERROR'
      errorMessage.value = error instanceof ApiResponseError
        ? error.response?.message ?? '反馈提交失败，请使用同一请求标识重试。'
        : '反馈提交失败，请检查连接后重试。'
      return false
    }
  }

  function reset(): void {
    status.value = 'IDLE'
    errorMessage.value = ''
    lastOperation.value = null
  }

  watch(() => conversation.conversationId, reset)

  return { status, errorMessage, lastOperation, submit, retry, reset }
})
