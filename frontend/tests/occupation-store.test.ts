import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { OccupationSearchResult, XiaomingResponse } from '@/services/api/types'
import { useOccupationStore } from '@/stores/occupation'

function searchResponse(queryToken: string, name: string): XiaomingResponse<OccupationSearchResult> {
  return {
    status: 'SUCCESS',
    intent: 'OCCUPATION_QUERY',
    message: '候选已返回',
    data: {
      queryToken,
      candidates: [{ occupationId: `id-${name}`, name, path: ['行业', name] }],
      requiresMoreDetail: false,
      prompt: '请选择候选',
      knowledgeVersion: 'knowledge-v1',
      occupationVersion: 'occupation-v1',
    },
    cards: [],
    evidence: [],
    suggestions: [],
    actions: [],
    missingFields: [],
    knowledgeVersion: 'knowledge-v1',
    traceId: 'trace-test',
    requestId: 'request-test',
    error: null,
  }
}

describe('occupation store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('waits 300 ms before searching', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const token = new URL(String(input), 'http://localhost').searchParams.get('queryToken') ?? ''
      return new Response(JSON.stringify(searchResponse(token, '计算机程序设计员')))
    })
    vi.stubGlobal('fetch', fetchMock)
    const store = useOccupationStore()

    store.scheduleSearch('程序员')
    await vi.advanceTimersByTimeAsync(299)
    expect(fetchMock).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(store.status).toBe('CANDIDATES')
  })

  it('discards an older response after a newer query token wins', async () => {
    const resolvers: Array<(response: Response) => void> = []
    const fetchMock = vi.fn<
      (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
    >((_input, _init) => new Promise<Response>(resolve => resolvers.push(resolve)))
    vi.stubGlobal('fetch', fetchMock)
    const store = useOccupationStore()

    store.scheduleSearch('程序员')
    await vi.advanceTimersByTimeAsync(300)
    const firstToken = new URL(String(fetchMock.mock.calls[0]![0]), 'http://localhost')
      .searchParams.get('queryToken')!

    store.scheduleSearch('教师')
    await vi.advanceTimersByTimeAsync(300)
    const secondToken = new URL(String(fetchMock.mock.calls[1]![0]), 'http://localhost')
      .searchParams.get('queryToken')!

    resolvers[1]!(new Response(JSON.stringify(searchResponse(secondToken, '一般教师'))))
    await flushPromises()
    resolvers[0]!(new Response(JSON.stringify(searchResponse(firstToken, '计算机程序设计员'))))
    await flushPromises()

    expect(store.candidates[0]?.name).toBe('一般教师')
  })

  it('turns a three second abort into a recoverable timeout state', async () => {
    vi.stubGlobal('fetch', vi.fn((_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
      }),
    ))
    const store = useOccupationStore()

    store.scheduleSearch('程序员')
    await vi.advanceTimersByTimeAsync(300)
    await vi.advanceTimersByTimeAsync(3_000)
    await flushPromises()

    expect(store.status).toBe('ERROR')
    expect(store.errorMessage).toContain('超过 3 秒')
  })
})
