import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  ApiResponseError,
  prepareOccupationConfirmation,
  searchOccupations,
  type IdempotentOccupationRequest,
} from '@/services/api/client'
import type { OccupationCandidate, OccupationConfirmationResult } from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'
import { generateUuid } from '@/utils/uuid'

export type OccupationStatus =
  | 'IDLE'
  | 'WAITING'
  | 'SEARCHING'
  | 'CANDIDATES'
  | 'NEED_INPUT'
  | 'CONFIRMING'
  | 'CONFIRMED'
  | 'NO_MATCH'
  | 'ERROR'

const SEARCH_DEBOUNCE_MS = 300
const SEARCH_TIMEOUT_MS = 3_000

export const useOccupationStore = defineStore('occupation', () => {
  const query = ref('')
  const status = ref<OccupationStatus>('IDLE')
  const candidates = ref<OccupationCandidate[]>([])
  const selectedId = ref<string | null>(null)
  const result = ref<OccupationConfirmationResult | null>(null)
  const prompt = ref('输入一个职业名称或日常工作内容。多个职业请分别查询。')
  const errorMessage = ref('')
  const knowledgeVersion = ref('')
  const occupationVersion = ref('')
  const supplementalMode = ref(false)
  const currentQueryToken = ref('')
  const lastFailedConfirmation = ref<IdempotentOccupationRequest | null>(null)
  const conversation = useConversationStore()

  let debounceTimer: number | undefined
  let timeoutTimer: number | undefined
  let activeController: AbortController | null = null

  const selectedCandidate = computed(() =>
    candidates.value.find(candidate => candidate.occupationId === selectedId.value) ?? null,
  )
  const canConfirm = computed(() =>
    selectedCandidate.value !== null && status.value !== 'CONFIRMING',
  )

  function scheduleSearch(value: string): void {
    query.value = value
    selectedId.value = null
    result.value = null
    errorMessage.value = ''
    supplementalMode.value = false
    clearDebounce()
    const token = `occupation-${generateUuid()}`
    currentQueryToken.value = token
    activeController?.abort()
    activeController = null
    if (value.trim().length === 0) {
      candidates.value = []
      status.value = 'IDLE'
      prompt.value = '输入一个职业名称或日常工作内容。多个职业请分别查询。'
      return
    }
    status.value = 'WAITING'
    prompt.value = '等待输入完成后开始查询…'
    debounceTimer = window.setTimeout(() => {
      void runSearch(value, token)
    }, SEARCH_DEBOUNCE_MS)
  }

  function searchNow(): void {
    const value = query.value
    clearDebounce()
    const token = `occupation-${generateUuid()}`
    currentQueryToken.value = token
    void runSearch(value, token)
  }

  async function runSearch(value: string, token: string): Promise<void> {
    if (value.trim().length === 0 || token !== currentQueryToken.value) return
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller
    status.value = 'SEARCHING'
    prompt.value = '正在当前正式职业表中查找候选…'
    timeoutTimer = window.setTimeout(() => controller.abort('timeout'), SEARCH_TIMEOUT_MS)
    try {
      const response = await searchOccupations(
        value.trim(),
        token,
        controller.signal,
      )
      if (token !== currentQueryToken.value || response.data?.queryToken !== token) return
      if (response.data === null) throw new Error('职业查询响应缺少数据')
      candidates.value = response.data.candidates.slice(0, 5)
      selectedId.value = null
      knowledgeVersion.value = response.data.knowledgeVersion
      occupationVersion.value = response.data.occupationVersion
      prompt.value = response.data.prompt
      status.value = response.data.requiresMoreDetail ? 'NEED_INPUT' : 'CANDIDATES'
    } catch (error) {
      if (token !== currentQueryToken.value) return
      if (controller.signal.aborted && controller.signal.reason !== 'timeout') return
      candidates.value = []
      status.value = 'ERROR'
      errorMessage.value = controller.signal.reason === 'timeout'
        ? '职业查询超过 3 秒，请检查连接后重试。'
        : error instanceof ApiResponseError
          ? error.response?.message ?? '职业查询失败，请重试。'
          : '职业查询失败，请重试。'
    } finally {
      if (timeoutTimer !== undefined) window.clearTimeout(timeoutTimer)
      timeoutTimer = undefined
      if (activeController === controller) activeController = null
    }
  }

  function select(occupationId: string): void {
    if (candidates.value.some(candidate => candidate.occupationId === occupationId)) {
      selectedId.value = occupationId
    }
  }

  async function confirmSelected(): Promise<void> {
    const candidate = selectedCandidate.value
    if (candidate === null || knowledgeVersion.value.length === 0 || status.value === 'CONFIRMING') return
    const operation = prepareOccupationConfirmation({
      productId: '36743',
      occupationId: candidate.occupationId,
      knowledgeVersion: knowledgeVersion.value,
    })
    await executeConfirmation(operation)
  }

  async function retryConfirmation(): Promise<void> {
    if (lastFailedConfirmation.value === null || status.value === 'CONFIRMING') return
    await executeConfirmation(lastFailedConfirmation.value)
  }

  async function executeConfirmation(operation: IdempotentOccupationRequest): Promise<void> {
    status.value = 'CONFIRMING'
    errorMessage.value = ''
    try {
      const response = await operation.execute()
      if (response.status === 'CONFLICT') {
        result.value = null
        candidates.value = []
        selectedId.value = null
        status.value = 'NEED_INPUT'
        prompt.value = response.message
        knowledgeVersion.value = response.knowledgeVersion
        return
      }
      if (response.data === null) throw new Error('职业确认响应缺少数据')
      result.value = response.data
      knowledgeVersion.value = response.data.knowledgeVersion
      occupationVersion.value = response.data.occupationVersion
      status.value = response.data.matched ? 'CONFIRMED' : 'NO_MATCH'
      lastFailedConfirmation.value = null
      if (response.data.matched) {
        conversation.setContextValue('confirmedOccupation', {
          occupationId: response.data.occupationId,
          category: response.data.category,
          occupationVersion: response.data.occupationVersion,
        })
      }
    } catch (error) {
      status.value = 'ERROR'
      errorMessage.value = error instanceof ApiResponseError
        ? error.response?.message ?? '职业确认失败，请使用同一请求标识重试。'
        : '职业确认失败，请使用同一请求标识重试。'
      lastFailedConfirmation.value = operation
    }
  }

  function noneMatch(): void {
    clearDebounce()
    activeController?.abort()
    activeController = null
    candidates.value = []
    selectedId.value = null
    result.value = null
    supplementalMode.value = true
    status.value = 'NEED_INPUT'
    prompt.value = '请补充行业、工作场所、使用工具，以及是否涉及高空、高压或驾驶等工作内容。'
  }

  function requery(): void {
    clearDebounce()
    activeController?.abort()
    activeController = null
    candidates.value = []
    selectedId.value = null
    result.value = null
    supplementalMode.value = false
    status.value = query.value.trim().length === 0 ? 'IDLE' : 'NEED_INPUT'
    prompt.value = '已保留原描述，请修改后重新查询。'
  }

  function clearDebounce(): void {
    if (debounceTimer !== undefined) window.clearTimeout(debounceTimer)
    debounceTimer = undefined
  }

  return {
    query,
    status,
    candidates,
    selectedId,
    result,
    prompt,
    errorMessage,
    knowledgeVersion,
    occupationVersion,
    supplementalMode,
    lastFailedConfirmation,
    selectedCandidate,
    canConfirm,
    scheduleSearch,
    searchNow,
    select,
    confirmSelected,
    retryConfirmation,
    noneMatch,
    requery,
  }
})
