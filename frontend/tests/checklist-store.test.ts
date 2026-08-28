import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ChecklistResult, XiaomingResponse } from '@/services/api/types'
import { useChecklistStore } from '@/stores/checklist'
import { useConversationStore } from '@/stores/conversation'
import { useProductStore } from '@/stores/product'

const result: XiaomingResponse<ChecklistResult> = {
  status: 'SUCCESS',
  intent: 'MATERIAL_CHECKLIST',
  message: '已生成',
  data: {
    checklistVersion: 'material-v1@knowledge-v1',
    knowledgeVersion: 'knowledge-v1',
    items: [
      { id: 'info', section: 'INPUT_INFORMATION', label: '准备信息', required: true, reason: '测试' },
      { id: 'read', section: 'READ_CONFIRM', label: '阅读条款', required: false, reason: '测试' },
    ],
    notice: '以正式页面为准',
  },
  cards: [],
  evidence: [],
  suggestions: [],
  actions: [],
  missingFields: [],
  knowledgeVersion: 'knowledge-v1',
  traceId: 'trace-checklist',
  requestId: 'request-checklist',
  error: null,
}

describe('checklist store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('uses one idempotent operation and saves checked state per conversation', async () => {
    const fetchMock = vi.fn<
      (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
    >(async (_input, _init) => new Response(JSON.stringify(result), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const conversation = useConversationStore()
    const product = useProductStore()
    product.context.knowledgeVersion = 'knowledge-v1'
    vi.spyOn(conversation, 'saveChecklist').mockResolvedValue(true)
    const checklist = useChecklistStore()

    expect(await checklist.generate()).toBe(true)
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.get('Idempotency-Key')).toMatch(/^idem-/)
    expect(conversation.saveChecklist).toHaveBeenCalledOnce()
    expect(checklist.status).toBe('READY')
  })

  it('retries with the same idempotency key after a network failure', async () => {
    const fetchMock = vi.fn<
      (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
    >()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(new Response(JSON.stringify(result), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const conversation = useConversationStore()
    const product = useProductStore()
    product.context.knowledgeVersion = 'knowledge-v1'
    vi.spyOn(conversation, 'saveChecklist').mockResolvedValue(true)
    const checklist = useChecklistStore()

    expect(await checklist.generate()).toBe(false)
    const firstHeaders = new Headers((fetchMock.mock.calls[0]?.[1] as RequestInit).headers)
    expect(await checklist.retry()).toBe(true)
    const secondHeaders = new Headers((fetchMock.mock.calls[1]?.[1] as RequestInit).headers)
    expect(secondHeaders.get('Idempotency-Key')).toBe(firstHeaders.get('Idempotency-Key'))
  })
})
