import { describe, expect, it, vi } from 'vitest'

import { generateUuid } from '@/utils/uuid'

describe('generateUuid', () => {
  it('uses the native randomUUID implementation when available', () => {
    const expected = '00000000-0000-4000-8000-000000000001' as ReturnType<Crypto['randomUUID']>
    const randomUUID = vi.fn((): ReturnType<Crypto['randomUUID']> => expected)

    expect(generateUuid({ randomUUID })).toBe(expected)
    expect(randomUUID).toHaveBeenCalledOnce()
  })

  it('builds an RFC 4122 version 4 UUID from secure random bytes on local HTTP', () => {
    const uuid = generateUuid({
      getRandomValues: values => {
        const bytes = new Uint8Array(values.buffer, values.byteOffset, values.byteLength)
        bytes.forEach((_, index) => { bytes[index] = index })
        return values
      },
    })

    expect(uuid).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f')
  })

  it('does not silently use weak randomness when Web Crypto is unavailable', () => {
    expect(() => generateUuid({})).toThrow('Browser secure randomness is unavailable.')
  })
})
