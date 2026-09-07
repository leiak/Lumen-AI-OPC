import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import useUserStore from '@/store/modules/user'
import { checkPermi, checkRole } from '@/utils/permission'

describe('checkPermi', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    setActivePinia(createPinia())
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleErrorSpy.mockRestore()
  })

  it('returns true when permission is held', () => {
    const store = useUserStore()
    store.permissions = ['sys:user:list', 'sys:user:add']
    expect(checkPermi(['sys:user:list'])).toBe(true)
  })

  it('returns false when permission is NOT held', () => {
    const store = useUserStore()
    store.permissions = ['sys:user:list']
    expect(checkPermi(['sys:user:add'])).toBe(false)
  })

  it('returns true via *:*:* wildcard', () => {
    const store = useUserStore()
    store.permissions = ['*:*:*']
    expect(checkPermi(['sys:anything'])).toBe(true)
  })

  it('returns true when at least one of the array matches', () => {
    const store = useUserStore()
    store.permissions = ['sys:user:list']
    expect(checkPermi(['sys:user:add', 'sys:user:list'])).toBe(true)
  })

  it('returns false and logs error for empty array', () => {
    expect(checkPermi([])).toBe(false)
    expect(consoleErrorSpy).toHaveBeenCalled()
  })

  it('returns false and logs error for null', () => {
    expect(checkPermi(null as any)).toBe(false)
    expect(consoleErrorSpy).toHaveBeenCalled()
  })

  it('returns false and logs error for undefined', () => {
    expect(checkPermi(undefined as any)).toBe(false)
    expect(consoleErrorSpy).toHaveBeenCalled()
  })
})

describe('checkRole', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    setActivePinia(createPinia())
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleErrorSpy.mockRestore()
  })

  it('returns true when role is held', () => {
    const store = useUserStore()
    store.roles = ['editor']
    expect(checkRole(['editor'])).toBe(true)
  })

  it('returns false when role is NOT held', () => {
    const store = useUserStore()
    store.roles = ['viewer']
    expect(checkRole(['admin'])).toBe(false)
  })

  it('returns true via "admin" wildcard', () => {
    const store = useUserStore()
    store.roles = ['admin']
    expect(checkRole(['editor', 'admin'])).toBe(true)
  })

  it('returns true when at least one of the array matches', () => {
    const store = useUserStore()
    store.roles = ['editor']
    expect(checkRole(['admin', 'editor'])).toBe(true)
  })

  it('returns false and logs error for empty array', () => {
    expect(checkRole([])).toBe(false)
    expect(consoleErrorSpy).toHaveBeenCalled()
  })

  it('returns false and logs error for null', () => {
    expect(checkRole(null as any)).toBe(false)
    expect(consoleErrorSpy).toHaveBeenCalled()
  })
})
