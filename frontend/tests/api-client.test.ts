import { describe, expect, it, vi } from 'vitest'

import { getProductContext, prepareChatRequest } from '@/services/api/client'
import type { ProductContext, XiaomingResponse } from '@/services/api/types'

function response<T>(data: T): XiaomingResponse<T> {
  return {
    status: 'SUCCESS',
    intent: null,
    message: 'ok',
    data,
    cards: [],
    evidence: [],
    suggestions: [],
    actions: [],
    missingFields: [],
    knowledgeVersion: 'v2.0-baseline',
    traceId: 'trace-0001',
    requestId: 'request-0001',
    error: null,
  }
}

describe('API client identity contract', () => {
  it('adds a safe request ID to read requests', async () => {
    const product: ProductContext = {
      productId: '36743',
      name: '达尔文12号',
      knowledgeVersion: 'v2.0-baseline',
      environment: 'test',
      authenticationRequired: false,
      accessMode: 'POC_NO_LOGIN',
      capabilities: [],
    }
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async () => new Response(JSON.stringify(response(product)), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await getProductContext()

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/v1/xiaoming/product-context')
    expect((init!.headers as Headers).get('X-Request-Id')).toMatch(/^xiaoming-web-/)
  })

  it('reuses the same idempotency and request IDs when an operation is retried', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async () => new Response(JSON.stringify(response(null)), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const operation = prepareChatRequest({
      productId: '36743',
      conversationId: 'conversation-0001',
      message: '保障什么',
      context: {},
    })

    await operation.execute()
    await operation.execute()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const firstHeaders = fetchMock.mock.calls[0]![1]!.headers as Headers
    const secondHeaders = fetchMock.mock.calls[1]![1]!.headers as Headers
    expect(firstHeaders.get('Idempotency-Key')).toBe(operation.identity.idempotencyKey)
    expect(secondHeaders.get('Idempotency-Key')).toBe(operation.identity.idempotencyKey)
    expect(firstHeaders.get('X-Request-Id')).toBe(secondHeaders.get('X-Request-Id'))
  })
})
