import { beforeEach, describe, expect, it } from 'vitest'
import useDictStore from '@/store/modules/dict'

describe('dict store', () => {
  beforeEach(() => {
    // setActivePinia(createPinia()) is wired in test/setup.ts beforeEach
  })

  it('initial state has empty dict array', () => {
    const store = useDictStore()
    expect(store.dict).toEqual([])
  })

  describe('setDict', () => {
    it('stores key/value pair', () => {
      const store = useDictStore()
      store.setDict('sys_user_sex', [{ value: '0', label: '男' }])
      expect(store.dict).toHaveLength(1)
      expect(store.dict[0].key).toBe('sys_user_sex')
      expect(store.dict[0].value).toEqual([{ value: '0', label: '男' }])
    })

    it('does NOT overwrite existing key (push semantics — known spec)', () => {
      const store = useDictStore()
      store.setDict('sys_user_sex', [{ value: '0', label: '男' }])
      store.setDict('sys_user_sex', [{ value: '1', label: '女' }])
      // Both entries coexist because setDict uses .push, not replace.
      expect(store.dict).toHaveLength(2)
      expect(store.dict[0].value).toEqual([{ value: '0', label: '男' }])
      expect(store.dict[1].value).toEqual([{ value: '1', label: '女' }])
    })

    it('skips empty string key', () => {
      const store = useDictStore()
      store.setDict('', [{ value: '0', label: 'x' }])
      expect(store.dict).toHaveLength(0)
    })

    it('skips null key', () => {
      const store = useDictStore()
      store.setDict(null as any, [{ value: '0', label: 'x' }])
      expect(store.dict).toHaveLength(0)
    })
  })

  describe('getDict', () => {
    it('returns the value for an existing key (first hit)', () => {
      const store = useDictStore()
      const val = [{ value: '1', label: 'foo' }]
      store.setDict('k', val)
      // Pinia wraps state in reactive proxies, so toEqual (not toBe).
      expect(store.getDict('k')).toEqual(val)
    })

    it('returns null for missing key', () => {
      const store = useDictStore()
      expect(store.getDict('nope')).toBeNull()
    })

    it('returns null for empty key (KNOWN BUG: && → ||, see W11.4 report)', () => {
      // The guard `if (_key == null && _key == "")` is always false,
      // so the code falls through to the loop, finds nothing, and returns null.
      // Pinning current behavior — fix is out-of-scope for W11.
      const store = useDictStore()
      expect(store.getDict('')).toBeNull()
    })

    it('returns null for null key (same known bug as above)', () => {
      const store = useDictStore()
      expect(store.getDict(null as any)).toBeNull()
    })
  })

  describe('removeDict', () => {
    it('removes existing entry and returns true', () => {
      const store = useDictStore()
      store.setDict('k', [{ value: '1', label: 'x' }])
      expect(store.removeDict('k')).toBe(true)
      expect(store.dict).toHaveLength(0)
    })

    it('returns false for missing key', () => {
      const store = useDictStore()
      expect(store.removeDict('nope')).toBe(false)
    })
  })

  describe('cleanDict', () => {
    it('clears the dict array', () => {
      const store = useDictStore()
      store.setDict('a', [])
      store.setDict('b', [])
      expect(store.dict).toHaveLength(2)
      store.cleanDict()
      expect(store.dict).toEqual([])
    })
  })

  describe('initDict', () => {
    it('is a no-op that can be called without side effects', () => {
      const store = useDictStore()
      store.setDict('a', [])
      store.initDict()
      // initDict doesn't touch state — only verifies no throw and no mutation
      expect(store.dict).toHaveLength(1)
    })
  })
})
