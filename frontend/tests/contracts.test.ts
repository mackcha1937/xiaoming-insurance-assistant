import { describe, expect, it } from 'vitest'

import {
  ACTION_TYPES,
  API_BASE_PATH,
  BUSINESS_STATUSES,
  CARD_TYPES,
  ERROR_CODES,
  INTENTS,
  PRODUCT,
} from '@/services/generated/contracts'

describe('generated Phase 05 contract', () => {
  it('freezes the product, API prefix and domain boundaries', () => {
    expect(API_BASE_PATH).toBe('/api/v1/xiaoming')
    expect(PRODUCT.productId).toBe('36743')
    expect(PRODUCT.name).toContain('达尔文12号')
    expect(BUSINESS_STATUSES).toHaveLength(6)
    expect(INTENTS).toHaveLength(9)
    expect(ERROR_CODES).toHaveLength(9)
    expect(ACTION_TYPES).toHaveLength(12)
    expect(CARD_TYPES).toEqual([
      'NOTICE',
      'QUESTION',
      'SUMMARY',
      'OCCUPATION_CANDIDATES',
      'OCCUPATION_RESULT',
      'MATERIAL_CHECKLIST',
    ])
  })

  it('does not duplicate intent codes', () => {
    expect(new Set(INTENTS.map(({ code }) => code)).size).toBe(INTENTS.length)
  })
})
