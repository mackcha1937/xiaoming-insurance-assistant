import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ConversationSnapshot, StoredConversation } from '@/db/conversationDb'

const db = vi.hoisted(() => ({
  rows: new Map<string, ConversationSnapshot>(),
  failNextSave: false,
  lastConversationId: null as string | null,
}))

vi.mock('@/db/conversationDb', () => {
  class ConversationStorageError extends Error {
    code: string
    constructor(code: string, message: string) {
      super(message)
      this.code = code
    }
  }

  class ConversationRepository {
    async getLastConversationId(): Promise<string | null> { return db.lastConversationId }
    async setLastConversationId(id: string): Promise<void> { db.lastConversationId = id }
    async list(): Promise<StoredConversation[]> {
      return [...db.rows.values()].map(row => structuredClone(row.conversation))
        .sort((left, right) => right.updatedAt - left.updatedAt)
    }
    async load(id: string): Promise<ConversationSnapshot> {
      const row = db.rows.get(id)
      if (!row) throw new ConversationStorageError('NOT_FOUND', '该历史咨询已不存在。')
      return structuredClone(row)
    }
    async save(snapshot: ConversationSnapshot): Promise<void> {
      if (db.failNextSave) {
        db.failNextSave = false
        throw new ConversationStorageError('STORAGE_FAILED', '模拟保存失败。')
      }
      db.rows.set(snapshot.conversation.id, structuredClone(snapshot))
      db.lastConversationId = snapshot.conversation.id
    }
    async delete(id: string): Promise<void> { db.rows.delete(id) }
    async updateAlias(id: string, alias: string): Promise<void> {
      const row = db.rows.get(id)
      if (!row) throw new ConversationStorageError('NOT_FOUND', '该历史咨询已不存在。')
      row.conversation.alias = alias
      row.conversation.updatedAt = Date.now()
    }
    async clearAll(): Promise<void> {
      db.rows.clear()
      db.lastConversationId = null
    }
    async exportAll(): Promise<string> { return JSON.stringify([...db.rows.values()]) }
  }

  function validateConversationAlias(value: string): string | null {
    const alias = value.trim()
    if (alias.length === 0 || alias.length > 40) return '备注需为 1—40 个字符。'
    if (/\b1\d{10}\b/.test(alias) || /\b\d{17}[\dXx]\b/.test(alias)) {
      return '备注不能包含手机号或身份证号，请改用非敏感称呼。'
    }
    return null
  }

  return { ConversationRepository, ConversationStorageError, validateConversationAlias }
})

import { useConversationStore } from '@/stores/conversation'

describe('conversation history store', () => {
  beforeEach(() => {
    db.rows.clear()
    db.failNextSave = false
    db.lastConversationId = null
    setActivePinia(createPinia())
  })

  it('atomically saves the old customer and restores isolated context', async () => {
    const store = useConversationStore()
    await store.initialize('knowledge-v1')
    const firstId = store.conversationId
    store.setContextValue('occupationId', '10000001')
    await flushPromises()

    expect(await store.createConversation('客户 B')).toBe(true)
    const secondId = store.conversationId
    expect(secondId).not.toBe(firstId)
    expect(store.context).toEqual({})

    expect(await store.switchConversation(firstId)).toBe(true)
    expect(store.context).toEqual({ occupationId: '10000001' })
    expect(await store.switchConversation(secondId)).toBe(true)
    expect(store.context).toEqual({})
  })

  it('does not switch customers when the atomic save fails', async () => {
    const store = useConversationStore()
    await store.initialize('knowledge-v1')
    const originalId = store.conversationId
    db.failNextSave = true

    expect(await store.createConversation('客户 B')).toBe(false)
    expect(store.conversationId).toBe(originalId)
    expect(store.storageMessage).toContain('模拟保存失败')
  })

  it('restores the explicitly active customer after a page reload', async () => {
    const firstStore = useConversationStore()
    await firstStore.initialize('knowledge-v1')
    const firstId = firstStore.conversationId
    expect(await firstStore.createConversation('客户 B')).toBe(true)
    expect(await firstStore.switchConversation(firstId)).toBe(true)

    setActivePinia(createPinia())
    const reloadedStore = useConversationStore()
    await reloadedStore.initialize('knowledge-v1')

    expect(reloadedStore.conversationId).toBe(firstId)
    expect(reloadedStore.title).toBe('未命名客户')
  })

  it('keeps historical answer evidence on its original knowledge version', async () => {
    const id = 'conversation-history-v1'
    db.rows.set(id, {
      conversation: {
        id,
        alias: '历史版本客户',
        productId: '36743',
        knowledgeVersion: 'knowledge-v1',
        context: {},
        createdAt: 1,
        updatedAt: 1,
      },
      messages: [{
        id: 'message-v1',
        conversationId: id,
        role: 'assistant',
        text: '历史回答',
        createdAt: 1,
        response: {
          status: 'SUCCESS',
          intent: 'COVERAGE_RESPONSIBILITY',
          message: '历史回答',
          data: {},
          cards: [],
          evidence: [{
            evidenceId: 'evidence-v1',
            documentName: '历史条款.pdf',
            section: '保险责任',
            pdfPage: 1,
            printedPage: 1,
            quote: '历史版本原文',
            knowledgeVersion: 'knowledge-v1',
          }],
          suggestions: [],
          actions: [],
          missingFields: [],
          knowledgeVersion: 'knowledge-v1',
          traceId: 'trace-v1',
          requestId: 'request-v1',
          error: null,
        },
      }],
      checklist: null,
    })
    db.lastConversationId = id

    const store = useConversationStore()
    await store.initialize('knowledge-v2')

    expect(store.versionOutdated).toBe(true)
    expect(store.messages[0]?.response?.knowledgeVersion).toBe('knowledge-v1')
    expect(store.messages[0]?.response?.evidence[0]?.knowledgeVersion).toBe('knowledge-v1')
  })

  it('rejects sensitive aliases before writing', async () => {
    const store = useConversationStore()
    await store.initialize('knowledge-v1')
    const originalSize = db.rows.size

    expect(await store.createConversation('客户 13812345678')).toBe(false)
    expect(db.rows.size).toBe(originalSize)
    expect(store.storageMessage).toContain('手机号')
  })

  it('rolls back a checklist checkbox when persistence fails', async () => {
    const store = useConversationStore()
    await store.initialize('knowledge-v1')
    expect(await store.saveChecklist({
      checklistVersion: 'checklist-v1',
      knowledgeVersion: 'knowledge-v1',
      items: [{ id: 'item-1', section: 'INPUT_INFORMATION', label: '准备信息', required: true, reason: '测试' }],
      notice: '测试清单',
    }, {
      insuredAgeYears: 30,
      applicantRelationship: 'SELF',
      idDocumentType: 'NATIONAL_ID',
      beneficiaryMode: 'STATUTORY',
      healthStatus: 'NOT_CHECKED',
    })).toBe(true)

    db.failNextSave = true
    expect(await store.toggleChecklistItem('item-1', true)).toBe(false)
    expect(store.checklist?.items[0]?.checked).toBe(false)
  })

  it('deletes the active customer and creates a safe empty conversation', async () => {
    const store = useConversationStore()
    await store.initialize('knowledge-v1')
    const deletedId = store.conversationId

    expect(await store.deleteConversation(deletedId)).toBe(true)
    expect(store.conversationId).not.toBe(deletedId)
    expect(store.messages).toEqual([])
    expect(db.rows.size).toBe(1)
  })
})
