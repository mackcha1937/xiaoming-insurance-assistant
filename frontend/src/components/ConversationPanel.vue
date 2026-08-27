<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import type { MissingField } from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'

const conversation = useConversationStore()
const input = ref('')
const transcript = ref<HTMLElement | null>(null)
const fieldValues = ref<Record<string, string>>({})

async function submit(): Promise<void> {
  const value = input.value
  if (value.trim().length === 0) return
  input.value = ''
  await conversation.send(value)
}

async function submitField(field: MissingField): Promise<void> {
  const value = fieldValues.value[field.name] ?? ''
  await conversation.provideField(field, value)
  fieldValues.value[field.name] = ''
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void submit()
  }
}

watch(() => conversation.messages.length, async () => {
  await nextTick()
  if (typeof transcript.value?.scrollTo === 'function') {
    transcript.value.scrollTo({ top: transcript.value.scrollHeight, behavior: 'smooth' })
  }
})
</script>

<template>
  <section class="conversation-panel" aria-labelledby="conversation-title">
    <div class="section-heading conversation-heading">
      <div>
        <p class="eyebrow">正式资料问答</p>
        <h2 id="conversation-title">和小明对话</h2>
      </div>
      <span>关键结论附原文依据</span>
    </div>

    <div ref="transcript" class="transcript" aria-live="polite" aria-label="对话记录">
      <article v-if="conversation.messages.length === 0" class="empty-conversation">
        <span aria-hidden="true">明</span>
        <div>
          <strong>可以开始提问了</strong>
          <p>我会先检查问题边界，再核对规则与正式资料。证据不足时不会猜测。</p>
        </div>
      </article>

      <article
        v-for="message in conversation.messages"
        :key="message.id"
        class="message-row"
        :class="message.role"
      >
        <div class="message-bubble">
          <span class="message-role">{{ message.role === 'user' ? '你' : '小明' }}</span>
          <p>{{ message.text }}</p>

          <template v-if="message.response">
            <div
              v-for="card in message.response.cards"
              :key="card.id"
              class="answer-card"
              :class="`card-${card.type.toLowerCase()}`"
            >
              <strong>{{ card.title }}</strong>
              <p v-if="typeof card.data.answer === 'string'">{{ card.data.answer }}</p>
              <p v-else-if="typeof card.data.message === 'string'">{{ card.data.message }}</p>
            </div>

            <form
              v-for="field in message.response.missingFields"
              :key="field.name"
              class="missing-field"
              @submit.prevent="submitField(field)"
            >
              <label :for="`field-${message.id}-${field.name}`">{{ field.label }}</label>
              <div>
                <input
                  :id="`field-${message.id}-${field.name}`"
                  v-model="fieldValues[field.name]"
                  :type="field.inputType === 'number' ? 'number' : 'text'"
                  :required="field.required"
                  inputmode="numeric"
                  min="0"
                  max="120"
                />
                <button type="submit" :disabled="conversation.pending">提交</button>
              </div>
            </form>

            <details v-if="message.response.evidence.length > 0" class="evidence-panel">
              <summary>查看原文依据（{{ message.response.evidence.length }}）</summary>
              <ol>
                <li v-for="evidence in message.response.evidence" :key="evidence.evidenceId">
                  <strong>{{ evidence.documentName }}</strong>
                  <span>{{ evidence.section }} · PDF 第 {{ evidence.pdfPage }} 页</span>
                  <blockquote>{{ evidence.quote }}</blockquote>
                </li>
              </ol>
            </details>
          </template>
        </div>
      </article>

      <div v-if="conversation.pending" class="thinking" role="status">
        <span></span><span></span><span></span>
        正在核对规则与证据
      </div>
    </div>

    <button
      v-if="conversation.lastFailedOperation"
      type="button"
      class="retry-button"
      :disabled="conversation.pending"
      @click="conversation.retry"
    >
      使用同一请求标识重试
    </button>

    <form class="composer-shell" @submit.prevent="submit">
      <label for="message-input">向小明描述你的问题</label>
      <div class="composer-control">
        <textarea
          id="message-input"
          v-model="input"
          rows="2"
          maxlength="2000"
          :disabled="conversation.pending"
          aria-describedby="composer-help"
          placeholder="例如：这款产品主要保什么？"
          @keydown="onKeydown"
        ></textarea>
        <button type="submit" :disabled="conversation.pending || input.trim().length === 0">发送</button>
      </div>
      <p id="composer-help">请勿输入身份证号、手机号或完整病历；Enter 发送，Shift + Enter 换行。</p>
    </form>
  </section>
</template>
