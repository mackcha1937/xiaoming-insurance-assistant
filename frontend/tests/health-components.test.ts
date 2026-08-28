import axe from 'axe-core'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'

import HealthDisclosurePanel from '@/components/HealthDisclosurePanel.vue'
import type { HealthFact } from '@/services/api/types'
import { useHealthStore } from '@/stores/health'

const draft: HealthFact = {
  factId: 'health-fact-0001',
  type: 'DISEASE',
  code: 'HYPERTENSION',
  label: '高血压',
  value: '高血压',
  numericValue: null,
  unit: null,
  date: null,
  polarity: 'PRESENT',
  sourceLocation: {
    sourceId: 'manual-text',
    sourceLabel: '手工文字',
    page: null,
    line: 1,
    excerpt: '高血压',
  },
  confidence: 0.95,
  confirmed: false,
}

describe('Phase 06 health disclosure panel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
  })

  it('requires an explicit consent action before showing health inputs', async () => {
    const wrapper = mount(HealthDisclosurePanel, { attachTo: document.body, props: { open: false } })
    await wrapper.setProps({ open: true })
    await nextTick()

    expect(wrapper.text()).toContain('本次处理授权')
    expect(wrapper.find('.health-text-label').exists()).toBe(false)
    const continueButton = wrapper.find('.health-consent .primary-action')
    expect(continueButton.attributes('disabled')).toBeDefined()
    await wrapper.find('.health-consent input[type="checkbox"]').setValue(true)
    await continueButton.trigger('click')
    expect(wrapper.find('.health-text-label').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders editable unconfirmed facts and traps focus in the discard warning', async () => {
    const wrapper = mount(HealthDisclosurePanel, { attachTo: document.body, props: { open: false } })
    await wrapper.setProps({ open: true })
    await wrapper.find('.health-consent input[type="checkbox"]').setValue(true)
    await wrapper.find('.health-consent .primary-action').trigger('click')
    const health = useHealthStore()
    health.facts = [{ ...draft }]
    health.status = 'REVIEW'
    await nextTick()

    expect(wrapper.findAll('.health-fact-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('0 / 1 已确认')
    await wrapper.find('.health-heading button').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    await wrapper.find('[role="alertdialog"]').trigger('keydown', { key: 'Escape' })
    await nextTick()
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(health.facts).toHaveLength(1)
    wrapper.unmount()
  })

  it('has no automatically detectable structural accessibility violations in the review step', async () => {
    const wrapper = mount(HealthDisclosurePanel, { attachTo: document.body, props: { open: false } })
    await wrapper.setProps({ open: true })
    await wrapper.find('.health-consent input[type="checkbox"]').setValue(true)
    await wrapper.find('.health-consent .primary-action').trigger('click')
    const health = useHealthStore()
    health.facts = [{ ...draft }]
    health.status = 'REVIEW'
    await nextTick()

    const result = await axe.run(wrapper.element, { rules: { 'color-contrast': { enabled: false } } })
    expect(result.violations).toEqual([])
    wrapper.unmount()
  })
})
