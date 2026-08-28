<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

import { focusFirst, trapDialogFocus } from '@/composables/useFocusTrap'
import type { HealthFact, HealthFactPolarity, HealthMatchState } from '@/services/api/types'
import { useHealthStore } from '@/stores/health'
import { useConversationStore } from '@/stores/conversation'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const health = useHealthStore()
const conversation = useConversationStore()
const consentChecked = ref(false)
const manualFact = ref('')
const panel = ref<HTMLElement | null>(null)
const closeDialog = ref<HTMLElement | null>(null)
const confirmClose = ref(false)
let closeReturnFocus: HTMLElement | null = null

const stateLabels: Record<HealthMatchState, string> = {
  HIT: '发现命中',
  NO_HIT_FOUND: '暂未发现命中',
  INSUFFICIENT: '信息不足',
}
const polarityLabels: Record<HealthFactPolarity, string> = {
  PRESENT: '存在 / 是',
  ABSENT: '不存在 / 否',
  UNKNOWN: '不确定',
}

const resultClass = computed(() => health.result === null ? '' : `health-state-${health.result.state.toLowerCase()}`)

watch([() => props.open, () => conversation.conversationId], async ([open]) => {
  if (!open) return
  health.hydrate()
  consentChecked.value = false
  await nextTick()
  panel.value?.focus()
})

watch(confirmClose, async open => {
  if (!open) return
  await nextTick()
  focusFirst(closeDialog.value)
})

function authorize(): void {
  if (!consentChecked.value) return
  health.authorize()
}

function selectFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  if (input.files !== null) health.addFiles(input.files)
  input.value = ''
}

function value(event: Event): string {
  return (event.target as HTMLInputElement | HTMLSelectElement).value
}

function updateText(fact: HealthFact, field: 'label' | 'value' | 'unit' | 'date', event: Event): void {
  const next = value(event)
  health.updateFact(fact.factId, { [field]: next.length === 0 && (field === 'unit' || field === 'date') ? null : next })
}

function updateNumber(fact: HealthFact, event: Event): void {
  const raw = value(event)
  health.updateFact(fact.factId, { numericValue: raw === '' ? null : Number(raw) })
}

function updatePolarity(fact: HealthFact, event: Event): void {
  health.updateFact(fact.factId, { polarity: value(event) as HealthFactPolarity })
}

function addFact(): void {
  health.addManualFact(manualFact.value)
  manualFact.value = ''
}

function requestClose(event: MouseEvent): void {
  if (!health.hasUnsavedWork) {
    emit('close')
    return
  }
  closeReturnFocus = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  confirmClose.value = true
}

async function keepEditing(): Promise<void> {
  confirmClose.value = false
  await nextTick()
  const target = closeReturnFocus?.isConnected ? closeReturnFocus : panel.value
  closeReturnFocus = null
  target?.focus()
}

function discardAndClose(): void {
  health.discardDrafts()
  confirmClose.value = false
  emit('close')
}

function onCloseDialogKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, closeDialog.value, () => { void keepEditing() })
}

