import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { INTENTS, PRODUCT } from '@/services/generated/contracts'
import { getProductContext } from '@/services/api/client'
import type { ProductContext } from '@/services/api/types'
import { useNetworkStore } from './network'

const foundationContext: ProductContext = {
  productId: PRODUCT.productId,
  name: PRODUCT.name,
  knowledgeVersion: PRODUCT.knowledgeVersion,
  environment: 'local',
  authenticationRequired: false,
  accessMode: 'POC_NO_LOGIN',
  capabilities: INTENTS.map(({ code, label }) => ({
    intent: code,
    label,
    enabled: [
      'COVERAGE_RESPONSIBILITY',
      'EXCLUSIONS_AND_LIMITATIONS',
      'APPLICATION_RULES',
      'APPLICATION_PROCESS',
      'CLAIMS_PATH',
    ].includes(code),
  })),
}

export const useProductStore = defineStore('product', () => {
  const context = ref<ProductContext>(foundationContext)
  const loading = ref(false)
  const connected = ref(false)
  const networkStore = useNetworkStore()

  const productShortName = computed(() => '达尔文12号')

  async function load(): Promise<void> {
    loading.value = true
    try {
      const response = await getProductContext()
      if (response.data !== null) {
        context.value = response.data
        connected.value = true
      }
    } catch {
      connected.value = false
      networkStore.reportError('后端服务尚未连接；页面仍可查看，但发送问题会提示重试。')
    } finally {
      loading.value = false
    }
  }

  return { context, loading, connected, productShortName, load }
})
