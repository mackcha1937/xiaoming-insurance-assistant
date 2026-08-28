import axe from 'axe-core'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import XiaomingPage from '@/views/XiaomingPage.vue'
import type {
  OccupationConfirmationResult,
  OccupationSearchResult,
  ProductContext,
  XiaomingResponse,
} from '@/services/api/types'
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
      'OCCUPATION_QUERY',
      'APPLICATION_PROCESS',
      'MATERIAL_CHECKLIST',
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

const occupationSearchPayload: XiaomingResponse<OccupationSearchResult> = {
  status: 'SUCCESS',
  intent: 'OCCUPATION_QUERY',
  message: '找到一个待确认职业。',
  data: {
    queryToken: 'query-token-from-request',
    candidates: [{
      occupationId: '1963061194759143433',
      name: '计算机程序设计员',
      path: ['计算机与互联网业人员', '软件和信息技术服务人员', '计算机程序设计员'],
    }],
    requiresMoreDetail: false,
    prompt: '找到一个待确认职业。确认前不会显示职业类别或可承保结论。',
    knowledgeVersion: 'knowledge-test',
    occupationVersion: 'occupation-test',
  },
  cards: [],
  evidence: [],
  suggestions: [],
  actions: [],
  missingFields: [],
  knowledgeVersion: 'knowledge-test',
  traceId: 'trace-occupation-search',
  requestId: 'request-occupation-search',
  error: null,
}

const occupationConfirmPayload: XiaomingResponse<OccupationConfirmationResult> = {
  status: 'SUCCESS',
  intent: 'OCCUPATION_QUERY',
  message: '已按当前职业表精确确认。',
  data: {
    matched: true,
    occupationId: '1963061194759143433',
    name: '计算机程序设计员',
    category: 1,
    path: ['计算机与互联网业人员', '软件和信息技术服务人员', '计算机程序设计员'],
    knowledgeVersion: 'knowledge-test',
    occupationVersion: 'occupation-test',
    notice: '该结果不代表保险公司最终承保结论。',
  },
  cards: [],
  evidence: [],
  suggestions: [],
  actions: [],
  missingFields: [],
  knowledgeVersion: 'knowledge-test',
  traceId: 'trace-occupation-confirm',
  requestId: 'request-occupation-confirm',
  error: null,
}

describe('Xiaoming Phase 05 page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      let body: unknown = chatPayload
      if (url.endsWith('/product-context')) body = payload
      if (url.includes('/occupations/search')) {
        const token = new URL(url, 'http://localhost').searchParams.get('queryToken') ?? ''
        body = {
          ...occupationSearchPayload,
          data: { ...occupationSearchPayload.data!, queryToken: token },
        }
      }
      if (url.endsWith('/occupations/confirm') && init?.method === 'POST') body = occupationConfirmPayload
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }))
  })

  it('shows the passwordless POC boundary and seven enabled capabilities', async () => {
    const wrapper = mount(XiaomingPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('暂不登录')
    expect(wrapper.text()).toContain('邀请码准入')
    expect(wrapper.findAll('.capability-grid button')).toHaveLength(9)
    expect(wrapper.findAll('.capability-grid button:not([disabled])')).toHaveLength(7)
    expect(wrapper.findAll('.capability-grid button[disabled]')).toHaveLength(2)
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

  it('requires candidate selection before revealing an occupation category', async () => {
    const wrapper = mount(XiaomingPage, { attachTo: document.body })
    await flushPromises()

    const occupationButton = wrapper.findAll('.capability-grid button')
      .find(button => button.text().includes('职业查询'))
    expect(occupationButton).toBeDefined()
    await occupationButton!.trigger('click')

    expect(wrapper.find('.occupation-panel').exists()).toBe(true)
    await wrapper.find('#occupation-query').setValue('程序员')
    await wrapper.find('.occupation-search-form').trigger('submit')
    await flushPromises()

    expect(wrapper.findAll('.occupation-candidates label')).toHaveLength(1)
    expect(wrapper.find('.occupation-candidates').text()).not.toContain('1 类')
    expect(wrapper.find('.primary-action').attributes('disabled')).toBeDefined()

    await wrapper.find('.occupation-candidates input').setValue(true)
    expect(wrapper.find('.primary-action').attributes('disabled')).toBeUndefined()
    await wrapper.find('.primary-action').trigger('click')
    await flushPromises()

    expect(wrapper.find('.occupation-result').text()).toContain('1 类')
    expect(wrapper.find('.occupation-result').text()).toContain('occupation-test')
    expect(wrapper.find('.occupation-result').text()).toContain('不代表保险公司最终承保结论')
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
