import type {
  ActionType,
  BusinessStatus,
  CardType,
  XiaomingErrorCode,
  XiaomingIntent,
} from '@/services/generated/contracts'

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiError {
  code: XiaomingErrorCode
  message: string
  retryable: boolean
  fields: FieldViolation[]
}

export interface Evidence {
  evidenceId: string
  documentName: string
  section: string
  pdfPage: number | null
  printedPage: number | null
  quote: string
  knowledgeVersion: string
}

export interface Action {
  id: string
  type: ActionType
  label: string
  primary: boolean
  enabled: boolean
  disabledReason: string | null
  payload: Record<string, unknown>
}

export interface Card {
  id: string
  schemaVersion: string
  type: CardType
  title: string
  data: Record<string, unknown>
}

export interface Suggestion {
  id: string
  label: string
  prompt: string
  intent: XiaomingIntent | null
}

export interface MissingField {
  name: string
  label: string
  inputType: string
  required: boolean
  options: string[]
}

export interface XiaomingResponse<T> {
  status: BusinessStatus
  intent: XiaomingIntent | null
  message: string
  data: T | null
  cards: Card[]
  evidence: Evidence[]
  suggestions: Suggestion[]
  actions: Action[]
  missingFields: MissingField[]
  knowledgeVersion: string
  traceId: string
  requestId: string
  error: ApiError | null
}

export interface CapabilityDescriptor {
  intent: XiaomingIntent
  label: string
  enabled: boolean
}

export interface ProductContext {
  productId: string
  name: string
  knowledgeVersion: string
  environment: string
  authenticationRequired: boolean
  accessMode: 'POC_NO_LOGIN' | 'INVITE_CODE'
  capabilities: CapabilityDescriptor[]
}

export interface ChatRequest {
  productId: '36743'
  conversationId: string
  message: string
  context: Record<string, unknown>
  knowledgeVersionHint?: string
}
