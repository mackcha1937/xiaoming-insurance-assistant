import { API_BASE_PATH } from '@/services/generated/contracts'
import type {
  ChatRequest,
  ChecklistGenerateRequest,
  ChecklistResult,
  HealthExtractionResult,
  HealthMatchRequest,
  HealthMatchResult,
  ApplicationLinkResult,
  FeedbackRequest,
  FeedbackResult,
  OccupationConfirmationResult,
  OccupationConfirmRequest,
  OccupationSearchResult,
  ProductContext,
  XiaomingResponse,
} from './types'

const SAFE_ID_PREFIX = 'xiaoming-web-'

export interface RequestIdentity {
  requestId: string
  idempotencyKey?: string
}

export interface IdempotentChatRequest {
  identity: Required<RequestIdentity>
  execute: () => Promise<XiaomingResponse<Record<string, unknown>>>
}

export interface IdempotentOccupationRequest {
  identity: Required<RequestIdentity>
  execute: () => Promise<XiaomingResponse<OccupationConfirmationResult>>
}

export interface IdempotentChecklistRequest {
  identity: Required<RequestIdentity>
  execute: () => Promise<XiaomingResponse<ChecklistResult>>
}

export interface HealthExtractionPayload {
  productId: '36743'
  conversationId: string
  knowledgeVersion: string
  authorized: true
  text?: string
  files?: File[]
}

export interface IdempotentHealthExtractionRequest {
  identity: Required<RequestIdentity>
  execute: (
    onProgress?: (progress: number) => void,
    signal?: AbortSignal,
  ) => Promise<XiaomingResponse<HealthExtractionResult>>
}

export interface IdempotentHealthMatchRequest {
  identity: Required<RequestIdentity>
  execute: () => Promise<XiaomingResponse<HealthMatchResult>>
}

export interface IdempotentFeedbackRequest {
  identity: Required<RequestIdentity>
  execute: () => Promise<XiaomingResponse<FeedbackResult>>
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

export function searchOccupations(
  query: string,
  queryToken: string,
  signal: AbortSignal,
  knowledgeVersionHint?: string,
): Promise<XiaomingResponse<OccupationSearchResult>> {
  const parameters = new URLSearchParams({
    productId: '36743',
    q: query,
    queryToken,
  })
  if (knowledgeVersionHint !== undefined && knowledgeVersionHint.length > 0) {
    parameters.set('knowledgeVersionHint', knowledgeVersionHint)
  }
  return request<OccupationSearchResult>(`/occupations/search?${parameters.toString()}`, { signal })
}

export function prepareOccupationConfirmation(
  payload: OccupationConfirmRequest,
): IdempotentOccupationRequest {
  const identity: Required<RequestIdentity> = {
    requestId: createRequestId(),
    idempotencyKey: createIdempotencyKey(),
  }
  return {
    identity,
    execute: () => request<OccupationConfirmationResult>('/occupations/confirm', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, identity),
  }
}

export function prepareChecklistGeneration(
  payload: ChecklistGenerateRequest,
): IdempotentChecklistRequest {
  const identity: Required<RequestIdentity> = {
    requestId: createRequestId(),
    idempotencyKey: createIdempotencyKey(),
  }
  return {
    identity,
    execute: () => request<ChecklistResult>('/checklists/generate', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, identity),
  }
}

export function prepareHealthExtraction(
  payload: HealthExtractionPayload,
): IdempotentHealthExtractionRequest {
  const identity: Required<RequestIdentity> = {
    requestId: createRequestId(),
    idempotencyKey: createIdempotencyKey(),
  }
  return {
    identity,
    execute: (onProgress, signal) => uploadHealthInput(payload, identity, onProgress, signal),
  }
}

function uploadHealthInput(
  payload: HealthExtractionPayload,
  identity: Required<RequestIdentity>,
  onProgress?: (progress: number) => void,
  signal?: AbortSignal,
): Promise<XiaomingResponse<HealthExtractionResult>> {
  const envelope = new URLSearchParams({
    productId: payload.productId,
    conversationId: payload.conversationId,
    knowledgeVersion: payload.knowledgeVersion,
    authorized: 'true',
  })
  const form = new FormData()
  if (payload.text !== undefined && payload.text.trim().length > 0) form.set('text', payload.text.trim())
  payload.files?.forEach(file => form.append('files', file))

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    const abort = (): void => xhr.abort()
    xhr.open('POST', `${API_BASE_PATH}/health/extract?${envelope.toString()}`)
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('X-Request-Id', identity.requestId)
    xhr.setRequestHeader('Idempotency-Key', identity.idempotencyKey)
    xhr.upload.onprogress = event => {
      if (event.lengthComputable) onProgress?.(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onload = () => {
      signal?.removeEventListener('abort', abort)
      const body = parseResponse<HealthExtractionResult>(xhr.responseText)
      if (xhr.status < 200 || xhr.status >= 300 || body === null) {
        reject(new ApiResponseError(xhr.status, body))
        return
      }
      onProgress?.(100)
      resolve(body)
    }
    xhr.onerror = () => {
      signal?.removeEventListener('abort', abort)
      reject(new ApiResponseError(0, null))
    }
    xhr.timeout = 60_000
    xhr.ontimeout = () => {
      signal?.removeEventListener('abort', abort)
      reject(new ApiResponseError(0, null))
    }
    xhr.onabort = () => {
      signal?.removeEventListener('abort', abort)
      reject(new DOMException('Upload cancelled', 'AbortError'))
    }
    signal?.addEventListener('abort', abort, { once: true })
    if (signal?.aborted) abort()
    else xhr.send(form)
  })
}

function parseResponse<T>(value: string): XiaomingResponse<T> | null {
  try {
    return JSON.parse(value) as XiaomingResponse<T>
  } catch {
    return null
  }
}

export function prepareHealthMatch(payload: HealthMatchRequest): IdempotentHealthMatchRequest {
  const identity: Required<RequestIdentity> = {
    requestId: createRequestId(),
    idempotencyKey: createIdempotencyKey(),
  }
  return {
    identity,
    execute: () => request<HealthMatchResult>('/health/match', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, identity),
  }
}

export function getApplicationUrl(channelType = 'advisor'): Promise<XiaomingResponse<ApplicationLinkResult>> {
  const parameters = new URLSearchParams({ productId: '36743', channelType })
  return request<ApplicationLinkResult>(`/application-url?${parameters.toString()}`)
}

export function prepareFeedback(payload: FeedbackRequest): IdempotentFeedbackRequest {
  const identity: Required<RequestIdentity> = {
    requestId: createRequestId(),
    idempotencyKey: createIdempotencyKey(),
  }
  return {
    identity,
    execute: () => request<FeedbackResult>('/feedback', {
      method: 'POST',
      body: JSON.stringify(payload),
    }, identity),
  }
}
