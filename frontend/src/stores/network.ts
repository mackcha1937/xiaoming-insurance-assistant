import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const useNetworkStore = defineStore('network', () => {
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
  const lastError = ref<string | null>(null)

  const statusLabel = computed(() => online.value ? '网络正常' : '当前离线')

  function setOnline(value: boolean): void {
    online.value = value
    if (value) lastError.value = null
  }

  function reportError(message: string): void {
    lastError.value = message
  }

  return { online, lastError, statusLabel, setOnline, reportError }
})
