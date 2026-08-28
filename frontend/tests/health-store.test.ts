import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as api from '@/services/api/client'
import type {
  HealthExtractionResult,
  HealthFact,
  HealthMatchResult,
  XiaomingResponse,
} from '@/services/api/types'
import { useConversationStore } from '@/stores/conversation'
import { useHealthStore } from '@/stores/health'
import { useProductStore } from '@/stores/product'

const fact = (id = 'health-fact-0001'): HealthFact => ({
  factId: id,
  type: 'DISEASE',
  code: 'HYPERTENSION',
  label: '高血压',
  value: '高血压',
  numericValue: null,
  unit: null,
  date: '2025-03-01',
  polarity: 'PRESENT',
  sourceLocation: {
    sourceId: 'manual-text',
    sourceLabel: '手工文字',
    page: null,
    line: 1,
    excerpt: '2025年3月因高血压住院',
  },
  confidence: 0.95,
  confirmed: false,
})

const response = <T>(data: T, status: XiaomingResponse<T>['status'] = 'SUCCESS'): XiaomingResponse<T> => ({
  status,
  intent: 'HEALTH_DISCLOSURE',
  message: 'ok',
  data,
  cards: [],
  evidence: [],
  suggestions: [],
  actions: [],
  missingFields: [],
  knowledgeVersion: 'knowledge-health-v1',
  traceId: 'trace-health-0001',
  requestId: 'request-health-0001',
  error: null,
})

const extraction = (facts: HealthFact[], fileStatus: 'SUCCESS' | 'FAILED' = 'SUCCESS'): HealthExtractionResult => ({
  extractionVersion: 'health-extraction-v1@knowledge-health-v1',
  knowledgeVersion: 'knowledge-health-v1',
  facts,
  files: [{
    fileId: 'health-file-0001',
    label: '文件 1',
    status: fileStatus,
    code: fileStatus === 'SUCCESS' ? 'FILE_EXTRACTED' : 'OCR_UNAVAILABLE',
    message: fileStatus === 'SUCCESS' ? '文件已处理' : '请改用手工输入',
    factCount: facts.length,
  }],
  notice: '请逐项核对。',
})

const matchResult = (): HealthMatchResult => ({
  matchVersion: 'health-match-v1@knowledge-health-v1',
  knowledgeVersion: 'knowledge-health-v1',
  state: 'HIT',
  questions: [{
    questionId: '2',
    questionText: '目前或曾经患有列明疾病。',
    state: 'HIT',
    matchedFactIds: ['health-fact-0001'],
    reason: '确认事实中出现列明疾病。',
    missingFields: [],
    evidence: {
      evidenceId: 'health-evidence-1',
      documentName: '健康告知预览',
      section: '健康告知',
      pdfPage: null,
      printedPage: null,
      quote: '目前或曾经患有列明疾病。',
      knowledgeVersion: 'knowledge-health-v1',
    },
  }],
  notice: '仅供人工核对，不构成核保、承保或医学结论。',
})

