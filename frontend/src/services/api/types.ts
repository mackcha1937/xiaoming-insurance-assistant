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

export interface OccupationCandidate {
  occupationId: string
  name: string
  path: string[]
}

export interface OccupationSearchResult {
  queryToken: string
  candidates: OccupationCandidate[]
  requiresMoreDetail: boolean
  prompt: string
  knowledgeVersion: string
  occupationVersion: string
}

export interface OccupationConfirmRequest {
  productId: '36743'
  occupationId: string
  knowledgeVersion: string
}

export interface OccupationConfirmationResult {
  matched: boolean
  occupationId: string
  name: string
  category: number | null
  path: string[]
  knowledgeVersion: string
  occupationVersion: string
  notice: string
}

export type ChecklistSection = 'INPUT_INFORMATION' | 'PREPARE_MATERIALS' | 'READ_CONFIRM'
export type ApplicantRelationship = 'SELF' | 'PARENT' | 'CHILD' | 'SPOUSE' | 'OTHER'
export type IdDocumentType = 'NATIONAL_ID' | 'PASSPORT' | 'OTHER'
export type BeneficiaryMode = 'STATUTORY' | 'DESIGNATED'
export type ChecklistHealthStatus = 'NOT_CHECKED' | 'NO_EXCEPTION' | 'NEEDS_DISCLOSURE'

export interface ChecklistItem {
  id: string
  section: ChecklistSection
  label: string
  required: boolean
  reason: string
}

export interface ChecklistResult {
  checklistVersion: string
  knowledgeVersion: string
  items: ChecklistItem[]
  notice: string
}

export interface ChecklistGenerateRequest {
  productId: '36743'
  conversationId: string
  knowledgeVersion: string
  insuredAgeYears: number
  applicantRelationship: ApplicantRelationship
  idDocumentType: IdDocumentType
  beneficiaryMode: BeneficiaryMode
  healthStatus: ChecklistHealthStatus
}

export type HealthFactType =
  | 'DISEASE'
  | 'SYMPTOM'
  | 'HOSPITALIZATION'
  | 'SURGERY'
  | 'MEDICATION'
  | 'EXAMINATION'
  | 'MEASUREMENT'
  | 'FOLLOW_UP'
  | 'CURRENT_STATUS'
  | 'ACTIVITY'
  | 'BIRTH_HISTORY'
  | 'PREGNANCY'
export type HealthFactPolarity = 'PRESENT' | 'ABSENT' | 'UNKNOWN'
export type HealthFileStatus = 'SUCCESS' | 'FAILED'
export type HealthMatchState = 'HIT' | 'NO_HIT_FOUND' | 'INSUFFICIENT'
export type InsuredSex = 'MALE' | 'FEMALE' | 'UNSPECIFIED'

export interface HealthSourceLocation {
  sourceId: string
  sourceLabel: string
  page: number | null
  line: number | null
  excerpt: string
}

export interface HealthFact {
  factId: string
  type: HealthFactType
  code: string
  label: string
  value: string
  numericValue: number | null
  unit: string | null
  date: string | null
  polarity: HealthFactPolarity
  sourceLocation: HealthSourceLocation
  confidence: number
  confirmed: boolean
}

export interface HealthFileResult {
  fileId: string
  label: string
  status: HealthFileStatus
  code: string
  message: string
  factCount: number
}

export interface HealthExtractionResult {
  extractionVersion: string
  knowledgeVersion: string
  facts: HealthFact[]
  files: HealthFileResult[]
  notice: string
}

export interface HealthQuestionMatch {
  questionId: string
  questionText: string
  state: HealthMatchState
  matchedFactIds: string[]
  reason: string
  missingFields: string[]
  evidence: Evidence
}

export interface HealthMatchResult {
  matchVersion: string
  knowledgeVersion: string
  state: HealthMatchState
  questions: HealthQuestionMatch[]
  notice: string
}

export interface HealthMatchRequest {
  productId: '36743'
  conversationId: string
  knowledgeVersion: string
  confirmed: true
  insuredAgeYears: number | null
  insuredSex: InsuredSex
  facts: HealthFact[]
}

export interface ApplicationLinkResult {
  productId: '36743'
  environment: string
  url: string
  allowedDomain: string
  expiresAt: string
  notice: string
}

export type FeedbackReason = 'EVIDENCE_MISMATCH' | 'RULE_MISMATCH' | 'UNCLEAR' | 'OUTDATED' | 'OTHER'

export interface FeedbackRequest {
  productId: '36743'
  conversationId: string
  knowledgeVersion: string
  intent: XiaomingIntent
  reason: FeedbackReason
  includeContext: boolean
  questionSummary?: string
  answerSummary?: string
  evidenceIds: string[]
}

export interface FeedbackResult {
  feedbackId: string
  status: 'ACCEPTED'
  acceptedAt: string
}
