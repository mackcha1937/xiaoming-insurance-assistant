import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  ApiResponseError,
  prepareChecklistGeneration,
  type IdempotentChecklistRequest,
} from '@/services/api/client'
import type {
  ApplicantRelationship,
  BeneficiaryMode,
  ChecklistGenerateRequest,
  ChecklistHealthStatus,
  IdDocumentType,
} from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'
import { useProductStore } from '@/stores/product'

export type ChecklistStatus = 'IDLE' | 'LOADING' | 'READY' | 'ERROR' | 'CONFLICT'
export type ChecklistForm = Omit<ChecklistGenerateRequest, 'productId' | 'conversationId' | 'knowledgeVersion'>

const defaultForm = (): ChecklistForm => ({
  insuredAgeYears: 30,
  applicantRelationship: 'SELF',
  idDocumentType: 'NATIONAL_ID',
  beneficiaryMode: 'STATUTORY',
  healthStatus: 'NOT_CHECKED',
})

export const useChecklistStore = defineStore('checklist', () => {
  const conversation = useConversationStore()
  const product = useProductStore()
  const status = ref<ChecklistStatus>('IDLE')
  const errorMessage = ref('')
  const lastOperation = ref<IdempotentChecklistRequest | null>(null)
  const form = reactive<ChecklistForm>(defaultForm())

  function hydrate(): void {
    const saved = conversation.checklist?.input
    if (saved === undefined) return
    form.insuredAgeYears = saved.insuredAgeYears
    form.applicantRelationship = saved.applicantRelationship as ApplicantRelationship
    form.idDocumentType = saved.idDocumentType as IdDocumentType
    form.beneficiaryMode = saved.beneficiaryMode as BeneficiaryMode
    form.healthStatus = saved.healthStatus as ChecklistHealthStatus
    status.value = 'READY'
  }

  async function generate(): Promise<boolean> {
    const operation = prepareChecklistGeneration({
      productId: '36743',
      conversationId: conversation.conversationId,
      knowledgeVersion: product.context.knowledgeVersion,
      ...form,
    })
    lastOperation.value = operation
    return execute(operation)
  }

  async function retry(): Promise<boolean> {
    if (lastOperation.value === null) return false
    return execute(lastOperation.value)
  }

  async function execute(operation: IdempotentChecklistRequest): Promise<boolean> {
    status.value = 'LOADING'
    errorMessage.value = ''
    try {
      const response = await operation.execute()
      if (response.status === 'CONFLICT') {
        status.value = 'CONFLICT'
        errorMessage.value = response.message
        return false
      }
      if (response.data === null) {
        status.value = 'ERROR'
        errorMessage.value = '服务未返回可保存的资料清单。'
        return false
      }
      const saved = await conversation.saveChecklist(response.data, { ...form })
      if (!saved) {
        status.value = 'ERROR'
        errorMessage.value = conversation.storageMessage || '清单已生成，但本机保存失败，原清单仍保留。'
        return false
      }
      status.value = 'READY'
      lastOperation.value = null
      return true
    } catch (error) {
      status.value = 'ERROR'
      errorMessage.value = error instanceof ApiResponseError
        ? error.response?.message ?? '资料清单请求失败，请使用同一请求标识重试。'
        : '资料清单请求失败，请检查服务连接后重试。'
      return false
    }
  }

  function resetForm(): void {
    Object.assign(form, defaultForm())
    status.value = conversation.checklist === null ? 'IDLE' : 'READY'
    errorMessage.value = ''
  }

  return { status, errorMessage, lastOperation, form, hydrate, generate, retry, resetForm }
})
