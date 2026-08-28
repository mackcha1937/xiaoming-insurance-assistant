<script setup lang="ts">
import { computed } from 'vue'

import type { CapabilityDescriptor } from '@/services/api/types'

const props = defineProps<{
  capabilities: CapabilityDescriptor[]
}>()

const emit = defineEmits<{
  select: [intent: CapabilityDescriptor['intent'], prompt: string]
}>()

const prompts: Record<string, string> = {
  COVERAGE_RESPONSIBILITY: '这款产品主要保什么？',
  EXCLUSIONS_AND_LIMITATIONS: '哪些情况不承担保险责任？',
  APPLICATION_RULES: '投保年龄和缴费期限有什么要求？',
  OCCUPATION_QUERY: '我想查询职业分类',
  APPLICATION_PROCESS: '投保前需要经过哪些步骤？',
  MATERIAL_CHECKLIST: '投保前需要准备什么资料？',
  HEALTH_DISCLOSURE: '我想核对健康告知',
  CLAIMS_PATH: '发生保险事故后如何报案？',
}

const enabledCapabilities = computed(() => props.capabilities.filter(capability => capability.enabled))
const phaseLabel = computed(() => enabledCapabilities.value.some(capability => capability.intent === 'HEALTH_DISCLOSURE')
  ? '阶段 6'
  : '阶段 5')
const enabledLabels = computed(() => enabledCapabilities.value.map(capability => capability.label).join('、'))
</script>

<template>
  <section class="capability-section" aria-labelledby="capability-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">限定域能力</p>
        <h2 id="capability-title">你可以这样问我</h2>
      </div>
      <span>{{ phaseLabel }} · 已开放 {{ enabledCapabilities.length }} 项</span>
    </div>

    <ul class="capability-grid" aria-describedby="capability-help">
      <li v-for="(capability, index) in capabilities" :key="capability.intent">
        <button
          type="button"
          :disabled="!capability.enabled"
          :class="{ enabled: capability.enabled }"
          :aria-label="`${capability.label}，${capability.enabled ? '当前可用' : '后续阶段开放'}`"
          @click="emit('select', capability.intent, prompts[capability.intent] ?? capability.label)"
        >
          <span class="capability-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
          <span>{{ capability.label }}</span>
          <small>{{ capability.enabled ? '点击提问' : '后续开放' }}</small>
        </button>
      </li>
    </ul>
    <p id="capability-help" class="supporting-copy">
      当前开放{{ enabledLabels }}；其余能力按阶段验收后开放。
    </p>
  </section>
</template>
