import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

import { ApiResponseError, getApplicationUrl } from '@/services/api/client'
import type { ApplicationLinkResult } from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'
import { useProductStore } from '@/stores/product'

export type ApplicationCheckState = 'READY' | 'INCOMPLETE' | 'STALE'
export type ApplicationStatus = 'IDLE' | 'LOADING' | 'CONFIRMING' | 'BLOCKED' | 'ERROR'

export interface ApplicationCheckItem {
  id: 'RULES' | 'OCCUPATION' | 'HEALTH' | 'CHECKLIST'
  label: string
  complete: boolean
  detail: string
}

export const useApplicationStore = defineStore('application', () => {
  const conversation = useConversationStore()
  const product = useProductStore()
  const status = ref<ApplicationStatus>('IDLE')
  const link = ref<ApplicationLinkResult | null>(null)
  const errorMessage = ref('')
  const blocked = ref(false)

  const items = computed<ApplicationCheckItem[]>(() => {
    const hasRules = conversation.messages.some(message => message.role === 'assistant'
      && message.response?.intent === 'APPLICATION_RULES' && message.response.status === 'SUCCESS')
    const occupation = conversation.context.confirmedOccupation as Record<string, unknown> | undefined
    const checklist = conversation.checklist
    const checklistComplete = checklist !== null
      && checklist.items.filter(item => item.required).every(item => item.checked)
    return [
      { id: 'RULES', label: '投保规则已核对', complete: hasRules, detail: hasRules ? '已按当前资料查询' : '建议先查询投保年龄与规则' },
      { id: 'OCCUPATION', label: '职业已确认', complete: occupation !== undefined, detail: occupation !== undefined ? '已有当前客户确认职业' : '尚未确认职业分类' },
      { id: 'HEALTH', label: '健康告知已核对', complete: conversation.healthCase !== null, detail: conversation.healthCase !== null ? '已有人工确认的三态结果' : '尚未完成健康告知核对' },
      { id: 'CHECKLIST', label: '必需资料已准备', complete: checklistComplete, detail: checklist === null ? '尚未生成资料清单' : checklistComplete ? '必需项均已勾选' : '仍有必需项未勾选' },
    ]
  })

  const checkState = computed<ApplicationCheckState>(() => {
    const stale = conversation.versionOutdated
      || conversation.checklist !== null && conversation.checklist.knowledgeVersion !== product.context.knowledgeVersion
      || conversation.healthCase !== null && conversation.healthCase.knowledgeVersion !== product.context.knowledgeVersion
    if (stale) return 'STALE'
    return items.value.every(item => item.complete) ? 'READY' : 'INCOMPLETE'
  })

  async function requestLink(): Promise<boolean> {
    status.value = 'LOADING'
    errorMessage.value = ''
    blocked.value = false
    try {
      const response = await getApplicationUrl()
      if (response.data === null) throw new Error('empty application link')
      link.value = response.data
      status.value = 'CONFIRMING'
      return true
    } catch (error) {
      link.value = null
      status.value = 'ERROR'
      errorMessage.value = error instanceof ApiResponseError
        ? error.response?.message ?? '正式投保地址暂不可用。'
        : '正式投保地址暂不可用，请返回小明稍后重试。'
      return false
    }
  }

  function cancelConfirmation(): void {
    status.value = 'IDLE'
    blocked.value = false
  }

  async function openLink(): Promise<boolean> {
    if (link.value === null || Date.parse(link.value.expiresAt) <= Date.now()) {
      link.value = null
      status.value = 'ERROR'
      errorMessage.value = '安全地址已过期，请重新获取。'
      return false
    }
    const opened = window.open(link.value.url, '_blank', 'noopener,noreferrer')
    if (opened === null) {
      blocked.value = true
      status.value = 'BLOCKED'
      errorMessage.value = '浏览器阻止了新窗口；您可以重试或复制安全地址。'
      return false
    }
    opened.opener = null
    status.value = 'IDLE'
    return true
  }

  async function copyLink(): Promise<boolean> {
    if (link.value === null || Date.parse(link.value.expiresAt) <= Date.now()) {
      errorMessage.value = '安全地址已过期，请重新获取。'
      return false
    }
    try {
      await navigator.clipboard.writeText(link.value.url)
      errorMessage.value = '安全地址已复制，请仅在当前设备使用。'
      return true
    } catch {
      errorMessage.value = '复制失败，请允许剪贴板权限后重试。'
      return false
    }
  }

  function hydrate(): void {
    status.value = 'IDLE'
    link.value = null
    errorMessage.value = ''
    blocked.value = false
  }

  watch(() => conversation.conversationId, hydrate)

  return { status, link, errorMessage, blocked, items, checkState, requestLink, cancelConfirmation, openLink, copyLink, hydrate }
})
