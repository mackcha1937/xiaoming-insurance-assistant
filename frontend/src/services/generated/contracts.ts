// Generated from ../../../../contracts/xiaoming-contract.json. Do not edit by hand.

export const CONTRACT_SCHEMA_VERSION = "1.0.0" as const
export const API_BASE_PATH = "/api/v1/xiaoming" as const
export const PRODUCT = {
  "productId": "36743",
  "name": "复星联合达尔文12号重大疾病保险（互联网）",
  "knowledgeVersion": "v2.0-baseline"
} as const

export const BUSINESS_STATUSES = [
  "SUCCESS",
  "NEED_INPUT",
  "INSUFFICIENT_EVIDENCE",
  "REFUSED",
  "CONFLICT",
  "ERROR"
] as const
export type BusinessStatus = (typeof BUSINESS_STATUSES)[number]

export const INTENTS = [
  {
    "code": "COVERAGE_RESPONSIBILITY",
    "label": "保障责任"
  },
  {
    "code": "EXCLUSIONS_AND_LIMITATIONS",
    "label": "免责与限制"
  },
  {
    "code": "APPLICATION_RULES",
    "label": "投保规则"
  },
  {
    "code": "OCCUPATION_QUERY",
    "label": "职业查询"
  },
  {
    "code": "APPLICATION_PROCESS",
    "label": "投保流程"
  },
  {
    "code": "MATERIAL_CHECKLIST",
    "label": "动态资料清单"
  },
  {
    "code": "HEALTH_DISCLOSURE",
    "label": "健康告知"
  },
  {
    "code": "CLAIMS_PATH",
    "label": "理赔路径"
  },
  {
    "code": "FORMAL_APPLICATION",
    "label": "正式投保"
  }
] as const
export type XiaomingIntent = (typeof INTENTS)[number]['code']

export const ERROR_CODES = [
  {
    "code": "VALIDATION_ERROR",
    "httpStatus": 400
  },
  {
    "code": "INTENT_NOT_ALLOWED",
    "httpStatus": 200
  },
  {
    "code": "EVIDENCE_INSUFFICIENT",
    "httpStatus": 200
  },
  {
    "code": "KNOWLEDGE_CONFLICT",
    "httpStatus": 200
  },
  {
    "code": "FILE_UNSUPPORTED",
    "httpStatus": 415
  },
  {
    "code": "RATE_LIMITED",
    "httpStatus": 429
  },
  {
    "code": "MODEL_TIMEOUT",
    "httpStatus": 504
  },
  {
    "code": "APPLICATION_URL_NOT_CONFIGURED",
    "httpStatus": 503
  },
  {
    "code": "DEPENDENCY_UNAVAILABLE",
    "httpStatus": 503
  }
] as const
export type XiaomingErrorCode = (typeof ERROR_CODES)[number]['code']

export const ACTION_TYPES = [
  "SELECT_CAPABILITY",
  "SUBMIT_MESSAGE",
  "PROVIDE_FIELD",
  "SELECT_OCCUPATION",
  "CONFIRM_OCCUPATION",
  "REQUERY_OCCUPATION",
  "GENERATE_CHECKLIST",
  "TOGGLE_CHECKLIST_ITEM",
  "REGENERATE_CHECKLIST",
  "AUTHORIZE_HEALTH_PROCESSING",
  "EXTRACT_HEALTH_FACTS",
  "EDIT_HEALTH_FACT",
  "CONFIRM_HEALTH_FACTS",
  "MATCH_HEALTH_DISCLOSURE",
  "RUN_APPLICATION_CHECK",
  "REQUEST_APPLICATION_URL",
  "OPEN_APPLICATION_URL",
  "COPY_APPLICATION_URL",
  "SUBMIT_FEEDBACK",
  "VIEW_EVIDENCE",
  "RETRY_REQUEST",
  "RETURN_TO_ASSISTANT"
] as const
export type ActionType = (typeof ACTION_TYPES)[number]

export const CARD_TYPES = [
  "NOTICE",
  "QUESTION",
  "SUMMARY",
  "OCCUPATION_CANDIDATES",
  "OCCUPATION_RESULT",
  "MATERIAL_CHECKLIST",
  "HEALTH_FACTS",
  "HEALTH_MATCH_RESULT",
  "APPLICATION_CHECK",
  "APPLICATION_LINK",
  "FEEDBACK_CONFIRMATION"
] as const
export type CardType = (typeof CARD_TYPES)[number]
