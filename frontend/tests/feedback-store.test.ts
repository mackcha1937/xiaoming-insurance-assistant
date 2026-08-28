import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as api from '@/services/api/client'
import type { FeedbackRequest, FeedbackResult, XiaomingResponse } from '@/services/api/types'
import { useFeedbackStore } from '@/stores/feedback'
import { useConversationStore } from '@/stores/conversation'

const accepted: XiaomingResponse<FeedbackResult> = {
  status: 'SUCCESS', intent: 'APPLICATION_RULES', message: 'accepted',
  data: { feedbackId: 'feedback-1', status: 'ACCEPTED', acceptedAt: '2026-08-28T00:00:00Z' },
  cards: [], evidence: [], suggestions: [], actions: [], missingFields: [],
  knowledgeVersion: 'knowledge-v1', traceId: 'trace-1', requestId: 'request-1', error: null,
}

const answer = (intent: XiaomingResponse<Record<string, unknown>>['intent']): XiaomingResponse<Record<string, unknown>> => ({
  status: 'SUCCESS', intent, message: 'ok', data: {}, cards: [],
  evidence: [{ evidenceId: 'evidence-1', documentName: 'doc.pdf', section: '规则', pdfPage: 1, printedPage: null, quote: '依据', knowledgeVersion: 'knowledge-v1' }],
  suggestions: [], actions: [], missingFields: [], knowledgeVersion: 'knowledge-v1',
  traceId: 'trace-1', requestId: 'request-1', error: null,
})

describe('Phase 07 feedback store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('excludes question and answer content by default', async () => {
    let captured: FeedbackRequest | null = null
    vi.spyOn(api, 'prepareFeedback').mockImplementation(payload => {
      captured = payload
      return { identity: { requestId: 'request-fixed', idempotencyKey: 'idem-fixed' }, execute: vi.fn().mockResolvedValue(accepted) }
    })

    await useFeedbackStore().submit(answer('APPLICATION_RULES'), '投保年龄？', '年龄范围', 'UNCLEAR', false)

    expect(captured).toMatchObject({ includeContext: false, reason: 'UNCLEAR', evidenceIds: ['evidence-1'] })
    expect(captured).not.toHaveProperty('questionSummary')
    expect(captured).not.toHaveProperty('answerSummary')
  })

  it('never includes context for health feedback even when requested', async () => {
    let captured: FeedbackRequest | null = null
    vi.spyOn(api, 'prepareFeedback').mockImplementation(payload => {
      captured = payload
      return { identity: { requestId: 'request-health', idempotencyKey: 'idem-health' }, execute: vi.fn().mockResolvedValue(accepted) }
    })

    await useFeedbackStore().submit(answer('HEALTH_DISCLOSURE'), '高血压', '健康回答', 'EVIDENCE_MISMATCH', true)

    expect(captured).toMatchObject({ intent: 'HEALTH_DISCLOSURE', includeContext: false })
    expect(captured).not.toHaveProperty('questionSummary')
    expect(captured).not.toHaveProperty('answerSummary')
  })

  it('retries the same operation and idempotency identity after failure', async () => {
    const execute = vi.fn()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(accepted)
    const prepare = vi.spyOn(api, 'prepareFeedback').mockReturnValue({
      identity: { requestId: 'request-retry', idempotencyKey: 'idem-retry' }, execute,
    })
    const feedback = useFeedbackStore()

    expect(await feedback.submit(answer('APPLICATION_RULES'), '', '', 'OUTDATED', false)).toBe(false)
    expect(feedback.lastOperation?.identity.idempotencyKey).toBe('idem-retry')
    expect(await feedback.retry()).toBe(true)
    expect(prepare).toHaveBeenCalledTimes(1)
    expect(execute).toHaveBeenCalledTimes(2)
  })

  it('discards a pending retry when the customer changes', async () => {
    vi.spyOn(api, 'prepareFeedback').mockReturnValue({
      identity: { requestId: 'request-customer-a', idempotencyKey: 'idem-customer-a' },
      execute: vi.fn().mockRejectedValue(new TypeError('offline')),
    })
    const feedback = useFeedbackStore()
    const conversation = useConversationStore()
    await feedback.submit(answer('APPLICATION_RULES'), '', '', 'UNCLEAR', false)
    expect(feedback.lastOperation).not.toBeNull()

    conversation.conversationId = 'conversation-customer-b'
    await nextTick()

    expect(feedback.lastOperation).toBeNull()
    expect(feedback.status).toBe('IDLE')
  })
})
