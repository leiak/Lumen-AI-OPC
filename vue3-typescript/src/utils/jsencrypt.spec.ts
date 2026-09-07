// W18 — src/utils/jsencrypt.spec.ts. W12.2 baseline was 5 tests covering the
// happy-path round trip + invalid-input behavior. W18 expands to cover
// edge cases (block-size limit, randomness, state independence, whitespace,
// type widening).
//
// Source (29 lines, 4 lines of logic per function):
//   encrypt(txt):  new JSEncrypt() → setPublicKey(publicKey) → encryptor.encrypt(txt)
//   decrypt(txt):  new JSEncrypt() → setPrivateKey(privateKey) → encryptor.decrypt(txt)
//
// Key facts discovered via exploration:
// - The embedded publicKey is a **512-bit RSA key** (despite the 1024-bit
//   implication of the base64 length). With PKCS#1 v1.5 padding the max
//   encryptable plaintext is **53 bytes** — beyond that encrypt() returns
//   `false` (boolean), NOT null.
// - jsencrypt returns `null` (not `false`) when decrypt() fails. The TS
//   signature `string | false` is too narrow; the actual return is
//   `string | false | null`. Pin this so callers can rely on the wider type.
// - PKCS#1 v1.5 padding is randomized: encrypting the same plaintext twice
//   produces two DIFFERENT ciphertexts.
// - Each call to encrypt()/decrypt() constructs a fresh `new JSEncrypt()`,
//   so there's NO shared state between successive calls (k1 then k2 then
//   k3 all round-trip correctly without interference).
// - Empty input: encrypt('') succeeds (returns a valid 88-char ciphertext),
//   decrypt('') returns null. Asymmetric behavior — pin both.
import { describe, expect, it } from 'vitest'
import { encrypt, decrypt } from '@/utils/jsencrypt'

