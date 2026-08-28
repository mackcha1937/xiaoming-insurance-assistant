import { describe, expect, it, vi } from 'vitest'

import { getApplicationUrl, getProductContext, prepareChatRequest, prepareFeedback, prepareHealthExtraction } from '@/services/api/client'
import type { ApplicationLinkResult, ProductContext, XiaomingResponse } from '@/services/api/types'

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

  it('uses browser-managed multipart boundaries and stable identities for health upload retries', async () => {
    class FakeXmlHttpRequest {
      static instances: FakeXmlHttpRequest[] = []
      upload: { onprogress: ((event: ProgressEvent) => void) | null } = { onprogress: null }
      headers = new Map<string, string>()
      body: FormData | null = null
      url = ''
      status = 200
      responseText = JSON.stringify(response({
        extractionVersion: 'health-extraction-v1@knowledge-v1',
        knowledgeVersion: 'knowledge-v1',
        facts: [],
        files: [],
        notice: 'ok',
      }))
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      onabort: (() => void) | null = null
      constructor() { FakeXmlHttpRequest.instances.push(this) }
      open(_method: string, url: string): void { this.url = url }
      setRequestHeader(name: string, value: string): void { this.headers.set(name, value) }
      send(body: FormData): void {
        this.body = body
        this.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 1 } as ProgressEvent)
        this.onload?.()
      }
      abort(): void { this.onabort?.() }
    }
    vi.stubGlobal('XMLHttpRequest', FakeXmlHttpRequest)
    const operation = prepareHealthExtraction({
      productId: '36743',
      conversationId: 'conversation-health-0001',
      knowledgeVersion: 'knowledge-v1',
      authorized: true,
      text: '高血压',
    })

    await operation.execute()
    await operation.execute()

    expect(FakeXmlHttpRequest.instances).toHaveLength(2)
    const [first, second] = FakeXmlHttpRequest.instances
    expect(first!.headers.has('Content-Type')).toBe(false)
    expect(first!.headers.get('Idempotency-Key')).toBe(operation.identity.idempotencyKey)
    expect(second!.headers.get('Idempotency-Key')).toBe(operation.identity.idempotencyKey)
    expect(first!.headers.get('X-Request-Id')).toBe(second!.headers.get('X-Request-Id'))
    expect(first!.body?.get('authorized')).toBeNull()
    expect(first!.body?.get('text')).toBe('高血压')
    expect(first!.url).toContain('authorized=true')
    expect(first!.url).not.toContain('高血压')
  })

  it('requests a server-built application URL without customer or health data', async () => {
    const link: ApplicationLinkResult = {
      productId: '36743', environment: 'test', url: 'https://apply.example.test/start?productId=36743',
      allowedDomain: 'apply.example.test', expiresAt: '2026-08-28T16:00:00Z', notice: 'safe',
    }
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async () => new Response(JSON.stringify(response(link)), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await getApplicationUrl()

    const [url] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/v1/xiaoming/application-url?productId=36743&channelType=advisor')
    expect(String(url)).not.toContain('conversation')
    expect(String(url)).not.toContain('health')
  })

  it('reuses feedback identity and defaults to no context', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async () => new Response(JSON.stringify(response({
      feedbackId: 'feedback-1', status: 'ACCEPTED', acceptedAt: '2026-08-28T15:00:00Z',
    })), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const operation = prepareFeedback({
      productId: '36743', conversationId: 'conversation-0001', knowledgeVersion: 'knowledge-v1',
      intent: 'APPLICATION_RULES', reason: 'UNCLEAR', includeContext: false, evidenceIds: [],
    })

    await operation.execute()
    await operation.execute()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const first = fetchMock.mock.calls[0]![1]!
    const second = fetchMock.mock.calls[1]![1]!
    expect((first.headers as Headers).get('Idempotency-Key')).toBe((second.headers as Headers).get('Idempotency-Key'))
    expect(String(first.body)).not.toContain('questionSummary')
    expect(String(first.body)).not.toContain('answerSummary')
  })
})