function warnBeforeUnload(event: BeforeUnloadEvent): void {
  if (health.status !== 'UPLOADING') return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', warnBeforeUnload))
</script>

<template>
  <section
    v-if="open"
    id="health-disclosure"
    ref="panel"
    class="health-panel"
    tabindex="-1"
    aria-labelledby="health-title"
  >
    <div class="section-heading health-heading">
      <div>
        <p class="eyebrow">先授权 · 再提取 · 后确认</p>
        <h2 id="health-title">健康告知核对</h2>
      </div>
      <button type="button" class="secondary-action" @click="requestClose">关闭</button>
    </div>

    <div v-if="health.interruptionMessage" class="health-alert" role="alert">
      <span>{{ health.interruptionMessage }}</span>
      <button type="button" @click="health.clearInterruption">知道了</button>
    </div>

    <div v-if="!health.consented" class="health-consent">
      <h3>步骤 1：本次处理授权</h3>
      <p>资料仅用于当前产品的健康告知事实核对。不要输入姓名、身份证号、手机号；原文件和中间内容不会保存在本机历史中。</p>
      <label>
        <input v-model="consentChecked" type="checkbox" />
        <span>我已阅读并同意处理本次输入的健康资料</span>
      </label>
      <button type="button" class="primary-action" :disabled="!consentChecked" @click="authorize">
        同意并继续
      </button>
      <div v-if="health.result" class="saved-health-summary">
        <strong>当前客户已有已确认结果：{{ stateLabels[health.result.state] }}</strong>
        <span>{{ health.result.knowledgeVersion }}</span>
      </div>
    </div>

    <template v-else>
      <div class="health-input-block">
        <div class="health-step-heading">
          <div>
            <h3>步骤 2：输入资料</h3>
            <p>支持文字、可解析 PDF、JPG、PNG；最多 5 个文件，单文件不超过 10 MB，PDF 不超过 30 页。</p>
          </div>
          <button type="button" class="secondary-action" @click="health.revokeConsent">撤销本次授权</button>
        </div>
        <label class="health-text-label">
          <span>粘贴非敏感健康资料</span>
          <textarea v-model="health.manualText" rows="4" maxlength="8000" placeholder="例如：2025年3月因高血压住院，目前服药。" />
        </label>
        <label class="file-picker">
          <span>选择文件</span>
          <input type="file" multiple accept=".txt,.pdf,.jpg,.jpeg,.png,text/plain,application/pdf,image/jpeg,image/png" @change="selectFiles" />
        </label>

        <ul v-if="health.files.length" class="health-file-list" aria-label="待处理文件">
          <li v-for="item in health.files" :key="item.id">
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ (item.size / 1024 / 1024).toFixed(2) }} MB · {{ item.message }}</span>
              <progress v-if="item.status === 'UPLOADING'" :value="item.progress" max="100">{{ item.progress }}%</progress>
            </div>
            <div class="health-file-actions">
              <button v-if="item.status === 'UPLOADING'" type="button" @click="health.cancelFile(item.id)">取消</button>
              <button v-if="(item.status === 'FAILED' || item.status === 'CANCELLED') && item.operation" type="button" @click="health.retryFile(item.id)">同一标识重试</button>
              <button v-if="item.status !== 'UPLOADING'" type="button" @click="health.removeFile(item.id)">移除</button>
            </div>
          </li>
        </ul>

        <button type="button" class="primary-action" :disabled="health.status === 'UPLOADING'" @click="health.processInputs">
          {{ health.status === 'UPLOADING' ? '正在处理…' : '提取事实草稿' }}
        </button>
      </div>

      <div v-if="health.facts.length" class="health-review">
        <div class="health-step-heading">
          <div>
            <h3>步骤 3：逐项核对</h3>
            <p>任何修改都会取消该事实的确认状态。只有全部确认后才能匹配。</p>
          </div>
          <span>{{ health.confirmedCount }} / {{ health.facts.length }} 已确认</span>
        </div>

        <article v-for="fact in health.facts" :key="fact.factId" class="health-fact-card">
          <div class="health-fact-meta">
            <span>{{ fact.type }}</span>
            <span>置信度 {{ Math.round(fact.confidence * 100) }}%</span>
            <button type="button" @click="health.deleteFact(fact.factId)">删除</button>
          </div>
          <div class="health-fact-fields">
            <label>
              <span>事实名称</span>
              <input :value="fact.label" maxlength="80" @input="updateText(fact, 'label', $event)" />
            </label>
            <label>
              <span>事实内容</span>
              <input :value="fact.value" maxlength="240" @input="updateText(fact, 'value', $event)" />
            </label>
            <label>
              <span>状态</span>
              <select :value="fact.polarity" @change="updatePolarity(fact, $event)">
                <option v-for="(label, option) in polarityLabels" :key="option" :value="option">{{ label }}</option>
              </select>
            </label>
            <label>
              <span>日期（可选）</span>
              <input type="date" :value="fact.date ?? ''" @input="updateText(fact, 'date', $event)" />
            </label>
            <label>
              <span>数值（可选）</span>
              <input type="number" step="any" :value="fact.numericValue ?? ''" @input="updateNumber(fact, $event)" />
            </label>
            <label>
              <span>单位（可选）</span>
              <input :value="fact.unit ?? ''" maxlength="24" @input="updateText(fact, 'unit', $event)" />
            </label>
          </div>
          <p class="health-source">来源：{{ fact.sourceLocation.sourceLabel }}<template v-if="fact.sourceLocation.page">，第 {{ fact.sourceLocation.page }} 页</template> · {{ fact.sourceLocation.excerpt }}</p>
          <label class="fact-confirm">
            <input type="checkbox" :checked="fact.confirmed" @change="health.confirmFact(fact.factId, ($event.target as HTMLInputElement).checked)" />
            <span>我已核对这条事实</span>
          </label>
        </article>

        <form class="manual-fact-row" @submit.prevent="addFact">
          <label>
            <span>人工补充遗漏事实</span>
            <input v-model="manualFact" maxlength="240" placeholder="仅填写与健康告知相关的非身份信息" />
          </label>
          <button type="submit" class="secondary-action">添加</button>
        </form>
        <button type="button" class="secondary-action" @click="health.confirmAll">确认全部有效事实</button>

        <div class="health-demographics">
          <label>
            <span>被保险人年龄（仅在相关问题需要时）</span>
            <input v-model.number="health.insuredAgeYears" type="number" min="0" max="120" placeholder="可留空" />
          </label>
          <label>
            <span>被保险人性别（仅在相关问题需要时）</span>
            <select v-model="health.insuredSex">
              <option value="UNSPECIFIED">未提供</option>
              <option value="MALE">男</option>
              <option value="FEMALE">女</option>
            </select>
          </label>
        </div>
        <button type="button" class="primary-action" :disabled="!health.canMatch || health.status === 'MATCHING'" @click="health.match">
          {{ health.status === 'MATCHING' ? '正在匹配…' : '匹配当前健康告知' }}
        </button>
      </div>

      <section v-if="health.result" class="health-result" :class="resultClass" aria-labelledby="health-result-title">
        <div>
          <p class="eyebrow">步骤 4：人工核对结果</p>
          <h3 id="health-result-title">{{ stateLabels[health.result.state] }}</h3>
          <span>{{ health.result.matchVersion }}</span>
        </div>
        <article v-for="question in health.result.questions" :key="`${question.questionId}-${question.state}`">
          <header>
            <strong>问题 {{ question.questionId }} · {{ stateLabels[question.state] }}</strong>
          </header>
          <p>{{ question.questionText }}</p>
          <p>{{ question.reason }}</p>
          <p v-if="question.missingFields.length">待补充：{{ question.missingFields.join('、') }}</p>
          <details>
            <summary>查看正式资料依据</summary>
            <blockquote>{{ question.evidence.quote }}</blockquote>
            <small>{{ question.evidence.documentName }} · {{ question.evidence.section }} · {{ question.evidence.knowledgeVersion }}</small>
          </details>
        </article>
        <p class="boundary-notice">{{ health.result.notice }}</p>
      </section>

      <div v-if="health.errorMessage" class="storage-error" role="alert">
        <span>{{ health.errorMessage }}</span>
        <button v-if="health.lastExtraction" type="button" @click="health.retryText">同一请求标识重试提取</button>
        <button v-if="health.lastMatch" type="button" @click="health.retryMatch">同一请求标识重试</button>
      </div>
    </template>
  </section>

  <div v-if="confirmClose" class="modal-backdrop" @click.self="keepEditing">
    <section ref="closeDialog" class="confirm-dialog" tabindex="-1" role="alertdialog" aria-modal="true" aria-labelledby="health-close-title" @keydown="onCloseDialogKeydown">
      <h2 id="health-close-title">放弃未完成的健康资料处理？</h2>
      <p>关闭后，未确认事实和已选择原文件会从页面移除；已经完成并保存的确认结果不受影响。</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="keepEditing">继续核对</button>
        <button type="button" class="danger-action" @click="discardAndClose">放弃并关闭</button>
      </div>
    </section>
  </div>
</template>
