import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as api from '@/services/api/client'
import type { ApplicationLinkResult, XiaomingResponse } from '@/services/api/types'
import { useApplicationStore } from '@/stores/application'
import { useConversationStore } from '@/stores/conversation'
import { useProductStore } from '@/stores/product'

const response = (data: ApplicationLinkResult): XiaomingResponse<ApplicationLinkResult> => ({
  status: 'SUCCESS', intent: 'FORMAL_APPLICATION', message: 'ok', data, cards: [], evidence: [],
  suggestions: [], actions: [], missingFields: [], knowledgeVersion: 'knowledge-v1',
  traceId: 'trace-1', requestId: 'request-1', error: null,
})

const link = (): ApplicationLinkResult => ({
  productId: '36743', environment: 'test', url: 'https://apply.example.test/start?productId=36743',
  allowedDomain: 'apply.example.test', expiresAt: new Date(Date.now() + 60_000).toISOString(), notice: 'safe',
})

describe('Phase 07 application preflight store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useProductStore().context = { ...useProductStore().context, knowledgeVersion: 'knowledge-v1' }
    vi.restoreAllMocks()
  })

  it('reports incomplete, ready and stale from current customer structured state', () => {
    const conversation = useConversationStore()
    const application = useApplicationStore()
    expect(application.checkState).toBe('INCOMPLETE')

    conversation.messages.push({
      id: 'assistant-1', role: 'assistant', text: 'ok', createdAt: 1,
      response: { ...response({} as ApplicationLinkResult), intent: 'APPLICATION_RULES', data: {} },
    })
    conversation.context = { confirmedOccupation: { occupationId: '1' } }
    conversation.checklist = {
      conversationId: conversation.conversationId, checklistVersion: 'c1', knowledgeVersion: 'knowledge-v1',
      input: { insuredAgeYears: 30, applicantRelationship: 'SELF', idDocumentType: 'NATIONAL_ID', beneficiaryMode: 'STATUTORY', healthStatus: 'NO_EXCEPTION' },
      items: [{ id: 'required', section: 'READ_CONFIRM', label: '阅读', required: true, reason: '规则', checked: true }],
      notice: 'ok', updatedAt: 1,
    }
    conversation.healthCase = { conversationId: conversation.conversationId, knowledgeVersion: 'knowledge-v1', facts: [], result: null as never, updatedAt: 1 }
    expect(application.checkState).toBe('READY')

    conversation.checklist.knowledgeVersion = 'old-version'
    expect(application.checkState).toBe('STALE')
  })

  it('opens only a fresh server-validated link and handles popup blocking', async () => {
    vi.spyOn(api, 'getApplicationUrl').mockResolvedValue(response(link()))
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    const application = useApplicationStore()

    expect(await application.requestLink()).toBe(true)
    expect(application.status).toBe('CONFIRMING')
    expect(await application.openLink()).toBe(false)
    expect(open).toHaveBeenCalledWith(link().url, '_blank', 'noopener,noreferrer')
    expect(application.status).toBe('BLOCKED')
  })

  it('refuses an expired cached link without opening a window', async () => {
    vi.spyOn(api, 'getApplicationUrl').mockResolvedValue(response({ ...link(), expiresAt: '2000-01-01T00:00:00Z' }))
    const open = vi.spyOn(window, 'open')
    const application = useApplicationStore()
    await application.requestLink()

    expect(await application.openLink()).toBe(false)
    expect(open).not.toHaveBeenCalled()
    expect(application.errorMessage).toContain('已过期')
  })

  it('clears a fetched link immediately when the customer changes', async () => {
    vi.spyOn(api, 'getApplicationUrl').mockResolvedValue(response(link()))
    const conversation = useConversationStore()
    const application = useApplicationStore()
    await application.requestLink()
    expect(application.link).not.toBeNull()

    conversation.conversationId = 'conversation-other-customer'
    await nextTick()

    expect(application.link).toBeNull()
    expect(application.status).toBe('IDLE')
  })
})
