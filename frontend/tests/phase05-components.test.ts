import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'

import ChecklistPanel from '@/components/ChecklistPanel.vue'
import NewConversationDialog from '@/components/NewConversationDialog.vue'
import { useConversationStore } from '@/stores/conversation'

describe('Phase 05 interactive components', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('traps keyboard focus and rejects a sensitive customer alias', async () => {
    const wrapper = mount(NewConversationDialog, {
      attachTo: document.body,
      props: { open: false },
    })
    await wrapper.setProps({ open: true })
    await nextTick()

    const input = wrapper.find<HTMLInputElement>('#new-customer-alias')
    expect(document.activeElement).toBe(input.element)
    await wrapper.find('.confirm-dialog').trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement?.textContent).toBe('保存并新建')
    await wrapper.find('.confirm-dialog').trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(input.element)

    await input.setValue('客户13812345678')
    await wrapper.find('.primary-action').trigger('click')
    expect(wrapper.text()).toContain('备注不能包含手机号或身份证号')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    wrapper.unmount()
  })

  it('renders three persisted checklist groups and preserves state when regeneration is cancelled', async () => {
    const conversation = useConversationStore()
    conversation.checklist = {
      conversationId: conversation.conversationId,
      checklistVersion: 'material-checklist-v1@knowledge-v1',
      knowledgeVersion: 'knowledge-v1',
      input: {
        insuredAgeYears: 8,
        applicantRelationship: 'PARENT',
        idDocumentType: 'PASSPORT',
        beneficiaryMode: 'DESIGNATED',
        healthStatus: 'NEEDS_DISCLOSURE',
      },
      items: [
        { id: 'info', section: 'INPUT_INFORMATION', label: '准备基本信息', required: true, reason: '正式页面录入。', checked: true },
        { id: 'material', section: 'PREPARE_MATERIALS', label: '准备关系证明', required: true, reason: '按页面要求核对。', checked: false },
        { id: 'read', section: 'READ_CONFIRM', label: '阅读电子保单说明', required: false, reason: '建议提前阅读。', checked: false },
      ],
      notice: '实际要求以正式投保页面为准。',
      updatedAt: Date.now(),
    }

    const wrapper = mount(ChecklistPanel, { attachTo: document.body, props: { open: true } })
    await nextTick()
    expect(wrapper.findAll('.checklist-group')).toHaveLength(3)
    expect(wrapper.text()).toContain('必需')
    expect(wrapper.text()).toContain('建议')
    expect(wrapper.text()).toContain('实际要求以正式投保页面为准')
    expect(wrapper.findAll('input:checked')).toHaveLength(1)

    const details = wrapper.find<HTMLDetailsElement>('details.checklist-menu')
    details.element.open = true
    await details.find('button').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    await wrapper.find('[role="alertdialog"]').trigger('keydown', { key: 'Escape' })
    await nextTick()
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(conversation.checklist?.items[0]?.checked).toBe(true)
    wrapper.unmount()
  })
})
