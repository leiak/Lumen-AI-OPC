// W12.2 — passwordRule spec. chrtype-driven validation + reactive updates.
//
// NOTE: passwordRule.ts reads cache.session.get('pwrChrtype') ONCE at module
// load and stashes it in a module-level ref. The ref is NOT reactive to
// later sessionStorage writes — it captures the value at import time. To
// test different chrtype values we must reset the module + re-import after
// priming cache.session. The `setChrTypeAndReload()` helper does this.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import cache from '@/plugins/cache'

async function setChrTypeAndReload(chrtype: string) {
  cache.session.set('pwrChrtype', chrtype)
  vi.resetModules()
  return await import('@/utils/passwordRule')
}

describe('passwordRule', () => {
  beforeEach(() => {
    // Reset before each test so setChrTypeAndReload() picks up fresh state.
    cache.session.remove('pwrChrtype')
  })

  // -----------------------------------------------------------------
  // API surface
  // -----------------------------------------------------------------
  it('1. usePasswordRule() returns {pwdChrType, pwdValidator, infoPwdValidator, pwdPromptValidator, registerPwdValidator}', async () => {
    const { usePasswordRule } = await setChrTypeAndReload('0')
    const rule = usePasswordRule()
    expect(Object.keys(rule).sort()).toEqual(
      ['infoPwdValidator', 'pwdChrType', 'pwdPromptValidator', 'pwdValidator', 'registerPwdValidator'].sort(),
    )
    expect(Array.isArray(rule.pwdValidator.value)).toBe(true)
    expect(Array.isArray(rule.infoPwdValidator.value)).toBe(true)
    expect(Array.isArray(rule.registerPwdValidator.value)).toBe(true)
    expect(typeof rule.pwdPromptValidator).toBe('function')
    expect(rule.pwdChrType.value).toBe('0')
  })

  // -----------------------------------------------------------------
  // chrtype 0 — any chars, forbidden: < > " ' | \
  // -----------------------------------------------------------------
  describe('chrtype 0 (any chars, forbidden <>"\'|\\)', () => {
    it('2. accepts "abc123!"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('0')
      const { pwdValidator } = usePasswordRule()
      const pattern = pwdValidator.value[2].pattern
      expect(pattern.test('abc123!')).toBe(true)
    })

    it('3. rejects "a<b" (contains forbidden char)', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('0')
      const { pwdValidator } = usePasswordRule()
      const pattern = pwdValidator.value[2].pattern
      expect(pattern.test('a<b')).toBe(false)
    })
  })

  // -----------------------------------------------------------------
  // chrtype 1 — digits only
  // -----------------------------------------------------------------
  describe('chrtype 1 (digits only)', () => {
    it('4. accepts "12345"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('1')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('12345')).toBe(true)
    })

    it('5. rejects "12a"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('1')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('12a')).toBe(false)
    })
  })

  // -----------------------------------------------------------------
  // chrtype 2 — letters only
  // -----------------------------------------------------------------
  describe('chrtype 2 (letters only)', () => {
    it('6. accepts "abc"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('2')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('abc')).toBe(true)
    })

    it('7. rejects "ab1"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('2')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('ab1')).toBe(false)
    })
  })

  // -----------------------------------------------------------------
  // chrtype 3 — letters + digits (both required)
  // -----------------------------------------------------------------
  describe('chrtype 3 (letters AND digits, both required)', () => {
    it('8. accepts "a1"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('3')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('a1')).toBe(true)
    })

    it('9. rejects "abc" (missing digits)', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('3')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('abc')).toBe(false)
    })
  })

  // -----------------------------------------------------------------
  // chrtype 4 — letters + digits + specials (all required)
  // -----------------------------------------------------------------
  describe('chrtype 4 (letters, digits AND specials, all required)', () => {
    it('10. accepts "Aa1!"', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('4')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('Aa1!')).toBe(true)
    })

    it('11. rejects "Aa1" (missing special)', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('4')
      const { pwdValidator } = usePasswordRule()
      expect(pwdValidator.value[2].pattern.test('Aa1')).toBe(false)
    })
  })

  // -----------------------------------------------------------------
  // registerPwdValidator — KNOWN bug: hardcodes chrtype=0
  // -----------------------------------------------------------------
  it('12. registerPwdValidator uses chrtype=0 even when pwdChrType is set to 2', async () => {
    const { usePasswordRule } = await setChrTypeAndReload('2')
    const { registerPwdValidator } = usePasswordRule()
    // With chrtype 2 active, a digit-only "12345" should FAIL pwdValidator,
    // but registerPwdValidator's pattern (hardcoded to chrtype 0's regex)
    // accepts anything that doesn't include forbidden chars.
    const registerPattern = registerPwdValidator.value[2].pattern
    expect(registerPattern.test('12345')).toBe(true) // <- register form accepts digits
    // This confirms registerPwdValidator IGNORES pwdChrType (documented bug).
  })

  // -----------------------------------------------------------------
  // pwdPromptValidator — returns string on error, undefined on success
  // -----------------------------------------------------------------
  describe('pwdPromptValidator', () => {
    it('13. rejects short password (< 6 chars)', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('0')
      const { pwdPromptValidator } = usePasswordRule()
      expect(pwdPromptValidator('abc12')).toBe('密码长度必须介于 6 和 20 之间')
    })

    it('14. rejects long password (> 20 chars)', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('0')
      const { pwdPromptValidator } = usePasswordRule()
      expect(pwdPromptValidator('a'.repeat(21))).toBe('密码长度必须介于 6 和 20 之间')
    })

    it('15. rejects forbidden chars (chrtype=0 default)', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('0')
      const { pwdPromptValidator } = usePasswordRule()
      expect(pwdPromptValidator('abc<def')).toBe('密码不能包含非法字符：< > " \' \\ |')
    })

    it('16. accepts a valid 6-20 char password with no forbidden chars', async () => {
      const { usePasswordRule } = await setChrTypeAndReload('0')
      const { pwdPromptValidator } = usePasswordRule()
      expect(pwdPromptValidator('validPass1!')).toBeUndefined()
    })
  })

  // -----------------------------------------------------------------
  // Reactive update — KNOWN LIMITATION
  // -----------------------------------------------------------------
  it('17. pwdChrType ref captures chrtype at module load; later sessionStorage writes do NOT update it', async () => {
    // The module-level `pwdChrType = ref(cache.session.get('pwrChrtype') || '0')`
    // snapshots the value at import time. This is a known design choice that
    // means: changing cache.session['pwrChrtype'] after import is a no-op
    // (you must reload the module to pick up a new chrtype). We pin this
    // behavior so a future "make it reactive" change is flagged.
    cache.session.set('pwrChrtype', '1')
    vi.resetModules()
    const { usePasswordRule } = await import('@/utils/passwordRule')
    const { pwdChrType } = usePasswordRule()
    expect(pwdChrType.value).toBe('1')

    // Mutate sessionStorage and verify the ref does NOT observe it.
    cache.session.set('pwrChrtype', '4')
    expect(pwdChrType.value).toBe('1') // unchanged — not reactive
  })
})