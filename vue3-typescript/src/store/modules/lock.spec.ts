import { beforeEach, describe, expect, it } from 'vitest'
import { useLockStore } from '@/store/modules/lock'

describe('lock store', () => {
  beforeEach(() => {
    // Reset localStorage between tests
    localStorage.clear()
  })

  it('initial isLock is false and lockPath defaults to /index', () => {
    const store = useLockStore()
    expect(store.isLock).toBe(false)
    expect(store.lockPath).toBe('/index')
  })

  it('lockScreen sets isLock=true and writes to localStorage', () => {
    const store = useLockStore()
    store.lockScreen('/dashboard')
    expect(store.isLock).toBe(true)
    expect(store.lockPath).toBe('/dashboard')
    expect(localStorage.getItem('screen-lock')).toBe('true')
    expect(localStorage.getItem('screen-lock-path')).toBe('/dashboard')
  })

  it('lockScreen with empty path falls back to /index', () => {
    const store = useLockStore()
    store.lockScreen('')
    expect(store.lockPath).toBe('/index')
    expect(localStorage.getItem('screen-lock-path')).toBe('/index')
  })

  it('unlockScreen resets isLock and lockPath', () => {
    const store = useLockStore()
    store.lockScreen('/x')
    store.unlockScreen()
    expect(store.isLock).toBe(false)
    expect(store.lockPath).toBe('/index')
    expect(localStorage.getItem('screen-lock')).toBe('false')
    expect(localStorage.getItem('screen-lock-path')).toBe('/index')
  })

  it('initializes from localStorage when screen-lock=true', () => {
    localStorage.setItem('screen-lock', 'true')
    localStorage.setItem('screen-lock-path', '/locked-page')
    const store = useLockStore()
    expect(store.isLock).toBe(true)
    expect(store.lockPath).toBe('/locked-page')
  })

  it('localStorage values are stored as strings "true"/"false"', () => {
    const store = useLockStore()
    store.lockScreen('/a')
    expect(typeof localStorage.getItem('screen-lock')).toBe('string')
    expect(localStorage.getItem('screen-lock')).toBe('true')
    store.unlockScreen()
    expect(localStorage.getItem('screen-lock')).toBe('false')
  })

  it('toggles back and forth idempotently', () => {
    const store = useLockStore()
    store.lockScreen('/p1')
    store.unlockScreen()
    store.lockScreen('/p2')
    expect(store.isLock).toBe(true)
    expect(store.lockPath).toBe('/p2')
  })
})
