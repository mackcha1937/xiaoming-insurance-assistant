import type {
  ChecklistGenerateRequest,
  ChecklistItem,
  XiaomingResponse,
} from '@/services/api/types'

export const DB_NAME = 'xiaoming-assistant-v2'
export const DB_SCHEMA_VERSION = 1

export interface StoredConversation {
  id: string
  alias: string
  productId: '36743'
  knowledgeVersion: string
  context: Record<string, unknown>
  createdAt: number
  updatedAt: number
}

export interface StoredMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  text: string
  createdAt: number
  response?: XiaomingResponse<Record<string, unknown>>
}

export interface StoredChecklistItem extends ChecklistItem {
  checked: boolean
}

export interface StoredChecklist {
  conversationId: string
  checklistVersion: string
  knowledgeVersion: string
  input: Omit<ChecklistGenerateRequest, 'productId' | 'conversationId' | 'knowledgeVersion'>
  items: StoredChecklistItem[]
  notice: string
  updatedAt: number
}

export interface ConversationSnapshot {
  conversation: StoredConversation
  messages: StoredMessage[]
  checklist: StoredChecklist | null
}

export type StorageErrorCode = 'UNAVAILABLE' | 'MIGRATION_FAILED' | 'STORAGE_FAILED' | 'NOT_FOUND'

export class ConversationStorageError extends Error {
  readonly code: StorageErrorCode

  constructor(code: StorageErrorCode, message: string, cause?: unknown) {
    super(message, { cause })
    this.name = 'ConversationStorageError'
    this.code = code
  }
}