describe('Phase 06 health workflow store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    const product = useProductStore()
    product.context = { ...product.context, knowledgeVersion: 'knowledge-health-v1' }
    vi.restoreAllMocks()
  })

  it('never calls extraction before explicit consent', async () => {
    const health = useHealthStore()
    const prepare = vi.spyOn(api, 'prepareHealthExtraction')
    health.manualText = '高血压'

    expect(await health.processInputs()).toBe(false)
    expect(prepare).not.toHaveBeenCalled()
    expect(health.errorMessage).toContain('明确同意')
  })

  it('keeps drafts unconfirmed, invalidates confirmation after edits, and stores only the confirmed result', async () => {
    const conversation = useConversationStore()
    const save = vi.spyOn(conversation, 'saveHealthCase').mockResolvedValue(true)
    vi.spyOn(conversation, 'clearHealthCase').mockResolvedValue(true)
    vi.spyOn(api, 'prepareHealthExtraction').mockReturnValue({
      identity: { requestId: 'request-health-0001', idempotencyKey: 'idem-health-0001' },
      execute: async () => response(extraction([fact()])),
    })
    vi.spyOn(api, 'prepareHealthMatch').mockReturnValue({
      identity: { requestId: 'request-health-0002', idempotencyKey: 'idem-health-0002' },
      execute: async () => response(matchResult()),
    })

    const health = useHealthStore()
    health.authorize()
    health.manualText = '2025年3月因高血压住院'
    expect(await health.processInputs()).toBe(true)
    expect(health.facts[0]?.confirmed).toBe(false)

    health.confirmAll()
    expect(health.canMatch).toBe(true)
    health.updateFact('health-fact-0001', { value: '高血压，持续服药' })
    expect(health.facts[0]?.confirmed).toBe(false)
    expect(health.canMatch).toBe(false)
    health.confirmFact('health-fact-0001', true)

    expect(await health.match()).toBe(true)
    expect(save).toHaveBeenCalledOnce()
    const saved = save.mock.calls[0]![0]
    expect(saved.facts).toHaveLength(1)
    expect(saved.facts[0]?.confirmed).toBe(true)
    expect(saved.result.state).toBe('HIT')
    expect(health.manualText).toBe('')
    expect(health.files).toEqual([])
  })

  it('supports partial file success without exposing original file names', async () => {
    const prepare = vi.spyOn(api, 'prepareHealthExtraction').mockImplementation(payload => ({
      identity: { requestId: crypto.randomUUID(), idempotencyKey: crypto.randomUUID() },
      execute: async onProgress => {
        onProgress?.(100)
        const success = payload.files?.[0]?.type === 'text/plain'
        return response(extraction(success ? [fact('health-fact-file1')] : [], success ? 'SUCCESS' : 'FAILED'), success ? 'SUCCESS' : 'NEED_INPUT')
      },
    }))
    const health = useHealthStore()
    health.authorize()
    health.addFiles([
      new File(['高血压'], '真实姓名-病历.txt', { type: 'text/plain' }),
      new File(['image'], '身份证照片.png', { type: 'image/png' }),
    ])

    expect(health.files.map(item => item.label)).toEqual(['文件 1', '文件 2'])
    expect(JSON.stringify(health.files.map(({ label, message }) => ({ label, message })))).not.toContain('真实姓名')
    expect(await health.processInputs()).toBe(true)
    expect(health.files.map(item => item.status)).toEqual(['SUCCESS', 'FAILED'])
    expect(health.facts).toHaveLength(1)

    const failedOperation = health.files[1]?.operation
    expect(failedOperation).not.toBeNull()
    expect(await health.retryFile(health.files[1]!.id)).toBe(false)
    expect(health.files[1]?.operation).toBe(failedOperation)
    expect(prepare).toHaveBeenCalledTimes(2)
  })

  it('cancels an individual in-flight file and retains no extracted facts', async () => {
    vi.spyOn(api, 'prepareHealthExtraction').mockReturnValue({
      identity: { requestId: 'request-health-cancel', idempotencyKey: 'idem-health-cancel' },
      execute: (_progress, signal) => new Promise((_resolve, reject) => {
        signal?.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError')), { once: true })
      }),
    })
    const health = useHealthStore()
    health.authorize()
    health.addFiles([new File(['abc'], 'private.txt', { type: 'text/plain' })])
    const pending = health.processInputs()
    await Promise.resolve()
    health.cancelFile(health.files[0]!.id)

    expect(await pending).toBe(false)
    expect(health.files[0]?.status).toBe('CANCELLED')
    expect(health.facts).toEqual([])
  })

  it('rejects a sixth file and restores only confirmed persisted facts for the active customer', () => {
    const conversation = useConversationStore()
    conversation.healthCase = {
      conversationId: conversation.conversationId,
      knowledgeVersion: 'knowledge-health-v1',
      facts: [{ ...fact(), confirmed: true }],
      result: matchResult(),
      updatedAt: 1,
    }
    const health = useHealthStore()
    health.hydrate()
    expect(health.status).toBe('RESULT')
    expect(health.facts[0]?.confirmed).toBe(true)

    const selected = Array.from({ length: 6 }, (_, index) => new File(['x'], `${index}.txt`, { type: 'text/plain' }))
    health.addFiles(selected)
    expect(health.files).toEqual([])
    expect(health.errorMessage).toContain('最多选择 5 个')
  })

  it('revokes consent and clears only unsaved health input', () => {
    const conversation = useConversationStore()
    conversation.healthCase = {
      conversationId: conversation.conversationId,
      knowledgeVersion: 'knowledge-health-v1',
      facts: [{ ...fact(), confirmed: true }],
      result: matchResult(),
      updatedAt: 1,
    }
    const health = useHealthStore()
    health.hydrate()
    health.authorize()
    health.manualText = '未保存的补充内容'
    health.addManualFact('未保存的人工事实')
    health.addFiles([new File(['x'], 'private.txt', { type: 'text/plain' })])

    health.revokeConsent()

    expect(health.consented).toBe(false)
    expect(health.manualText).toBe('')
    expect(health.files).toEqual([])
    expect(health.result?.state).toBe('HIT')
    expect(health.facts).toHaveLength(1)
    expect(health.facts[0]?.confirmed).toBe(true)
  })
})
