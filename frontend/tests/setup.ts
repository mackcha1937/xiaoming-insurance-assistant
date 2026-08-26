import { afterEach, vi } from 'vitest'

let sequence = 0
Object.defineProperty(globalThis, 'crypto', {
  configurable: true,
  value: {
    randomUUID: vi.fn(() => `00000000-0000-4000-8000-${String(++sequence).padStart(12, '0')}`),
  },
})

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})
