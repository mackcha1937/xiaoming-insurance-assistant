<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import { focusFirst, trapDialogFocus } from '@/composables/useFocusTrap'
import type { ChecklistSection } from '@/services/api/types'
import { useChecklistStore } from '@/stores/checklist'
import { useConversationStore } from '@/stores/conversation'

const props = defineProps<{ open: boolean }>()
const checklistStore = useChecklistStore()
const conversation = useConversationStore()
const unfinishedOnly = ref(false)
const confirmRegenerate = ref(false)
const panel = ref<HTMLElement | null>(null)
const regenerateDialog = ref<HTMLElement | null>(null)
let regenerateReturnFocus: HTMLElement | null = null

const sectionLabels: Record<ChecklistSection, string> = {
  INPUT_INFORMATION: '需录入信息',
  PREPARE_MATERIALS: '需准备材料',
  READ_CONFIRM: '需阅读确认文件',
}

const sections = computed(() => {
  const items = conversation.checklist?.items ?? []
  return (Object.keys(sectionLabels) as ChecklistSection[]).map(section => ({
    section,
    label: sectionLabels[section],
    items: items.filter(item => item.section === section && (!unfinishedOnly.value || !item.checked)),
  })).filter(group => group.items.length > 0)
})

const completedCount = computed(() => conversation.checklist?.items.filter(item => item.checked).length ?? 0)
const totalCount = computed(() => conversation.checklist?.items.length ?? 0)

watch([() => props.open, () => conversation.conversationId], async ([open]) => {
  if (!open) return
  checklistStore.hydrate()
  unfinishedOnly.value = false
  await nextTick()
  panel.value?.focus()
})

watch(confirmRegenerate, async value => {
  if (!value) return
  await nextTick()
  focusFirst(regenerateDialog.value)
})

async function generate(): Promise<void> {
  await checklistStore.generate()
}

async function regenerate(): Promise<void> {
  await closeRegenerate()
  await checklistStore.generate()
}

async function toggle(id: string, event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const success = await conversation.toggleChecklistItem(id, input.checked)
  if (!success) input.checked = !input.checked
}

function onRegenerateKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, regenerateDialog.value, () => { void closeRegenerate() })
}

function requestRegenerate(event: MouseEvent): void {
  regenerateReturnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  confirmRegenerate.value = true
}

async function closeRegenerate(): Promise<void> {
  confirmRegenerate.value = false
  await nextTick()
  const target = regenerateReturnFocus?.isConnected ? regenerateReturnFocus : panel.value
  regenerateReturnFocus = null
  target?.focus()
}
</script>

<template>
  <section
    v-if="open"
    id="material-checklist"
    ref="panel"
    class="checklist-panel"
    tabindex="-1"
    aria-labelledby="checklist-title"
  >
    <div class="section-heading">
      <div>
        <p class="eyebrow">当前客户专属</p>
        <h2 id="checklist-title">动态资料清单</h2>
      </div>
      <span>只保存非敏感条件</span>
    </div>

    <div v-if="conversation.versionOutdated" class="version-update" role="status">
      <strong>正式资料已有新版本</strong>
      <p>历史回答仍保留 {{ conversation.conversationKnowledgeVersion }}；可按最新资料重新生成清单。</p>
    </div>

    <form v-if="conversation.checklist === null" class="checklist-form" @submit.prevent="generate">
      <p>这里只选择条件，不填写姓名、证件号码、手机号或健康详情。</p>
      <div class="checklist-fields">
        <label>
          <span>被保险人周岁年龄</span>
          <input v-model.number="checklistStore.form.insuredAgeYears" type="number" min="0" max="120" required />
        </label>
        <label>
          <span>投保关系</span>
          <select v-model="checklistStore.form.applicantRelationship">
            <option value="SELF">本人</option>
            <option value="PARENT">父母为子女投保</option>
            <option value="CHILD">子女为父母投保</option>
            <option value="SPOUSE">配偶</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label>
          <span>证件类型</span>
          <select v-model="checklistStore.form.idDocumentType">
            <option value="NATIONAL_ID">居民身份证</option>
            <option value="PASSPORT">护照</option>
            <option value="OTHER">其他有效证件</option>
          </select>
        </label>
        <label>
          <span>受益人设置</span>
          <select v-model="checklistStore.form.beneficiaryMode">
            <option value="STATUTORY">法定受益人</option>
            <option value="DESIGNATED">指定受益人</option>
          </select>
        </label>
        <label>
          <span>健康告知准备状态</span>
          <select v-model="checklistStore.form.healthStatus">
            <option value="NOT_CHECKED">尚未核对</option>
            <option value="NO_EXCEPTION">暂未发现需特别说明</option>
            <option value="NEEDS_DISCLOSURE">正式投保时需如实告知</option>
          </select>
        </label>
      </div>
      <button type="submit" class="primary-action" :disabled="checklistStore.status === 'LOADING'">
        {{ checklistStore.status === 'LOADING' ? '正在生成…' : '生成资料清单' }}
      </button>
    </form>

    <div v-else class="checklist-result">
      <div class="checklist-summary">
        <div>
          <strong>已完成 {{ completedCount }} / {{ totalCount }}</strong>
          <span>{{ conversation.checklist.checklistVersion }}</span>
        </div>
        <button type="button" class="secondary-action" @click="unfinishedOnly = !unfinishedOnly">
          {{ unfinishedOnly ? '查看全部' : '查看未完成项' }}
        </button>
        <details class="item-menu checklist-menu">
          <summary aria-label="更多清单操作">…</summary>
          <div class="item-menu-popover">
            <button type="button" @click="requestRegenerate($event)">重新生成</button>
          </div>
        </details>
      </div>

      <p v-if="sections.length === 0" class="empty-state">当前筛选下没有未完成项。</p>
      <fieldset v-for="group in sections" :key="group.section" class="checklist-group">
        <legend>{{ group.label }}</legend>
        <label v-for="item in group.items" :key="item.id" class="checklist-item">
          <input type="checkbox" :checked="item.checked" @change="toggle(item.id, $event)" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.reason }}</small>
          </span>
          <em :class="item.required ? 'required' : 'suggested'">{{ item.required ? '必需' : '建议' }}</em>
        </label>
      </fieldset>
      <p class="boundary-notice">{{ conversation.checklist.notice }}</p>
    </div>

    <div v-if="checklistStore.errorMessage" class="storage-error" role="alert">
      <span>{{ checklistStore.errorMessage }}</span>
      <button v-if="checklistStore.lastOperation" type="button" @click="checklistStore.retry">同一请求标识重试</button>
    </div>
  </section>

  <div v-if="confirmRegenerate" class="modal-backdrop" @click.self="closeRegenerate">
    <section ref="regenerateDialog" class="confirm-dialog" tabindex="-1" role="alertdialog" aria-modal="true" aria-labelledby="regenerate-title" @keydown="onRegenerateKeydown">
      <h2 id="regenerate-title">覆盖当前资料清单？</h2>
      <p>确认后会按当前条件和最新资料重新生成，并清空原勾选；取消会完整保留现状。</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="closeRegenerate">取消</button>
        <button type="button" class="primary-action" @click="regenerate">确认重新生成</button>
      </div>
    </section>
  </div>
</template>
