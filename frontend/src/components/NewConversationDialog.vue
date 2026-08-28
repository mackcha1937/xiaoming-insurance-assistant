<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import { trapDialogFocus } from '@/composables/useFocusTrap'
import { validateConversationAlias } from '@/db/conversationDb'

const props = defineProps<{ open: boolean; errorMessage?: string | null }>()
const emit = defineEmits<{
  cancel: []
  confirm: [alias: string]
}>()

const alias = ref('')
const validation = ref('')
const input = ref<HTMLInputElement | null>(null)
const dialog = ref<HTMLElement | null>(null)
let returnFocus: HTMLElement | null = null

watch(() => props.open, async value => {
  if (!value) {
    await nextTick()
    if (returnFocus?.isConnected) returnFocus.focus()
    returnFocus = null
    return
  }
  returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  alias.value = ''
  validation.value = ''
  await nextTick()
  input.value?.focus()
})

function submit(): void {
  const message = validateConversationAlias(alias.value)
  if (message !== null) {
    validation.value = message
    input.value?.focus()
    return
  }
  emit('confirm', alias.value.trim())
}

function onKeydown(event: KeyboardEvent): void {
  trapDialogFocus(event, dialog.value, () => emit('cancel'))
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('cancel')">
    <section
      ref="dialog"
      class="confirm-dialog"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      aria-labelledby="new-customer-title"
      @keydown="onKeydown"
    >
      <p class="eyebrow">客户隔离</p>
      <h2 id="new-customer-title">新建客户咨询</h2>
      <p>当前咨询会先原子保存，不会删除。创建失败或取消时仍留在当前客户。</p>
      <label for="new-customer-alias">非敏感备注</label>
      <input
        id="new-customer-alias"
        ref="input"
        v-model="alias"
        maxlength="40"
        autocomplete="off"
        placeholder="例如：张先生（请勿填写手机号或证件号）"
        :aria-describedby="validation ? 'new-customer-error' : 'new-customer-help'"
      />
      <p id="new-customer-help" class="supporting-copy">只填写便于区分的非敏感称呼，1—40 个字符。</p>
      <p v-if="validation" id="new-customer-error" class="inline-error" role="alert">{{ validation }}</p>
      <p v-if="errorMessage" class="inline-error" role="alert">{{ errorMessage }}</p>
      <div class="dialog-actions">
        <button type="button" class="secondary-action" @click="emit('cancel')">取消</button>
        <button type="button" class="primary-action" @click="submit">保存并新建</button>
      </div>
    </section>
  </div>
</template>
