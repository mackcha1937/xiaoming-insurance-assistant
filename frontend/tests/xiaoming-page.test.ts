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
  capabilities: INTENTS.map(({ code, label }) => ({ intent: code, label, enabled: false })),
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

describe('Xiaoming Phase 01 page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })))
  })

  it('shows the passwordless POC boundary and all nine disabled capabilities', async () => {
    const wrapper = mount(XiaomingPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('暂不登录')
    expect(wrapper.text()).toContain('邀请码准入')
    expect(wrapper.findAll('.capability-grid button')).toHaveLength(9)
    expect(wrapper.findAll('.capability-grid button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.find('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
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

    expect(wrapper.text()).toContain('前端骨架可用')
    expect(wrapper.text()).toContain('36743')
    expect(wrapper.findAll('.capability-grid button')).toHaveLength(9)
  })
})
