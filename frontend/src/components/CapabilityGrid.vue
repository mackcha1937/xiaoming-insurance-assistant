<script setup lang="ts">
import type { CapabilityDescriptor } from '@/services/api/types'

defineProps<{
  capabilities: CapabilityDescriptor[]
}>()

const emit = defineEmits<{
  select: [prompt: string]
}>()

const prompts: Record<string, string> = {
  COVERAGE_RESPONSIBILITY: '这款产品主要保什么？',
  EXCLUSIONS_AND_LIMITATIONS: '哪些情况不承担保险责任？',
  APPLICATION_RULES: '投保年龄和缴费期限有什么要求？',
  APPLICATION_PROCESS: '投保前需要经过哪些步骤？',
  CLAIMS_PATH: '发生保险事故后如何报案？',
}
</script>

<template>
  <section class="capability-section" aria-labelledby="capability-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">限定域能力</p>
        <h2 id="capability-title">你可以这样问我</h2>
      </div>
      <span>阶段 3 · 已开放 5 项</span>
    </div>

    <ul class="capability-grid" aria-describedby="capability-help">
      <li v-for="(capability, index) in capabilities" :key="capability.intent">
        <button
          type="button"
          :disabled="!capability.enabled"
          :class="{ enabled: capability.enabled }"
          :aria-label="`${capability.label}，${capability.enabled ? '当前可用' : '后续阶段开放'}`"
          @click="emit('select', prompts[capability.intent] ?? capability.label)"
        >
          <span class="capability-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
          <span>{{ capability.label }}</span>
          <small>{{ capability.enabled ? '点击提问' : '后续开放' }}</small>
        </button>
      </li>
    </ul>
    <p id="capability-help" class="supporting-copy">
      当前开放保障责任、免责限制、投保规则、投保流程和理赔路径；其余能力按阶段验收后开放。
    </p>
  </section>
</template>