const requestResult = <T>(request: IDBRequest<T>): Promise<T> => new Promise((resolve, reject) => {
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

const transactionDone = (transaction: IDBTransaction): Promise<void> => new Promise((resolve, reject) => {
  transaction.oncomplete = () => resolve()
  transaction.onabort = () => reject(transaction.error)
  transaction.onerror = () => reject(transaction.error)
})

export function openConversationDb(): Promise<IDBDatabase> {
  if (!('indexedDB' in globalThis)) {
    return Promise.reject(new ConversationStorageError('UNAVAILABLE', '当前浏览器不支持本机历史存储。'))
  }
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_SCHEMA_VERSION)
    let upgrading = false
    request.onupgradeneeded = () => {
      upgrading = true
      const db = request.result
      try {
        if (!db.objectStoreNames.contains('conversations')) {
          const store = db.createObjectStore('conversations', { keyPath: 'id' })
          store.createIndex('updatedAt', 'updatedAt')
          store.createIndex('alias', 'alias')
        }
        if (!db.objectStoreNames.contains('messages')) {
          const store = db.createObjectStore('messages', { keyPath: 'id' })
          store.createIndex('conversationId', 'conversationId')
          store.createIndex('conversationCreatedAt', ['conversationId', 'createdAt'])
        }
        if (!db.objectStoreNames.contains('checklists')) {
          db.createObjectStore('checklists', { keyPath: 'conversationId' })
        }
        if (!db.objectStoreNames.contains('healthFacts')) {
          db.createObjectStore('healthFacts', { keyPath: 'conversationId' })
        }
        if (!db.objectStoreNames.contains('preferences')) {
          db.createObjectStore('preferences', { keyPath: 'key' })
        }
      } catch (error) {
        request.transaction?.abort()
        reject(new ConversationStorageError(
          'MIGRATION_FAILED',
          '本机历史升级失败，旧数据已保留。请重试；如仍失败，可在“历史咨询”中尝试导出或清空本机数据。',
          error,
        ))
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(new ConversationStorageError(
      upgrading ? 'MIGRATION_FAILED' : 'STORAGE_FAILED',
      upgrading
        ? '本机历史升级失败，旧数据已保留。请重试；如仍失败，可在“历史咨询”中尝试导出或清空本机数据。'
        : '无法打开本机历史。',
      request.error,
    ))
    request.onblocked = () => reject(new ConversationStorageError(
      'MIGRATION_FAILED',
      '其他页面正在使用旧版历史。请关闭其他页面后重试；旧数据不会被静默删除。',
    ))
  })
}

export class ConversationRepository {
  async getLastConversationId(): Promise<string | null> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction('preferences', 'readonly')
      const row = await requestResult<{ key: string; value: unknown } | undefined>(
        transaction.objectStore('preferences').get('lastConversationId'),
      )
      await transactionDone(transaction)
      return typeof row?.value === 'string' ? row.value : null
    } catch (error) {
      throw this.failure('读取当前客户失败。', error)
    } finally {
      db.close()
    }
  }

  async setLastConversationId(id: string): Promise<void> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction('preferences', 'readwrite')
      transaction.objectStore('preferences').put({ key: 'lastConversationId', value: id })
      await transactionDone(transaction)
    } catch (error) {
      throw this.failure('记录当前客户失败，未切换客户。', error)
    } finally {
      db.close()
    }
  }

  async list(): Promise<StoredConversation[]> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction('conversations', 'readonly')
      const rows = await requestResult(transaction.objectStore('conversations').getAll())
      await transactionDone(transaction)
      return rows.sort((left, right) => right.updatedAt - left.updatedAt)
    } catch (error) {
      throw this.failure('读取历史咨询失败。', error)
    } finally {
      db.close()
    }
  }

  async load(id: string): Promise<ConversationSnapshot> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction(['conversations', 'messages', 'checklists'], 'readonly')
      const conversationPromise = requestResult<StoredConversation | undefined>(
        transaction.objectStore('conversations').get(id),
      )
      const messagesPromise = requestResult<StoredMessage[]>(
        transaction.objectStore('messages').index('conversationId').getAll(id),
      )
      const checklistPromise = requestResult<StoredChecklist | undefined>(
        transaction.objectStore('checklists').get(id),
      )
      const [conversation, messages, checklist] = await Promise.all([
        conversationPromise,
        messagesPromise,
        checklistPromise,
      ])
      await transactionDone(transaction)
      if (conversation === undefined) {
        throw new ConversationStorageError('NOT_FOUND', '该历史咨询已不存在。')
      }
      return {
        conversation,
        messages: messages.sort((left, right) => left.createdAt - right.createdAt),
        checklist: checklist ?? null,
      }
    } catch (error) {
      if (error instanceof ConversationStorageError) throw error
      throw this.failure('恢复历史咨询失败。', error)
    } finally {
      db.close()
    }
  }

  async save(snapshot: ConversationSnapshot): Promise<void> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction(
        ['conversations', 'messages', 'checklists', 'preferences'],
        'readwrite',
      )
      transaction.objectStore('conversations').put(snapshot.conversation)
      const messageStore = transaction.objectStore('messages')
      const oldKeys = await requestResult<IDBValidKey[]>(messageStore.index('conversationId').getAllKeys(
        snapshot.conversation.id,
      ))
      oldKeys.forEach(key => messageStore.delete(key))
      snapshot.messages.forEach(message => messageStore.put(message))
      const checklistStore = transaction.objectStore('checklists')
      if (snapshot.checklist === null) checklistStore.delete(snapshot.conversation.id)
      else checklistStore.put(snapshot.checklist)
      transaction.objectStore('preferences').put({
        key: 'lastConversationId',
        value: snapshot.conversation.id,
      })
      await transactionDone(transaction)
    } catch (error) {
      throw this.failure('保存当前咨询失败，未切换客户。', error)
    } finally {
      db.close()
    }
  }

  async delete(id: string): Promise<void> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction(
        ['conversations', 'messages', 'checklists', 'healthFacts'],
        'readwrite',
      )
      transaction.objectStore('conversations').delete(id)
      const messages = transaction.objectStore('messages')
      const keys = await requestResult<IDBValidKey[]>(messages.index('conversationId').getAllKeys(id))
      keys.forEach(key => messages.delete(key))
      transaction.objectStore('checklists').delete(id)
      transaction.objectStore('healthFacts').delete(id)
      await transactionDone(transaction)
    } catch (error) {
      throw this.failure('删除历史咨询失败，原记录仍保留。', error)
    } finally {
      db.close()
    }
  }

  async updateAlias(id: string, alias: string): Promise<void> {
    const db = await openConversationDb()
    try {
      const transaction = db.transaction('conversations', 'readwrite')
      const store = transaction.objectStore('conversations')
      const existing = await requestResult<StoredConversation | undefined>(store.get(id))
      if (existing === undefined) {
        transaction.abort()
        throw new ConversationStorageError('NOT_FOUND', '该历史咨询已不存在。')
      }
      store.put({ ...existing, alias, updatedAt: Date.now() })
      await transactionDone(transaction)
    } catch (error) {
      if (error instanceof ConversationStorageError) throw error
      throw this.failure('修改备注失败，原备注仍保留。', error)
    } finally {
      db.close()
    }
  }

  async clearAll(): Promise<void> {
    const db = await openConversationDb()
    try {
      const names = ['conversations', 'messages', 'checklists', 'healthFacts', 'preferences']
      const transaction = db.transaction(names, 'readwrite')
      names.forEach(name => transaction.objectStore(name).clear())
      await transactionDone(transaction)
    } catch (error) {
      throw this.failure('清空本机历史失败，原数据仍保留。', error)
    } finally {
      db.close()
    }
  }

  async exportAll(): Promise<string> {
    const db = await openConversationDb()
    try {
      const names = ['conversations', 'messages', 'checklists', 'healthFacts', 'preferences']
      const transaction = db.transaction(names, 'readonly')
      const values = await Promise.all(names.map(async name => ({
        name,
        rows: await requestResult(transaction.objectStore(name).getAll()),
      })))
      await transactionDone(transaction)
      return JSON.stringify({ schemaVersion: DB_SCHEMA_VERSION, exportedAt: new Date().toISOString(), values }, null, 2)
    } catch (error) {
      throw this.failure('导出本机历史失败。', error)
    } finally {
      db.close()
    }
  }

  private failure(message: string, cause: unknown): ConversationStorageError {
    return new ConversationStorageError('STORAGE_FAILED', message, cause)
  }
}

export function validateConversationAlias(value: string): string | null {
  const alias = value.trim()
  if (alias.length === 0 || alias.length > 40) return '备注需为 1—40 个字符。'
  if (/\b1\d{10}\b/.test(alias) || /\b\d{17}[\dXx]\b/.test(alias)) {
    return '备注不能包含手机号或身份证号，请改用非敏感称呼。'
  }
  return null
}
