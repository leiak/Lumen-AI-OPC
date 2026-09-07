import { beforeEach, describe, expect, it, vi } from 'vitest'

// Mock js-cookie BEFORE importing auth (which imports Cookies at module level).
// Use inline factory to avoid top-level variable hoisting pitfall.
vi.mock('js-cookie', () => {
  const mock = {
    get: vi.fn(),
    set: vi.fn(),
    remove: vi.fn(),
  }
  return { default: mock }
})

import Cookies from 'js-cookie'
import {
  getExpiresIn,
  getToken,
  removeExpiresIn,
  removeToken,
  setExpiresIn,
  setToken,
} from '@/utils/auth'

const cookiesMock = Cookies as unknown as {
  get: ReturnType<typeof vi.fn>
  set: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
}

describe('auth utils', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getToken / setToken / removeToken', () => {
    it('getToken delegates to Cookies.get with key "Admin-Token"', () => {
      cookiesMock.get.mockReturnValueOnce('abc123')
      expect(getToken()).toBe('abc123')
      expect(cookiesMock.get).toHaveBeenCalledWith('Admin-Token')
    })

    it('setToken delegates to Cookies.set with key "Admin-Token" and value', () => {
      cookiesMock.set.mockReturnValueOnce('abc123')
      const ret = setToken('abc123')
      expect(cookiesMock.set).toHaveBeenCalledWith('Admin-Token', 'abc123')
      expect(ret).toBe('abc123')
    })

    it('removeToken delegates to Cookies.remove with key "Admin-Token"', () => {
      removeToken()
      expect(cookiesMock.remove).toHaveBeenCalledWith('Admin-Token')
    })
  })

  describe('Expires-In helpers', () => {
    it('getExpiresIn returns -1 fallback when cookie missing (known spec)', () => {
      cookiesMock.get.mockReturnValueOnce(undefined as any)
      expect(getExpiresIn()).toBe(-1)
      expect(cookiesMock.get).toHaveBeenCalledWith('Admin-Expires-In')
    })

    it('getExpiresIn returns cookie value when present', () => {
      cookiesMock.get.mockReturnValueOnce('7200')
      expect(getExpiresIn()).toBe('7200')
    })

    it('setExpiresIn(number) writes time.toString() to cookie', () => {
      setExpiresIn(7200)
      expect(cookiesMock.set).toHaveBeenCalledWith('Admin-Expires-In', '7200')
    })

    it('setExpiresIn(string) writes string directly', () => {
      setExpiresIn('7200')
      expect(cookiesMock.set).toHaveBeenCalledWith('Admin-Expires-In', '7200')
    })

    it('removeExpiresIn delegates to Cookies.remove with key "Admin-Expires-In"', () => {
      removeExpiresIn()
      expect(cookiesMock.remove).toHaveBeenCalledWith('Admin-Expires-In')
    })
  })
})
