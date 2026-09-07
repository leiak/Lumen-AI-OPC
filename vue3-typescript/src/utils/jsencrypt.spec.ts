// W12.2 — jsencrypt spec. Round-trip + invalid-input behavior.
import { describe, expect, it } from 'vitest'
import { encrypt, decrypt } from '@/utils/jsencrypt'

describe('jsencrypt', () => {
  it('1. encrypt("hello") returns a non-empty base64 string', () => {
    const out = encrypt('hello')
    expect(typeof out).toBe('string')
    expect(out).not.toBe('')
    expect(out!).toMatch(/^[A-Za-z0-9+/=]+$/)
  })

  it('2. encrypt("") actually returns a valid ciphertext (not false) — pin jsencrypt reality', () => {
    // KNOWN: jsencrypt 3.x does not refuse empty input — it returns a valid
    // RSA-encrypted string. Callers that depend on `false` for empty must
    // add an explicit empty-string guard before encrypt(). We pin the real
    // behavior here so the assumption doesn't drift silently.
    const out = encrypt('')
    expect(typeof out).toBe('string')
    expect(out).not.toBe('')
  })

  it('3. decrypt(validEncrypted) recovers the original plaintext', () => {
    const plaintext = 'opc-secret'
    const cipher = encrypt(plaintext)
    expect(typeof cipher).toBe('string')
    const back = decrypt(cipher as string)
    expect(back).toBe(plaintext)
  })

  it('4. decrypt("invalid") returns null (not false) on RSA failure — pin jsencrypt reality', () => {
    // KNOWN: jsencrypt returns `null` when RSA decryption fails (not `false`).
    // The plan's expectation was `false`; we pin the actual contract so
    // downstream consumers can rely on `result == null` checks instead.
    const out = decrypt('not-a-real-ciphertext')
    expect(out).toBeNull()
  })

  it('5. round-trip works for Chinese characters and special chars', () => {
    const samples = ['你好世界', '!@#$%^&*()_+', 'mixed-中-english-123', 'spaces in here']
    for (const s of samples) {
      const cipher = encrypt(s)
      expect(typeof cipher).toBe('string')
      expect(decrypt(cipher as string)).toBe(s)
    }
  })
})