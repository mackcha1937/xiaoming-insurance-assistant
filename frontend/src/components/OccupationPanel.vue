<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import { useOccupationStore } from '@/stores/occupation'

const props = defineProps<{
  open: boolean
}>()

const occupation = useOccupationStore()
const queryInput = ref<HTMLInputElement | null>(null)

watch(() => props.open, async (open) => {
  if (!open) return
  await nextTick()
  queryInput.value?.focus()
})

function updateQuery(event: Event): void {
  occupation.scheduleSearch((event.target as HTMLInputElement).value)
}
</script>

<template>
  <section
    v-if="open"
    id="occupation-search"
    class="occupation-panel"
    aria-labelledby="occupation-title"
  >
    <div class="section-heading occupation-heading">
      <div>
        <p class="eyebrow">Phase 04 · 人工确认后判断</p>
        <h2 id="occupation-title">职业查询</h2>
      </div>
      <span>当前正式职业表</span>
    </div>

    <p class="occupation-boundary">
      先搜索并选择最符合实际工作内容的候选。确认前不会显示职业类别，也不会给出承保结论。
    </p>

    <form class="occupation-search-form" role="search" @submit.prevent="occupation.searchNow">
      <label for="occupation-query">职业名称或日常工作内容</label>
      <div>
        <input
          id="occupation-query"
          ref="queryInput"
          :value="occupation.query"
          type="search"
          maxlength="200"
          autocomplete="off"
          placeholder="例如：软件工程师，或在软件公司写代码"
          aria-describedby="occupation-search-help"
          @input="updateQuery"
        />
        <button
          type="submit"
          :disabled="occupation.query.trim().length === 0 || occupation.status === 'SEARCHING'"
        >
          查询
        </button>
      </div>
      <p id="occupation-search-help">输入停止 300 ms 后自动查询；多个职业请分别查询。</p>
    </form>

    <div
      class="occupation-status"
      :class="{ error: occupation.status === 'ERROR' }"
      role="status"
      aria-live="polite"
    >
      <span v-if="occupation.status === 'SEARCHING'" class="status-spinner" aria-hidden="true"></span>
      <p>{{ occupation.errorMessage || occupation.prompt }}</p>
    </div>

    <fieldset v-if="occupation.candidates.length > 0" class="occupation-candidates">
      <legend>请选择一项待确认职业（最多 5 项）</legend>
      <label
        v-for="candidate in occupation.candidates"
        :key="candidate.occupationId"
        :class="{ selected: occupation.selectedId === candidate.occupationId }"
      >
        <input
          type="radio"
          name="occupation-candidate"
          :value="candidate.occupationId"
          :checked="occupation.selectedId === candidate.occupationId"
          @change="occupation.select(candidate.occupationId)"
        />
        <span>
          <strong>{{ candidate.name }}</strong>
          <small>{{ candidate.path.join(' › ') }}</small>
        </span>
      </label>
    </fieldset>

    <div v-if="occupation.candidates.length > 0" class="occupation-actions">
      <button
        type="button"
        class="primary-action"
        :disabled="!occupation.canConfirm"
        @click="occupation.confirmSelected"
      >
        {{ occupation.status === 'CONFIRMING' ? '确认中…' : '确认职业' }}
      </button>
      <button type="button" class="secondary-action" @click="occupation.noneMatch">
        都不是
      </button>
    </div>

    <div v-if="occupation.supplementalMode" class="occupation-supplement">
      <strong>补充工作内容后再查</strong>
      <p>建议写明行业、场所、工具，以及是否涉及高空、高压、驾驶或其他危险操作。</p>
    </div>

    <article
      v-if="occupation.result"
      class="occupation-result"
      :class="{ unmatched: !occupation.result.matched }"
    >
      <template v-if="occupation.result.matched">
        <p class="eyebrow">已按职业编码精确确认</p>
        <h3>{{ occupation.result.name }}</h3>
        <dl>
          <div>
            <dt>职业编码</dt>
            <dd>{{ occupation.result.occupationId }}</dd>
          </div>
          <div>
            <dt>职业类别</dt>
            <dd>{{ occupation.result.category }} 类</dd>
          </div>
          <div>
            <dt>分类路径</dt>
            <dd>{{ occupation.result.path.join(' › ') }}</dd>
          </div>
          <div>
            <dt>职业表版本</dt>
            <dd>{{ occupation.result.occupationVersion }}</dd>
          </div>
        </dl>
      </template>
      <template v-else>
        <h3>当前版本未命中</h3>
      </template>
      <p>{{ occupation.result.notice }}</p>
    </article>

    <div v-if="occupation.status === 'ERROR'" class="occupation-actions">
      <button
        v-if="occupation.lastFailedConfirmation"
        type="button"
        class="primary-action"
        @click="occupation.retryConfirmation"
      >
        使用同一请求标识重试确认
      </button>
      <button v-else type="button" class="primary-action" @click="occupation.searchNow">
        重试查询
      </button>
    </div>

    <button
      v-if="occupation.status === 'CONFIRMED' || occupation.status === 'NO_MATCH' || occupation.supplementalMode"
      type="button"
      class="text-action"
      @click="occupation.requery"
    >
      重新查询（保留原描述）
    </button>
  </section>
</template>
