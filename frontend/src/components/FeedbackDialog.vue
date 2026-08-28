<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'

import { trapDialogFocus } from '@/composables/useFocusTrap'
import { createPageIsolation } from '@/composables/usePageIsolation'
import type { FeedbackReason, XiaomingResponse } from '@/services/api/types'
import { useFeedbackStore } from '@/stores/feedback'

const props = defineProps<{
  open: boolean
  response: XiaomingResponse<Record<string, unknown>> | null
  question: string
  answer: string
}>()
const emit = defineEmits<{ close: [] }>()
const feedback = useFeedbackStore()
const dialog = ref<HTMLElement | null>(null)
const reason = ref<FeedbackReason>('UNCLEAR')
const includeContext = ref(false)
let returnFocus: HTMLElement | null = null
const isolatePage = createPageIsolation()

watch(() => props.open, async value => {
  isolatePage(value)
  if (!value) {
    await nextTick()
    returnFocus?.focus()
    returnFocus = null
    return
  }
  returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  feedback.reset()
  reason.value = 'UNCLEAR'
  includeContext.value = false
  await nextTick()
  dialog.value?.focus()
})

onUnmounted(() => isolatePage(false))

async function submit(): Promise<void> {
  if (props.response === null) return
  await feedback.submit(props.response, props.question, props.answer, reason.value, includeContext.value)
}

function onKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, dialog.value, () => emit('close'))
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
      <section ref="dialog" class="confirm-dialog feedback-dialog" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="feedback-title" @keydown="onKeydown">
      <p class="eyebrow">顾问主动反馈</p>
      <h2 id="feedback-title">这条回答有什么问题？</h2>
      <p>默认只提交原因和引用 ID，不提交问题或回答内容；健康告知永远不附带内容。</p>
      <label>反馈原因
        <select v-model="reason">
          <option value="EVIDENCE_MISMATCH">依据不符</option>
          <option value="RULE_MISMATCH">规则不符</option>
          <option value="UNCLEAR">表述不清</option>
          <option value="OUTDATED">信息过期</option>
          <option value="OTHER">其他</option>
        </select>
      </label>
      <label class="application-acknowledge">
        <input v-model="includeContext" type="checkbox" :disabled="response?.intent === 'HEALTH_DISCLOSURE'" />
        明确同意附带脱敏后的问题和回答摘要
      </label>
      <p v-if="response?.intent === 'HEALTH_DISCLOSURE'" class="supporting-copy">健康告知反馈仅提交原因和引用 ID。</p>
      <p v-if="feedback.status === 'SUCCESS'" role="status">反馈已安全接收。</p>
      <p v-if="feedback.errorMessage" class="inline-error" role="alert">{{ feedback.errorMessage }}</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="emit('close')">{{ feedback.status === 'SUCCESS' ? '完成' : '取消' }}</button>
        <button v-if="feedback.status !== 'SUCCESS'" type="button" class="primary-action" :disabled="feedback.status === 'SUBMITTING'" @click="feedback.lastOperation ? feedback.retry() : submit()">
          {{ feedback.status === 'SUBMITTING' ? '正在提交…' : feedback.lastOperation ? '同一标识重试' : '提交反馈' }}
        </button>
      </div>
      </section>
    </div>
  </Teleport>
</template>
