import { API_BASE_PATH } from '@/services/generated/contracts'
import type { ChatRequest, ProductContext, XiaomingResponse } from './types'

const SAFE_ID_PREFIX = 'xiaoming-web-'

export interface RequestIdentity {
  requestId: string
  idempotencyKey?: string
}

export interface IdempotentChatRequest {
  identity: Required<RequestIdentity>
  execute: () => Promise<XiaomingResponse<Record<string, unknown>>>
}

export class ApiResponseError extends Error {
  readonly status: number
  readonly response: XiaomingResponse<unknown> | null

  constructor(status: number, response: XiaomingResponse<unknown> | null) {
    super(response?.error?.message ?? `Request failed with HTTP ${status}`)
    this.name = 'ApiResponseError'
    this.status = status
    this.response = response
  }
}

export function createRequestId(): string {
  return `${SAFE_ID_PREFIX}${crypto.randomUUID()}`
}

export function createIdempotencyKey(): string {
  return `idem-${crypto.randomUUID()}`
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  identity: RequestIdentity = { requestId: createRequestId() },
): Promise<XiaomingResponse<T>> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('X-Request-Id', identity.requestId)
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (identity.idempotencyKey !== undefined) {
    headers.set('Idempotency-Key', identity.idempotencyKey)
  }

  const response = await fetch(`${API_BASE_PATH}${path}`, { ...init, headers })
  const payload = (await response.json().catch(() => null)) as XiaomingResponse<T> | null
  if (!response.ok || payload === null) {
    throw new ApiResponseError(response.status, payload)
  }
  return payload
}

export function getProductContext(): Promise<XiaomingResponse<ProductContext>> {
  return request<ProductContext>('/product-context')
}

export function prepareChatRequest(payload: ChatRequest): IdempotentChatRequest {
  const identity: Required<RequestIdentity> = {
    requestId: createRequestId(),
    idempotencyKey: createIdempotencyKey(),
  }
  return {
    identity,
    execute: () => request<Record<string, unknown>>('/chat', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, identity),
  }
}