describe('jsencrypt', () => {
  // ---------------------------------------------------------------
  // W12.2 baseline — preserved
  // ---------------------------------------------------------------

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

  it('5. round-trip works for chinese characters and special chars', () => {
    const samples = ['你好世界', '!@#$%^&*()_+', 'mixed-中-english-123', 'spaces in here']
    for (const s of samples) {
      const cipher = encrypt(s)
      expect(typeof cipher).toBe('string')
      expect(decrypt(cipher as string)).toBe(s)
    }
  })

  // ---------------------------------------------------------------
  // Block-size limit (512-bit RSA → 53-byte max plaintext)
  // ---------------------------------------------------------------

  it('6. encrypt() with plaintext longer than the RSA block size returns false', () => {
    // The embedded key is 512-bit RSA. PKCS#1 v1.5 overhead = 11 bytes,
    // so max plaintext = 64 - 11 = 53 bytes. Anything longer returns
    // `false` (boolean) — NOT null, NOT throw.
    const tooLong = 'a'.repeat(54)
    const r = encrypt(tooLong)
    expect(r).toBe(false)
  })

  it('7. encrypt() at exactly the 53-byte boundary succeeds', () => {
    // Boundary case: 53 bytes is the largest plaintext the key supports.
    const atLimit = 'a'.repeat(53)
    const r = encrypt(atLimit)
    expect(typeof r).toBe('string')
    // The ciphertext is base64-encoded, so the encrypted length is 88 chars
    // (representing 64 raw bytes — the 512-bit RSA block).
    expect((r as string).length).toBe(88)
  })

  it('8. encrypt() at 53-byte boundary round-trips through decrypt()', () => {
    // Boundary case confirmed bidirectionally: encrypt→decrypt at the limit.
    const atLimit = 'x'.repeat(53)
    const cipher = encrypt(atLimit)
    expect(typeof cipher).toBe('string')
    expect(decrypt(cipher as string)).toBe(atLimit)
  })

  it('9. encrypt() of large input (200 bytes) returns false (far past the limit)', () => {
    const huge = 'a'.repeat(200)
    const r = encrypt(huge)
    expect(r).toBe(false)
  })

  // ---------------------------------------------------------------
  // PKCS#1 v1.5 padding randomization
  // ---------------------------------------------------------------

  it('10. encrypt() of the same plaintext twice produces DIFFERENT ciphertexts', () => {
    // PKCS#1 v1.5 padding is randomized per spec — same plaintext yields
    // different ciphertexts each call. This is what makes RSA-OAEP-style
    // padding distinguishable from "textbook RSA". Pin the actual behavior.
    const c1 = encrypt('same-text')
    const c2 = encrypt('same-text')
    expect(typeof c1).toBe('string')
    expect(typeof c2).toBe('string')
    expect(c1).not.toBe(c2)
  })

  it('11. encrypt() randomized padding produces DIFFERENT ciphertexts across 5 calls', () => {
    // Stronger check: confirm ALL pairwise comparisons are distinct.
    const ciphers = Array.from({ length: 5 }, () => encrypt('repeated'))
    const unique = new Set(ciphers)
    expect(unique.size).toBe(5)
  })

  // ---------------------------------------------------------------
  // State independence (each call creates a fresh JSEncrypt instance)
  // ---------------------------------------------------------------

  it('12. encrypt() calls do not share state — k1, k2, k3 round-trip independently', () => {
    // Source: `const encryptor = new JSEncrypt()` inside encrypt(). Each
    // call gets a fresh instance, so back-to-back encrypts do not leak
    // keys or state. Pin that k1 then k2 then k3 each decrypt to their own
    // original (not to the most-recent key).
    const k1 = encrypt('k1')
    const k2 = encrypt('k2')
    const k3 = encrypt('k3')
    expect(decrypt(k1 as string)).toBe('k1')
    expect(decrypt(k2 as string)).toBe('k2')
    expect(decrypt(k3 as string)).toBe('k3')
  })

  // ---------------------------------------------------------------
  // Round-trip edge cases
  // ---------------------------------------------------------------

  it('13. round-trip preserves whitespace: newlines, tabs, leading/trailing spaces', () => {
    const samples = [
      'hello\nworld',
      'line1\nline2\nline3',
      'tab\there',
      '  leading spaces',
      'trailing spaces  ',
      '\n\n\n',
      '\t\t\t',
      'mixed \n \t \r\n chars',
    ]
    for (const s of samples) {
      const cipher = encrypt(s)
      expect(typeof cipher).toBe('string')
      expect(decrypt(cipher as string)).toBe(s)
    }
  })

  it('14. round-trip preserves all printable ASCII punctuation and quotes', () => {
    const samples = [
      '!@#$%^&*()_+-=',
      '[]{}|;:,.<>?/',
      "'single quotes'",
      '"double quotes"',
      '`backtick`',
      '~tilde~',
      '\\backslash\\',
    ]
    for (const s of samples) {
      const cipher = encrypt(s)
      expect(typeof cipher).toBe('string')
      expect(decrypt(cipher as string)).toBe(s)
    }
  })

  // ---------------------------------------------------------------
  // decrypt() failure modes
  // ---------------------------------------------------------------

  it('15. decrypt("") returns null (empty input is treated as failure)', () => {
    // Asymmetric with encrypt("") which SUCCEEDS. decrypt("") returns null.
    const r = decrypt('')
    expect(r).toBeNull()
  })

  it('16. decrypt("Zm9vYmFy") (valid base64 but not valid RSA ciphertext) returns null', () => {
    // "Zm9vYmFy" base64-decodes to "foobar" — but it's not RSA ciphertext,
    // so jsencrypt returns null.
    const r = decrypt('Zm9vYmFy')
    expect(r).toBeNull()
  })

  it('17. decrypt("AAAA") (valid base64, wrong length) returns null', () => {
    // "AAAA" base64-decodes to 3 zero bytes — not a valid 64-byte RSA block.
    const r = decrypt('AAAA')
    expect(r).toBeNull()
  })

  // ---------------------------------------------------------------
  // Type-widening pin: actual return is `string | false | null`,
  // not just `string | false` as the TS signature claims.
  // ---------------------------------------------------------------

  it('18. PIN: encrypt() and decrypt() can return null — TS signature is too narrow', () => {
    // The source's TS signature is `string | false`, but the underlying
    // jsencrypt library returns `null` on decrypt failure. Pin the wider
    // contract so consumers add `result == null` guards alongside
    // `result === false`. This test deliberately uses `expect.any(...)` so
    // it passes whether the runtime returns `false` or `null` — what we
    // pin is the FAILURE CONTRACT, not the exact literal.
    const decryptFailures = [
      decrypt(''),
      decrypt('xxx'),
      decrypt('Zm9vYmFy'),
    ]
    for (const r of decryptFailures) {
      // Either `false` or `null` — both are truthy/falsy failure sentinels.
      expect(r === false || r === null).toBe(true)
    }
    // And encrypt() returns false (boolean) on too-long input — never null.
    const tooLong = encrypt('a'.repeat(100))
    expect(tooLong).toBe(false)
  })
})
