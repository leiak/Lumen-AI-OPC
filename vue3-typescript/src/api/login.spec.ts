// W19 — src/api/login.spec.ts. RuoYi built-in auth endpoints.
// 7 functions: login / register / refreshToken / getInfo /
// unlockScreen / logout / getCodeImg. All thin wrappers around @/utils/request.
//
// Pinned behaviors:
// - login() sends headers={isToken:false, repeatSubmit:false} to skip token
//   injection and the repeat-submit guard for the login endpoint.
// - login() data envelope is {username, password, code, uuid} in that order.
// - register() sends headers={isToken:false} but does NOT set repeatSubmit.
// - getCodeImg() overrides the default 10000ms timeout to 20000ms.
// - getCodeImg() also sets isToken:false (verification image doesn't need auth).
// - logout() uses HTTP DELETE.
// - unlockScreen() wraps password in a {password} envelope (not bare string).
// - Every function returns the request() promise (1:1 passthrough).
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  login,
  register,
  refreshToken,
  getInfo,
  unlockScreen,
  logout,
  getCodeImg,
} from '@/api/login'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200 } as any)
})

describe('api/login — login()', () => {
  it('1. login(u,p,c,u) -> POST /auth/login with isToken:false + repeatSubmit:false headers', () => {
    login('admin', 'pwd123', 'abcd', 'uuid-1')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/login',
      headers: { isToken: false, repeatSubmit: false },
      method: 'post',
      data: { username: 'admin', password: 'pwd123', code: 'abcd', uuid: 'uuid-1' },
    })
  })

  it('2. login() preserves argument order in data envelope (username, password, code, uuid)', () => {
    // Source explicitly destructures in this order; the resulting object
    // literal preserves insertion order. Pin the field order so a future
    // refactor that reorders keys is flagged.
    login('a', 'b', 'c', 'd')
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg.data)).toEqual(['username', 'password', 'code', 'uuid'])
  })

  it('3. login() passes empty strings verbatim (no input validation in API layer)', () => {
    login('', '', '', '')
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({
        data: { username: '', password: '', code: '', uuid: '' },
      }),
    )
  })

  it('4. login() return value is the request() promise', () => {
    const sentinel = Symbol('login-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(login('u', 'p', 'c', 'uuid')).toBe(sentinel)
  })
})

describe('api/login — register()', () => {
  it('5. register({userName,password,code,uuid}) -> POST /auth/register with isToken:false', () => {
    register({ userName: 'alice', password: 'pw', code: '1234', uuid: 'uuid-r' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/register',
      headers: { isToken: false },
      method: 'post',
      data: { userName: 'alice', password: 'pw', code: '1234', uuid: 'uuid-r' },
    })
  })

  it('6. register() does NOT set repeatSubmit:false (login-only flag)', () => {
    register({ userName: 'bob' })
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.headers).toEqual({ isToken: false })
    expect('repeatSubmit' in arg.headers).toBe(false)
  })

  it('7. register() with empty object {} passes {} verbatim', () => {
    register({})
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ data: {} }),
    )
  })

  it('8. register() return value is the request() promise', () => {
    const sentinel = Symbol('register-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(register({})).toBe(sentinel)
  })
})

describe('api/login — refreshToken()', () => {
  it('9. refreshToken() -> POST /auth/refresh (no body, no special headers)', () => {
    refreshToken()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/refresh',
      method: 'post',
    })
  })

  it('10. refreshToken() call config has exactly 2 keys: url + method (no data, no headers)', () => {
    refreshToken()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('11. refreshToken() return value is the request() promise', () => {
    const sentinel = Symbol('refresh-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(refreshToken()).toBe(sentinel)
  })
})

describe('api/login — getInfo()', () => {
  it('12. getInfo() -> GET /system/user/getInfo (no body, no headers)', () => {
    getInfo()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/user/getInfo',
      method: 'get',
    })
  })

  it('13. getInfo() call config has exactly 2 keys: url + method', () => {
    getInfo()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('14. getInfo() return value is the request() promise', () => {
    const sentinel = Symbol('info-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getInfo()).toBe(sentinel)
  })
})

describe('api/login — unlockScreen()', () => {
  it('15. unlockScreen("secret") -> POST /auth/unlockscreen with data={password}', () => {
    unlockScreen('secret')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/unlockscreen',
      method: 'post',
      data: { password: 'secret' },
    })
  })

  it('16. unlockScreen("") passes empty password verbatim', () => {
    unlockScreen('')
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ data: { password: '' } }),
    )
  })

  it('17. unlockScreen() does NOT set isToken or repeatSubmit headers (screen-lock is authed)', () => {
    unlockScreen('pwd')
    const arg = requestMock.mock.calls[0][0] as any
    expect('headers' in arg).toBe(false)
  })

  it('18. unlockScreen() return value is the request() promise', () => {
    const sentinel = Symbol('unlock-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(unlockScreen('p')).toBe(sentinel)
  })
})

describe('api/login — logout()', () => {
  it('19. logout() -> DELETE /auth/logout (no body, no headers)', () => {
    logout()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/logout',
      method: 'delete',
    })
  })

  it('20. logout() call config has exactly 2 keys: url + method', () => {
    logout()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('21. logout() return value is the request() promise', () => {
    const sentinel = Symbol('logout-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(logout()).toBe(sentinel)
  })
})

describe('api/login — getCodeImg()', () => {
  it('22. getCodeImg() -> GET /code with isToken:false + timeout:20000', () => {
    getCodeImg()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/code',
      headers: { isToken: false },
      method: 'get',
      timeout: 20000,
    })
  })

  it('23. getCodeImg() overrides default 10000ms timeout to 20000ms (verification is slower)', () => {
    // Source's default axios timeout is 10000ms; getCodeImg passes 20000
    // because CAPTCHA image generation can take longer on slow connections.
    getCodeImg()
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.timeout).toBe(20000)
  })

  it('24. getCodeImg() sets isToken:false (verification image is unauthenticated)', () => {
    getCodeImg()
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.headers).toEqual({ isToken: false })
  })

  it('25. getCodeImg() return value is the request() promise', () => {
    const sentinel = Symbol('code-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(getCodeImg()).toBe(sentinel)
  })
})

describe('api/login — module behavior', () => {
  it('26. all 7 exports are functions', () => {
    expect(typeof login).toBe('function')
    expect(typeof register).toBe('function')
    expect(typeof refreshToken).toBe('function')
    expect(typeof getInfo).toBe('function')
    expect(typeof unlockScreen).toBe('function')
    expect(typeof logout).toBe('function')
    expect(typeof getCodeImg).toBe('function')
  })

  it('27. each export calls request exactly once per invocation', () => {
    login('u', 'p', 'c', 'uuid')
    register({})
    refreshToken()
    getInfo()
    unlockScreen('pw')
    logout()
    getCodeImg()
    expect(requestMock).toHaveBeenCalledTimes(7)
  })

  it('28. all 7 endpoints have distinct URLs (no accidental cross-routing)', () => {
    login('u', 'p', 'c', 'uuid')
    register({})
    refreshToken()
    getInfo()
    unlockScreen('pw')
    logout()
    getCodeImg()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(7)
    expect(urls.sort()).toEqual([
      '/auth/login',
      '/auth/logout',
      '/auth/refresh',
      '/auth/register',
      '/auth/unlockscreen',
      '/code',
      '/system/user/getInfo',
    ])
  })
})
