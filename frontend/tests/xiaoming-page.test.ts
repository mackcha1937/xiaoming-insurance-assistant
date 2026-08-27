import axe from 'axe-core'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import XiaomingPage from '@/views/XiaomingPage.vue'
import type { ProductContext, XiaomingResponse } from '@/services/api/types'
import { INTENTS } from '@/services/generated/contracts'

const product: ProductContext = {
  productId: '36743',
  name: '复星联合达尔文12号重大疾病保险（互联网）',
  knowledgeVersion: 'v2.0-baseline',
  environment: 'test',
  authenticationRequired: false,
  accessMode: 'POC_NO_LOGIN',
  capabilities: INTENTS.map(({ code, label }) => ({
    intent: code,
    label,
    enabled: [
      'COVERAGE_RESPONSIBILITY',
      'EXCLUSIONS_AND_LIMITATIONS',
      'APPLICATION_RULES',
      'APPLICATION_PROCESS',
      'CLAIMS_PATH',
    ].includes(code),
  })),
}

const payload: XiaomingResponse<ProductContext> = {
  status: 'SUCCESS',
  intent: null,
  message: 'ready',
  data: product,
  cards: [],
  evidence: [],
  suggestions: [],
  actions: [],
  missingFields: [],
  knowledgeVersion: 'v2.0-baseline',
  traceId: 'trace-0001',
  requestId: 'request-0001',
  error: null,
}

const chatPayload: XiaomingResponse<Record<string, unknown>> = {
  status: 'SUCCESS',
  intent: 'COVERAGE_RESPONSIBILITY',
  message: '已根据当前正式资料生成回答。',
  data: { partial: false },
  cards: [{
    id: 'coverage-card',
    schemaVersion: '1.0',
    type: 'SUMMARY',
    title: '保障责任',
    data: { answer: '根据正式条款，本产品包含约定的重大疾病保险责任。' },
  }],
  evidence: [{
    evidenceId: 'evidence-1',
    documentName: '主险条款.pdf',
    section: '保险责任',
    pdfPage: 3,
    printedPage: 1,
    quote: '被保险人符合约定时，保险人承担相应保险责任。',
    knowledgeVersion: 'knowledge-test',
  }],
  suggestions: [],
  actions: [],
  missingFields: [],
  knowledgeVersion: 'knowledge-test',
  traceId: 'trace-0002',
  requestId: 'request-0002',
  error: null,
}

describe('Xiaoming Phase 03 page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const body = String(input).endsWith('/product-context') ? payload : chatPayload
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }))
  })

  it('shows the passwordless POC boundary and only five enabled capabilities', async () => {
    const wrapper = mount(XiaomingPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('暂不登录')
    expect(wrapper.text()).toContain('邀请码准入')
    expect(wrapper.findAll('.capability-grid button')).toHaveLength(9)
    expect(wrapper.findAll('.capability-grid button:not([disabled])')).toHaveLength(5)
    expect(wrapper.findAll('.capability-grid button[disabled]')).toHaveLength(4)
    expect(wrapper.find('textarea').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
  })

  it('sends an enabled prompt and renders structured evidence', async () => {
    const wrapper = mount(XiaomingPage, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('.capability-grid button:not([disabled])').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('重大疾病保险责任')
    expect(wrapper.find('.answer-card').exists()).toBe(true)
    expect(wrapper.find('.evidence-panel summary').text()).toContain('原文依据')
    expect(wrapper.find('.evidence-panel').text()).toContain('主险条款.pdf')
  })

  it('has no automatically detectable structural accessibility violations', async () => {
    const wrapper = mount(XiaomingPage, { attachTo: document.body })
    await flushPromises()

    const result = await axe.run(wrapper.element, {
      rules: {
        'color-contrast': { enabled: false },
      },
    })
    expect(result.violations).toEqual([])
  })

  it('falls back to the frozen local contract when the backend is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
    const wrapper = mount(XiaomingPage)
    await flushPromises()

    expect(wrapper.text()).toContain('服务连接提示')
    expect(wrapper.text()).toContain('36743')
    expect(wrapper.findAll('.capability-grid button')).toHaveLength(9)
  })
})
