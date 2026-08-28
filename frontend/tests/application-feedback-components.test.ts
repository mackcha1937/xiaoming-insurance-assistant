import axe from 'axe-core'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ApplicationPanel from '@/components/ApplicationPanel.vue'
import FeedbackDialog from '@/components/FeedbackDialog.vue'
import * as api from '@/services/api/client'
import type { ApplicationLinkResult, XiaomingResponse } from '@/services/api/types'

const applicationLink: ApplicationLinkResult = {
  productId: '36743', environment: 'test', url: 'https://apply.example.test/start?productId=36743',
  allowedDomain: 'apply.example.test', expiresAt: new Date(Date.now() + 60_000).toISOString(), notice: 'safe',
}
const linkResponse: XiaomingResponse<ApplicationLinkResult> = {
  status: 'SUCCESS', intent: 'FORMAL_APPLICATION', message: 'ok', data: applicationLink,
  cards: [], evidence: [], suggestions: [], actions: [], missingFields: [], knowledgeVersion: 'knowledge-v1',
  traceId: 'trace-1', requestId: 'request-1', error: null,
}
const answer: XiaomingResponse<Record<string, unknown>> = {
  status: 'SUCCESS', intent: 'APPLICATION_RULES', message: 'ok', data: {}, cards: [], evidence: [],
  suggestions: [], actions: [], missingFields: [], knowledgeVersion: 'knowledge-v1',
  traceId: 'trace-1', requestId: 'request-1', error: null,
}

describe('Phase 07 application and feedback components', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('requires explicit acknowledgement and returns focus when application confirmation is cancelled', async () => {
    vi.spyOn(api, 'getApplicationUrl').mockResolvedValue(linkResponse)
    const page = document.createElement('div')
    page.className = 'page-shell'
    document.body.append(page)
    const wrapper = mount(ApplicationPanel, { attachTo: document.body, props: { open: true } })
    const trigger = wrapper.find('.application-panel > .primary-action')
    ;(trigger.element as HTMLElement).focus()
    await trigger.trigger('click')
    await flushPromises()

    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
    expect(page.hasAttribute('inert')).toBe(true)
    const confirmButton = document.querySelector<HTMLButtonElement>('.application-confirm .primary-action')!
    expect(confirmButton.disabled).toBe(true)
    document.querySelector<HTMLInputElement>('.application-acknowledge input')!.click()
    await nextTick()
    expect(confirmButton.disabled).toBe(false)
    document.querySelector<HTMLElement>('[role="dialog"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(page.hasAttribute('inert')).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
    wrapper.unmount()
    page.remove()
  })

  it('keeps health feedback context opt-in disabled', async () => {
    const wrapper = mount(FeedbackDialog, {
      attachTo: document.body,
      props: { open: true, response: { ...answer, intent: 'HEALTH_DISCLOSURE' }, question: '问题', answer: '回答' },
    })
    expect(document.querySelector<HTMLInputElement>('.feedback-dialog .application-acknowledge input')?.disabled).toBe(true)
    expect(document.querySelector('.feedback-dialog')?.textContent).toContain('健康告知反馈仅提交原因和引用 ID')
    wrapper.unmount()
  })

  it('has no structural accessibility violations in both dialogs', async () => {
    vi.spyOn(api, 'getApplicationUrl').mockResolvedValue(linkResponse)
    const application = mount(ApplicationPanel, { attachTo: document.body, props: { open: true } })
    await application.find('.application-panel > .primary-action').trigger('click')
    await flushPromises()
    expect((await axe.run(document.querySelector('.application-confirm')!, { rules: { 'color-contrast': { enabled: false } } })).violations).toEqual([])
    application.unmount()

    const feedback = mount(FeedbackDialog, {
      attachTo: document.body,
      props: { open: true, response: answer, question: '问题', answer: '回答' },
    })
    await nextTick()
    expect((await axe.run(document.querySelector('.feedback-dialog')!, { rules: { 'color-contrast': { enabled: false } } })).violations).toEqual([])
    feedback.unmount()
  })
})
