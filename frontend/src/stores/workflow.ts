import { defineStore } from 'pinia'
import { ref } from 'vue'

export type WorkflowState = 'FOUNDATION' | 'READY_FOR_BUSINESS' | 'ERROR'

export const useWorkflowStore = defineStore('workflow', () => {
  const state = ref<WorkflowState>('FOUNDATION')
  const activeCapability = ref<string | null>(null)

  function reset(): void {
    state.value = 'FOUNDATION'
    activeCapability.value = null
  }

  return { state, activeCapability, reset }
})
