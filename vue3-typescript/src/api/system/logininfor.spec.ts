// W23 — src/api/system/logininfor.spec.ts. RuoYi built-in login info (登录日志).
// 4 endpoints: list / delLogininfor / unlockLogininfor / cleanLogininfor.
//
// Pinned behaviors:
// - `list` is exported as a NAMED function `list` (not `listLogininfor`)
//   unlike other modules in this directory. Document the asymmetric naming
//   so callers don't expect `listLogininfor`.
// - unlockLogininfor(userName) embeds username in URL path without encoding.
// - delLogininfor accepts `number | number[]` and concatenates the same as
//   dict/data and config del (Array.toString → comma-join).
// - cleanLogininfor() uses HTTP DELETE — non-idempotent semantics via DELETE.
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

import request from '@/utils/request'
import {
  list,
  delLogininfor,
  unlockLogininfor,
  cleanLogininfor,
} from '@/api/system/logininfor'

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockResolvedValue({ code: 200, data: {} } as any)
})

describe('api/system/logininfor — list()', () => {
  it('1. list({userName,ipaddr,status}) -> GET /system/logininfor/list with params', () => {
    list({ userName: 'admin', ipaddr: '127.0.0.1', status: '0' } as any)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/logininfor/list',
      method: 'get',
      params: { userName: 'admin', ipaddr: '127.0.0.1', status: '0' },
    })
  })

  it('2. list() uses `params` not `data` (GET envelope via query string)', () => {
    list({ foo: 'bar' } as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.params).toEqual({ foo: 'bar' })
    expect('data' in arg).toBe(false)
  })

  it('3. list() uses bare export name (not listLogininfor) — pin naming asymmetry', () => {
    // Source: `export function list(query: ...)` — bare name, unlike
    // other modules in this directory which use prefix (listConfig,
    // listDept, listPost, etc.). Document the asymmetry.
    list({} as any)
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/logininfor/list')
  })

  it('4. list() return value is the request() promise', () => {
    const sentinel = Symbol('list-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(list({} as any)).toBe(sentinel)
  })
})

describe('api/system/logininfor — delLogininfor()', () => {
  it('5. delLogininfor(100) -> DELETE /system/logininfor/100 (single id)', () => {
    delLogininfor(100)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/logininfor/100',
      method: 'delete',
    })
  })

  it('6. delLogininfor([1,2,3]) -> DELETE /system/logininfor/1,2,3 (array comma-join)', () => {
    delLogininfor([1, 2, 3])
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/logininfor/1,2,3',
      method: 'delete',
    })
  })

  it('7. delLogininfor() call config has exactly 2 keys: url + method', () => {
    delLogininfor(1)
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('8. delLogininfor() return value is the request() promise', () => {
    const sentinel = Symbol('delLogininfor-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(delLogininfor(1)).toBe(sentinel)
  })
})

describe('api/system/logininfor — unlockLogininfor()', () => {
  it('9. unlockLogininfor("admin") -> GET /system/logininfor/unlock/admin', () => {
    unlockLogininfor('admin')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/logininfor/unlock/admin',
      method: 'get',
    })
  })

  it('10. unlockLogininfor() embeds userName in path via concat (no encodeURIComponent)', () => {
    // Pin: username with special chars is appended verbatim. The unlock
    // endpoint is authed admin action — usernames are safe identifiers.
    unlockLogininfor('a b')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.url).toBe('/system/logininfor/unlock/a b')
  })

  it('11. unlockLogininfor() uses GET (not POST/DELETE) — idempotent unlock action', () => {
    unlockLogininfor('x')
    const arg = requestMock.mock.calls[0][0] as any
    expect(arg.method).toBe('get')
  })

  it('12. unlockLogininfor() return value is the request() promise', () => {
    const sentinel = Symbol('unlock-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(unlockLogininfor('u')).toBe(sentinel)
  })
})

describe('api/system/logininfor — cleanLogininfor()', () => {
  it('13. cleanLogininfor() -> DELETE /system/logininfor/clean (no body, no params)', () => {
    cleanLogininfor()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/system/logininfor/clean',
      method: 'delete',
    })
  })

  it('14. cleanLogininfor() call config has exactly 2 keys: url + method', () => {
    cleanLogininfor()
    const arg = requestMock.mock.calls[0][0] as any
    expect(Object.keys(arg).sort()).toEqual(['method', 'url'])
  })

  it('15. cleanLogininfor() return value is the request() promise', () => {
    const sentinel = Symbol('clean-promise')
    requestMock.mockReturnValue(sentinel as any)
    expect(cleanLogininfor()).toBe(sentinel)
  })
})

describe('api/system/logininfor — module behavior', () => {
  it('16. all 4 exports are functions', () => {
    expect(typeof list).toBe('function')
    expect(typeof delLogininfor).toBe('function')
    expect(typeof unlockLogininfor).toBe('function')
    expect(typeof cleanLogininfor).toBe('function')
  })

  it('17. each export calls request exactly once per invocation', () => {
    list({} as any)
    delLogininfor(1)
    unlockLogininfor('u')
    cleanLogininfor()
    expect(requestMock).toHaveBeenCalledTimes(4)
  })

  it('18. all 4 endpoints have distinct URLs (no cross-routing)', () => {
    list({} as any)
    delLogininfor(1)
    unlockLogininfor('u')
    cleanLogininfor()
    const urls = requestMock.mock.calls.map((c) => (c[0] as any).url)
    expect(new Set(urls).size).toBe(4)
    expect(urls.sort()).toEqual([
      '/system/logininfor/1',
      '/system/logininfor/clean',
      '/system/logininfor/list',
      '/system/logininfor/unlock/u',
    ])
  })

  it('19. methods span GET/DELETE (login logs are read-only + clear)', () => {
    list({} as any)
    delLogininfor(1)
    unlockLogininfor('u')
    cleanLogininfor()
    const methods = requestMock.mock.calls.map((c) => (c[0] as any).method)
    expect(methods.sort()).toEqual(['delete', 'delete', 'get', 'get'])
  })
})