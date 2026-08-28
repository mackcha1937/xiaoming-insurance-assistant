interface BrowserRandomSource {
  randomUUID?: Crypto['randomUUID']
  getRandomValues?: Crypto['getRandomValues']
}

/**
 * Generate a UUID in secure and insecure browser contexts.
 *
 * `crypto.randomUUID()` is restricted to secure contexts in several browsers,
 * while `crypto.getRandomValues()` remains available on a local HTTP address.
 */
export function generateUuid(
  source: BrowserRandomSource | undefined = globalThis.crypto,
): string {
  if (typeof source?.randomUUID === 'function') return source.randomUUID()
  if (typeof source?.getRandomValues !== 'function') {
    throw new Error('Browser secure randomness is unavailable.')
  }

  const bytes = new Uint8Array(16)
  source.getRandomValues(bytes)
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')

  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
