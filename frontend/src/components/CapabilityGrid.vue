<script setup lang="ts">
import type { CapabilityDescriptor } from '@/services/api/types'

defineProps<{
  capabilities: CapabilityDescriptor[]
}>()
</script>

<template>
  <section class="capability-section" aria-labelledby="capability-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">限定域能力</p>
        <h2 id="capability-title">你可以这样问我</h2>
      </div>
      <span>阶段 1 / 9 项</span>
    </div>

    <ul class="capability-grid" aria-describedby="capability-help">
      <li v-for="(capability, index) in capabilities" :key="capability.intent">
        <button
          type="button"
          disabled
          :aria-label="`${capability.label}，后续阶段开放`"
        >
          <span class="capability-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
          <span>{{ capability.label }}</span>
          <small>即将开放</small>
        </button>
      </li>
    </ul>
    <p id="capability-help" class="supporting-copy">
      当前阶段只验证工程、接口和视觉骨架，所有业务入口保持禁用。
    </p>
  </section>
</template>
