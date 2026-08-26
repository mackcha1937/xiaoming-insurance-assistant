import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useConversationStore = defineStore('conversation', () => {
  const conversationId = ref(`conversation-${crypto.randomUUID()}`)
  const title = ref('新对话')
  const messageCount = ref(0)

  function reset(): void {
    conversationId.value = `conversation-${crypto.randomUUID()}`
    title.value = '新对话'
    messageCount.value = 0
  }

  return { conversationId, title, messageCount, reset }
})
