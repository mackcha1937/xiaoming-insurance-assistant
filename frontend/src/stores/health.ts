import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  ApiResponseError,
  prepareHealthExtraction,
  prepareHealthMatch,
  type IdempotentHealthExtractionRequest,
  type IdempotentHealthMatchRequest,
} from '@/services/api/client'
import type {
  HealthFact,
  HealthMatchResult,
  HealthFactPolarity,
  InsuredSex,
} from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'
import { useProductStore } from '@/stores/product'
import { generateUuid } from '@/utils/uuid'

const MAX_FILES = 5
const MAX_FILE_BYTES = 10 * 1024 * 1024
const UPLOAD_MARKER = 'xiaoming-health-upload-active'
const ALLOWED_TYPES = new Set(['text/plain', 'application/pdf', 'image/jpeg', 'image/png'])

export type HealthStatus = 'IDLE' | 'UPLOADING' | 'REVIEW' | 'MATCHING' | 'RESULT' | 'ERROR'
export type LocalFileStatus = 'QUEUED' | 'UPLOADING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

export interface LocalHealthFile {
  id: string
  label: string
  size: number
  status: LocalFileStatus
  progress: number
  message: string
  file: File
  operation: IdempotentHealthExtractionRequest | null
}

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

export const useHealthStore = defineStore('health', () => {
  const conversation = useConversationStore()
  const product = useProductStore()
  const consented = ref(false)
  const status = ref<HealthStatus>('IDLE')
  const manualText = ref('')
  const files = ref<LocalHealthFile[]>([])
  const facts = ref<HealthFact[]>([])
  const result = ref<HealthMatchResult | null>(null)
  const insuredAgeYears = ref<number | null>(null)
  const insuredSex = ref<InsuredSex>('UNSPECIFIED')
  const errorMessage = ref('')
  const interruptionMessage = ref(readInterruptedMessage())
  const lastExtraction = ref<IdempotentHealthExtractionRequest | null>(null)
  const lastTextValue = ref('')
  const lastMatch = ref<IdempotentHealthMatchRequest | null>(null)
  const controllers = new Map<string, AbortController>()

  const confirmedCount = computed(() => facts.value.filter(fact => fact.confirmed).length)
  const canMatch = computed(() => facts.value.length > 0 && confirmedCount.value === facts.value.length)
  const hasUnsavedWork = computed(() => status.value === 'UPLOADING'
    || files.value.length > 0
    || facts.value.some(fact => !fact.confirmed))

  function hydrate(): void {
    cancelAll()
    consented.value = false
    manualText.value = ''
    files.value = []
    errorMessage.value = ''
    lastExtraction.value = null
    lastTextValue.value = ''
    lastMatch.value = null
    const saved = conversation.healthCase
    facts.value = saved === null ? [] : clone(saved.facts)
    result.value = saved === null ? null : clone(saved.result)
    status.value = saved === null ? 'IDLE' : 'RESULT'
  }

  function authorize(): void {
    consented.value = true
    errorMessage.value = ''
  }

  function addFiles(selected: FileList | File[]): void {
    errorMessage.value = ''
    const incoming = Array.from(selected)
    if (files.value.length + incoming.length > MAX_FILES) {
      errorMessage.value = '每次最多选择 5 个文件。'
      return
    }
    for (const file of incoming) {
      const label = `文件 ${files.value.length + 1}`
      if (file.size <= 0 || file.size > MAX_FILE_BYTES) {
        files.value.push(localFile(file, label, 'FAILED', '单文件必须大于 0 且不超过 10 MB。'))
      } else if (!ALLOWED_TYPES.has(file.type)) {
        files.value.push(localFile(file, label, 'FAILED', '仅支持 TXT、可解析 PDF、JPG 或 PNG。'))
      } else {
        files.value.push(localFile(file, label, 'QUEUED', '等待处理'))
      }
    }
  }

  function removeFile(id: string): void {
    controllers.get(id)?.abort()
    controllers.delete(id)
    files.value = files.value.filter(item => item.id !== id)
  }

  async function processInputs(): Promise<boolean> {
    if (!consented.value) {
      errorMessage.value = '请先阅读并明确同意本次健康资料处理。'
      return false
    }
    const text = manualText.value.trim()
    const queued = files.value.filter(item => item.status === 'QUEUED' || item.status === 'FAILED')
      .filter(item => item.file.size > 0 && item.file.size <= MAX_FILE_BYTES && ALLOWED_TYPES.has(item.file.type))
    if (text.length === 0 && queued.length === 0) {
      errorMessage.value = '请输入健康资料文字或选择文件。'
      return false
    }

    status.value = 'UPLOADING'
    errorMessage.value = ''
    result.value = null
    markUploading(true)
    const tasks: Promise<boolean>[] = []
    if (text.length > 0) tasks.push(processText(text))
    queued.forEach(item => tasks.push(processFile(item)))
    const outcomes = await Promise.all(tasks)
    markUploading(false)
    status.value = facts.value.length > 0 ? 'REVIEW' : 'ERROR'
    if (!outcomes.some(Boolean) && errorMessage.value.length === 0) {
      errorMessage.value = '没有提取到可核对事实，请按文件提示改用手工文字。'
    }
    return facts.value.length > 0
  }

  async function processText(text: string): Promise<boolean> {
    const operation = lastExtraction.value !== null && lastTextValue.value === text
      ? lastExtraction.value
      : prepareHealthExtraction({
          productId: '36743',
          conversationId: conversation.conversationId,
          knowledgeVersion: product.context.knowledgeVersion,
          authorized: true,
          text,
        })
    lastExtraction.value = operation
    lastTextValue.value = text
    try {
      const response = await operation.execute()
      mergeResponse(response.data?.facts ?? [])
      if (response.data === null || response.data.facts.length === 0) errorMessage.value = response.message
      if (response.data !== null && response.data.facts.length > 0) {
        lastExtraction.value = null
        lastTextValue.value = ''
      }
      return response.data !== null && response.data.facts.length > 0
    } catch (error) {
      errorMessage.value = extractionError(error)
      return false
    }
  }

  async function processFile(item: LocalHealthFile): Promise<boolean> {
    item.status = 'UPLOADING'
    item.progress = 0
    item.message = '正在上传并提取'
    const controller = new AbortController()
    controllers.set(item.id, controller)
    const operation = item.operation ?? prepareHealthExtraction({
      productId: '36743',
      conversationId: conversation.conversationId,
      knowledgeVersion: product.context.knowledgeVersion,
      authorized: true,
      files: [item.file],
    })
    item.operation = operation
    try {
      const response = await operation.execute(progress => { item.progress = progress }, controller.signal)
      const serverFile = response.data?.files[0]
      mergeResponse(response.data?.facts ?? [])
      item.status = serverFile?.status === 'SUCCESS' ? 'SUCCESS' : 'FAILED'
      item.progress = 100
      item.message = serverFile?.message ?? response.message
      if (item.status === 'SUCCESS') item.operation = null
      return item.status === 'SUCCESS'
    } catch (error) {
      item.status = error instanceof DOMException && error.name === 'AbortError' ? 'CANCELLED' : 'FAILED'
      item.message = item.status === 'CANCELLED' ? '已取消；服务器不会保存原文件。' : extractionError(error)
      return false
    } finally {
      controllers.delete(item.id)
    }
  }

  function cancelFile(id: string): void {
    controllers.get(id)?.abort()
  }

  async function retryFile(id: string): Promise<boolean> {
    const item = files.value.find(candidate => candidate.id === id)
    if (item === undefined || item.operation === null || status.value === 'UPLOADING') return false
    status.value = 'UPLOADING'
    errorMessage.value = ''
    markUploading(true)
    const success = await processFile(item)
    markUploading(false)
    status.value = facts.value.length > 0 ? 'REVIEW' : success ? 'REVIEW' : 'ERROR'
    return success
  }

  async function retryText(): Promise<boolean> {
    if (lastExtraction.value === null || lastTextValue.value.length === 0 || status.value === 'UPLOADING') return false
    status.value = 'UPLOADING'
    errorMessage.value = ''
    markUploading(true)
    const success = await processText(lastTextValue.value)
    markUploading(false)
    status.value = facts.value.length > 0 ? 'REVIEW' : success ? 'REVIEW' : 'ERROR'
    return success
  }

  function cancelAll(): void {
    controllers.forEach(controller => controller.abort())
    controllers.clear()
    markUploading(false)
  }

  function updateFact(id: string, patch: Partial<Pick<HealthFact,
    'label' | 'value' | 'numericValue' | 'unit' | 'date' | 'polarity'>>): void {
    const fact = facts.value.find(candidate => candidate.factId === id)
    if (fact === undefined) return
    Object.assign(fact, patch, { confirmed: false })
    invalidateResult()
  }

  function deleteFact(id: string): void {
    facts.value = facts.value.filter(fact => fact.factId !== id)
    invalidateResult()
  }

  function addManualFact(value: string): void {
    const trimmed = value.trim()
    if (trimmed.length === 0) return
    facts.value.push({
      factId: `health-fact-${generateUuid()}`,
      type: 'CURRENT_STATUS',
      code: 'MANUAL_OTHER',
      label: '手工补充',
      value: trimmed,
      numericValue: null,
      unit: null,
      date: null,
      polarity: 'UNKNOWN',
      sourceLocation: {
        sourceId: 'manual-review',
        sourceLabel: '人工补充',
        page: null,
        line: null,
        excerpt: trimmed.slice(0, 240),
      },
      confidence: 1,
      confirmed: false,
    })
    invalidateResult()
  }

  function confirmFact(id: string, confirmed: boolean): void {
    const fact = facts.value.find(candidate => candidate.factId === id)
    if (fact === undefined || fact.label.trim().length === 0 || fact.value.trim().length === 0) return
    fact.confirmed = confirmed
    invalidateResult()
  }

  function confirmAll(): void {
    facts.value.forEach(fact => {
      fact.confirmed = fact.label.trim().length > 0 && fact.value.trim().length > 0
    })
    invalidateResult()
  }

  async function match(): Promise<boolean> {
    if (!canMatch.value) {
      errorMessage.value = '必须逐项确认全部事实后才能匹配健康告知。'
      return false
    }
    const operation = prepareHealthMatch({
      productId: '36743',
      conversationId: conversation.conversationId,
      knowledgeVersion: product.context.knowledgeVersion,
      confirmed: true,
      insuredAgeYears: insuredAgeYears.value,
      insuredSex: insuredSex.value,
      facts: clone(facts.value),
    })
    lastMatch.value = operation
    status.value = 'MATCHING'
    errorMessage.value = ''
    try {
      const response = await operation.execute()
      if (response.data === null) throw new Error('empty health result')
      result.value = response.data
      const saved = await conversation.saveHealthCase({
        knowledgeVersion: response.data.knowledgeVersion,
        facts: clone(facts.value.filter(fact => fact.confirmed)),
        result: clone(response.data),
      })
      if (!saved) {
        status.value = 'ERROR'
        errorMessage.value = conversation.storageMessage || '匹配已完成，但本机保存失败。'
        return false
      }
      releaseInputs()
      status.value = 'RESULT'
      lastMatch.value = null
      return true
    } catch (error) {
      status.value = 'ERROR'
      errorMessage.value = error instanceof ApiResponseError
        ? error.response?.message ?? '健康告知匹配失败，请使用同一请求标识重试。'
        : '健康告知匹配失败，请检查服务连接后重试。'
      return false
    }
  }

  async function retryMatch(): Promise<boolean> {
    if (lastMatch.value === null) return false
    status.value = 'MATCHING'
    try {
      const response = await lastMatch.value.execute()
      if (response.data === null) throw new Error('empty health result')
      result.value = response.data
      const saved = await conversation.saveHealthCase({
        knowledgeVersion: response.data.knowledgeVersion,
        facts: clone(facts.value.filter(fact => fact.confirmed)),
        result: clone(response.data),
      })
      status.value = saved ? 'RESULT' : 'ERROR'
      if (saved) releaseInputs()
      return saved
    } catch (error) {
      status.value = 'ERROR'
      errorMessage.value = extractionError(error)
      return false
    }
  }

  function discardDrafts(): void {
    cancelAll()
    releaseInputs()
    const saved = conversation.healthCase
    facts.value = saved === null ? [] : clone(saved.facts)
    result.value = saved === null ? null : clone(saved.result)
    status.value = saved === null ? 'IDLE' : 'RESULT'
    errorMessage.value = ''
  }

  function revokeConsent(): void {
    discardDrafts()
    consented.value = false
  }

  function invalidateResult(): void {
    result.value = null
    status.value = facts.value.length > 0 ? 'REVIEW' : 'IDLE'
  }

  function releaseInputs(): void {
    cancelAll()
    files.value = []
    manualText.value = ''
    lastExtraction.value = null
    lastTextValue.value = ''
  }

  function clearInterruption(): void {
    interruptionMessage.value = ''
  }

  function mergeResponse(incoming: HealthFact[]): void {
    const ids = new Set(facts.value.map(fact => fact.factId))
    facts.value.push(...incoming.filter(fact => !ids.has(fact.factId)).map(fact => ({ ...clone(fact), confirmed: false })))
  }

  function localFile(file: File, label: string, fileStatus: LocalFileStatus, message: string): LocalHealthFile {
    return {
      id: generateUuid(), label, size: file.size, status: fileStatus,
      progress: 0, message, file, operation: null,
    }
  }

  function extractionError(error: unknown): string {
    if (error instanceof ApiResponseError) return error.response?.message ?? '健康资料处理失败，请重试。'
    return '健康资料处理失败，请检查服务连接后重试。'
  }

  function markUploading(active: boolean): void {
    try {
      if (active) sessionStorage.setItem(UPLOAD_MARKER, conversation.conversationId)
      else sessionStorage.removeItem(UPLOAD_MARKER)
    } catch {
      // Private browsing can deny session storage; upload safety does not depend on this marker.
    }
  }

  function readInterruptedMessage(): string {
    try {
      if (sessionStorage.getItem(UPLOAD_MARKER) !== null) {
        sessionStorage.removeItem(UPLOAD_MARKER)
        return '上次页面在上传期间关闭，原文件未保存；请重新选择文件。'
      }
    } catch {
      return ''
    }
    return ''
  }

  return {
    consented,
    status,
    manualText,
    files,
    facts,
    result,
    insuredAgeYears,
    insuredSex,
    errorMessage,
    interruptionMessage,
    lastExtraction,
    lastMatch,
    confirmedCount,
    canMatch,
    hasUnsavedWork,
    hydrate,
    authorize,
    addFiles,
    removeFile,
    processInputs,
    cancelFile,
    retryFile,
    retryText,
    updateFact,
    deleteFact,
    addManualFact,
    confirmFact,
    confirmAll,
    match,
    retryMatch,
    discardDrafts,
    revokeConsent,
    clearInterruption,
  }
})
