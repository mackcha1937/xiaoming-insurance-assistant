import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import ConversationPanel from '@/components/ConversationPanel.vue'
import { useConversationStore } from '@/stores/conversation'

describe('untrusted conversation rendering', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders user, model and evidence payloads as text instead of executable markup', () => {
    const conversation = useConversationStore()
    const payload = '<img src=x onerror="globalThis.__xss=true"><script>globalThis.__xss=true</script>'
    conversation.messages = [
      { id: 'user-xss', role: 'user', text: payload, createdAt: 1 },
      {
        id: 'assistant-xss',
        role: 'assistant',
        text: payload,
        createdAt: 2,
        response: {
          status: 'SUCCESS',
          intent: 'COVERAGE_RESPONSIBILITY',
          message: payload,
          data: {},
          cards: [{ id: 'card-xss', schemaVersion: '1.0', type: 'SUMMARY', title: payload, data: { answer: payload } }],
          evidence: [{
            evidenceId: 'evidence-xss', documentName: payload, section: payload,
            pdfPage: 1, printedPage: 1, quote: payload, knowledgeVersion: 'knowledge-v1',
          }],
          suggestions: [], actions: [], missingFields: [], knowledgeVersion: 'knowledge-v1',
          traceId: 'trace-xss', requestId: 'request-xss', error: null,
        },
      },
    ]

    const wrapper = mount(ConversationPanel)

    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.html()).toContain('&lt;script&gt;')
    expect(wrapper.text()).toContain('globalThis.__xss=true')
    expect((globalThis as typeof globalThis & { __xss?: boolean }).__xss).toBeUndefined()
  })
})
