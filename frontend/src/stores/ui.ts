import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const liveMessage = ref('')
  const menuOpen = ref(false)

  function announce(message: string): void {
    liveMessage.value = ''
    queueMicrotask(() => {
      liveMessage.value = message
    })
  }

  return { liveMessage, menuOpen, announce }
})
